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
 * Pins the Death Donation role matrix (SRS Requirement 05, section 2) to the
 * controller.
 *
 * Reflection rather than a running server on purpose: it needs no Spring context
 * and no database - which matters here, because this project's integration tests
 * point at a shared remote Postgres - and it fails the moment somebody adds a
 * death donation endpoint without an annotation, which is exactly the regression
 * worth catching. The runtime behaviour of these expressions is covered
 * separately by DeathDonationAuthorizationTest against the service.
 */
class DeathDonationEndpointSecurityTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    /** Everyone who takes part in the workflow may read (MMD02 / MMD03). */
    private static final String READ =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** Only the District Office raises and edits (MMD01 / MMD04). */
    private static final String ENTRY = "hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')";

    /** The three decision levels (MMD05 / MMD06 / MMD07). */
    private static final String DECIDE =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')";

    /** MMD04 manual status changes, including the Inactive right. */
    private static final String STATUS_CHANGE =
            "hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** MMD06 escalation belongs to the District Committee alone. */
    private static final String FORWARD_TO_PD = "hasAnyRole('DISTRICT_COMMITTEE','SUPER_ADMIN')";

    private static final Map<String, String> EXPECTED = expected();

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();

        // Reads (MMD02 / MMD03)
        map.put("searchRequests", READ);
        map.put("getRelationshipOptions", READ);
        map.put("getRequestsByMember", READ);
        map.put("getRequest", READ);
        map.put("refreshRelatives", READ);
        map.put("getRequiredDocuments", READ);
        map.put("getDocuments", READ);
        map.put("downloadDocument", READ);

        // Entry and editing (MMD01 / MMD04)
        map.put("saveRequest", ENTRY);
        map.put("submitRequest", ENTRY);
        map.put("markIncomplete", ENTRY);
        map.put("populateDeceasedMember", ENTRY);
        map.put("uploadDocument", ENTRY);
        map.put("deleteDocument", ENTRY);
        map.put("forwardToDistrictCommittee", ENTRY);

        // Update doubles as the Concerns Identified path once a request is
        // locked, which the SRS opens to every approving level.
        map.put("updateRequest", DECIDE);
        map.put("updateConcerns", DECIDE);

        // Decisions (MMD05 / MMD06 / MMD07)
        map.put("approveRequest", DECIDE);
        map.put("rejectRequest", DECIDE);
        map.put("refreshDonationEntitlement", DECIDE);
        map.put("forwardToPdCommittee", FORWARD_TO_PD);

        // MMD04 status matrix
        map.put("changeStatus", STATUS_CHANGE);

        return map;
    }

    @Test
    void everyDeathDonationEndpointCarriesAPreAuthorize() {
        List<String> unguarded = handlerMethods(DeathDonationRequestController.class).stream()
                .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("every Death Donation endpoint must state who may call it")
                .isEmpty();
    }

    @Test
    void theRoleMatrixMatchesTheSrs() {
        Map<String, String> actual = handlerMethods(DeathDonationRequestController.class).stream()
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
     * The SRS names only the District Office and Head Office as actors. These
     * three roles are actors elsewhere in the system, so it would be easy to add
     * one here by reflex; this fails if anybody does.
     */
    @Test
    void rolesWithNoPartInTheDonationFlowAppearNowhere() {
        String allExpressions = handlerMethods(DeathDonationRequestController.class).stream()
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(annotation -> annotation != null)
                .map(PreAuthorize::value)
                .collect(Collectors.joining(" "));

        assertThat(allExpressions)
                .as("ACCOUNTS is not an actor in SRS section 2")
                .doesNotContain("ACCOUNTS")
                .as("SCHOLARSHIP_OFFICER is not an actor in SRS section 2")
                .doesNotContain("SCHOLARSHIP_OFFICER")
                .as("DEATH_DONATION_OFFICER is not an actor in SRS section 2")
                .doesNotContain("DEATH_DONATION_OFFICER");
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
                .filter(DeathDonationEndpointSecurityTest::isHandler)
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
