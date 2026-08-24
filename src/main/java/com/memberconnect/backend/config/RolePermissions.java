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
 * given to SCHOLARSHIP_OFFICER and withheld from the Board roles.
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
                Permission.US_REQUEST_VIEW,
                Permission.US_REQUEST_CREATE,
                Permission.US_REQUEST_EDIT,
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
                Permission.US_REQUEST_VIEW,
                Permission.US_REQUEST_SET_INACTIVE,
                Permission.US_REQUEST_REOPEN,
                Permission.US_LIST_VIEW,
                Permission.US_LIST_CREATE,
                Permission.US_LIST_PRINT,
                Permission.US_LIST_PROCESS,
                Permission.US_LIST_DELETE,
                Permission.US_APPROVED_EDIT,
                Permission.US_FUND_VIEW,
                Permission.US_FUND_CREATE,
                Permission.US_FUND_EDIT,
                Permission.US_FUND_SUBMIT,
                Permission.US_FUND_INCOMPLETE,
                Permission.US_FUND_APPROVE,
                Permission.US_FUND_SET_INACTIVE,
                Permission.US_FUND_REOPEN,
                Permission.US_FINANCE_DISBURSE,
                Permission.US_MASTER_VIEW,
                // Member Transfers (MMC29-MMC30). MMC30 names the District Office as
                // the approver, resolved here in favour of Head Office for the reason
                // given at the top of this file: the office that raises a request does
                // not approve it. District Office keeps VIEW and CREATE only.
                Permission.MT_REQUEST_VIEW,
                Permission.MT_REQUEST_APPROVE,
                Permission.MT_REQUEST_SET_INACTIVE));

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
                Permission.US_REQUEST_VIEW,
                Permission.US_REQUEST_SET_INACTIVE,
                Permission.US_REQUEST_REOPEN,
                Permission.US_LIST_VIEW,
                Permission.US_LIST_CREATE,
                Permission.US_LIST_PRINT,
                Permission.US_LIST_PROCESS,
                Permission.US_LIST_DELETE,
                Permission.US_APPROVED_EDIT,
                Permission.US_FUND_VIEW,
                Permission.US_FUND_CREATE,
                Permission.US_FUND_EDIT,
                Permission.US_FUND_SUBMIT,
                Permission.US_FUND_INCOMPLETE,
                Permission.US_FUND_APPROVE,
                Permission.US_FUND_SET_INACTIVE,
                Permission.US_FUND_REOPEN,
                Permission.US_MASTER_VIEW,
                // Member Transfers. Mirrors this role's standing elsewhere: it reads
                // requests and holds the Inactive right, but approval of a transfer
                // stays with Head Office.
                Permission.MT_REQUEST_VIEW,
                Permission.MT_REQUEST_SET_INACTIVE));

        // Scholarship Officer — not named as an actor anywhere in the SRS, but it is the
        // only role whose name fits ownership of the Exam Master (exam dates and district
        // cut-off marks), and it is the seat chosen for the University Scholarship
        // Committee (MMS26).
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
                Permission.G5_EXAM_MASTER_VIEW,
                Permission.G5_EXAM_MASTER_MANAGE,
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
     * HEAD_OFFICE is absent because it already holds both through GRADE5_BOARD. Only
     * DISTRICT_OFFICE and HEAD_OFFICE accounts can carry the flag at all -
     * UserAdminService forces it false for every other role.
     */
    private static final Map<Role, Set<Permission>> AUTHORITY_GRANTS = new EnumMap<>(Role.class);

    static {
        AUTHORITY_GRANTS.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_SET_INACTIVE,
                Permission.G5_REQUEST_REOPEN));
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
