package com.memberconnect.backend.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;

/**
 * The Scholarship role/permission matrix — the single place that decides which role
 * holds which right, for both Grade 5 (G5_*) and University (US_*) Scholarships.
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
 * Roles absent from a module's grants (DEATH_DONATION_OFFICER everywhere) hold no
 * rights there at all, rather than falling through to a permissive default.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        // Super Admin holds everything, including rights added in future. Listing it
        // as allOf rather than an explicit set means a new Permission constant can
        // never be accidentally locked away from the only always-seeded account.
        MATRIX.put(Role.SUPER_ADMIN, EnumSet.allOf(Permission.class));

        // District Office — MMS01-MMS05. Raises and maintains requests; cannot approve,
        // cannot deactivate, cannot reopen a Board rejection.
        MATRIX.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_EXAM_MASTER_VIEW,
                // University scholarship requests (MMS21-MMS25) only.
                //
                // Fund requests (MMS42-MMS47) are deliberately absent in full — not even
                // US_FUND_VIEW. Briefly granted on 2026-08-19 and revoked on 2026-08-20:
                // the disbursement track belongs to Head Office / Board Secretary, and
                // this role neither raises nor reads it. Because US_FUND_VIEW is what
                // canAccessFundRequests() keys on, dropping it also removes the Fund
                // Requests item from this role's sidebar.
                Permission.US_REQUEST_VIEW,
                Permission.US_REQUEST_CREATE,
                Permission.US_REQUEST_EDIT,
                Permission.US_REQUEST_SUBMIT,
                Permission.US_REQUEST_INCOMPLETE,
                Permission.US_MASTER_VIEW));

        // Head Office — MMS06-MMS19. Owns the approval track end to end.
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
                // University board track (MMS27-MMS41) and fund requests end to end
                // (MMS42-MMS47). Deliberately WITHOUT US_COMMITTEE_APPROVE, so the
                // Committee gate is not cleared by the same office that runs the Board.
                //
                // US_FUND_APPROVE was granted here on 2026-08-19 by product decision:
                // Head Office raises fund requests, so it also decides them. This is a
                // knowing relaxation of the split that used to keep US_APPROVED_EDIT
                // (changing a payee's bank account) apart from releasing payment into
                // that account — both now sit with this role. If that pairing ever has
                // to be broken again, move US_FUND_APPROVE out of HEAD_OFFICE and leave
                // it with BOARD_SECRETARY, which is where it lived before.
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
                // Fund request status changes from View Mode (New <-> Inactive).
                // Withheld from District Office, which raises fund requests.
                Permission.US_FUND_SET_INACTIVE,
                Permission.US_FUND_REOPEN,
                // MMS48 — hand an approved fund request to the Finance Module. Granted
                // here on 2026-08-20 alongside ACCOUNTS, which keeps it as the actual
                // Finance Department. Deliberately NOT added to BOARD_SECRETARY: that
                // role mirrors Head Office on the board track, not on finance.
                Permission.US_FINANCE_DISBURSE,
                Permission.US_MASTER_VIEW));

        // Board Secretary — the same approval track, plus delete privileges. Mirrors
        // DELETE_RIGHTS_ROLES in the frontend's Member Registration matrix so the two
        // modules do not disagree about who may destroy an approval list.
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
                // Everything Head Office holds on the University side, including
                // US_FUND_APPROVE.
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
                Permission.US_MASTER_VIEW));

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

        // Accounts — "Head Office - Finance Department" in 2.2.1. Read-only on the
        // scholarship side; owns disbursement once MMS20 exists.
        MATRIX.put(Role.ACCOUNTS, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_LIST_VIEW,
                Permission.G5_FINANCE_DISBURSE,
                Permission.US_REQUEST_VIEW,
                Permission.US_LIST_VIEW,
                Permission.US_FUND_VIEW,
                Permission.US_FINANCE_DISBURSE));

        // DEATH_DONATION_OFFICER is intentionally absent — no Grade 5 rights.
    }

    private RolePermissions() {
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
