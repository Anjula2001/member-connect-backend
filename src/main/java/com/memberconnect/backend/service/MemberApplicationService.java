package com.memberconnect.backend.service;
import com.memberconnect.backend.dto.NicValidationResponseDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.EducationalDistrictZoneRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

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

    @Autowired
    private ModelMapper modelMapper;

    public MemberApplicationDTO saveMemberApplication(MemberApplicationDTO memberApplicationDTO) {
        validateNicForPersistence(memberApplicationDTO.getNicNumber(), null);
        validateDistrictZoneForPersistence(
                memberApplicationDTO.getEducationalDistrict(),
                memberApplicationDTO.getEducationalZone()
        );
        Member_Application application = modelMapper.map(memberApplicationDTO, Member_Application.class);
        application.setApplicationID("APP-" + System.currentTimeMillis());
        Member_Application saved = memberApplicationRepository.save(application);
        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    public List<MemberApplicationDTO>getAllMemberApplications(){
        List<Member_Application>memberApplications = memberApplicationRepository.findAll();
        return modelMapper.map(memberApplications, new TypeToken<List<MemberApplicationDTO>>() {}.getType());
    }

    public MemberApplicationDTO updateMemberApplication(Long id, MemberApplicationDTO dto) {
        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        if (dto.getNicNumber() != null) {
            validateNicForPersistence(dto.getNicNumber(), id);
        }
        validateDistrictZoneOnUpdate(existing, dto);
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
        applyNonNullFields(existing, dto);

        Member_Application saved = memberApplicationRepository.save(existing);

        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    private void applyNonNullFields(Member_Application existing, MemberApplicationDTO dto) {
        if (dto.getApplicationDate() != null) existing.setApplicationDate(dto.getApplicationDate());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());

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

        app.setStatus(status);

        return modelMapper.map(memberApplicationRepository.save(app), MemberApplicationDTO.class);
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

        return new NicValidationResponseDTO(
                true,
                false,
                "NIC is valid and available."
        );
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