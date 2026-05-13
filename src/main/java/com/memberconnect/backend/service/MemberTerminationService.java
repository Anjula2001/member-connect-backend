package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MemberTerminationDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberTermination;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MemberTerminationRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MemberTerminationService {

    @Autowired
    private MemberTerminationRepository memberTerminationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ModelMapper modelMapper;

    // Create a new termination request for a member
    public MemberTerminationDTO createTermination(MemberTerminationDTO dto) {
        // Validate member exists
        Member member = memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found with id: " + dto.getMemberId()));

        // Check if member already has termination 
        memberTerminationRepository.findByMemberIdAndTerminationStatus(
                dto.getMemberId(),
                TerminationStatus.PENDING).ifPresent(t -> {
                    throw new RuntimeException("Member already has a pending termination request");
                });
        memberTerminationRepository.findByMemberIdAndTerminationStatus(
                dto.getMemberId(),
                TerminationStatus.SUBMITTED_FOR_APPROVAL).ifPresent(t -> {
                    throw new RuntimeException("Member already has a termination request submitted for approval");
                });

        MemberTermination termination = modelMapper.map(dto, MemberTermination.class);
        termination.setMember(member);
        termination.setTerminationStatus(TerminationStatus.PENDING);

        MemberTermination saved = memberTerminationRepository.save(termination);
        return convertToDTO(saved);
    }

    
     // Get termination by ID
     
    public MemberTerminationDTO getTerminationById(Long id) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));
        return convertToDTO(termination);
    }

    // Get termination by Termination ID (string)

    public MemberTerminationDTO getTerminationByTerminationId(String terminationId) {
        MemberTermination termination = memberTerminationRepository.findByTerminationId(terminationId)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + terminationId));
        return convertToDTO(termination);
    }

    // Get all terminations for a specific member
     
    public List<MemberTerminationDTO> getTerminationsByMemberId(Long memberId) {
        // Validate member exists
        memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found with id: " + memberId));

        return memberTerminationRepository.findByMemberId(memberId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get all pending terminations

    public List<MemberTerminationDTO> getPendingTerminations() {
        return memberTerminationRepository.findByTerminationStatusOrderByRequestedDateDesc(TerminationStatus.PENDING)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get all terminations by status

    public List<MemberTerminationDTO> getTerminationsByStatus(TerminationStatus status) {
        return memberTerminationRepository.findByTerminationStatus(status)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    
    // Get all terminations
    
    public List<MemberTerminationDTO> getAllTerminations() {
        return memberTerminationRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    
     // Approve a termination request
     
    public MemberTerminationDTO approveTermination(Long id, String approvedBy) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));

        if (!termination.getTerminationStatus().equals(TerminationStatus.PENDING)
                && !termination.getTerminationStatus().equals(TerminationStatus.SUBMITTED_FOR_APPROVAL)
                && !termination.getTerminationStatus().equals(TerminationStatus.ADDED_TO_APPROVAL_LIST)) {
            throw new RuntimeException("Only pending or submitted terminations can be approved");
        }

        termination.setTerminationStatus(TerminationStatus.APPROVED);
        termination.setApprovedBy(approvedBy);
        termination.setApprovedDate(java.time.LocalDate.now());
        termination.setUpdatedAt(LocalDateTime.now());

        MemberTermination updated = memberTerminationRepository.save(termination);
        return convertToDTO(updated);
    }


    // Reject a termination request
    public MemberTerminationDTO rejectTermination(Long id, String remarks) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));

        if (!termination.getTerminationStatus().equals(TerminationStatus.PENDING)
                && !termination.getTerminationStatus().equals(TerminationStatus.SUBMITTED_FOR_APPROVAL)
                && !termination.getTerminationStatus().equals(TerminationStatus.ADDED_TO_APPROVAL_LIST)) {
            throw new RuntimeException("Only pending or submitted terminations can be rejected");
        }

        termination.setTerminationStatus(TerminationStatus.REJECTED);
        termination.setRemarks(remarks);
        termination.setUpdatedAt(LocalDateTime.now());

        MemberTermination updated = memberTerminationRepository.save(termination);
        return convertToDTO(updated);
    }


     // Process an approved termination (finalize it)

    public MemberTerminationDTO processTermination(Long id, String processedBy) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));

        if (!termination.getTerminationStatus().equals(TerminationStatus.APPROVED)) {
            throw new RuntimeException("Only approved terminations can be processed");
        }

        termination.setTerminationStatus(TerminationStatus.PROCESSED);
        termination.setProcessedBy(processedBy);
        termination.setProcessedDate(java.time.LocalDate.now());
        termination.setUpdatedAt(LocalDateTime.now());

        // Update member status to TERMINATED
        Member member = termination.getMember();
        member.setStatus(MemberStatus.TERMINATED);
        memberRepository.save(member);

        MemberTermination updated = memberTerminationRepository.save(termination);
        return convertToDTO(updated);
    }

    // Update termination
     
    public MemberTerminationDTO updateTermination(Long id, MemberTerminationDTO dto) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));

        if (!termination.getTerminationStatus().equals(TerminationStatus.PENDING)) {
            throw new RuntimeException("Only pending terminations can be updated");
        }

        if (dto.getTerminationReason() != null) {
            termination.setTerminationReason(dto.getTerminationReason());
        }
        if (dto.getTerminationDate() != null) {
            termination.setTerminationDate(dto.getTerminationDate());
        }
        if (dto.getRemarks() != null) {
            termination.setRemarks(dto.getRemarks());
        }

        termination.setUpdatedAt(LocalDateTime.now());
        MemberTermination updated = memberTerminationRepository.save(termination);
        return convertToDTO(updated);
    }

    // Delete termination (only if pending or rejected)
     
    public void deleteTermination(Long id) {
        MemberTermination termination = memberTerminationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Termination not found with id: " + id));

        if (termination.getTerminationStatus().equals(TerminationStatus.PROCESSED)) {
            throw new RuntimeException("Cannot delete processed terminations");
        }

        memberTerminationRepository.delete(termination);
    }

    // Convert MemberTermination entity to DTO
     
    private MemberTerminationDTO convertToDTO(MemberTermination termination) {
        MemberTerminationDTO dto = new MemberTerminationDTO();
        dto.setId(termination.getId());
        dto.setTerminationId(termination.getTerminationId());
        dto.setMemberId(termination.getMember().getId());
        dto.setMemberName(termination.getMember().getFullName());
        dto.setMemberId_Code(termination.getMember().getMemberId());
        dto.setTerminationReason(termination.getTerminationReason());
        dto.setTerminationStatus(termination.getTerminationStatus());
        dto.setTerminationDate(termination.getTerminationDate());
        dto.setRequestedDate(termination.getRequestedDate());
        dto.setApprovedDate(termination.getApprovedDate());
        dto.setProcessedDate(termination.getProcessedDate());
        dto.setRemarks(termination.getRemarks());
        dto.setApprovedBy(termination.getApprovedBy());
        dto.setProcessedBy(termination.getProcessedBy());
        dto.setCreatedAt(termination.getCreatedAt());
        dto.setUpdatedAt(termination.getUpdatedAt());
        return dto;
    }
}
