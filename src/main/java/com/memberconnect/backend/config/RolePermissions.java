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
 * approves them. The SRS actor tables for MMS06/MMS13 name "District Office System
 * User" as the approver, but every child function underneath them (MMS07-MMS12,
 * MMS14-MMS19) and the 2.2.1 process narrative put approval at Head Office. That
 * conflict was resolved in favour of Head Office: letting the office that creates a
 * request also approve it defeats the Board Meeting control the SRS is built around.
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

        // District Office — MMS01-MMS05. Raises and maintains requests; cannot approve,
        // cannot deactivate, cannot reopen a Board rejection.
        MATRIX.put(Role.DISTRICT_OFFICE, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_REQUEST_CREATE,
                Permission.G5_REQUEST_EDIT,
                Permission.G5_REQUEST_SUBMIT,
                Permission.G5_REQUEST_INCOMPLETE,
                Permission.G5_EXAM_MASTER_VIEW));

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
                Permission.G5_EXAM_MASTER_VIEW));

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
                Permission.G5_EXAM_MASTER_VIEW));

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

        // Accounts — "Head Office - Finance Department" in 2.2.1. Read-only on the
        // scholarship side; owns disbursement once MMS20 exists.
        MATRIX.put(Role.ACCOUNTS, EnumSet.of(
                Permission.G5_REQUEST_VIEW,
                Permission.G5_LIST_VIEW,
                Permission.G5_FINANCE_DISBURSE));

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
