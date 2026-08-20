package com.memberconnect.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.ProfileChangeListItemDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.ProfileChangeSortBy;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.enums.RequestReceivedOn;
import com.memberconnect.backend.service.ProfileChangeSearchService;

/**
 * The unified "All Member Profile Change Requests List" (Requirement 02,
 * MMC02 / MMC06 / MMC15 / MMC19).
 *
 * One endpoint replaces the four separate "get all" calls the list screen used to make,
 * which is what allows Type, Status, Location, Received On, Search and Sort to be
 * applied across the types rather than within one of them at a time.
 */
@RestController
@RequestMapping("/api/profile-changes")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE')")
public class ProfileChangeController {

    private final ProfileChangeSearchService searchService;

    public ProfileChangeController(ProfileChangeSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Every filter is optional. Omitting types searches all four; omitting statuses
     * returns every status — the SRS's default of "Submitted for Approval" is a
     * default on the screen, not a server-side one, so that the same endpoint can also
     * back the approval-list builder, which needs Rejected rows too.
     */
    @GetMapping
    public List<ProfileChangeListItemDTO> search(
            @RequestParam(required = false) List<ProfileChangeType> types,
            @RequestParam(required = false) List<ApplicationStatus> statuses,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) RequestReceivedOn receivedOn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProfileChangeSortBy sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean descending
    ) {
        return searchService.search(
                types,
                statuses,
                locations,
                receivedOn,
                from,
                to,
                search,
                sortBy,
                descending
        );
    }

    /**
     * MMC02: "If the logged in user has only access to the current district location,
     * this field will be un-editable and the current location will be auto selected."
     *
     * Enforced here rather than only in the dropdown, because a disabled control is not
     * an access rule — a district user editing the request could otherwise list every
     * other district's requests. Head Office and above keep whatever they asked for,
     * including nothing, which means all locations.
     */
    /*
     * There is deliberately no district lock here.
     *
     * MMC02 describes the Location filter as un-editable and auto-selected for a user
     * with access to a single district, and this controller used to enforce that by
     * replacing any requested location with the caller's own assignedDistrict. The
     * client has since settled the rule the other way: District Office searches all
     * locations, which fits MMC01's "The Member can go to any District Office and
     * request to change their profile information irrespective of the district of
     * their working address" - a request raised in one district is routinely about a
     * member working in another.
     *
     * The lock was also silently breaking the screen. A request stores the member's
     * district as its submissionLocation, so a District Office user assigned to any
     * other district searched their own district, matched nothing, and saw an empty
     * list with no error to explain it.
     */
}
