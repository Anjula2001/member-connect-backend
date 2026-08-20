package com.memberconnect.backend.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;

/**
 * The Grade 5 Scholarship and Member Retirement role/permission matrix — the single
 * place that decides which role holds which right.
 *
 * Segregation of duties is the point: District Office raises requests, Head Office
 * approves them. The SRS actor tables for MMS06/MMS13 name "District Office System
 * User" as the approver, but every child function underneath them (MMS07-MMS12,
 * MMS14-MMS19) and the 2.2.1 process narrative put approval at Head Office. That
 * conflict was resolved in favour of Head Office: letting the office that creates a
 * request also approve it defeats the Board Meeting control the SRS is built around.
 *
 * Retirement carries the identical conflict — its MMT16 actor table names "District
 * Office System User" while 3.1.1 says "the Authorized User from the District Office"
 * — and is resolved the same way and for the same reason. The clerk who raises a
 * retirement request must not be able to approve it, so RET_REQUEST_APPROVE is held
 * by Head Office only and DISTRICT_OFFICE holds no approval right at all.
 *
 * Roles absent from this map (DEATH_DONATION_OFFICER) hold no rights at all, rather
 * than falling through to a permissive default.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        // Super Admin holds everything, including rights added in future. Listing it
        // as allOf rather than an explicit set means a new Permission constant can
        // never be accidentally locked away from the only always-seeded account.
        MATRIX.put(Role.SUPER_ADMIN, EnumSet.allOf(Permission.class));

        // District Office — MMS01-MMS05 and MMT12-MMT15. Raises and maintains requests;
        // cannot approve, cannot deactivate, cannot reopen a Board rejection, and cannot
        // pull a retirement request back out of approval.
        MATRIX.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_EXAM_MASTER_VIEW,

                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_CREATE,
                Permission.RET_REQUEST_EDIT,
                Permission.RET_REQUEST_SUBMIT,
                Permission.RET_REQUEST_INCOMPLETE));

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

                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_APPROVE,
                Permission.RET_REQUEST_SET_INACTIVE,
                Permission.RET_REQUEST_RETURN_TO_NEW));

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

                Permission.RET_REQUEST_VIEW,
                Permission.RET_REQUEST_SET_INACTIVE,
                Permission.RET_REQUEST_RETURN_TO_NEW));

        // Scholarship Officer — not named as an actor anywhere in the SRS, but it is the
        // only role whose name fits ownership of the Exam Master (exam dates and district
        // cut-off marks). Given request-entry rights too so the role is usable on its own.
        MATRIX.put(Role.SCHOLARSHIP_OFFICER, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_LIST_VIEW,
                Permission.G5_EXAM_MASTER_VIEW,
                Permission.G5_EXAM_MASTER_MANAGE));

        // Accounts — "Head Office - Finance Department" in 2.2.1 and in the retirement
        // 3.1.1 narrative. Read-only on the scholarship side; owns disbursement once
        // MMS20 exists. Read-only on retirement too: MMT17 hands approved retirements to
        // the Finance Module over an API that does not exist yet, so there is no right to
        // grant for it here.
        MATRIX.put(Role.ACCOUNTS, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_LIST_VIEW,
                Permission.G5_FINANCE_DISBURSE,

                Permission.RET_REQUEST_VIEW));

        // SCHOLARSHIP_OFFICER holds no retirement rights — it is not an actor in MMT12-MMT17.
        // DEATH_DONATION_OFFICER is intentionally absent — no Grade 5 or retirement rights.
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
