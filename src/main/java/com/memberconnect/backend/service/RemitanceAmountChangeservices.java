package com.memberconnect.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.ProfileChangeDecisionDTO;
import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.dto.RemittanceChangeLineDTO;
import com.memberconnect.backend.dto.RemittanceMasterAccountDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberRemittance;
import com.memberconnect.backend.model.RemittanceAmountChange;
import com.memberconnect.backend.model.RemittanceChangeRequestLine;
import com.memberconnect.backend.repository.MemberRemittanceRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RemittanceAmountChangeRepo;

/**
 * Remittance Amount Change Requests (Requirement 02, MMC14-MMC17).
 *
 * The request is a parent plus one line per editable account. It used to be a single
 * row holding one newRemittanceAmount - stored as a String - and one account type, so
 * a member changing three accounts produced one row whose amount was the total and
 * whose type was whichever account came first. Nothing survived that could be approved
 * into the member's record.
 *
 * The member side is {@link MemberRemittance}, one row per account code, which is what
 * an approval writes to.
 */
@Service
@Transactional
public class RemitanceAmountChangeservices {

    @Autowired
    private RemittanceAmountChangeRepo remittanceAmountChangeRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RequestNumberGenerator requestNumberGenerator;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberRemittanceRepository memberRemittanceRepository;

    @Autowired
    private RemittanceMasterService remittanceMasterService;

    @Autowired
    private ProfileChangeStatusPolicy statusPolicy;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotificationService notificationService;

    // ── Read ─────────────────────────────────────────────────────────────────

