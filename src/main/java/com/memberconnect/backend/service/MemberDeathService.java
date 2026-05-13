package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.CreateMemberDeathDTO;
import com.memberconnect.backend.dto.MarkIncompleteDTO;
import com.memberconnect.backend.dto.MemberDeathResponseDTO;
import com.memberconnect.backend.enums.DeathRecordStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathDocument;
import com.memberconnect.backend.model.MemberDeathMinorAccount;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemberDeathService {

    @Autowired
    private MemberDeathRecordRepository recordRepository;

    @Autowired
    private MemberRepository memberRepository;

    // ── CREATE ─────────────────────────────────────────────────────────────────

    @Transactional
    public MemberDeathResponseDTO createRecord(CreateMemberDeathDTO dto) {
        MemberDeathRecord record = new MemberDeathRecord();
        record.setStatus(DeathRecordStatus.NEW);
        
        mapDtoToEntity(dto, record);
        
        return toResponseDTO(recordRepository.save(record));
    }

    // ── SAVE (DRAFT) ───────────────────────────────────────────────────────────

    @Transactional
    public MemberDeathResponseDTO saveRecord(Long id, CreateMemberDeathDTO dto) {
        MemberDeathRecord record = findRecord(id);
        
        if (record.getStatus() != DeathRecordStatus.NEW && record.getStatus() != DeathRecordStatus.INCOMPLETE) {
            throw new RuntimeException("Cannot update record in status: " + record.getStatus());
        }

        mapDtoToEntity(dto, record);
        
        return toResponseDTO(recordRepository.save(record));
    }

    // ── SUBMIT ─────────────────────────────────────────────────────────────────

    @Transactional
    public MemberDeathResponseDTO submitRecord(Long id, CreateMemberDeathDTO dto) {
        MemberDeathRecord record = findRecord(id);

        if (record.getStatus() != DeathRecordStatus.NEW && record.getStatus() != DeathRecordStatus.INCOMPLETE) {
            throw new RuntimeException("Cannot submit record from status: " + record.getStatus());
        }

        // Apply any final updates from the form if provided
        if (dto != null) {
            mapDtoToEntity(dto, record);
        }

        // Run validation rules
        validateForSubmit(record);

        record.setStatus(DeathRecordStatus.SUBMITTED_FOR_APPROVAL);
        return toResponseDTO(recordRepository.save(record));
    }

    // ── MARK INCOMPLETE ────────────────────────────────────────────────────────

    @Transactional
    public MemberDeathResponseDTO markIncomplete(Long id, MarkIncompleteDTO dto) {
        MemberDeathRecord record = findRecord(id);
        
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new RuntimeException("Reason is required to mark as incomplete");
        }

        record.setStatus(DeathRecordStatus.INCOMPLETE);
        record.setIncompleteReason(dto.getReason());
        
        return toResponseDTO(recordRepository.save(record));
    }

    // ── GET SINGLE RECORD ──────────────────────────────────────────────────────

    public MemberDeathResponseDTO getRecord(Long id) {
        return toResponseDTO(findRecord(id));
    }

    // ── GET ALL RECORDS ────────────────────────────────────────────────────────

    public List<MemberDeathResponseDTO> getAllRecords() {
        return recordRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ── HELPERS & VALIDATION ───────────────────────────────────────────────────

    private MemberDeathRecord findRecord(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member Death Record not found: " + id));
    }

    private void mapDtoToEntity(CreateMemberDeathDTO dto, MemberDeathRecord record) {
        Member member = memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found: " + dto.getMemberId()));
        
        // "Member must be in ACTIVE state"
        if (member.getStatus() == null || !"ACTIVE".equalsIgnoreCase(member.getStatus().name())) {
            throw new RuntimeException("Member must be in ACTIVE state to record death");
        }

        record.setMember(member);
        record.setInformedDate(dto.getInformedDate());
        record.setDeceasedDate(dto.getDeceasedDate());
        record.setCauseOfDeath(dto.getCauseOfDeath());
        record.setComment(dto.getComment());
        record.setConcernsIdentified(dto.getConcernsIdentified());

        record.setNomineeFullName(dto.getNomineeFullName());
        record.setNomineeAddress(dto.getNomineeAddress());
        record.setNomineeRelationship(dto.getNomineeRelationship());
        record.setNomineeIdentificationTypeAndNumber(dto.getNomineeIdentificationTypeAndNumber());
        record.setNomineeMobileNo(dto.getNomineeMobileNo());
        record.setNomineeEmailAddress(dto.getNomineeEmailAddress());

        record.setBank(dto.getBank());
        record.setBankBranch(dto.getBankBranch());
        record.setAccountNumber(dto.getAccountNumber());

        // Update Minor Accounts
        record.getMinorAccounts().clear();
        if (dto.getMinorAccounts() != null) {
            for (CreateMemberDeathDTO.MinorAccountDTO mDto : dto.getMinorAccounts()) {
                MemberDeathMinorAccount ma = new MemberDeathMinorAccount();
                ma.setMemberDeathRecord(record);
                ma.setMinorAccountNumber(mDto.getMinorAccountNumber());
                ma.setMinorAccountHolderName(mDto.getMinorAccountHolderName());
                ma.setDisbursementBank(mDto.getDisbursementBank());
                ma.setBranch(mDto.getBranch());
                ma.setDisbursementAccountNumber(mDto.getDisbursementAccountNumber());
                record.getMinorAccounts().add(ma);
            }
        }

        // Update Documents
        record.getDocuments().clear();
        if (dto.getDocuments() != null) {
            for (CreateMemberDeathDTO.DocumentDTO dDto : dto.getDocuments()) {
                MemberDeathDocument doc = new MemberDeathDocument();
                doc.setMemberDeathRecord(record);
                doc.setDocumentType(dDto.getDocumentType());
                doc.setFileName(dDto.getFileName());
                doc.setMimeType(dDto.getMimeType());
                doc.setMandatory(dDto.getMandatory() != null ? dDto.getMandatory() : false);
                record.getDocuments().add(doc);
            }
        }
    }

    private void validateForSubmit(MemberDeathRecord record) {
        LocalDate today = LocalDate.now();

        if (record.getInformedDate() == null || record.getInformedDate().isAfter(today)) {
            throw new RuntimeException("Informed date must not be in the future");
        }
        if (record.getDeceasedDate() == null || record.getDeceasedDate().isAfter(today)) {
            throw new RuntimeException("Deceased date must not be in the future");
        }
        if (record.getCauseOfDeath() == null || record.getCauseOfDeath().trim().isEmpty()) {
            throw new RuntimeException("Cause of death is required");
        }
        if (record.getNomineeMobileNo() == null || record.getNomineeMobileNo().trim().isEmpty()) {
            throw new RuntimeException("Nominee mobile number is required");
        }
        if (record.getBank() == null || record.getBank().trim().isEmpty()) {
            throw new RuntimeException("Bank name is required");
        }
        if (record.getBankBranch() == null || record.getBankBranch().trim().isEmpty()) {
            throw new RuntimeException("Bank branch is required");
        }
        if (record.getAccountNumber() == null || record.getAccountNumber().trim().isEmpty()) {
            throw new RuntimeException("Account number is required");
        }

        // Check if mandatory documents are provided
        long mandatoryDocsCount = record.getDocuments().stream().filter(MemberDeathDocument::getMandatory).count();
        if (mandatoryDocsCount == 0) {
            // Note: Adjust logic if there is a specific list of mandatory docs required
            // For now, if no mandatory docs were added, throw error
            throw new RuntimeException("Please upload all mandatory documents");
        }

        // Business Logic checks: Outstanding loans
        if (hasOutstandingLoans(record.getMember().getId())) {
            throw new RuntimeException("Submit rejected: Outstanding loan balances exist for this member");
        }
        if (hasIndirectObligations(record.getMember().getId())) {
            throw new RuntimeException("Submit rejected: Indirect loan obligations exist for this member");
        }
    }

    // Mock checks - in real world these would query the Loan Management module
    private boolean hasOutstandingLoans(Long memberId) {
        // Return false to allow the happy path during testing
        return false;
    }

    private boolean hasIndirectObligations(Long memberId) {
        return false;
    }

    private MemberDeathResponseDTO toResponseDTO(MemberDeathRecord entity) {
        MemberDeathResponseDTO dto = new MemberDeathResponseDTO();
        dto.setId(entity.getId());
        dto.setRecordId(entity.getRecordId());
        dto.setMemberId(entity.getMember().getId());
        dto.setMemberName(entity.getMember().getNameWithInitials()); // Assuming member has name
        dto.setMemberNic(entity.getMember().getNic());

        dto.setInformedDate(entity.getInformedDate());
        dto.setDeceasedDate(entity.getDeceasedDate());
        dto.setCauseOfDeath(entity.getCauseOfDeath());
        dto.setComment(entity.getComment());
        dto.setConcernsIdentified(entity.getConcernsIdentified());

        dto.setNomineeFullName(entity.getNomineeFullName());
        dto.setNomineeAddress(entity.getNomineeAddress());
        dto.setNomineeRelationship(entity.getNomineeRelationship());
        dto.setNomineeIdentificationTypeAndNumber(entity.getNomineeIdentificationTypeAndNumber());
        dto.setNomineeMobileNo(entity.getNomineeMobileNo());
        dto.setNomineeEmailAddress(entity.getNomineeEmailAddress());

        dto.setBank(entity.getBank());
        dto.setBankBranch(entity.getBankBranch());
        dto.setAccountNumber(entity.getAccountNumber());

        dto.setStatus(entity.getStatus());
        dto.setIncompleteReason(entity.getIncompleteReason());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Map Minors
        List<CreateMemberDeathDTO.MinorAccountDTO> minors = entity.getMinorAccounts().stream().map(m -> {
            CreateMemberDeathDTO.MinorAccountDTO mDto = new CreateMemberDeathDTO.MinorAccountDTO();
            mDto.setMinorAccountNumber(m.getMinorAccountNumber());
            mDto.setMinorAccountHolderName(m.getMinorAccountHolderName());
            mDto.setDisbursementBank(m.getDisbursementBank());
            mDto.setBranch(m.getBranch());
            mDto.setDisbursementAccountNumber(m.getDisbursementAccountNumber());
            return mDto;
        }).collect(Collectors.toList());
        dto.setMinorAccounts(minors);

        // Map Docs
        List<CreateMemberDeathDTO.DocumentDTO> docs = entity.getDocuments().stream().map(d -> {
            CreateMemberDeathDTO.DocumentDTO dDto = new CreateMemberDeathDTO.DocumentDTO();
            dDto.setDocumentType(d.getDocumentType());
            dDto.setFileName(d.getFileName());
            dDto.setMimeType(d.getMimeType());
            dDto.setMandatory(d.getMandatory());
            return dDto;
        }).collect(Collectors.toList());
        dto.setDocuments(docs);

        return dto;
    }
}
