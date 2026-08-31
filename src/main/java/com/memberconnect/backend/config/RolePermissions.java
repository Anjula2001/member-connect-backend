package com.memberconnect.backend.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;

/**
 * The Grade 5 Scholarship role/permission matrix — the single place that decides
 * which role holds which right.
 *
 * Segregation of duties is the point: District Office raises requests, Head Office
 * approves them. Both SRS sections name "District Office System User" as the approver
 * in their parent Approve/Reject function (MMS06/MMS13, MMS27/MMS34), but every child
 * function underneath them and both process narratives put approval at Head Office.
 * Those actor cells are copy-paste artefacts and were resolved in favour of Head
 * Office: an office that approves its own requests defeats the Board Meeting control
 * both modules are built around.
 *
 * University Scholarships adds a second separation the Grade 5 flow does not have.
 * MMS26 puts a Committee between submission and the Board. The SRS seats that
 * Committee at Head Office, but Head Office also runs the Board step — so holding
 * both would collapse two gates into one signature. US_COMMITTEE_APPROVE is therefore
 * given to SCHOLARSHIP_OFFICER and withheld from the Board roles - with one narrowing
 * added on 2026-08-27: an *authorised* Head Office account holds it through
 * AUTHORITY_GRANTS, so the Committee signature belongs to a named officer rather than
 * to every Head Office login. District Office is still excluded outright, because it
 * raises requests.
 *
 * Roles absent from this map (DEATH_DONATION_OFFICER) hold no Grade 5 rights at all,
 * rather than falling through to a permissive default.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        // Super Admin holds everything, including rights added in future. Listing it
        // as allOf rather than an explicit set means a new Permission constant can
        // never be accidentally locked away from the only always-seeded account.
        MATRIX.put(Role.SUPER_ADMIN, EnumSet.allOf(Permission.class));

        // District Office — MMS01-MMS05 and MMT12-MMT16. On the scholarship side it
        // raises and maintains requests only: cannot approve, deactivate, or reopen a
        // Board rejection.
        //
        // On retirement it owns the request end to end, including approval. That follows
        // MMT16's actor table, which names "District Office System User" as the approver.
        // It does mean the office that raises a retirement request can also approve it —
        // the four-eyes split that Grade 5 keeps does not apply here.
        MATRIX.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_EXAM_MASTER_VIEW,
                // Retirement (MMT12-MMT16): raised, maintained and approved here.
                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_CREATE,
                Permission.RET_REQUEST_EDIT,
                Permission.RET_REQUEST_SUBMIT,
                Permission.RET_REQUEST_INCOMPLETE,
                Permission.RET_REQUEST_APPROVE,
                Permission.RET_REQUEST_RETURN_TO_NEW,
                Permission.RET_REQUEST_SET_INACTIVE,
                // University (MMS21-MMS25): raises and maintains requests only.
                // US_REQUEST_EDIT moved to AUTHORITY_GRANTS on 2026-08-27 — editing a
                // NEW/INCOMPLETE request is an authorised officer's right, not the
                // whole office's.
                Permission.US_REQUEST_VIEW,
                Permission.US_REQUEST_CREATE,
                Permission.US_REQUEST_SUBMIT,
                Permission.US_REQUEST_INCOMPLETE,
                Permission.US_MASTER_VIEW,
                // Member Transfers (MMC27-MMC29). Raises and reads; approval and the
                // Inactive right sit with Head Office.
                Permission.MT_REQUEST_VIEW,
                Permission.MT_REQUEST_CREATE));

        // Head Office — MMS06-MMS19 and MMT16. Owns both approval tracks end to end.
        // Deliberately holds no RET_REQUEST_CREATE: retirement requests are raised at the
        // District Office, and granting creation here would put both halves of the
        // maker/checker split in one office.
        MATRIX.put(Role.HEAD_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_SET_INACTIVE,
                Permission.G5_REQUEST_REOPEN,
                Permission.G5_LIST_VIEW,
                Permission.G5_LIST_CREATE,
                Permission.G5_LIST_PRINT,
                Permission.G5_LIST_PROCESS,
                Permission.G5_LIST_DELETE,
                Permission.G5_EXAM_MASTER_VIEW,
                // Retirement: approves, but never creates (see above).
                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_APPROVE,
                Permission.RET_REQUEST_SET_INACTIVE,
                Permission.RET_REQUEST_RETURN_TO_NEW,
                // University: the Board half plus fund requests (MMS27-MMS48).
                // US_REQUEST_EDIT, US_REQUEST_SET_INACTIVE, US_REQUEST_REOPEN,
                // US_LIST_DELETE and US_COMMITTEE_APPROVE are NOT here as of
                // 2026-08-27 — see AUTHORITY_GRANTS. Nor are US_FUND_EDIT,
                // US_FUND_APPROVE, US_FUND_SET_INACTIVE and US_FUND_REOPEN, moved
                // there the same day: the office raises fund requests, an authorised
                // officer decides and alters them.
                Permission.US_REQUEST_VIEW,
                Permission.US_LIST_VIEW,
                Permission.US_LIST_CREATE,
                Permission.US_LIST_PRINT,
                Permission.US_LIST_PROCESS,
                Permission.US_APPROVED_EDIT,
                Permission.US_FUND_VIEW,
                Permission.US_FUND_CREATE,
                Permission.US_FUND_SUBMIT,
                Permission.US_FUND_INCOMPLETE,
                Permission.US_FINANCE_DISBURSE,
                Permission.US_MASTER_VIEW,
                // Member Transfers (MMC29-MMC30). MMC30 names the District Office as
                // the approver. That was previously resolved here in favour of Head
                // Office, and the product decision of 2026-08-27 reverses it: the
                // decision goes back where MMC30 puts it, with an authorised District
                // Office officer. Head Office is left reading transfers only, plus the
                // Inactive right as an authorised officer — see AUTHORITY_GRANTS.
                Permission.MT_REQUEST_VIEW));

        // Board Secretary — the same Grade 5 approval track, plus delete privileges.
        // Mirrors DELETE_RIGHTS_ROLES in the frontend's Member Registration matrix so the
        // two modules do not disagree about who may destroy an approval list.
        //
        // On the retirement side it gets the housekeeping rights (Inactive, Return to New)
        // that INACTIVE_RIGHTS_ROLES already grants it elsewhere, but NOT
        // RET_REQUEST_APPROVE: MMT16 runs no Board Meeting, so retirement approval stays
        // with Head Office alone.
        MATRIX.put(Role.BOARD_SECRETARY, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_SET_INACTIVE,
                Permission.G5_REQUEST_REOPEN,
                Permission.G5_LIST_VIEW,
                Permission.G5_LIST_CREATE,
                Permission.G5_LIST_PRINT,
                Permission.G5_LIST_PROCESS,
                Permission.G5_LIST_DELETE,
                Permission.G5_EXAM_MASTER_VIEW,
                // Retirement: housekeeping only, no RET_REQUEST_APPROVE (see above).
                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_SET_INACTIVE,
                Permission.RET_REQUEST_RETURN_TO_NEW,
                // University: same Board track, minus US_FINANCE_DISBURSE.
                //
                // Loses US_REQUEST_SET_INACTIVE, US_REQUEST_REOPEN and US_LIST_DELETE
                // on 2026-08-27, and US_FUND_EDIT, US_FUND_APPROVE,
                // US_FUND_SET_INACTIVE and US_FUND_REOPEN the same day. All seven
                // became authorised-officer rights, and UserAdminService forces the
                // authority flag false for this role, so there is no authorised Board
                // Secretary to grant them back to. It still raises and prepares a fund
                // request through US_FUND_CREATE / SUBMIT / INCOMPLETE.
                Permission.US_REQUEST_VIEW,
                Permission.US_LIST_VIEW,
                Permission.US_LIST_CREATE,
                Permission.US_LIST_PRINT,
                Permission.US_LIST_PROCESS,
                Permission.US_APPROVED_EDIT,
                Permission.US_FUND_VIEW,
                Permission.US_FUND_CREATE,
                Permission.US_FUND_SUBMIT,
                Permission.US_FUND_INCOMPLETE,
                Permission.US_MASTER_VIEW,
                // Member Transfers. Reads requests only: MT_REQUEST_SET_INACTIVE went
                // to AUTHORITY_GRANTS on 2026-08-27 and this role cannot carry the
                // flag, so there is no authorised Board Secretary to grant it back to.
                Permission.MT_REQUEST_VIEW));

        // Scholarship Officer — not named as an actor anywhere in the SRS, but it is the
        // the seat chosen for the University Scholarship Committee (MMS26). It reads the
        // Exam Master but does not maintain it: cut-off marks decide who qualifies, so
        // that stays with Super Admin.
        //
        // Note the asymmetry between the two modules, which is deliberate. On Grade 5
        // this role may raise requests, because Grade 5 has no committee step for it to
        // then approve. On University it may NOT raise, edit or submit requests: holding
        // both US_REQUEST_CREATE and US_COMMITTEE_APPROVE would let one person create a
        // request and then clear the very gate that exists to scrutinise it.
        MATRIX.put(Role.SCHOLARSHIP_OFFICER, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_LIST_VIEW,
                // Reads the Exam Master; no longer maintains it. Exam dates and district
                // cut-off marks decide who qualifies for a scholarship, so editing them
                // is Super Admin's alone - see Grade5ExamManagementController.
                Permission.G5_EXAM_MASTER_VIEW,
                // University: Committee only, plus the masters it owns.
                Permission.US_REQUEST_VIEW,
                Permission.US_COMMITTEE_APPROVE,
                Permission.US_LIST_VIEW,
                Permission.US_FUND_VIEW,
                Permission.US_MASTER_VIEW,
                Permission.US_MASTER_MANAGE));

        // Accounts — "Head Office - Finance Department" in 2.2.1 and in the retirement
        // 3.1.1 narrative. Read-only on the scholarship side; owns disbursement once
        // MMS20 exists. Read-only on retirement too: MMT17 hands approved retirements to
        // the Finance Module over an API that does not exist yet, so there is no right to
        // grant for it here.
        MATRIX.put(Role.ACCOUNTS, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_LIST_VIEW,
                Permission.G5_FINANCE_DISBURSE,
                Permission.RET_REQUEST_VIEW,
                Permission.US_REQUEST_VIEW,
                Permission.US_LIST_VIEW,
                Permission.US_FUND_VIEW,
                Permission.US_FINANCE_DISBURSE));

        // SCHOLARSHIP_OFFICER holds no retirement rights — it is not an actor in MMT12-MMT17.
        // DEATH_DONATION_OFFICER is intentionally absent — no Grade 5 or retirement rights.
    }

    /**
     * Rights granted by the per-account authority flag, ON TOP of whatever the role
     * already holds (see User.isAuthorized).
     *
     * Grade 5 puts both of these behind wording the role matrix cannot express. SRS
     * 2.3.4 qualifies "-> INACTIVE" with "the user needs Inactive rights", and reopening
     * a Board rejection is described the same way - a right held by a particular officer
     * rather than by the whole District Office. Withholding them from DISTRICT_OFFICE
     * outright was the closest a role list could get; the authority flag is what the SRS
     * actually describes, so an authorized District Office officer holds them and an
     * ordinary clerk in the same office still does not.
     *
     * HEAD_OFFICE holds both Grade 5 rights already through GRADE5_BOARD; its entry
     * below is University-only. Only DISTRICT_OFFICE and HEAD_OFFICE accounts can carry
     * the flag at all - UserAdminService forces it false for every other role.
     *
     * University Scholarships uses the same mechanism as of 2026-08-27 for four
     * actions, by product decision:
     *
     *   edit a NEW/INCOMPLETE request .... authorised District Office, authorised Head Office
     *   change a request's status ........ authorised District Office, authorised Head Office
     *   committee Approve / Reject ....... authorised Head Office (SCHOLARSHIP_OFFICER too)
     *   delete an approval list .......... authorised Head Office
     *
     * and for three more on the Fund Request side the same day, Head Office only —
     * District Office holds no fund request rights at all, authorised or not:
     *
     *   edit a NEW/INCOMPLETE fund request .... authorised Head Office
     *   change a fund request's status ........ authorised Head Office
     *   fund request Approve / Reject ......... authorised Head Office
     *
     * US_FUND_CREATE, US_FUND_SUBMIT and US_FUND_INCOMPLETE were deliberately left in
     * MATRIX: raising and preparing a fund request stays the whole office's work.
     *
     * Member Transfers joined on the same date, and this one reverses an earlier
     * resolution rather than narrowing an existing one:
     *
     *   Approve / Reject a transfer ....... authorised District Office (nobody else)
     *   change a transfer's status ........ authorised District Office, authorised Head Office
     *
     * MMC30 names the District Office as the approver; this file previously overrode
     * that in favour of Head Office. The override is gone, so HEAD_OFFICE now holds no
     * MT_REQUEST_APPROVE at all and an authorised Head Office officer may cancel a
     * transfer without being able to decide one. MT_REQUEST_CREATE stays in MATRIX for
     * DISTRICT_OFFICE, which makes the flag the only maker/checker control left on this
     * module - it is worth something only while some District Office accounts are left
     * unauthorised.
     *
     * Because User.getAuthorities() emits these alongside the role's own permissions,
     * the existing @PreAuthorize("hasAuthority('US_...')") on each endpoint enforces the
     * narrowing without any controller change.
     */
    private static final Map<Role, Set<Permission>> AUTHORITY_GRANTS = new EnumMap<>(Role.class);

    static {
        AUTHORITY_GRANTS.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_SET_INACTIVE,
                Permission.G5_REQUEST_REOPEN,
                // University (2026-08-27). Edit and status change only. Deliberately no
                // US_COMMITTEE_APPROVE: this role holds US_REQUEST_CREATE, and pairing
                // the two would let one office raise a request and clear the very
                // committee gate that exists to scrutinise it.
                Permission.US_REQUEST_EDIT,
                Permission.US_REQUEST_SET_INACTIVE,
                Permission.US_REQUEST_REOPEN,
                // Member Transfers (2026-08-27). MMC30 seats the decision here, so an
                // authorised officer both decides a transfer and may take one to
                // Inactive. MT_REQUEST_CREATE stays in MATRIX: any District Office
                // clerk raises a transfer, only an authorised officer decides it —
                // which is the whole maker/checker split on this module now.
                Permission.MT_REQUEST_APPROVE,
                Permission.MT_REQUEST_SET_INACTIVE));

        // University (2026-08-27). Head Office holds no US_REQUEST_CREATE, so seating a
        // second Committee signature here does not collapse the maker/checker split the
        // way it would at District Office. SCHOLARSHIP_OFFICER keeps US_COMMITTEE_APPROVE
        // through MATRIX and remains the primary Committee seat.
        AUTHORITY_GRANTS.put(Role.HEAD_OFFICE, EnumSet.of(
                Permission.US_REQUEST_EDIT,
                Permission.US_REQUEST_SET_INACTIVE,
                Permission.US_REQUEST_REOPEN,
                Permission.US_LIST_DELETE,
                Permission.US_COMMITTEE_APPROVE,
                // Fund requests (2026-08-27). US_FUND_CREATE / SUBMIT / INCOMPLETE stay
                // in MATRIX: the whole office raises and prepares a fund request, and
                // only deciding or altering one is narrowed to an authorised officer.
                // That is the maker/checker split the 2026-08-19 decision gave up when
                // it let the office that raises fund requests also approve them.
                Permission.US_FUND_EDIT,
                Permission.US_FUND_APPROVE,
                Permission.US_FUND_SET_INACTIVE,
                Permission.US_FUND_REOPEN,
                // Member Transfers (2026-08-27). The Inactive right only — Head Office
                // holds no MT_REQUEST_APPROVE in any form after MMC30 was restored to
                // the District Office. An authorised Head Office officer may cancel a
                // transfer but may not decide one; that asymmetry is deliberate.
                Permission.MT_REQUEST_SET_INACTIVE));
    }

    private RolePermissions() {
    }

    /** The extra rights this account gains from the authority flag; empty when unset. */
    public static Set<Permission> forAuthority(Role role, boolean authorized) {
        if (role == null || !authorized) {
            return Collections.emptySet();
        }
        return AUTHORITY_GRANTS.getOrDefault(role, Collections.emptySet());
    }

    /** Role rights plus anything the authority flag adds. */
    public static boolean has(Role role, boolean authorized, Permission permission) {
        return has(role, permission) || forAuthority(role, authorized).contains(permission);
    }

    public static Set<Permission> forRole(Role role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return MATRIX.getOrDefault(role, Collections.emptySet());
    }

    public static boolean has(Role role, Permission permission) {
        return forRole(role).contains(permission);
    }
}
