package com.memberconnect.backend.service;
import com.memberconnect.backend.dto.NicValidationResponseDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.RemittanceMasterAccountDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MembershipEligibilityConfig;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.EducationalDistrictZoneRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Field;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class MemberApplicationService {
    private static final Pattern OLD_NIC_PATTERN = Pattern.compile("^\\d{9}[VX]$");
    private static final Pattern NEW_NIC_PATTERN = Pattern.compile("^\\d{12}$");

    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EducationalDistrictZoneRepository educationalDistrictZoneRepository;

    // Read-only use of the Termination module's data, purely to detect a rejoining
    // applicant and show their previous membership details. Nothing here modifies it.
    @Autowired
    private TerminationRequestRepository terminationRequestRepository;

    @Autowired
    private RemittanceMasterService remittanceMasterService;

    @Autowired
    private MembershipEligibilityService membershipEligibilityService;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private AuditService auditService;

    public MemberApplicationDTO saveMemberApplication(MemberApplicationDTO memberApplicationDTO) {
        // Same "Inactive rights" rule as updateStatus()/updatePartial() — the create form
        // exposes a Status Override field that a District Office user could otherwise use
        // to smuggle a new application straight to Inactive.
        if (memberApplicationDTO.getStatus() == ApplicationStatus.INACTIVE && !currentUserHasInactiveRights()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have rights to create an application with Inactive status."
            );
        }
        validateNicForPersistence(memberApplicationDTO.getNicNumber(), null);
        validateDistrictZoneForPersistence(
                memberApplicationDTO.getEducationalDistrict(),
                memberApplicationDTO.getEducationalZone()
        );
        // Spec: age is validated on Save (not only Submit), against a configurable limit.
        validateApplicantAge(memberApplicationDTO.getDateOfBirth());
        applyRemittanceMaster(memberApplicationDTO);

        Member_Application application = modelMapper.map(memberApplicationDTO, Member_Application.class);
        application.setApplicationID(generateApplicationId());
        // Spec MR01: "Once saved the status will change to 'New'". Without this an
        // application saved with no explicit status was persisted with status = null,
        // which the registration list then rendered as the placeholder "PENDING" - a
        // status no backend path ever writes and the spec does not define.
        if (application.getStatus() == null) {
            application.setStatus(ApplicationStatus.NEW);
        }
        // Flag rejoins so the application is marked for the rest of its life. Uses the
        // same terminated-member lookup the NIC 'Validate' button surfaces.
        application.setRejoinFlag(findTerminatedMemberByNic(memberApplicationDTO.getNicNumber()).isPresent());
        Member_Application saved = memberApplicationRepository.save(application);
        auditService.record(AuditService.MODULE_APPLICATION, saved.getId(),
                "Application Created", null, saved.getApplicationID(), null);
        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    /**
     * Age must fall inside the configured eligibility limits, derived from the entered
     * Date of Birth against the current system date.
     */
    private void validateApplicantAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return; // Mandatory-field checks handle a missing DOB at Submit time.
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date of Birth cannot be in the future.");
        }
        MembershipEligibilityConfig config = membershipEligibilityService.getConfig();
        int age = Period.between(dateOfBirth, today).getYears();
        if (age < config.getMinimumAge() || age > config.getMaximumAge()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                    "The applicant's age (%d) is outside the eligible limit of %d to %d years.",
                    age, config.getMinimumAge(), config.getMaximumAge()));
        }
    }

    /**
     * Applies the Remittance Master rules to the incoming amounts: fixed amounts are
     * forced (the field is read-only on the form, so we never trust a supplied value),
     * and user-entered amounts must meet any configured minimum.
     */
    private void applyRemittanceMaster(MemberApplicationDTO dto) {
        for (RemittanceMasterAccountDTO account : remittanceMasterService.getActive()) {
            BigDecimal supplied = readRemittanceAmount(dto, account.getAccountCode());

            if (account.getFixedAmount() != null) {
                writeRemittanceAmount(dto, account.getAccountCode(), account.getFixedAmount());
                continue;
            }
            if (supplied != null
                    && account.getMinimumAmount() != null
                    && supplied.compareTo(account.getMinimumAmount()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                        "%s must be at least %s.", account.getAccountName(), account.getMinimumAmount().toPlainString()));
            }
            if (supplied != null && supplied.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        account.getAccountName() + " cannot be negative.");
            }
        }
    }

    private BigDecimal readRemittanceAmount(MemberApplicationDTO dto, RemittanceAccountCode code) {
        return switch (code) {
            case SHARE -> dto.getShareAccountAmount();
            case SPECIAL_DEPOSIT -> dto.getSpecialDepositAmount();
            case FIXED_DEPOSIT -> dto.getFixedDepositAmount();
            case SCHOLARSHIP_DEATH_DONATION_PENSION -> dto.getScholarshipDeathDonationPensionAmount();
        };
    }

    private void writeRemittanceAmount(MemberApplicationDTO dto, RemittanceAccountCode code, BigDecimal value) {
        switch (code) {
            case SHARE -> dto.setShareAccountAmount(value);
            case SPECIAL_DEPOSIT -> dto.setSpecialDepositAmount(value);
            case FIXED_DEPOSIT -> dto.setFixedDepositAmount(value);
            case SCHOLARSHIP_DEATH_DONATION_PENSION -> dto.setScholarshipDeathDonationPensionAmount(value);
        }
    }

    // Generates a sequential, year-scoped ID such as "APP-2026-001", matching the
    // convention already used elsewhere in the app (e.g. RetirementService.generateRequestNo()).
    private String generateApplicationId() {
        int year = LocalDate.now().getYear();
        String prefix = "APP-" + year + "-";

        return memberApplicationRepository
                .findFirstByApplicationIDStartingWithOrderByApplicationIDDesc(prefix)
                .map(last -> {
                    String lastId = last.getApplicationID();
                    int lastSeq = Integer.parseInt(lastId.substring(lastId.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    public List<MemberApplicationDTO>getAllMemberApplications(){
        List<Member_Application>memberApplications = memberApplicationRepository.findAll();
        return modelMapper.map(memberApplications, new TypeToken<List<MemberApplicationDTO>>() {}.getType());
    }

    /**
     * Server-side search for the New Member Registration List. Previously the whole
     * application table was shipped to the browser and filtered there, which does not
     * scale; this mirrors MemberService.searchMembers().
     *
     * Applications already converted into Members (APPROVED) are excluded — the spec
     * states this screen only shows registrations not yet approved as Members.
     */
    public List<MemberApplicationDTO> searchApplications(
            String query,
            List<ApplicationStatus> statuses,
            List<String> locations,
            LocalDate receivedFrom,
            LocalDate receivedTo,
            String sortBy,
            String sortDirection) {

        final String q = (query == null || query.isBlank()) ? null : query.toLowerCase().trim();
        final boolean filterByStatus = statuses != null && !statuses.isEmpty();
        final boolean filterByLocation = locations != null && !locations.isEmpty();

        List<Member_Application> results = memberApplicationRepository.findAll().stream()
                .filter(app -> app.getStatus() != ApplicationStatus.APPROVED)
                .filter(app -> !filterByStatus || statuses.contains(app.getStatus()))
                .filter(app -> !filterByLocation
                        || (app.getSubmissionLocation() != null
                            && locations.stream().anyMatch(loc -> loc.equalsIgnoreCase(app.getSubmissionLocation()))))
                .filter(app -> matchesReceivedPeriod(app.getApplicationDate(), receivedFrom, receivedTo))
                .filter(app -> q == null || matchesKeyword(app, q))
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        results.sort(applicationComparator(sortBy, sortDirection));

        return results.stream()
                .map(app -> modelMapper.map(app, MemberApplicationDTO.class))
                .toList();
    }

    /** Search across the applicant's names and NIC, as the spec specifies. */
    private boolean matchesKeyword(Member_Application app, String q) {
        return containsIgnoreCase(app.getFullName(), q)
                || containsIgnoreCase(app.getNameAsInPayroll(), q)
                || containsIgnoreCase(app.getNameWithInitials(), q)
                || containsIgnoreCase(app.getNicNumber(), q);
    }

    private boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    /**
     * applicationDate is stored as a String; parse defensively so a malformed or empty
     * value simply fails the date filter instead of breaking the whole search.
     */
    private boolean matchesReceivedPeriod(String applicationDate, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        LocalDate parsed = parseApplicationDate(applicationDate);
        if (parsed == null) {
            return false;
        }
        return (from == null || !parsed.isBefore(from)) && (to == null || !parsed.isAfter(to));
    }

    private LocalDate parseApplicationDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private Comparator<Member_Application> applicationComparator(String sortBy, String sortDirection) {
        Comparator<Member_Application> comparator = switch (sortBy == null ? "" : sortBy) {
            case "status" -> Comparator.comparing(
                    app -> app.getStatus() == null ? "" : app.getStatus().name(),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "district" -> Comparator.comparing(
                    Member_Application::getSubmissionLocation,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "zone" -> Comparator.comparing(
                    Member_Application::getEducationalZone,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            // Default is applied date; fall back to the raw string when unparseable.
            default -> Comparator.comparing(
                    app -> parseApplicationDate(app.getApplicationDate()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "desc".equalsIgnoreCase(sortDirection) ? comparator.reversed() : comparator;
    }

    public MemberApplicationDTO updateMemberApplication(Long id, MemberApplicationDTO dto) {
        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        if (dto.getNicNumber() != null) {
            validateNicForPersistence(dto.getNicNumber(), id);
        }
        validateDistrictZoneOnUpdate(existing, dto);
        // Editing must be held to the same eligibility/remittance rules as creating,
        // otherwise an out-of-range value can be introduced after the initial save.
        if (dto.getDateOfBirth() != null) {
            validateApplicantAge(dto.getDateOfBirth());
        }
        applyRemittanceMaster(dto);
        applyNonNullFields(existing, dto);
        Member_Application updated = memberApplicationRepository.save(existing);
        return modelMapper.map(updated, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO updatePartial(Long id, MemberApplicationDTO dto) {

        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        if (dto.getNicNumber() != null) {
            validateNicForPersistence(dto.getNicNumber(), id);
        }
        validateDistrictZoneOnUpdate(existing, dto);
        if (dto.getDateOfBirth() != null) {
            validateApplicantAge(dto.getDateOfBirth());
        }
        applyRemittanceMaster(dto);
        applyNonNullFields(existing, dto);

        Member_Application saved = memberApplicationRepository.save(existing);

        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    private void applyNonNullFields(Member_Application existing, MemberApplicationDTO dto) {
        if (dto.getApplicationDate() != null) existing.setApplicationDate(dto.getApplicationDate());
        if (dto.getStatus() != null) {
            // Same "Inactive rights" rule as updateStatus() — this partial-update path is
            // also reachable from the board approval flow, so it needs the same guard.
            boolean touchesInactive = dto.getStatus() == ApplicationStatus.INACTIVE
                    || existing.getStatus() == ApplicationStatus.INACTIVE;
            if (touchesInactive && !currentUserHasInactiveRights()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have rights to set this application's status to Inactive."
                );
            }
            // MR04's transition table - stops the Status Override field moving an
            // application into a board-owned state (Added to Board Approval List,
            // Rejected, Approved) that no board record backs.
            ApplicationStatusPolicy.checkTransition(existing.getStatus(), dto.getStatus());
            existing.setStatus(dto.getStatus());
            // The pre-list status is only of use while the board still holds the record.
            // Once MR10 decides it, drop the memo so a later listing captures fresh.
            if (dto.getStatus() != ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
                existing.setStatusBeforeBoardList(null);
            }
        }
        if (dto.getSubmissionLocation() != null) existing.setSubmissionLocation(dto.getSubmissionLocation());

        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getFullName() != null) existing.setFullName(dto.getFullName());
        if (dto.getNameAsInPayroll() != null) existing.setNameAsInPayroll(dto.getNameAsInPayroll());
        if (dto.getNameWithInitials() != null) existing.setNameWithInitials(dto.getNameWithInitials());
        if (dto.getNicNumber() != null) existing.setNicNumber(dto.getNicNumber());

        if (dto.getDateOfBirth() != null) existing.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) existing.setGender(dto.getGender());
        if (dto.getPreferredLanguage() != null) existing.setPreferredLanguage(dto.getPreferredLanguage());

        if (dto.getPermanentPrivateAddress() != null) existing.setPermanentPrivateAddress(dto.getPermanentPrivateAddress());
        if (dto.getWorkingLocationType() != null) existing.setWorkingLocationType(dto.getWorkingLocationType());
        if (dto.getDesignation() != null) existing.setDesignation(dto.getDesignation());
        if (dto.getNatureOfOccupation() != null) existing.setNatureOfOccupation(dto.getNatureOfOccupation());
        if (dto.getEducationalDistrict() != null) existing.setEducationalDistrict(dto.getEducationalDistrict());
        if (dto.getEducationalZone() != null) existing.setEducationalZone(dto.getEducationalZone());
        if (dto.getWorkingLocation() != null) existing.setWorkingLocation(dto.getWorkingLocation());
        if (dto.getWorkingLocationAddress() != null) existing.setWorkingLocationAddress(dto.getWorkingLocationAddress());
        if (dto.getComputerNoInPayslip() != null) existing.setComputerNoInPayslip(dto.getComputerNoInPayslip());
        if (dto.getSalaryPayingOffice() != null) existing.setSalaryPayingOffice(dto.getSalaryPayingOffice());
        if (dto.getOfficeTelephone() != null) existing.setOfficeTelephone(dto.getOfficeTelephone());
        if (dto.getPrivateTelephone() != null) existing.setPrivateTelephone(dto.getPrivateTelephone());
        if (dto.getMobileNumber() != null) existing.setMobileNumber(dto.getMobileNumber());
        if (dto.getEmailAddress() != null) existing.setEmailAddress(dto.getEmailAddress());

        if (dto.getShareAccountAmount() != null) existing.setShareAccountAmount(dto.getShareAccountAmount());
        if (dto.getSpecialDepositAmount() != null) existing.setSpecialDepositAmount(dto.getSpecialDepositAmount());
        if (dto.getFixedDepositAmount() != null) existing.setFixedDepositAmount(dto.getFixedDepositAmount());
        if (dto.getScholarshipDeathDonationPensionAmount() != null)
            existing.setScholarshipDeathDonationPensionAmount(dto.getScholarshipDeathDonationPensionAmount());

        if (dto.getNomineeFullName() != null) existing.setNomineeFullName(dto.getNomineeFullName());
        if (dto.getNomineeRelationship() != null) existing.setNomineeRelationship(dto.getNomineeRelationship());
        if (dto.getIdentification() != null) existing.setIdentification(dto.getIdentification());
        if (dto.getIdentificationNumber() != null) existing.setIdentificationNumber(dto.getIdentificationNumber());
        if (dto.getIdentificationDetails() != null) existing.setIdentificationDetails(dto.getIdentificationDetails());
        if (dto.getNomineeAddress() != null) existing.setNomineeAddress(dto.getNomineeAddress());
        String boardDecisionReason = readBoardDecisionReason(dto);
        if (boardDecisionReason != null) existing.setBoardDecisionReason(boardDecisionReason);
    }

    private String readBoardDecisionReason(MemberApplicationDTO dto) {
        try {
            Field field = MemberApplicationDTO.class.getDeclaredField("boardDecisionReason");
            field.setAccessible(true);
            return (String) field.get(dto);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    public String deleteMemberApplication(Long id) {

        if (!memberApplicationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        memberApplicationRepository.deleteById(id);

        return "Application deleted successfully";
    }

    public MemberApplicationDTO getApplicationById(Long id) {

        Member_Application application = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"
                ));

        return modelMapper.map(application, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO getApplicationByNic(String nic) {

        Member_Application application = memberApplicationRepository.findByNicNumber(nic)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"
                ));

        return modelMapper.map(application, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO updateStatus(Long id, ApplicationStatus status) {

        Member_Application app = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"));

        // Setting/removing INACTIVE requires the "Inactive rights" the spec reserves for
        // Head Office / Board Secretary / Super Admin — District Office can submit and edit
        // applications but cannot deactivate them. (The controller allows District Office
        // through for the ordinary New -> Submitted for Approval transition.)
        boolean touchesInactive = status == ApplicationStatus.INACTIVE || app.getStatus() == ApplicationStatus.INACTIVE;
        if (touchesInactive && !currentUserHasInactiveRights()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have rights to set this application's status to Inactive."
            );
        }

        ApplicationStatus before = app.getStatus();
        // Allows MR01's Submit (New -> Submitted for Approval) and MR04's overrides,
        // and refuses everything the board flow owns.
        ApplicationStatusPolicy.checkTransition(before, status);
        app.setStatus(status);
        Member_Application saved = memberApplicationRepository.save(app);
        auditService.record(AuditService.MODULE_APPLICATION, saved.getId(), "Status Changed",
                before == null ? null : before.name(),
                status == null ? null : status.name(), null);

        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    private boolean currentUserHasInactiveRights() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return false;
        }
        Role role = user.getRole();
        return role == Role.HEAD_OFFICE || role == Role.BOARD_SECRETARY || role == Role.SUPER_ADMIN;
    }

    public NicValidationResponseDTO validateNic(String nicNumber, Long excludeApplicationId) {
        String normalizedInput = normalizeNic(nicNumber);
        if (!isValidNic(normalizedInput)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid NIC number. Use old format (123456789V/X) or new format (200012345678)."
            );
        }

        boolean duplicateExists = hasDuplicateNic(normalizedInput, excludeApplicationId);
        if (duplicateExists) {
            return new NicValidationResponseDTO(
                    true,
                    true,
                    "A Member/ Application with the same NIC Number exists."
            );
        }

        // Not a blocking duplicate — but if it matches a previously terminated member the
        // user must be shown that member's details and the application flagged as a Rejoin.
        Optional<Member> terminated = findTerminatedMemberByNic(nicNumber);
        if (terminated.isPresent()) {
            return buildRejoinResponse(terminated.get());
        }

        return new NicValidationResponseDTO(
                true,
                false,
                "NIC is valid and available."
        );
    }

    /**
     * Finds a TERMINATED member with this NIC, comparing old/new NIC formats the same
     * way the duplicate check does. Terminated members deliberately do not block a new
     * application — they turn it into a Rejoin.
     */
    private Optional<Member> findTerminatedMemberByNic(String nicNumber) {
        String normalizedInput = normalizeNic(nicNumber);
        if (!isValidNic(normalizedInput)) {
            return Optional.empty();
        }
        Set<String> inputKeys = buildComparableNicKeys(normalizedInput);

        return memberRepository.findAllByNicIsNotNull().stream()
                .filter(member -> member.getStatus() == MemberStatus.TERMINATED)
                .filter(member -> {
                    String existing = normalizeNic(member.getNic());
                    return isValidNic(existing)
                            && !Collections.disjoint(buildComparableNicKeys(existing), inputKeys);
                })
                .findFirst();
    }

    /** Assembles the previous-membership details shown in the Rejoin popup. */
    private NicValidationResponseDTO buildRejoinResponse(Member member) {
        NicValidationResponseDTO response = new NicValidationResponseDTO(
                true, false,
                "This NIC belongs to a previously terminated Member. "
                        + "You may continue — the application will be flagged as a Rejoin."
        );
        response.setRejoin(true);
        response.setPreviousMemberId(member.getMemberId());
        response.setPreviousMemberName(
                member.getNameWithInitials() != null ? member.getNameWithInitials() : member.getFullName());
        response.setMembershipStartDate(member.getMembershipStartDate());

        // Termination details live in the Termination module; read the most recent
        // approved request for this member. Absent one, the popup still shows identity.
        terminationRequestRepository.findByMemberId(member.getMemberId()).stream()
                .filter(request -> request.getStatus() == TerminationRequestStatus.APPROVED)
                .max(Comparator.comparing(TerminationRequest::getEffectiveDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .ifPresent(request -> {
                    response.setTerminatedDate(request.getEffectiveDate());
                    response.setTerminationReason(request.getTerminationReason());
                    response.setTerminationComments(request.getComment());
                });

        return response;
    }

    private void validateNicForPersistence(String nicNumber, Long excludeApplicationId) {
        NicValidationResponseDTO validation = validateNic(nicNumber, excludeApplicationId);
        if (validation.isDuplicate()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validation.getMessage());
        }
    }

    private void validateDistrictZoneOnUpdate(Member_Application existing, MemberApplicationDTO dto) {
        String district = dto.getEducationalDistrict() != null
                ? dto.getEducationalDistrict()
                : existing.getEducationalDistrict();
        String zone = dto.getEducationalZone() != null
                ? dto.getEducationalZone()
                : existing.getEducationalZone();

        validateDistrictZoneForPersistence(district, zone);
    }

    private void validateDistrictZoneForPersistence(String district, String zone) {
        if (district == null || district.isBlank() || zone == null || zone.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Educational district and zone are required."
            );
        }

        boolean exists = educationalDistrictZoneRepository.existsByDistrictIgnoreCaseAndZoneIgnoreCase(
                district.trim(),
                zone.trim()
        );

        if (!exists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid educational district and zone combination."
            );
        }
    }

    private boolean hasDuplicateNic(String normalizedInput, Long excludeApplicationId) {
        Set<String> inputKeys = buildComparableNicKeys(normalizedInput);

        boolean duplicateInApplications = memberApplicationRepository.findAllByNicNumberIsNotNull().stream()
                .filter(application -> excludeApplicationId == null || !Objects.equals(application.getId(), excludeApplicationId))
                .map(Member_Application::getNicNumber)
                .filter(Objects::nonNull)
                .map(this::normalizeNic)
                .filter(this::isValidNic)
                .map(this::buildComparableNicKeys)
                .anyMatch(existingKeys -> !Collections.disjoint(existingKeys, inputKeys));

        if (duplicateInApplications) {
            return true;
        }

        return memberRepository.findAllByNicIsNotNull().stream()
                // A terminated member must NOT block a new application — the spec allows
                // the user to proceed, flagging it as a Rejoin instead. Those are detected
                // separately by findTerminatedMemberByNic().
                .filter(member -> member.getStatus() != MemberStatus.TERMINATED)
                .map(Member::getNic)
                .filter(Objects::nonNull)
                .map(this::normalizeNic)
                .filter(this::isValidNic)
                .map(this::buildComparableNicKeys)
                .anyMatch(existingKeys -> !Collections.disjoint(existingKeys, inputKeys));
    }

    private String normalizeNic(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private boolean isValidNic(String normalized) {
        return OLD_NIC_PATTERN.matcher(normalized).matches() || NEW_NIC_PATTERN.matcher(normalized).matches();
    }

    private Set<String> buildComparableNicKeys(String normalized) {
        Set<String> keys = new HashSet<>();

        if (OLD_NIC_PATTERN.matcher(normalized).matches()) {
            String digits = normalized.substring(0, 9);
            keys.add(digits + "V");
            keys.add(digits + "X");
            keys.add("19" + digits);
            return keys;
        }

        if (NEW_NIC_PATTERN.matcher(normalized).matches()) {
            keys.add(normalized);
            if (normalized.startsWith("19")) {
                String oldDigits = normalized.substring(2);
                keys.add(oldDigits + "V");
                keys.add(oldDigits + "X");
            }
        }

        return keys;
    }
}