    public List<RemittanceAmountChangeDTO> getRemitanceRequests() {
        return remittanceAmountChangeRepo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public RemittanceAmountChangeDTO remitanceRequestgetBhyID(Integer id) {
        return remittanceAmountChangeRepo.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * The screen's starting point for a new request (MMC14): every editable account the
     * member holds, with the current amount in both columns, so the New Value section
     * opens pre-populated with the existing values as the SRS requires.
     */
    public RemittanceAmountChangeDTO newRequestFor(String memberId) {
        Member member = requireActiveMember(memberId);

        RemittanceAmountChangeDTO dto = new RemittanceAmountChangeDTO();
        dto.setMemberId(member.getMemberId());
        dto.setSubmissionLocation(locationOf(member));
        applyMemberDetails(dto, member);

        Map<RemittanceAccountCode, BigDecimal> current = currentAmounts(member.getId());

        for (RemittanceMasterAccountDTO account : editableAccounts()) {
            BigDecimal amount = current.getOrDefault(account.getAccountCode(), BigDecimal.ZERO);
            RemittanceChangeLineDTO line = new RemittanceChangeLineDTO();
            line.setAccountCode(account.getAccountCode());
            line.setAccountName(account.getAccountName());
            line.setMinimumAmount(account.getMinimumAmount());
            line.setMandatory(account.getMandatory());
            line.setOldAmount(amount);
            line.setNewAmount(amount);
            dto.getLines().add(line);
        }

        return dto;
    }

    private RemittanceAmountChangeDTO toDto(RemittanceAmountChange entity) {
        RemittanceAmountChangeDTO dto = modelMapper.map(entity, RemittanceAmountChangeDTO.class);

        // ModelMapper cannot see through the lazy parent reference on each line, so the
        // rows are mapped by hand and decorated from the master.
        Map<RemittanceAccountCode, RemittanceMasterAccountDTO> master = masterByCode();
        List<RemittanceChangeLineDTO> lines = new ArrayList<>();
        for (RemittanceChangeRequestLine line : entity.getLines()) {
            RemittanceMasterAccountDTO account = master.get(line.getAccountCode());
            RemittanceChangeLineDTO row = new RemittanceChangeLineDTO();
            row.setAccountCode(line.getAccountCode());
            row.setAccountName(account != null ? account.getAccountName() : line.getAccountCode().name());
            row.setMinimumAmount(account != null ? account.getMinimumAmount() : null);
            row.setMandatory(account != null ? account.getMandatory() : null);
            row.setOldAmount(line.getOldAmount());
            row.setNewAmount(line.getNewAmount());
            lines.add(row);
        }
        dto.setLines(lines);

        if (entity.getMemberId() != null) {
            memberRepository.findByMemberId(entity.getMemberId())
                    .ifPresent(member -> applyMemberDetails(dto, member));
        }

        return dto;
    }

    private void applyMemberDetails(RemittanceAmountChangeDTO dto, Member member) {
        dto.setMemberFullName(member.getFullName());
        dto.setMemberNameWithInitials(member.getNameWithInitials());
        dto.setMemberNic(member.getNic());
    }

    // ── Create / update ──────────────────────────────────────────────────────

    public RemittanceAmountChangeDTO saveRemittanceRequest(RemittanceAmountChangeDTO dto) {
        RemittanceAmountChange entity = new RemittanceAmountChange();
        entity.setMemberId(dto.getMemberId());
        entity.setSubmissionLocation(dto.getSubmissionLocation());
        entity.setRequestNo(nextRequestNo());
        entity.setRequestedDate(LocalDate.now());
        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);

        Member member = requireActiveMember(dto.getMemberId());
        if (entity.getSubmissionLocation() == null || entity.getSubmissionLocation().isBlank()) {
            entity.setSubmissionLocation(locationOf(member));
        }

        applyLines(entity, member, dto.getLines());

        return toDto(remittanceAmountChangeRepo.save(entity));
    }

    /**
     * Edits a request and puts it back into the queue.
     *
     * MMC14 does not allow editing a submitted record; in-place editing is enabled at
     * the client's direction, so an edited request returns to Submitted for Approval
     * and loses any earlier decision rather than keeping a stamp that no longer
     * describes its contents.
     */
    public RemittanceAmountChangeDTO updateRemittanceRequest(Integer id, RemittanceAmountChangeDTO dto) {
        RemittanceAmountChange existing = remittanceAmountChangeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Remittance change request not found: " + id));

        ApplicationStatus previousStatus = existing.getStatus();
        Member member = requireActiveMember(existing.getMemberId());

        applyLines(existing, member, dto.getLines());

        existing.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        existing.setRejectReason(null);
        existing.setProcessedBy(null);
        existing.setProcessedAt(null);

        if (previousStatus != ApplicationStatus.SUBMITTED_FOR_APPROVAL) {
            auditService.recordStatusChange(
                    AuditService.MODULE_REMITTANCE_CHANGE,
                    existing.getMemberId(),
                    existing.getRequestNo(),
                    previousStatus,
                    ApplicationStatus.SUBMITTED_FOR_APPROVAL
            );
        }

        return toDto(remittanceAmountChangeRepo.save(existing));
    }

    /**
     * Rebuilds the request's lines, taking a fresh snapshot of what the member holds
     * and validating each requested amount against its configured minimum.
     *
     * MMC14 puts the minimum-amount check on Submit, and it is enforced here rather
     * than only on the screen: the rule is about what may be stored, not about what is
     * convenient to type.
     */
    private void applyLines(
            RemittanceAmountChange entity,
            Member member,
            List<RemittanceChangeLineDTO> submitted
    ) {
        if (submitted == null || submitted.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At least one remittance account amount is required.");
        }

        Map<RemittanceAccountCode, RemittanceMasterAccountDTO> master = masterByCode();
        Map<RemittanceAccountCode, BigDecimal> current = currentAmounts(member.getId());

        List<RemittanceChangeRequestLine> rebuilt = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (RemittanceChangeLineDTO row : submitted) {
            RemittanceAccountCode code = row.getAccountCode();
            if (code == null) {
                problems.add("An account code is required on every line.");
                continue;
            }

            RemittanceMasterAccountDTO account = master.get(code);
            if (account == null || Boolean.FALSE.equals(account.getActive())) {
                problems.add(code + " is not an active remittance account.");
                continue;
            }
            if (account.getFixedAmount() != null) {
                // A fixed account is not the member's to change.
                problems.add(account.getAccountName() + " is a fixed amount and cannot be changed.");
                continue;
            }

            BigDecimal amount = row.getNewAmount();
            if (amount == null) {
                problems.add(account.getAccountName() + " needs an amount.");
                continue;
            }
            if (amount.signum() < 0) {
                problems.add(account.getAccountName() + " cannot be negative.");
                continue;
            }
            if (account.getMinimumAmount() != null && amount.compareTo(account.getMinimumAmount()) < 0) {
                problems.add(account.getAccountName() + " must be at least "
                        + account.getMinimumAmount().toPlainString() + ".");
                continue;
            }

            RemittanceChangeRequestLine line = new RemittanceChangeRequestLine();
            line.setAccountCode(code);
            line.setOldAmount(current.get(code));
            line.setNewAmount(amount);
            rebuilt.add(line);
        }

        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }

        entity.setLines(rebuilt);
    }

    // ── Approve / Reject (MMC17) ─────────────────────────────────────────────

