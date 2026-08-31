package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.CauseOfDeathDTO;
import com.memberconnect.backend.dto.MemberDeathDocumentDTO;
import com.memberconnect.backend.dto.MemberDeathRecordDTO;
import com.memberconnect.backend.service.MemberDeathRecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Record Member Death (SRS section 4, MMT18-MMT25).
 *
 * Roles here mirror the SRS actors. Entry and editing belong to the District
 * Office; the three decision levels each belong to their own role, which is what
 * stops one clerk walking a record from submission to approval on their own. The
 * service repeats every check at runtime (assertMayDecideAtCurrentLevel,
 * assertCallerMayAccess), so these annotations are the outer gate, not the only
 * one.
 */
@RestController
@RequestMapping("/api/member-death-records")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberDeathRecordController {

    /** Anyone who takes part in the death workflow may read it. */
    private static final String READ_ROLES =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** Only the District Office raises and edits records (MMT18/MMT21). */
    private static final String ENTRY_ROLES = "hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')";

    private final MemberDeathRecordService memberDeathRecordService;

    public MemberDeathRecordController(MemberDeathRecordService memberDeathRecordService) {
        this.memberDeathRecordService = memberDeathRecordService;
    }

    // ---------------- Reads ----------------

    @GetMapping("/cause-of-death")
    @PreAuthorize(READ_ROLES)
    public List<CauseOfDeathDTO> getCauseOfDeathOptions() {
        return memberDeathRecordService.getCauseOfDeathOptions();
    }

    /**
     * MMT19. {@code locations} is honoured for Head Office and committee users;
     * a District Office user is pinned to their own district regardless of what
     * they ask for.
     */
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<MemberDeathRecordDTO> searchRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false, defaultValue = "informedDate") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) List<String> locations
    ) {
        return memberDeathRecordService.searchRequests(
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder,
                locations
        );
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize(READ_ROLES)
    public List<MemberDeathRecordDTO> getRecordsByMember(@PathVariable String memberId) {
        return memberDeathRecordService.getRecordsByMember(memberId);
    }

    @GetMapping("/member/{memberId}/active")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<MemberDeathRecordDTO> getActiveRecordForMember(@PathVariable String memberId) {
        MemberDeathRecordDTO record = memberDeathRecordService.getActiveRecordForMember(memberId);
        if (record == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(record);
    }

    @GetMapping("/{recordNo}")
    @PreAuthorize(READ_ROLES)
    public MemberDeathRecordDTO getRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.getRecordByRecordNo(recordNo);
    }

    // ---------------- Entry and editing (MMT18 / MMT21) ----------------

    @PostMapping("/{memberId}")
    @PreAuthorize(ENTRY_ROLES)
    public MemberDeathRecordDTO saveRecord(
            @PathVariable String memberId,
            @RequestBody MemberDeathRecordDTO dto
    ) {
        return memberDeathRecordService.saveRecord(memberId, dto);
    }

    @PutMapping("/{recordNo}")
    @PreAuthorize(ENTRY_ROLES)
    public MemberDeathRecordDTO updateRecord(
            @PathVariable String recordNo,
            @RequestBody MemberDeathRecordDTO dto
    ) {
        dto.setRecordNo(recordNo);
        MemberDeathRecordDTO existing = memberDeathRecordService.getRecordByRecordNo(recordNo);
        return memberDeathRecordService.saveRecord(existing.getMemberId(), dto);
    }

    @PostMapping("/{recordNo}/submit")
    @PreAuthorize(ENTRY_ROLES)
    public MemberDeathRecordDTO submitRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.submitRecord(recordNo);
    }

    @PutMapping("/{recordNo}/mark-incomplete")
    @PreAuthorize(ENTRY_ROLES)
    public MemberDeathRecordDTO markIncomplete(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.markIncomplete(recordNo, body.get("reason"));
    }

    /**
     * Manual status change within the MMT21 matrix only. Making a record inactive
     * additionally needs inactive rights, which the service checks.
     */
    @PutMapping("/{recordNo}/change-status")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    public MemberDeathRecordDTO changeStatus(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.changeStatus(recordNo, body.get("status"));
    }

    // ---------------- Decisions (MMT22 / MMT23 / MMT24) ----------------

    /**
     * Approve at the level the record currently sits. The annotation admits every
     * decision role; the service then requires the caller to own the level the
     * record is actually at, so a District Committee user cannot approve a record
     * still waiting on the District Office.
     */
    @PutMapping("/{recordNo}/approve")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')")
    public MemberDeathRecordDTO approveRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.approveRecord(recordNo);
    }

    @PutMapping("/{recordNo}/reject")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')")
    public MemberDeathRecordDTO rejectRecord(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.rejectRecord(recordNo, body.get("reason"));
    }

    /** MMT22: escalate from the District Office to the District Committee. */
    @PutMapping("/{recordNo}/forward-to-district-committee")
    @PreAuthorize(ENTRY_ROLES)
    public MemberDeathRecordDTO forwardToDistrictCommittee(
            @PathVariable String recordNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return memberDeathRecordService.forwardToNextLevel(recordNo, concernsOf(body));
    }

    /** MMT23: escalate from the District Committee to the P&D Committee. */
    @PutMapping("/{recordNo}/forward-to-pd-committee")
    @PreAuthorize("hasAnyRole('DISTRICT_COMMITTEE','SUPER_ADMIN')")
    public MemberDeathRecordDTO forwardToPdCommittee(
            @PathVariable String recordNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return memberDeathRecordService.forwardToNextLevel(recordNo, concernsOf(body));
    }

    // ---------------- Death Donation entitlement (SRS 4.2.3) ----------------

    /**
     * The SRS refresh button: takes whatever the three operator-editable inputs
     * currently hold and recalculates the rest of the entitlement from them.
     *
     * Open to the committee roles as well as the District Office, because the SRS
     * lets an authorised user adjust these amounts while the record is in View
     * Mode at their level.
     */
    @PostMapping("/{recordNo}/donation/refresh")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')")
    public MemberDeathRecordDTO refreshDonationEntitlement(
            @PathVariable String recordNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return memberDeathRecordService.refreshDonationEntitlement(
                recordNo,
                integerOf(body, "monthsRemitted"),
                decimalOf(body, "receivedPast12Months"),
                decimalOf(body, "creditedToSpecialFixedAccount")
        );
    }

    // ---------------- Documents ----------------
    //
    // Uploads and downloads now go through the generic DocumentController routes
    // (/api/member-death-records/{recordNo}/documents/{requiredDocumentId}/upload
    // and .../documents/{id}/download), which read the required set from the
    // Supporting Documents master.
    //
    // The legacy upload and download handlers that used to live here have been
    // removed rather than deprecated: their paths collided with the generic ones
    // and, because a literal segment outranks a path variable, THIS controller
    // won every time. That is what made an upload arrive here with a numeric
    // required-document id where a document type was expected. The two routes
    // below have no such overlap and stay, so the per-record legacy list remains
    // readable.

    @GetMapping("/{recordNo}/documents")
    @PreAuthorize(READ_ROLES)
    public List<MemberDeathDocumentDTO> getDocuments(@PathVariable String recordNo) {
        return memberDeathRecordService.getDocuments(recordNo);
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize(ENTRY_ROLES)
    public void deleteDocument(@PathVariable Long documentId) {
        memberDeathRecordService.deleteDocument(documentId);
    }

    private String concernsOf(Map<String, String> body) {
        return body != null ? body.get("concerns") : null;
    }

    /** A missing or blank value means "leave it alone", not "set it to zero". */
    private Integer integerOf(Map<String, String> body, String key) {
        String raw = rawOf(body, key);
        return raw != null ? Integer.valueOf(raw) : null;
    }

    private BigDecimal decimalOf(Map<String, String> body, String key) {
        String raw = rawOf(body, key);
        return raw != null ? new BigDecimal(raw) : null;
    }

    private String rawOf(Map<String, String> body, String key) {
        if (body == null) {
            return null;
        }
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
