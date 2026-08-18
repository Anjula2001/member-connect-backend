package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.CauseOfDeathDTO;
import com.memberconnect.backend.dto.MemberDeathDocumentDTO;
import com.memberconnect.backend.dto.MemberDeathMinorDisbursementDTO;
import com.memberconnect.backend.dto.MemberDeathRecordDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.enums.MemberDeathRecordStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.CauseOfDeath;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.LoanObligation;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathDocument;
import com.memberconnect.backend.model.MemberDeathMinorAccount;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.CauseOfDeathRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberDeathDocumentRepository;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@SuppressWarnings("null")
public class MemberDeathRecordService {

    private static final Set<String> MANDATORY_DOCUMENT_TYPES = Set.of("DEATH_CERTIFICATE");
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("DEATH_CERTIFICATE", "OTHER");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemberDeathRecordRepository recordRepository;
    private final MemberDeathDocumentRepository documentRepository;
    private final MemberRepository memberRepository;
    private final CauseOfDeathRepository causeOfDeathRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;
    private final S3Service s3Service;

    public MemberDeathRecordService(
            MemberDeathRecordRepository recordRepository,
            MemberDeathDocumentRepository documentRepository,
            MemberRepository memberRepository,
            CauseOfDeathRepository causeOfDeathRepository,
            MinorSavingsAccountRepository minorSavingsAccountRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository,
            S3Service s3Service
    ) {
        this.recordRepository = recordRepository;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
        this.causeOfDeathRepository = causeOfDeathRepository;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.s3Service = s3Service;
    }

