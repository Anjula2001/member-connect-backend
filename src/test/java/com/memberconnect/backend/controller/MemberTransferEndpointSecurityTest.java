package com.memberconnect.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.memberconnect.backend.config.RolePermissions;
import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;

/**
 * Pins the Member Transfer role matrix (SRS section 6, MMC27-MMC30) to the controller.
 *
 * Reflection rather than a running server, like the other endpoint security tests: it
 * needs no Spring context and fails the moment a transfer endpoint is added without an
 * annotation - the regression actually worth catching, since this controller had none
 * at all until the rights were introduced.
 */
class MemberTransferEndpointSecurityTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    private static final String VIEW = "hasAuthority('MT_REQUEST_VIEW')";
    private static final String CREATE = "hasAuthority('MT_REQUEST_CREATE')";
    private static final String APPROVE = "hasAuthority('MT_REQUEST_APPROVE')";
    private static final String SET_INACTIVE = "hasAuthority('MT_REQUEST_SET_INACTIVE')";

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("getAllRequests", VIEW);
        map.put("getRequestById", VIEW);
        map.put("getRequestAwaitingApproval", VIEW);
        map.put("submitRequest", CREATE);
        map.put("changeRequestStatus", SET_INACTIVE);
        map.put("approveRequest", APPROVE);
        map.put("rejectRequest", APPROVE);
        return map;
    }

    @Test
    void everyMemberTransferEndpointCarriesAPreAuthorize() {
        List<String> unguarded = handlerMethods().stream()
                .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("every Member Transfer endpoint must state who may call it")
                .isEmpty();
    }

    @Test
    void theRightsMatrixMatchesTheSrs() {
        Map<String, String> actual = handlerMethods().stream()
                .collect(Collectors.toMap(
                        Method::getName,
                        method -> normalise(method.getAnnotation(PreAuthorize.class).value()),
                        (a, b) -> a,
                        LinkedHashMap::new));

        assertThat(actual)
                .as("the annotations and the SRS role matrix must not drift apart")
                .containsExactlyInAnyOrderEntriesOf(expected());
    }

    /**
     * MMC30's actor cell names the District Office, resolved in favour of Head Office:
     * the office that raises a transfer must not be the office that approves it. This
     * is the assertion that fails if anyone grants both to one role.
     */
    @Test
    void noRoleBothRaisesAndApprovesATransfer() {
        Set<Role> conflicted = EnumSet.noneOf(Role.class);

        for (Role role : Role.values()) {
            if (role == Role.SUPER_ADMIN) {
                // Holds everything by design; it is the seeded administrator, not an
                // office taking part in the workflow.
                continue;
            }
            Set<Permission> held = RolePermissions.forRole(role);
            if (held.contains(Permission.MT_REQUEST_CREATE)
                    && held.contains(Permission.MT_REQUEST_APPROVE)) {
                conflicted.add(role);
            }
        }

        assertThat(conflicted)
                .as("a role that raises a transfer must not also approve it")
                .isEmpty();
    }

    @Test
    void headOfficeApprovesAndDistrictOfficeRaises() {
        assertThat(RolePermissions.forRole(Role.HEAD_OFFICE))
                .contains(Permission.MT_REQUEST_APPROVE, Permission.MT_REQUEST_SET_INACTIVE);

        assertThat(RolePermissions.forRole(Role.DISTRICT_OFFICE))
                .contains(Permission.MT_REQUEST_CREATE, Permission.MT_REQUEST_VIEW)
                .doesNotContain(Permission.MT_REQUEST_APPROVE, Permission.MT_REQUEST_SET_INACTIVE);
    }

    private static List<Method> handlerMethods() {
        return Arrays.stream(MemberTransferController.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(MemberTransferEndpointSecurityTest::isHandler)
                .toList();
    }

    private static boolean isHandler(Method method) {
        return MAPPINGS.stream().anyMatch(mapping -> method.getAnnotation(mapping) != null);
    }

    /** The expressions are split across source lines; compare them whitespace-free. */
    private static String normalise(String expression) {
        return expression.replaceAll("\\s+", "");
    }
}
