package com.memberconnect.backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.MemberAdHocDocumentDTO;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberAdHocDocument;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.MemberAdHocDocumentRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * Ad-hoc documents on a Member Profile (Requirement 05, MMD09).
 *
 * Two rules shape everything here:
 *
 * 1. Only District Office, Head Office and Super Admin may use this at all.
 * 2. A District Office caller is confined to members of their own district. Enforced
 *    here rather than in the screen, because a hidden button is not an access rule -
 *    the endpoints are reachable directly. Head Office and Super Admin keep the scope
 *    they already have, which is every member.
 *
 * Documents are immutable once saved: there is deliberately no delete. The Add Documents
 * screen lets a user remove files staged in the current session, but those never reach
 * this service - they are dropped client-side before Save.
 */
@Service
public class MemberAdHocDocumentService {

    /**
     * MMD09 names District Office and Head Office; SUPER_ADMIN is added on top of the
     * SRS, as it is throughout the rest of the system.
     *
     * SUPER_ADMIN is unscoped here, like Head Office - only DISTRICT_OFFICE is narrowed
     * to a district below.
     */
    private static final Set<Role> ALLOWED_ROLES =
            EnumSet.of(Role.DISTRICT_OFFICE, Role.HEAD_OFFICE, Role.SUPER_ADMIN);

    private final MemberAdHocDocumentRepository repository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public MemberAdHocDocumentService(
            MemberAdHocDocumentRepository repository,
            MemberRepository memberRepository,
            S3Service s3Service) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.s3Service = s3Service;
    }

    /** Every ad-hoc document on file for this member, oldest first. */
    public List<MemberAdHocDocumentDTO> listForMember(String memberId) {
        assertCallerMayAccess(memberId);

        return repository.findByMemberIdOrderByUploadedAtAsc(memberId).stream()
                .map(MemberAdHocDocumentService::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Files one document against the member.
     *
     * The member's district is copied onto the row so later access checks do not have to
     * load the Member again, and so a subsequent Member Transfer does not silently move
     * documents that were filed before it.
     */
    public MemberAdHocDocumentDTO upload(String memberId, MultipartFile file) throws IOException {
        Member member = assertCallerMayAccess(memberId);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String storageKey = s3Service.uploadFile(
                file, S3Service.folder("member-adhoc-documents", memberId));

        MemberAdHocDocument document = new MemberAdHocDocument();
        document.setMemberId(memberId);
        document.setSubmissionLocation(member.getSubmissionLocation());
        document.setFileName(file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : storageKey);
        document.setFileType(file.getContentType());
        document.setFilePath(storageKey);
        document.setUploadedAt(LocalDateTime.now());
        document.setUploadedBy(currentUsername());

        return toDto(repository.save(document));
    }

    /**
     * The bytes behind one document, for the filename-click download MMD09 describes.
     *
     * Access is checked against the document's own member, not the one named in the URL,
     * so a mismatched path cannot widen anyone's reach. The two are then required to
     * agree: a request for member A's document under member B's URL is a caller bug, and
     * failing it loudly beats serving the file from a path that misdescribes it.
     */
    public MemberAdHocDocument getForDownload(String memberId, Long documentId) {
        MemberAdHocDocument document = repository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ad-hoc document not found"));

        assertCallerMayAccess(document.getMemberId());

        if (!document.getMemberId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ad-hoc document not found for member " + memberId);
        }

        return document;
    }

    public byte[] download(MemberAdHocDocument document) {
        return s3Service.downloadFile(document.getFilePath());
    }

    /**
     * Confirms the caller may touch this member's ad-hoc documents, and returns the
     * Member so callers that need it do not load it twice.
     */
    private Member assertCallerMayAccess(String memberId) {
        User user = currentUser();
        Role role = user == null ? null : user.getRole();

        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to ad-hoc documents");
        }

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member not found: " + memberId));

        if (role == Role.DISTRICT_OFFICE) {
            String assigned = user.getAssignedDistrict();

            // A district user with no district assigned can be scoped to nothing
            // safely, but not to everything - the same rule the termination, retirement
            // and dormant searches apply.
            if (assigned == null || assigned.isBlank()
                    || !assigned.equals(member.getSubmissionLocation())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This member belongs to another district");
            }
        }

        return member;
    }

    private static MemberAdHocDocumentDTO toDto(MemberAdHocDocument document) {
        return new MemberAdHocDocumentDTO(
                document.getId(),
                document.getMemberId(),
                document.getFileName(),
                document.getFileType(),
                document.getUploadedAt(),
                document.getUploadedBy());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }

    private String currentUsername() {
        User user = currentUser();
        return user == null ? null : user.getUsername();
    }
}