    public List<CauseOfDeathDTO> getCauseOfDeathOptions() {
        return causeOfDeathRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapCauseToDto)
                .toList();
    }

    public MemberRetirementValidationDTO validateMemberForDeathRecord(String memberId) {
        List<Loan> ownLoans = loanRepository.findByMemberId(memberId);
        List<LoanObligation> obligations = obligationRepository.findByMemberId(memberId);

        boolean hasOutstandingLoans = !ownLoans.isEmpty();
        boolean hasLoanObligations = !obligations.isEmpty();
        double totalOutstandingBalance = ownLoans.stream()
                .mapToDouble(Loan::getBalance)
                .sum();

        MemberRetirementValidationDTO dto = new MemberRetirementValidationDTO();
        dto.setHasOutstandingLoans(hasOutstandingLoans);
        dto.setHasLoanObligations(hasLoanObligations);
        dto.setTotalOutstandingLoanBalance(totalOutstandingBalance);
        dto.setCanSubmit(!hasOutstandingLoans && !hasLoanObligations);
        dto.setMessage(buildValidationMessage(hasOutstandingLoans, hasLoanObligations));
        return dto;
    }

    public List<MemberDeathRecordDTO> searchRequests(
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        return recordRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .filter(dto -> matchesStatuses(dto, statuses))
                .filter(dto -> matchesInformedDateRange(dto, fromDate, toDate))
                .filter(dto -> matchesSearch(dto, searchKey))
                .sorted((left, right) -> compareForSort(left, right, sortBy, sortOrder))
                .toList();
    }

    public List<MemberDeathRecordDTO> getRecordsByMember(String memberId) {
        return recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MemberDeathRecordDTO getRecordByRecordNo(String recordNo) {
        return mapToResponse(getRecordEntity(recordNo));
    }

    public MemberDeathRecordDTO getActiveRecordForMember(String memberId) {
        return recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .filter(record -> record.getStatus() != MemberDeathRecordStatus.INACTIVE)
                .findFirst()
                .map(this::mapToResponse)
                .orElse(null);
    }

    public MemberDeathRecordDTO saveRecord(String memberId, MemberDeathRecordDTO dto) {
        Member member = getMemberForSave(memberId);
        MemberDeathRecord record;

        if (dto.getRecordNo() != null && !dto.getRecordNo().isBlank()) {
            record = getRecordEntity(dto.getRecordNo());
            if (!record.getMember().getMemberId().equals(memberId)) {
                throw new RuntimeException("Record does not belong to the specified member");
            }
            if (!isEditable(record.getStatus())) {
                throw new RuntimeException("Record cannot be edited in its current status");
            }
        } else {
            MemberDeathRecord existing = recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                    .stream()
                    .filter(r -> r.getStatus() != MemberDeathRecordStatus.INACTIVE)
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                record = existing;
                if (!isEditable(record.getStatus())) {
                    throw new RuntimeException("Record cannot be edited in its current status");
                }
            } else {
                record = new MemberDeathRecord();
                record.setRecordId(generateRecordId());
                record.setMember(member);
                record.setStatus(MemberDeathRecordStatus.NEW);
            }
        }

        validateRecordDto(dto, false);
        applyRecordFields(record, member, dto);
        replaceMinorAccounts(record, dto.getMinorDisbursements());

        MemberDeathRecord saved = recordRepository.save(record);

        if (member.getStatus() == MemberStatus.ACTIVE) {
            member.setStatus(MemberStatus.MEMBER_DEATH_RECORDED);
            memberRepository.save(member);
        }

        return mapToResponse(saved);
    }

    public MemberDeathRecordDTO submitRecord(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);

        if (!isSubmittable(record.getStatus())) {
            throw new RuntimeException("Record cannot be submitted in its current status");
        }

        MemberRetirementValidationDTO validation = validateMemberForDeathRecord(record.getMember().getMemberId());
        if (!validation.isCanSubmit()) {
            throw new RuntimeException(validation.getMessage());
        }

        validateRecordDto(mapToResponse(record), true);
        validateMandatoryDocuments(record.getRecordId());
        validateMinorAccountsForSubmit(record);

        record.setStatus(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL);
        return mapToResponse(recordRepository.save(record));
    }

    public MemberDeathRecordDTO markIncomplete(String recordNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Incomplete reason is required");
        }

        MemberDeathRecord record = getRecordEntity(recordNo);
        if (!isSubmittable(record.getStatus()) && record.getStatus() != MemberDeathRecordStatus.INCOMPLETE) {
            throw new RuntimeException("Record cannot be marked incomplete in its current status");
        }

        record.setStatus(MemberDeathRecordStatus.INCOMPLETE);
        record.setIncompleteReason(reason.trim());
        return mapToResponse(recordRepository.save(record));
    }

    public MemberDeathRecordDTO changeStatus(String recordNo, String statusValue) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        MemberDeathRecordStatus newStatus = parseStatus(statusValue);

        if (newStatus == MemberDeathRecordStatus.NEW) {
            Set<MemberDeathRecordStatus> revertable = Set.of(
                    MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL,
                    MemberDeathRecordStatus.DISTRICT_COMMITTEE,
                    MemberDeathRecordStatus.PD_COMMITTEE
            );
            if (!revertable.contains(record.getStatus())) {
                throw new RuntimeException("Status cannot be changed to New from the current status");
            }
            record.setIncompleteReason(null);
            record.setRejectReason(null);
        }

        record.setStatus(newStatus);
        return mapToResponse(recordRepository.save(record));
    }

    public MemberDeathRecordDTO approveRecord(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        if (record.getStatus() != MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL
                && record.getStatus() != MemberDeathRecordStatus.DISTRICT_COMMITTEE
                && record.getStatus() != MemberDeathRecordStatus.PD_COMMITTEE) {
            throw new RuntimeException("Record cannot be approved in its current status");
        }

        record.setStatus(MemberDeathRecordStatus.APPROVED);
        MemberDeathRecord saved = recordRepository.save(record);

        Member member = record.getMember();
        member.setStatus(MemberStatus.DECEASED);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    public MemberDeathRecordDTO rejectRecord(String recordNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Reject reason is required");
        }

        MemberDeathRecord record = getRecordEntity(recordNo);
        if (record.getStatus() != MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL
                && record.getStatus() != MemberDeathRecordStatus.DISTRICT_COMMITTEE
                && record.getStatus() != MemberDeathRecordStatus.PD_COMMITTEE) {
            throw new RuntimeException("Record cannot be rejected in its current status");
        }

        record.setStatus(MemberDeathRecordStatus.REJECTED);
        record.setRejectReason(reason.trim());
        return mapToResponse(recordRepository.save(record));
    }

    public MemberDeathDocumentDTO uploadDocument(String recordNo, String documentType, MultipartFile file)
            throws IOException {
        MemberDeathRecord record = getRecordEntity(recordNo);

        if (!isEditable(record.getStatus())) {
            throw new RuntimeException("Documents cannot be uploaded after submission");
        }

        String normalizedType = normalizeDocumentType(documentType);
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }

        List<MemberDeathDocument> existing = documentRepository.findByRecord_RecordIdAndDocumentType(
                record.getRecordId(),
                normalizedType
        );
        for (MemberDeathDocument existingDocument : existing) {
            // Best-effort: do not block replacement if old S3 object cannot be deleted
            deleteDocumentFileQuietly(existingDocument);
            documentRepository.delete(existingDocument);
        }
        documentRepository.flush();

        String fileKey;
        try {
            fileKey = s3Service.uploadFile(file);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to upload file to storage: " + ex.getMessage(), ex);
        }

        MemberDeathDocument document = new MemberDeathDocument();
        document.setRecord(record);
        document.setDocumentType(normalizedType);
        document.setFileName(file.getOriginalFilename());
        document.setFileType(file.getContentType());
        document.setMimeType(file.getContentType());
        document.setFilePath(fileKey);
        document.setMandatory(MANDATORY_DOCUMENT_TYPES.contains(normalizedType));
        document.setUploadedAt(LocalDateTime.now());

        MemberDeathDocument saved = documentRepository.save(document);
        MemberDeathDocumentDTO dto = mapDocumentToDto(saved);
        dto.setRecordNo(record.getRecordId());
        return dto;
    }

    public List<MemberDeathDocumentDTO> getDocuments(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        return documentRepository.findByRecord_RecordId(record.getRecordId())
                .stream()
                .map(this::mapDocumentToDto)
                .toList();
    }

    public void deleteDocument(Long documentId) {
        MemberDeathDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!isEditable(document.getRecord().getStatus())) {
            throw new RuntimeException("Documents cannot be deleted after submission");
        }

        deleteDocumentFileQuietly(document);
        documentRepository.delete(document);
    }

    public byte[] downloadDocument(Long documentId) {
        MemberDeathDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return s3Service.downloadFile(document.getFilePath());
    }

    public void seedCauseOfDeathIfEmpty() {
        if (causeOfDeathRepository.count() > 0) {
            return;
        }

        causeOfDeathRepository.saveAll(List.of(
                createCause("NATURAL", "Natural Causes"),
                createCause("ACCIDENT", "Accident"),
                createCause("ILLNESS", "Illness"),
                createCause("OTHER", "Other")
        ));
    }

    private CauseOfDeath createCause(String code, String name) {
        CauseOfDeath cause = new CauseOfDeath();
        cause.setCode(code);
        cause.setName(name);
        cause.setActive(true);
        return cause;
    }

    private Member getMemberForSave(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE
                && member.getStatus() != MemberStatus.MEMBER_DEATH_RECORDED) {
            throw new RuntimeException("Only active members can have a death record initiated");
        }

        return member;
    }

    private void validateRecordDto(MemberDeathRecordDTO dto, boolean forSubmit) {
        if (dto.getInformedDate() == null || dto.getInformedDate().isBlank()) {
            throw new RuntimeException("Informed date is required");
        }
        if (dto.getDeceasedDate() == null || dto.getDeceasedDate().isBlank()) {
            throw new RuntimeException("Deceased date is required");
        }
        if (dto.getCauseOfDeathId() == null) {
            throw new RuntimeException("Cause of death is required");
        }

        LocalDate informedDate = LocalDate.parse(dto.getInformedDate());
        LocalDate deceasedDate = LocalDate.parse(dto.getDeceasedDate());
        LocalDate today = LocalDate.now();

        if (informedDate.isAfter(today)) {
            throw new RuntimeException("Informed date cannot be a future date");
        }
        if (deceasedDate.isAfter(today)) {
            throw new RuntimeException("Deceased date cannot be a future date");
        }
        if (deceasedDate.isAfter(informedDate)) {
            throw new RuntimeException("Deceased date cannot be after informed date");
        }

        causeOfDeathRepository.findById(dto.getCauseOfDeathId())
                .orElseThrow(() -> new RuntimeException("Invalid cause of death"));

        if (forSubmit) {
            if (dto.getNomineeMobile() == null || dto.getNomineeMobile().isBlank()) {
                throw new RuntimeException("Nominee mobile number is required");
            }
            if (dto.getNomineeBankId() == null) {
                throw new RuntimeException("Nominee bank is required");
            }
            if (dto.getNomineeBranchId() == null) {
                throw new RuntimeException("Nominee bank branch is required");
            }
            if (dto.getNomineeAccountNo() == null || dto.getNomineeAccountNo().isBlank()) {
                throw new RuntimeException("Nominee account number is required");
            }
        }
    }

    private void validateMandatoryDocuments(String recordId) {
        List<MemberDeathDocument> documents = documentRepository.findByRecord_RecordId(recordId);
        for (String mandatoryType : MANDATORY_DOCUMENT_TYPES) {
            boolean uploaded = documents.stream()
                    .anyMatch(doc -> mandatoryType.equalsIgnoreCase(doc.getDocumentType()));
            if (!uploaded) {
                throw new RuntimeException("Mandatory document not uploaded: " + mandatoryType);
            }
        }
    }

    private void validateMinorAccountsForSubmit(MemberDeathRecord record) {
        List<MinorSavingsAccount> minorAccounts = minorSavingsAccountRepository
                .findByMemberId(record.getMember().getMemberId());
        if (minorAccounts.isEmpty()) {
            return;
        }

        for (MinorSavingsAccount account : minorAccounts) {
            MemberDeathMinorAccount minorAccount = record.getMinorAccounts().stream()
                    .filter(item -> account.getMinorAccountNo().equals(item.getMinorAccountNumber()))
                    .findFirst()
                    .orElse(null);

            if (minorAccount == null
                    || minorAccount.getDisbursementBank() == null
                    || minorAccount.getDisbursementBank().isBlank()
                    || minorAccount.getBranch() == null
                    || minorAccount.getBranch().isBlank()
                    || minorAccount.getDisbursementAccountNumber() == null
                    || minorAccount.getDisbursementAccountNumber().isBlank()) {
                throw new RuntimeException(
                        "Disbursement details are required for minor savings account: " + account.getMinorAccountNo()
                );
            }
        }
    }

    private void applyRecordFields(MemberDeathRecord record, Member member, MemberDeathRecordDTO dto) {
        CauseOfDeath cause = causeOfDeathRepository.findById(dto.getCauseOfDeathId())
                .orElseThrow(() -> new RuntimeException("Invalid cause of death"));

        String bankName = resolveBankName(dto.getNomineeBankId());
        String branchName = resolveBranchName(dto.getNomineeBranchId());
        String accountNo = defaultString(dto.getNomineeAccountNo());
        String mobile = defaultString(dto.getNomineeMobile());

        record.setInformedDate(LocalDate.parse(dto.getInformedDate()));
        record.setDeceasedDate(LocalDate.parse(dto.getDeceasedDate()));
        record.setCauseOfDeath(cause.getName());
        record.setComment(trimToNull(dto.getComment()));
        record.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));
        record.setNomineeFullName(member.getNomineeFullName());
        record.setNomineeRelationship(member.getNomineeRelationship());
        record.setNomineeAddress(member.getNomineeAddress());
        record.setNomineeIdentificationTypeAndNumber(buildIdentification(member));
        record.setNomineeMobileNo(mobile);
        record.setNomineeEmailAddress(trimToNull(dto.getNomineeEmail()));
        record.setBank(bankName);
        record.setBankBranch(branchName);
        record.setAccountNumber(accountNo);
        record.setNomineeMobile(mobile);
        record.setNomineeEmail(trimToNull(dto.getNomineeEmail()));
        record.setNomineeBankId(dto.getNomineeBankId());
        record.setNomineeBranchId(dto.getNomineeBranchId());
        record.setNomineeAccountNo(trimToNull(dto.getNomineeAccountNo()));

        if (dto.getDeathDonationAmount() != null) {
            record.setDeathDonationAmount(dto.getDeathDonationAmount());
        }

        if (record.getStatus() == MemberDeathRecordStatus.INCOMPLETE) {
            record.setStatus(MemberDeathRecordStatus.NEW);
            record.setIncompleteReason(null);
        }
    }

    private void replaceMinorAccounts(MemberDeathRecord record, List<MemberDeathMinorDisbursementDTO> items) {
        record.getMinorAccounts().clear();

        if (items == null) {
            return;
        }

        for (MemberDeathMinorDisbursementDTO item : items) {
            if (item.getMinorAccountNo() == null || item.getMinorAccountNo().isBlank()) {
                continue;
            }

            MemberDeathMinorAccount minorAccount = new MemberDeathMinorAccount();
            minorAccount.setRecord(record);
            minorAccount.setMinorAccountNumber(item.getMinorAccountNo().trim());
            minorAccount.setMinorAccountHolderName(trimToNull(item.getHolderName()));
            minorAccount.setDisbursementBank(
                    item.getDisbursementBankName() != null
                            ? item.getDisbursementBankName()
                            : resolveBankName(item.getDisbursementBankId())
            );
            minorAccount.setBranch(
                    item.getDisbursementBranchName() != null
                            ? item.getDisbursementBranchName()
                            : resolveBranchName(item.getDisbursementBranchId())
            );
            minorAccount.setDisbursementAccountNumber(trimToNull(item.getDisbursementAccountNo()));
            record.getMinorAccounts().add(minorAccount);
        }
    }

    private MemberDeathRecordDTO mapToResponse(MemberDeathRecord record) {
        Member member = record.getMember();

        MemberDeathRecordDTO dto = new MemberDeathRecordDTO();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordId());
        dto.setMemberId(member != null ? member.getMemberId() : null);
        dto.setStatus(record.getStatus().name());

        if (member != null) {
            dto.setMemberFullName(member.getFullName());
            dto.setMemberNameWithInitials(member.getNameWithInitials());
            dto.setMemberNic(member.getNic());
        }

        dto.setNomineeFullName(firstNonBlank(record.getNomineeFullName(), member != null ? member.getNomineeFullName() : null));
        dto.setNomineeRelationship(firstNonBlank(record.getNomineeRelationship(), member != null ? member.getNomineeRelationship() : null));
        dto.setNomineeAddress(firstNonBlank(record.getNomineeAddress(), member != null ? member.getNomineeAddress() : null));
        dto.setNomineeIdentificationType(member != null && member.getIdentification() != null
                ? member.getIdentification().name()
                : null);
        dto.setNomineeIdentificationNumber(member != null ? member.getIdentificationNumber() : null);

        dto.setInformedDate(record.getInformedDate().toString());
        dto.setDeceasedDate(record.getDeceasedDate().toString());
        dto.setCauseOfDeathName(record.getCauseOfDeath());
        dto.setCauseOfDeathId(resolveCauseOfDeathId(record.getCauseOfDeath()));
        dto.setComment(record.getComment());
        dto.setConcernsIdentified(record.getConcernsIdentified());
        dto.setNomineeMobile(firstNonBlank(record.getNomineeMobile(), record.getNomineeMobileNo()));
        dto.setNomineeEmail(firstNonBlank(record.getNomineeEmail(), record.getNomineeEmailAddress()));
        dto.setNomineeBankId(record.getNomineeBankId());
        dto.setNomineeBranchId(record.getNomineeBranchId());
        dto.setNomineeAccountNo(firstNonBlank(record.getNomineeAccountNo(), record.getAccountNumber()));
        dto.setDeathDonationAmount(record.getDeathDonationAmount());
        dto.setIncompleteReason(record.getIncompleteReason());
        dto.setRejectReason(record.getRejectReason());
        dto.setEditable(isEditable(record.getStatus()));
        dto.setSubmittable(isSubmittable(record.getStatus()));

        dto.setNomineeBankName(firstNonBlank(record.getBank(), resolveBankName(record.getNomineeBankId())));
        dto.setNomineeBranchName(firstNonBlank(record.getBankBranch(), resolveBranchName(record.getNomineeBranchId())));

        String memberId = member != null ? member.getMemberId() : null;
        if (memberId != null) {
            dto.setHasLoanBalance(loanRepository.existsByMemberIdAndBalanceGreaterThan(memberId, 0.0));
            dto.setHasIndirectObligations(obligationRepository.existsByMemberId(memberId));
        }

        dto.setMinorDisbursements(record.getMinorAccounts().stream()
                .map(this::mapMinorAccountToDto)
                .toList());
        dto.setDocuments(documentRepository.findByRecord_RecordId(record.getRecordId())
                .stream()
                .map(this::mapDocumentToDto)
                .toList());

        return dto;
    }

    private MemberDeathMinorDisbursementDTO mapMinorAccountToDto(MemberDeathMinorAccount item) {
        MemberDeathMinorDisbursementDTO dto = new MemberDeathMinorDisbursementDTO();
        dto.setId(item.getId());
        dto.setMinorAccountNo(item.getMinorAccountNumber());
        dto.setHolderName(item.getMinorAccountHolderName());
        dto.setDisbursementBankName(item.getDisbursementBank());
        dto.setDisbursementBranchName(item.getBranch());
        dto.setDisbursementAccountNo(item.getDisbursementAccountNumber());
        return dto;
    }

    private MemberDeathDocumentDTO mapDocumentToDto(MemberDeathDocument document) {
        MemberDeathDocumentDTO dto = new MemberDeathDocumentDTO();
        dto.setId(document.getId());
        dto.setRecordNo(document.getRecord().getRecordId());
        dto.setDocumentType(document.getDocumentType());
        dto.setFileName(document.getFileName());
        dto.setFileType(document.getFileType());
        dto.setMandatory(document.isMandatory());
        dto.setUploadedAt(document.getUploadedAt().format(DATE_TIME_FORMATTER));
        return dto;
    }

    private CauseOfDeathDTO mapCauseToDto(CauseOfDeath cause) {
        CauseOfDeathDTO dto = new CauseOfDeathDTO();
        dto.setId(cause.getId());
        dto.setCode(cause.getCode());
        dto.setName(cause.getName());
        return dto;
    }

    private MemberDeathRecord getRecordEntity(String recordNo) {
        return recordRepository.findByRecordId(recordNo)
                .orElseThrow(() -> new RuntimeException("Member death record not found"));
    }

    private boolean isEditable(MemberDeathRecordStatus status) {
        return status == MemberDeathRecordStatus.NEW || status == MemberDeathRecordStatus.INCOMPLETE;
    }

    private boolean isSubmittable(MemberDeathRecordStatus status) {
        return status == MemberDeathRecordStatus.NEW || status == MemberDeathRecordStatus.INCOMPLETE;
    }

    private MemberDeathRecordStatus parseStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            throw new RuntimeException("Status is required");
        }

        String normalized = statusValue.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace("PND", "PD");

        try {
            return MemberDeathRecordStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid status: " + statusValue);
        }
    }

    private String generateRecordId() {
        int year = LocalDate.now().getYear();
        String prefix = "MD-" + year + "-";

        return recordRepository.findLastRecordByPrefix(prefix)
                .map(lastRecord -> {
                    String lastNo = lastRecord.getRecordId();
                    int lastSeq = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    private String resolveBankName(Long bankId) {
        if (bankId == null) {
            return "-";
        }
        return bankRepository.findById(bankId)
                .map(bank -> bank.getName())
                .orElse("-");
    }

    private String resolveBranchName(Long branchId) {
        if (branchId == null) {
            return "-";
        }
        return branchRepository.findById(branchId)
                .map(branch -> branch.getName())
                .orElse("-");
    }

    private Long resolveCauseOfDeathId(String causeName) {
        if (causeName == null || causeName.isBlank()) {
            return null;
        }
        return causeOfDeathRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(cause -> causeName.equalsIgnoreCase(cause.getName())
                        || causeName.equalsIgnoreCase(cause.getCode()))
                .map(CauseOfDeath::getId)
                .findFirst()
                .orElse(null);
    }

    private String buildIdentification(Member member) {
        String type = member.getIdentification() != null ? member.getIdentification().name() : "";
        String number = member.getIdentificationNumber() != null ? member.getIdentificationNumber() : "";
        if (type.isBlank() && number.isBlank()) {
            return null;
        }
        return (type + " " + number).trim();
    }

    private String normalizeDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new RuntimeException("Document type is required");
        }
        String normalizedType = documentType.trim().toUpperCase();
        if (!ALLOWED_DOCUMENT_TYPES.contains(normalizedType)) {
            throw new RuntimeException("Unsupported document type: " + documentType);
        }
        return normalizedType;
    }

    private boolean matchesStatuses(MemberDeathRecordDTO dto, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return true;
        }
        return statuses.stream()
                .map(this::normalizeStatusFilter)
                .anyMatch(status -> status.equalsIgnoreCase(dto.getStatus()));
    }

    private String normalizeStatusFilter(String status) {
        return status.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace("PND", "PD");
    }

    private boolean matchesInformedDateRange(MemberDeathRecordDTO dto, String fromDate, String toDate) {
        if (dto.getInformedDate() == null) {
            return false;
        }
        LocalDate date = LocalDate.parse(dto.getInformedDate());
        if (fromDate != null && !fromDate.isBlank() && date.isBefore(LocalDate.parse(fromDate))) {
            return false;
        }
        if (toDate != null && !toDate.isBlank() && date.isAfter(LocalDate.parse(toDate))) {
            return false;
        }
        return true;
    }

    private boolean matchesSearch(MemberDeathRecordDTO dto, String searchKey) {
        if (searchKey == null || searchKey.isBlank()) {
            return true;
        }
        String key = searchKey.toLowerCase();
        return contains(dto.getRecordNo(), key)
                || contains(dto.getMemberId(), key)
                || contains(dto.getMemberFullName(), key)
                || contains(dto.getMemberNameWithInitials(), key)
                || contains(dto.getMemberNic(), key)
                || contains(dto.getCauseOfDeathName(), key);
    }

    private int compareForSort(MemberDeathRecordDTO left, MemberDeathRecordDTO right, String sortBy, String sortOrder) {
        int result;
        if ("status".equalsIgnoreCase(sortBy)) {
            result = safeString(left.getStatus()).compareToIgnoreCase(safeString(right.getStatus()));
        } else if ("memberId".equalsIgnoreCase(sortBy)) {
            result = safeString(left.getMemberId()).compareToIgnoreCase(safeString(right.getMemberId()));
        } else {
            result = safeString(left.getInformedDate()).compareTo(safeString(right.getInformedDate()));
        }
        return "desc".equalsIgnoreCase(sortOrder) ? -result : result;
    }

    private String buildValidationMessage(boolean hasOutstandingLoans, boolean hasLoanObligations) {
        StringBuilder messageBuilder = new StringBuilder();
        if (hasOutstandingLoans) {
            messageBuilder.append("Member has outstanding loan balances");
        }
        if (hasLoanObligations) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(". ");
            }
            messageBuilder.append("Member has indirect loan obligations as nominee for another member's active loan");
        }
        return messageBuilder.toString();
    }

    private void deleteDocumentFileQuietly(MemberDeathDocument document) {
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            return;
        }
        try {
            s3Service.deleteFile(document.getFilePath());
        } catch (Exception ex) {
            System.err.println(
                    "Failed to delete S3 file for member death document "
                            + document.getId()
                            + ": "
                            + ex.getMessage()
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : "-";
    }

    private String firstNonBlank(String primary, String fallback) {
        String trimmedPrimary = trimToNull(primary);
        if (trimmedPrimary != null) {
            return trimmedPrimary;
        }
        return trimToNull(fallback);
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}