    /**
     * Approves or rejects, in one transaction that also writes the member's remittance.
     *
     * Mirrors the Basic Profile decision endpoint: set the status, apply the approved
     * amounts, write the audit row and notify - all together, so a failure part-way
     * cannot leave the member changed with the request still pending.
     */
    public RemittanceAmountChangeDTO decide(Integer id, ProfileChangeDecisionDTO decision) {
        if (decision == null || decision.getDecision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A decision is required.");
        }

        RemittanceAmountChange request = remittanceAmountChangeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Remittance change request not found: " + id));

        statusPolicy.assertDecidable(request.getStatus());

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The member for this request could not be found: " + request.getMemberId()));

        boolean approving = decision.getDecision() == ProfileChangeDecisionDTO.Decision.APPROVE;

        if (!approving) {
            String reason = statusPolicy.requireRejectReason(decision.getRejectReason());

            request.setStatus(ApplicationStatus.REJECTED);
            request.setRejectReason(reason);
            stampProcessed(request);
            RemittanceAmountChange saved = remittanceAmountChangeRepo.save(request);

            // MMC17: on reject the member's amounts are deliberately left untouched.
            auditService.record(
                    AuditService.MODULE_REMITTANCE_CHANGE,
                    member.getId(),
                    "REJECTED",
                    null,
                    null,
                    "Request " + saved.getRequestNo() + " rejected: " + reason
            );
            notificationService.sendProfileChangeRejected(
                    member, ProfileChangeType.REMITTANCE, saved.getRequestNo(), reason);

            return toDto(saved);
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        for (RemittanceChangeRequestLine line : request.getLines()) {
            MemberRemittance row = memberRemittanceRepository
                    .findByMemberIdAndAccountCode(member.getId(), line.getAccountCode())
                    .orElseGet(() -> {
                        MemberRemittance created = new MemberRemittance();
                        created.setMember(member);
                        created.setAccountCode(line.getAccountCode());
                        return created;
                    });

            before.put(line.getAccountCode().name(),
                    row.getAmount() == null ? "0" : row.getAmount().toPlainString());

            row.setAmount(line.getNewAmount());
            row.setEffectiveFrom(LocalDate.now());
            row.setUpdatedAt(LocalDateTime.now());
            memberRemittanceRepository.save(row);

            after.put(line.getAccountCode().name(), line.getNewAmount().toPlainString());
        }

        request.setStatus(ApplicationStatus.APPROVED);
        request.setRejectReason(null);
        stampProcessed(request);
        RemittanceAmountChange saved = remittanceAmountChangeRepo.save(request);

        auditService.recordFieldChanges(
                AuditService.MODULE_REMITTANCE_CHANGE,
                member.getId(),
                "APPROVED",
                before,
                after,
                "Request " + saved.getRequestNo() + " approved"
        );
        notificationService.sendProfileChangeApproved(
                member, ProfileChangeType.REMITTANCE, saved.getRequestNo());

        return toDto(saved);
    }

    /** MMC16 View Mode: Inactive only, and only with Inactive rights. */
    public RemittanceAmountChangeDTO updateStatus(Integer id, ApplicationStatus target) {
        RemittanceAmountChange request = remittanceAmountChangeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Remittance change request not found: " + id));

        ApplicationStatus previous = request.getStatus();
        statusPolicy.assertManualStatusChange(previous, target);

        request.setStatus(target);
        RemittanceAmountChange saved = remittanceAmountChangeRepo.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_REMITTANCE_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                previous,
                target
        );

        return toDto(saved);
    }

    public String DeleteRemittanceRequest(@NonNull Integer id) {
        RemittanceAmountChange existing = remittanceAmountChangeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Remittance change request not found: " + id));

        auditService.recordStatusChange(
                AuditService.MODULE_REMITTANCE_CHANGE,
                existing.getMemberId(),
                existing.getRequestNo(),
                existing.getStatus(),
                null
        );

        remittanceAmountChangeRepo.deleteById(id);
        return "Deleted successfully";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String nextRequestNo() {
        String prefix = requestNumberGenerator.prefixFor(ProfileChangeType.REMITTANCE);
        return requestNumberGenerator.next(
                ProfileChangeType.REMITTANCE,
                remittanceAmountChangeRepo.findFirstByRequestNoStartingWithOrderByRequestNoDesc(prefix)
                        .map(RemittanceAmountChange::getRequestNo)
        );
    }

    private void stampProcessed(RemittanceAmountChange request) {
        request.setProcessedBy(statusPolicy.currentUsername());
        request.setProcessedAt(LocalDateTime.now());
    }

    /**
     * The member, provided the membership is active. MMC14 gates the entry on it, and
     * checking here means the API cannot be used to route around the screen.
     */
    private Member requireActiveMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A membership number is required to raise a remittance change request.");
        }

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No member found with membership number " + memberId
                                + ". Raise the request from the member's profile."));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Remittance changes can only be requested for an active member. "
                            + memberId + " is " + member.getStatus() + ".");
        }

        return member;
    }

    private String locationOf(Member member) {
        return member.getSubmissionLocation() != null
                ? member.getSubmissionLocation()
                : member.getEducationalDistrict();
    }

    private Map<RemittanceAccountCode, BigDecimal> currentAmounts(Long memberDbId) {
        Map<RemittanceAccountCode, BigDecimal> amounts = new LinkedHashMap<>();
        memberRemittanceRepository.findByMemberIdOrderByAccountCodeAsc(memberDbId)
                .forEach(row -> amounts.put(row.getAccountCode(), row.getAmount()));
        return amounts;
    }

    private Map<RemittanceAccountCode, RemittanceMasterAccountDTO> masterByCode() {
        Map<RemittanceAccountCode, RemittanceMasterAccountDTO> byCode = new LinkedHashMap<>();
        remittanceMasterService.getAll().forEach(a -> byCode.put(a.getAccountCode(), a));
        return byCode;
    }

    /** MMC14: only the accounts a member may actually change are offered. */
    private List<RemittanceMasterAccountDTO> editableAccounts() {
        return remittanceMasterService.getActive().stream()
                .filter(a -> a.getFixedAmount() == null)
                .toList();
    }
}
