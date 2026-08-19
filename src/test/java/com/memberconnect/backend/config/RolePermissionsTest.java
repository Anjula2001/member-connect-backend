package com.memberconnect.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;

/**
 * Locks in the Grade 5 role/permission matrix.
 *
 * These are not exhaustive coverage of the map — they pin the handful of grants where
 * getting it wrong is a control failure rather than an inconvenience, so that a future
 * edit to RolePermissions has to be deliberate about them.
 */
class RolePermissionsTest {

    @Test
    void districtOfficeRaisesRequestsButCannotApproveThem() {
        assertTrue(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_REQUEST_CREATE));
        assertTrue(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_REQUEST_SUBMIT));

        // The segregation of duties the whole matrix exists to enforce: the office that
        // creates a request must not be able to approve it, list it, or delete a list.
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_LIST_CREATE));
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_LIST_PROCESS));
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_LIST_DELETE));
    }

    @Test
    void districtOfficeCannotDeactivateOrReopenARejection() {
        // SRS 2.3.4 gates both of these behind rights held above ordinary editing.
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_REQUEST_SET_INACTIVE));
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_REQUEST_REOPEN));
    }

    @Test
    void headOfficeOwnsTheApprovalTrackButDoesNotCreateRequests() {
        assertTrue(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_LIST_CREATE));
        assertTrue(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_LIST_PROCESS));
        assertTrue(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_LIST_PRINT));
        assertTrue(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_REQUEST_SET_INACTIVE));
        assertTrue(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_REQUEST_REOPEN));

        assertFalse(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_REQUEST_CREATE));
    }

    @Test
    void examMasterIsWritableOnlyByScholarshipOfficerAndSuperAdmin() {
        assertTrue(RolePermissions.has(Role.SCHOLARSHIP_OFFICER, Permission.G5_EXAM_MASTER_MANAGE));
        assertTrue(RolePermissions.has(Role.SUPER_ADMIN, Permission.G5_EXAM_MASTER_MANAGE));

        // Everyone else may read cut-off marks (the request entry screen needs them)
        // but not rewrite them.
        assertTrue(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_EXAM_MASTER_VIEW));
        assertFalse(RolePermissions.has(Role.DISTRICT_OFFICE, Permission.G5_EXAM_MASTER_MANAGE));
        assertFalse(RolePermissions.has(Role.HEAD_OFFICE, Permission.G5_EXAM_MASTER_MANAGE));
    }

    @Test
    void deathDonationOfficerHoldsNoGrade5RightsAtAll() {
        // Previously this role reached the whole module by falling through a catch-all
        // branch in the sidebar. Absence from the matrix must mean no access, not
        // default access.
        for (Permission permission : Permission.values()) {
            assertFalse(
                    RolePermissions.has(Role.DEATH_DONATION_OFFICER, permission),
                    "DEATH_DONATION_OFFICER should not hold " + permission);
        }
    }

    @Test
    void accountsIsReadOnlyApartFromDisbursement() {
        assertTrue(RolePermissions.has(Role.ACCOUNTS, Permission.G5_REQUEST_VIEW));
        assertTrue(RolePermissions.has(Role.ACCOUNTS, Permission.G5_FINANCE_DISBURSE));

        assertFalse(RolePermissions.has(Role.ACCOUNTS, Permission.G5_REQUEST_CREATE));
        assertFalse(RolePermissions.has(Role.ACCOUNTS, Permission.G5_LIST_PROCESS));
    }

    @Test
    void superAdminHoldsEveryPermissionIncludingFutureOnes() {
        // Declared as EnumSet.allOf, so a newly added Permission constant is granted
        // automatically rather than being locked away from the only seeded account.
        for (Permission permission : Permission.values()) {
            assertTrue(
                    RolePermissions.has(Role.SUPER_ADMIN, permission),
                    "SUPER_ADMIN should hold " + permission);
        }
    }

    @Test
    void aNullRoleHoldsNothing() {
        assertFalse(RolePermissions.has(null, Permission.G5_REQUEST_VIEW));
        assertTrue(RolePermissions.forRole(null).isEmpty());
    }
}
