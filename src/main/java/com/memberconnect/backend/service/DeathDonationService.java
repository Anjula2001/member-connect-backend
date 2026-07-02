package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.DeathDonationDeceasedPopulateDTO;
import com.memberconnect.backend.dto.DeathDonationDocumentDTO;
import com.memberconnect.backend.dto.DeathDonationRelativeDTO;
import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.enums.DeathDonationRequestStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.DeathDonationDocument;
import com.memberconnect.backend.model.DeathDonationRelative;
import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.DeathDonationDocumentRepository;
import com.memberconnect.backend.repository.DeathDonationRequestRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class DeathDonationService {

    private static final int ALLOWED_MONTHS_FROM_DECEASED = 3;
    private static final Set<String> MANDATORY_DOCUMENT_TYPES = Set.of(
            "DEATH_CERTIFICATE",
            "NIC_COPY"
    );

    private final DeathDonationRequestRepository requestRepository;
    private final DeathDonationDocumentRepository documentRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public DeathDonationService(
            DeathDonationRequestRepository requestRepository,
            DeathDonationDocumentRepository documentRepository,
            MemberRepository memberRepository,
            S3Service s3Service
    ) {
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
        this.s3Service = s3Service;
    }

    public List<DeathDonationRequestDTO> getRequestsByMember(String memberId) {
        return requestRepository.findByMember_MemberIdOrderByRequestedDateDesc(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<DeathDonationRequestDTO> searchRequests(
            List<String> locations,
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        return requestRepository.findAllByOrderByRequestedDateDesc()
                .stream()
                .map(this::mapToResponse)
                .filter(dto -> matchesStatuses(dto, statuses))
                .filter(dto -> matchesRequestedDateRange(dto, fromDate, toDate))
                .filter(dto -> matchesSearch(dto, searchKey))
                .filter(dto -> matchesLocation(dto, locations))
                .sorted((left, right) -> compareForSort(left, right, sortBy, sortOrder))
                .toList();
    }

    public DeathDonationRequestDTO getRequestByRequestNo(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        return mapToResponse(request);
    }

    public DeathDonationRequestDTO saveRequest(String memberId, DeathDonationRequestDTO dto) {
        validateRequestDto(dto, false);

        DeathDonationRequest request;
        if (dto.getRequestNo() != null && !dto.getRequestNo().isBlank()) {
            request = getRequestEntity(dto.getRequestNo());
            memberId = request.getMember().getMemberId();
            if (!isEditable(request.getStatus())) {
                applyConcernsOnlyUpdate(request, dto);
                return mapToResponse(requestRepository.save(request));
            }
        } else {
            request = new DeathDonationRequest();
            request.setRequestNo(generateRequestNo());
            request.setMember(getActiveMember(memberId));
            request.setStatus(DeathDonationRequestStatus.NEW);
        }

        applyRequestFields(request, dto);
        replaceRelatives(request, dto.getRelatives());

        DeathDonationRequest saved = requestRepository.save(request);
        return mapToResponse(saved);
    }

    public DeathDonationRequestDTO submitRequest(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);

        if (!isSubmittable(request.getStatus())) {
            throw new RuntimeException("Request cannot be submitted in its current status");
        }

        validateRequestDto(mapToResponse(request), true);
        validateMandatoryDocuments(request.getRequestNo());

        request.setStatus(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        return mapToResponse(requestRepository.save(request));
    }

    public DeathDonationRequestDTO markIncomplete(String requestNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Incomplete reason is required");
        }

        DeathDonationRequest request = getRequestEntity(requestNo);

        if (!isSubmittable(request.getStatus()) && request.getStatus() != DeathDonationRequestStatus.INCOMPLETE) {
            throw new RuntimeException("Request cannot be marked incomplete in its current status");
        }

        request.setStatus(DeathDonationRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason.trim());
        return mapToResponse(requestRepository.save(request));
    }

    public DeathDonationRequestDTO approveRequest(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);

        if (request.getStatus() != DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL) {
            throw new RuntimeException("Only submitted requests can be approved");
        }

        request.setStatus(DeathDonationRequestStatus.APPROVED);
        return mapToResponse(requestRepository.save(request));
    }

    public DeathDonationRequestDTO rejectRequest(String requestNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Reject reason is required");
        }

        DeathDonationRequest request = getRequestEntity(requestNo);

        if (request.getStatus() != DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL) {
            throw new RuntimeException("Only submitted requests can be rejected");
        }

        request.setStatus(DeathDonationRequestStatus.REJECTED);
        request.setRejectReason(reason.trim());
        return mapToResponse(requestRepository.save(request));
    }

    public List<DeathDonationRelativeDTO> refreshRelatives(
            String deathCertificateNumber,
            String excludeRequestNo
    ) {
        if (deathCertificateNumber == null || deathCertificateNumber.isBlank()) {
            throw new RuntimeException("Death Certificate Number is required");
        }

        return requestRepository
                .findByDeathCertificateNumberIgnoreCase(deathCertificateNumber.trim())
                .stream()
                .filter(request -> excludeRequestNo == null
                        || excludeRequestNo.isBlank()
                        || !request.getRequestNo().equalsIgnoreCase(excludeRequestNo.trim()))
                .map(request -> {
                    DeathDonationRelativeDTO relative = new DeathDonationRelativeDTO();
                    String relativeMemberId = request.getMember().getMemberId();
                    relative.setRelativeMemberId(relativeMemberId);
                    relative.setRelationshipToDeceased(request.getRelationshipToDeceased());
                    relative.setAutoPopulated(true);
                    relative.setRelativeMemberName(
                            request.getMember().getFullName() != null
                                    ? request.getMember().getFullName()
                                    : relativeMemberId
                    );
                    return relative;
                })
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                DeathDonationRelativeDTO::getRelativeMemberId,
                                relative -> relative,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    public DeathDonationDeceasedPopulateDTO populateDeceasedMember(String deceasedMemberId) {
        if (deceasedMemberId == null || deceasedMemberId.isBlank()) {
            throw new RuntimeException("Deceased Member ID is required");
        }

        Member deceased = memberRepository.findByMemberId(deceasedMemberId.trim())
                .orElseThrow(() -> new RuntimeException("Deceased member not found"));

        DeathDonationDeceasedPopulateDTO dto = new DeathDonationDeceasedPopulateDTO();
        dto.setDeceasedMemberId(deceased.getMemberId());
        dto.setDeceasedName(deceased.getFullName());
        dto.setDeceasedPlaceOfWork(resolvePlaceOfWork(deceased));
        return dto;
    }

    public DeathDonationDocumentDTO uploadDocument(
            String requestNo,
            String documentType,
            MultipartFile file
    ) throws IOException {
        DeathDonationRequest request = getRequestEntity(requestNo);

        if (!isEditable(request.getStatus())) {
            throw new RuntimeException("Documents cannot be uploaded after submission");
        }

        if (documentType == null || documentType.isBlank()) {
            throw new RuntimeException("Document type is required");
        }

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is required");
        }

        String normalizedDocumentType = documentType.trim().toUpperCase();
        List<DeathDonationDocument> existing = documentRepository.findByRequest_RequestNoAndDocumentType(
                requestNo,
                normalizedDocumentType
        );
        for (DeathDonationDocument existingDocument : existing) {
            deleteDocumentFile(existingDocument);
            documentRepository.delete(existingDocument);
        }

        String fileKey = s3Service.uploadFile(file);

        DeathDonationDocument document = new DeathDonationDocument();
        document.setRequest(request);
        document.setRequestNo(request.getRequestNo());
        document.setDocumentType(normalizedDocumentType);
        document.setFileName(file.getOriginalFilename());
        document.setFileType(file.getContentType());
        document.setMimeType(file.getContentType());
        document.setFilePath(fileKey);
        document.setMandatory(MANDATORY_DOCUMENT_TYPES.contains(normalizedDocumentType));
        document.setUploadedAt(LocalDateTime.now());

        return mapDocumentToDto(documentRepository.save(document));
    }

    public List<DeathDonationDocumentDTO> getDocuments(String requestNo) {
        return documentRepository.findByRequest_RequestNo(requestNo)
                .stream()
                .map(this::mapDocumentToDto)
                .toList();
    }

    public void deleteDocument(Long documentId) {
        DeathDonationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        DeathDonationRequest request = document.getRequest();
        if (!isEditable(request.getStatus())) {
            throw new RuntimeException("Documents cannot be deleted after submission");
        }

        deleteDocumentFile(document);
        documentRepository.delete(document);
    }

    public byte[] downloadDocument(Long documentId) {
        DeathDonationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return s3Service.downloadFile(document.getFilePath());
    }

    private void applyRequestFields(DeathDonationRequest request, DeathDonationRequestDTO dto) {
        request.setRelationshipToDeceased(dto.getRelationshipToDeceased().trim());
        request.setRequestedDate(LocalDate.parse(dto.getRequestedDate()));
        request.setDeceasedMember(dto.isDeceasedMember());
        request.setDeceasedMemberId(
                dto.isDeceasedMember() ? trimToNull(dto.getDeceasedMemberId()) : null
        );
        request.setDeceasedName(dto.getDeceasedName().trim());
        request.setMaidenNameIfMarried(trimToNull(dto.getMaidenNameIfMarried()));
        request.setDeceasedDate(LocalDate.parse(dto.getDeceasedDate()));
        request.setDeathCertificateNumber(dto.getDeathCertificateNumber().trim());
        request.setDeceasedPlaceOfWork(trimToNull(dto.getDeceasedPlaceOfWork()));
        request.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));

        if (request.getStatus() == DeathDonationRequestStatus.INCOMPLETE) {
            request.setStatus(DeathDonationRequestStatus.NEW);
            request.setIncompleteReason(null);
        }
    }

    private void applyConcernsOnlyUpdate(DeathDonationRequest request, DeathDonationRequestDTO dto) {
        request.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));
    }

    private void replaceRelatives(DeathDonationRequest request, List<DeathDonationRelativeDTO> relatives) {
        request.getRelatives().clear();

        if (relatives == null) {
            return;
        }

        for (DeathDonationRelativeDTO relativeDto : relatives) {
            if (relativeDto.getRelativeMemberId() == null || relativeDto.getRelativeMemberId().isBlank()) {
                continue;
            }
            if (relativeDto.getRelationshipToDeceased() == null || relativeDto.getRelationshipToDeceased().isBlank()) {
                continue;
            }

            DeathDonationRelative relative = new DeathDonationRelative();
            relative.setRequest(request);
            relative.setRelativeMemberId(relativeDto.getRelativeMemberId().trim());
            relative.setRelationshipToDeceased(relativeDto.getRelationshipToDeceased().trim());
            relative.setAutoPopulated(relativeDto.isAutoPopulated());
            request.getRelatives().add(relative);
        }
    }

    private void validateRequestDto(DeathDonationRequestDTO dto, boolean forSubmit) {
        if (dto.getRelationshipToDeceased() == null || dto.getRelationshipToDeceased().isBlank()) {
            throw new RuntimeException("Relationship to the deceased is required");
        }

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new RuntimeException("Requested Date is required");
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        if (requestedDate.isAfter(LocalDate.now())) {
            throw new RuntimeException("Requested Date cannot be a future date");
        }

        if (dto.getDeceasedName() == null || dto.getDeceasedName().isBlank()) {
            throw new RuntimeException("Name of the deceased is required");
        }

        if (dto.getDeceasedDate() == null || dto.getDeceasedDate().isBlank()) {
            throw new RuntimeException("Deceased Date is required");
        }

        if (dto.getDeathCertificateNumber() == null || dto.getDeathCertificateNumber().isBlank()) {
            throw new RuntimeException("Death Certificate Number is required");
        }

        if (dto.isDeceasedMember()) {
            if (dto.getDeceasedMemberId() == null || dto.getDeceasedMemberId().isBlank()) {
                throw new RuntimeException("Deceased Member ID is required when deceased is a member");
            }
            if (!memberRepository.findByMemberId(dto.getDeceasedMemberId().trim()).isPresent()) {
                throw new RuntimeException("Deceased Member ID is not valid");
            }
        }

        if (forSubmit && dto.getRelatives() != null) {
            for (DeathDonationRelativeDTO relative : dto.getRelatives()) {
                if (!relative.isAutoPopulated()
                        && (relative.getRelativeMemberId() == null || relative.getRelationshipToDeceased() == null)) {
                    throw new RuntimeException("All manually added relatives must have member ID and relationship");
                }
            }
        }
    }

    private void validateMandatoryDocuments(String requestNo) {
        for (String documentType : MANDATORY_DOCUMENT_TYPES) {
            if (!documentRepository.existsByRequest_RequestNoAndDocumentType(requestNo, documentType)) {
                throw new RuntimeException("Mandatory document missing: " + formatDocumentType(documentType));
            }
        }
    }

    private DeathDonationRequestDTO mapToResponse(DeathDonationRequest request) {
        Member member = request.getMember();

        DeathDonationRequestDTO dto = new DeathDonationRequestDTO();
        dto.setId(request.getId());
        dto.setRequestNo(request.getRequestNo());
        dto.setMemberId(member != null ? member.getMemberId() : null);
        dto.setMemberFullName(member != null ? member.getFullName() : null);
        dto.setMemberNameWithInitials(member != null ? member.getNameWithInitials() : null);
        dto.setMemberNameAsInPayroll(member != null ? member.getNameAsInPayroll() : null);
        dto.setMemberNic(member != null ? member.getNic() : null);
        dto.setMemberWorkingLocation(member != null ? member.getWorkingLocation() : null);
        dto.setMemberEducationalDistrict(member != null ? member.getEducationalDistrict() : null);
        dto.setStatus(request.getStatus().name());
        dto.setRelationshipToDeceased(request.getRelationshipToDeceased());
        dto.setRequestedDate(request.getRequestedDate().toString());
        dto.setDeceasedMember(request.isDeceasedMember());
        dto.setDeceasedMemberId(request.getDeceasedMemberId());
        dto.setDeceasedName(request.getDeceasedName());
        dto.setMaidenNameIfMarried(request.getMaidenNameIfMarried());
        dto.setDeceasedDate(request.getDeceasedDate().toString());
        dto.setDeathCertificateNumber(request.getDeathCertificateNumber());
        dto.setDeceasedPlaceOfWork(request.getDeceasedPlaceOfWork());
        dto.setConcernsIdentified(request.getConcernsIdentified());
        dto.setIncompleteReason(request.getIncompleteReason());
        dto.setRejectReason(request.getRejectReason());
        dto.setDateRangeWarning(isOutsideAllowedDateRange(request.getRequestedDate(), request.getDeceasedDate()));

        dto.setRelatives(request.getRelatives().stream().map(relative -> {
            DeathDonationRelativeDTO relativeDto = new DeathDonationRelativeDTO();
            relativeDto.setId(relative.getId());
            relativeDto.setRelativeMemberId(relative.getRelativeMemberId());
            relativeDto.setRelationshipToDeceased(relative.getRelationshipToDeceased());
            relativeDto.setAutoPopulated(relative.isAutoPopulated());
            Member relativeMember = memberRepository.findByMemberId(relative.getRelativeMemberId()).orElse(null);
            relativeDto.setRelativeMemberName(
                    relativeMember != null ? relativeMember.getFullName() : relative.getRelativeMemberId()
            );
            return relativeDto;
        }).toList());

        return dto;
    }

    private DeathDonationDocumentDTO mapDocumentToDto(DeathDonationDocument document) {
        DeathDonationDocumentDTO dto = new DeathDonationDocumentDTO();
        dto.setId(document.getId());
        dto.setRequestNo(document.getRequestNo());
        dto.setDocumentType(document.getDocumentType());
        dto.setFileName(document.getFileName());
        dto.setFileType(document.getFileType());
        dto.setUploadedAt(document.getUploadedAt());
        return dto;
    }

    private DeathDonationRequest getRequestEntity(String requestNo) {
        return requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Death donation request not found"));
    }

    private Member getActiveMember(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new RuntimeException("Death donation is only available for members with Active status");
        }

        return member;
    }

    private boolean isEditable(DeathDonationRequestStatus status) {
        return status == DeathDonationRequestStatus.NEW
                || status == DeathDonationRequestStatus.INCOMPLETE;
    }

    private boolean isSubmittable(DeathDonationRequestStatus status) {
        return status == DeathDonationRequestStatus.NEW
                || status == DeathDonationRequestStatus.INCOMPLETE;
    }

    private boolean isOutsideAllowedDateRange(LocalDate requestedDate, LocalDate deceasedDate) {
        return requestedDate.isAfter(deceasedDate.plusMonths(ALLOWED_MONTHS_FROM_DECEASED));
    }

    private String resolvePlaceOfWork(Member member) {
        if (member.getWorkingLocation() != null && !member.getWorkingLocation().isBlank()) {
            return member.getWorkingLocation();
        }
        if (member.getWorkingLocationAddress() != null && !member.getWorkingLocationAddress().isBlank()) {
            return member.getWorkingLocationAddress();
        }
        if (member.getSalaryPayingOffice() != null && !member.getSalaryPayingOffice().isBlank()) {
            return member.getSalaryPayingOffice();
        }
        return null;
    }

    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "DD-" + year + "-";

        return requestRepository.findLastRequestByPrefix(prefix)
                .map(lastRequest -> {
                    String lastNo = lastRequest.getRequestNo();
                    int lastSeq = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesSearch(DeathDonationRequestDTO dto, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String key = search.trim().toLowerCase();
        return contains(dto.getRequestNo(), key)
                || contains(dto.getDeceasedName(), key)
                || contains(dto.getDeathCertificateNumber(), key)
                || contains(dto.getMemberId(), key)
                || contains(dto.getMemberFullName(), key)
                || contains(dto.getMemberNameWithInitials(), key)
                || contains(dto.getMemberNameAsInPayroll(), key)
                || contains(dto.getMemberNic(), key);
    }

    private boolean matchesStatuses(DeathDonationRequestDTO dto, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return true;
        }

        String requestStatus = dto.getStatus() == null ? "" : dto.getStatus().toUpperCase();
        return statuses.stream()
                .map(status -> status == null ? "" : status.trim().toUpperCase())
                .anyMatch(requestStatus::equals);
    }

    private boolean matchesRequestedDateRange(
            DeathDonationRequestDTO dto,
            String fromDate,
            String toDate
    ) {
        if ((fromDate == null || fromDate.isBlank()) && (toDate == null || toDate.isBlank())) {
            return true;
        }

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            return false;
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());

        if (fromDate != null && !fromDate.isBlank()) {
            LocalDate from = LocalDate.parse(fromDate);
            if (requestedDate.isBefore(from)) {
                return false;
            }
        }

        if (toDate != null && !toDate.isBlank()) {
            LocalDate to = LocalDate.parse(toDate);
            if (requestedDate.isAfter(to)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesLocation(DeathDonationRequestDTO dto, List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return true;
        }

        for (String locationKey : locations) {
            if (locationKey == null || locationKey.isBlank() || "all".equalsIgnoreCase(locationKey)) {
                return true;
            }

            String normalized = locationKey.trim().replace("_", " ").toLowerCase();
            if (contains(dto.getMemberWorkingLocation(), normalized)
                    || contains(dto.getMemberEducationalDistrict(), normalized)) {
                return true;
            }
        }

        return false;
    }

    private int compareForSort(
            DeathDonationRequestDTO left,
            DeathDonationRequestDTO right,
            String sortBy,
            String sortOrder
    ) {
        int result;

        if ("deceasedDate".equalsIgnoreCase(sortBy)) {
            result = compareDates(left.getDeceasedDate(), right.getDeceasedDate());
        } else if ("status".equalsIgnoreCase(sortBy)) {
            result = compareStrings(left.getStatus(), right.getStatus());
        } else if ("memberId".equalsIgnoreCase(sortBy)) {
            result = compareStrings(left.getMemberId(), right.getMemberId());
        } else {
            result = compareDates(left.getRequestedDate(), right.getRequestedDate());
        }

        return "desc".equalsIgnoreCase(sortOrder) ? -result : result;
    }

    private int compareDates(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareStrings(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareToIgnoreCase(right);
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private void deleteDocumentFile(DeathDonationDocument document) {
        if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
            s3Service.deleteFile(document.getFilePath());
        }
    }

    private String formatDocumentType(String documentType) {
        return switch (documentType) {
            case "DEATH_CERTIFICATE" -> "Death Certificate";
            case "NIC_COPY" -> "NIC Copy";
            default -> documentType.replace('_', ' ');
        };
    }
}
