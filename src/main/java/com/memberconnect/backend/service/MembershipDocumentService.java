package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.dto.MemberDocumentDispatchDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.MembershipDocumentType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDocumentDispatch;
import com.memberconnect.backend.repository.MemberDocumentDispatchRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Membership documentation printing (MR15-17) and dispatch (MR18).
 *
 * Printing only records THAT a document was printed — the printable layout itself
 * is rendered in the browser, per the spec's assumption that printing is a
 * template view rather than a device integration.
 */
@Service
@Transactional
public class MembershipDocumentService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberDocumentDispatchRepository dispatchRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private AuditService auditService;

    /**
     * When true, only members with every document printed can be dispatched.
     * Spec 4.10 requires this to be configurable.
     */
    @Value("${dispatch.require.all.documents.printed:true}")
    private boolean requireAllDocumentsPrinted;

    // ---------------------------------------------------------------- printing

    /**
     * Marks the given members' document as printed. Only ACTIVE members can have
     * documentation printed. Without {@code reprint}, a document that already has
     * a printed date is rejected — the spec makes reprinting a separate, explicit,
     * one-at-a-time action rather than something a bulk Select All can trigger.
     */
    public List<MemberDTO> markPrinted(MembershipDocumentType type, List<Long> memberIds, boolean reprint) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No members selected.");
        }
        if (reprint && memberIds.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Re-printing is done one membership at a time.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Member> members = memberRepository.findAllById(memberIds);
        if (members.size() != memberIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more members were not found.");
        }

        for (Member member : members) {
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Membership documentation can only be printed for Active members ("
                                + member.getMemberId() + " is " + member.getStatus() + ").");
            }
            if (!reprint && printedAt(member, type) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This document has already been printed for " + member.getMemberId()
                                + ". Use Re-Print instead.");
            }
            setPrintedAt(member, type, now);
            auditService.record(AuditService.MODULE_MEMBER, member.getId(),
                    reprint ? "Documentation Re-Printed" : "Documentation Printed",
                    null, label(type), null);
        }

        return memberRepository.saveAll(members).stream().map(this::toMemberDto).toList();
    }

    private String label(MembershipDocumentType type) {
        return switch (type) {
            case MEMBERSHIP_CARD -> "Membership Card";
            case SIGNATURE_CARD -> "Signature Card";
            case PASSBOOK -> "Passbook";
        };
    }

    /**
     * Public and static so MemberService can apply the same mapping when filtering
     * "Members without <document>" (MR15/16/17). Two switches over the same three
     * columns would be two places to forget a document type.
     */
    public static LocalDateTime printedAt(Member member, MembershipDocumentType type) {
        return switch (type) {
            case MEMBERSHIP_CARD -> member.getMembershipCardPrintedAt();
            case SIGNATURE_CARD -> member.getSignatureCardPrintedAt();
            case PASSBOOK -> member.getPassbookPrintedAt();
        };
    }

    private void setPrintedAt(Member member, MembershipDocumentType type, LocalDateTime at) {
        switch (type) {
            case MEMBERSHIP_CARD -> member.setMembershipCardPrintedAt(at);
            case SIGNATURE_CARD -> member.setSignatureCardPrintedAt(at);
            case PASSBOOK -> member.setPassbookPrintedAt(at);
        }
    }

    // ---------------------------------------------------------------- dispatch

    /** Members eligible to be dispatched, honouring the all-documents-printed setting. */
    public List<MemberDTO> getDispatchCandidates(boolean onlyNonDispatched) {
        return memberRepository.findByStatus(MemberStatus.ACTIVE).stream()
                .filter(m -> !onlyNonDispatched || m.getDocumentsDispatchedAt() == null)
                .filter(m -> !requireAllDocumentsPrinted || allDocumentsPrinted(m))
                .map(this::toMemberDto)
                .toList();
    }

    private boolean allDocumentsPrinted(Member m) {
        return m.getMembershipCardPrintedAt() != null
                && m.getSignatureCardPrintedAt() != null
                && m.getPassbookPrintedAt() != null;
    }

    /**
     * Records a dispatch batch, flags the members as dispatched, and notifies each
     * of them. Already-dispatched members are rejected rather than silently
     * re-dispatched.
     */
    public MemberDocumentDispatchDTO createDispatch(List<Long> memberIds, String dispatchedBy) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No members selected.");
        }

        List<Member> members = memberRepository.findAllById(memberIds);
        if (members.size() != memberIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more members were not found.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (Member member : members) {
            if (member.getDocumentsDispatchedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Documentation for " + member.getMemberId() + " has already been dispatched.");
            }
            if (requireAllDocumentsPrinted && !allDocumentsPrinted(member)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not all membership documentation has been printed for " + member.getMemberId() + ".");
            }
            member.setDocumentsDispatchedAt(now);
        }

        MemberDocumentDispatch dispatch = new MemberDocumentDispatch();
        dispatch.setDispatchNo(generateDispatchNo());
        dispatch.setDispatchDate(LocalDate.now());
        dispatch.setDispatchedBy(dispatchedBy);
        dispatch.setCreatedAt(now);
        dispatch.setMembers(members);

        memberRepository.saveAll(members);
        MemberDocumentDispatch saved = dispatchRepository.save(dispatch);

        for (Member member : members) {
            auditService.record(AuditService.MODULE_MEMBER, member.getId(),
                    "Documentation Dispatched", null, saved.getDispatchNo(), null);
        }

        // Best-effort: a bounced notification must not roll back the dispatch.
        for (Member member : members) {
            notificationService.sendDocumentationDispatched(member);
        }

        return toDispatchDto(saved, false);
    }

    public List<MemberDocumentDispatchDTO> getDispatches() {
        return dispatchRepository.findAllByOrderByDispatchDateDescIdDesc().stream()
                .map(d -> toDispatchDto(d, false))
                .toList();
    }

    /** Single dispatch including its members — backs the Dispatch Report (spec 5.5). */
    public MemberDocumentDispatchDTO getDispatch(String dispatchNo) {
        MemberDocumentDispatch dispatch = dispatchRepository.findByDispatchNo(dispatchNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));
        return toDispatchDto(dispatch, true);
    }

    // Sequential, year-scoped ID, matching the convention used elsewhere.
    private String generateDispatchNo() {
        String prefix = "DSP-" + LocalDate.now().getYear() + "-";
        return dispatchRepository
                .findFirstByDispatchNoStartingWithOrderByDispatchNoDesc(prefix)
                .map(last -> {
                    String no = last.getDispatchNo();
                    int seq = Integer.parseInt(no.substring(no.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", seq + 1);
                })
                .orElse(prefix + "001");
    }

    private MemberDocumentDispatchDTO toDispatchDto(MemberDocumentDispatch d, boolean includeMembers) {
        MemberDocumentDispatchDTO dto = new MemberDocumentDispatchDTO();
        dto.setId(d.getId());
        dto.setDispatchNo(d.getDispatchNo());
        dto.setDispatchDate(d.getDispatchDate());
        dto.setDispatchedBy(d.getDispatchedBy());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setMemberCount(d.getMembers() == null ? 0 : d.getMembers().size());
        if (includeMembers && d.getMembers() != null) {
            dto.setMembers(d.getMembers().stream().map(this::toMemberDto).toList());
        }
        return dto;
    }

    private MemberDTO toMemberDto(Member member) {
        MemberDTO dto = modelMapper.map(member, MemberDTO.class);
        if (member.getApplication() != null) {
            dto.setApplicationId(member.getApplication().getId());
        }
        return dto;
    }
}
