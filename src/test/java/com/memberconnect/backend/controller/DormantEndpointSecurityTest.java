package com.memberconnect.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pins the Dormant Membership role matrix (SRS Requirement 05, section 4) to the
 * controller.
 *
 * Reflection rather than a running server on purpose: it needs no Spring context
 * and no database - which matters here, because this project's integration tests
 * point at a shared remote Postgres - and it fails the moment somebody adds a
 * dormant endpoint without an annotation. Before this module was locked down all
 * thirteen of its endpoints answered to any authenticated user, so that is the
 * regression most worth catching. The runtime behaviour of these expressions is
 * covered separately by DormantAuthorizationTest against the service.
 */
class DormantEndpointSecurityTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    /** MMD12, plus the District Office read-only view admitted by SRS 4.2.3. */
    private static final String READ =
            "hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE','SUPER_ADMIN')";

    /** The dormancy period decides who gets inactivated - membership policy. */
    private static final String CONFIG_WRITE = "hasRole('SUPER_ADMIN')";

    /** MMD11: "Authorized Head Office System User". */
    private static final String IDENTIFICATION = "hasAnyRole('HEAD_OFFICE','SUPER_ADMIN')";

    /** MMD13/14/16/17/18: the board half. */
    private static final String BOARD = "hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** MMD15: "delete privileges", narrower than ordinary Head Office access. */
    private static final String DELETE = "hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')";

    private static final Map<String, String> EXPECTED = expected();

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();

        // Configuration. Reading the settings comes with the screen; changing
        // them does not.
        map.put("getConfig", READ);
        map.put("updateConfig", CONFIG_WRITE);

        // MMD11 manual identification run.
        map.put("runIdentification", IDENTIFICATION);

        // MMD12 view / search, filter metadata included - the dropdowns are
        // scoped by the service the same way the results are.
        map.put("getLocations", READ);
        map.put("getMemberTypes", READ);
        map.put("search", READ);

        // MMD13 / MMD14 / MMD17 / MMD18 - the board half.
        map.put("createApprovalList", BOARD);
        map.put("getAllApprovalLists", BOARD);
        map.put("getApprovalList", BOARD);
        map.put("getApprovalListMembers", BOARD);
        map.put("processApprovalList", BOARD);

        // MMD15 deletion is a separate privilege, so Head Office is absent here
        // even though it is present on every other board operation.
        map.put("deleteApprovalList", DELETE);

        return map;
    }

    @Test
    void everyDormantEndpointCarriesAPreAuthorize() {
        List<String> unguarded = handlerMethods(DormantMembershipController.class).stream()
                .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("every Dormant Membership endpoint must state who may call it")
                .isEmpty();
    }

    @Test
    void theRoleMatrixMatchesTheSrs() {
        Map<String, String> actual = handlerMethods(DormantMembershipController.class).stream()
                .collect(Collectors.toMap(
                        Method::getName,
                        method -> normalise(method.getAnnotation(PreAuthorize.class).value()),
                        (a, b) -> a,
                        LinkedHashMap::new));

        assertThat(actual)
                .as("the annotations and the SRS role matrix must not drift apart")
                .containsExactlyInAnyOrderEntriesOf(normalised(EXPECTED));
    }

    /**
     * SRS section 4 names only the Head Office System User and - for the view
     * functions - a district-scoped user. The two committees are approval levels
     * for Member Death and Death Donations, not for dormancy, and the three
     * officer roles are actors in neither; all five would be easy to add here by
     * reflex from a neighbouring controller. This fails if anybody does.
     */
    @Test
    void rolesWithNoPartInTheDormantFlowAppearNowhere() {
        String allExpressions = handlerMethods(DormantMembershipController.class).stream()
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(annotation -> annotation != null)
                .map(PreAuthorize::value)
                .collect(Collectors.joining(" "));

        assertThat(allExpressions)
                .as("DISTRICT_COMMITTEE is not an actor in SRS section 4")
                .doesNotContain("DISTRICT_COMMITTEE")
                .as("PD_COMMITTEE is not an actor in SRS section 4")
                .doesNotContain("PD_COMMITTEE")
                .as("ACCOUNTS is not an actor in SRS section 4")
                .doesNotContain("ACCOUNTS")
                .as("SCHOLARSHIP_OFFICER is not an actor in SRS section 4")
                .doesNotContain("SCHOLARSHIP_OFFICER")
                .as("DEATH_DONATION_OFFICER is not an actor in SRS section 4")
                .doesNotContain("DEATH_DONATION_OFFICER");
    }

    /**
     * The District Office read-only view is the one asymmetry in this matrix
     * that is easy to widen by accident - READ and BOARD differ by exactly that
     * role. Nothing that writes may carry it.
     */
    @Test
    void districtOfficeMayReadButNeverWrite() {
        List<String> writeHandlers = List.of(
                "updateConfig", "runIdentification", "createApprovalList",
                "processApprovalList", "deleteApprovalList");

        Map<String, String> actual = handlerMethods(DormantMembershipController.class).stream()
                .filter(method -> method.getAnnotation(PreAuthorize.class) != null)
                .collect(Collectors.toMap(
                        Method::getName,
                        method -> method.getAnnotation(PreAuthorize.class).value(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        assertThat(actual)
                .as("every write handler must be present to be checked")
                .containsKeys(writeHandlers.toArray(String[]::new));

        writeHandlers.forEach(handler ->
                assertThat(actual.get(handler))
                        .as("%s writes, so DISTRICT_OFFICE must not appear on it", handler)
                        .doesNotContain("DISTRICT_OFFICE"));
    }

    private static Map<String, String> normalised(Map<String, String> source) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> normalise(entry.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static List<Method> handlerMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(DormantEndpointSecurityTest::isHandler)
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
