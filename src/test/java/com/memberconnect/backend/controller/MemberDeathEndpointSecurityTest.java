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
 * Pins the Record Member Death role matrix (SRS section 4) to the controllers.
 *
 * Reflection rather than a running server on purpose: it needs no Spring context
 * and no database, and it fails the moment somebody adds a death endpoint without
 * an annotation - which is exactly the regression worth catching. The runtime
 * behaviour of these expressions is covered separately by
 * MemberDeathAuthorizationTest against the service.
 */
class MemberDeathEndpointSecurityTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    /** Everyone who takes part in the workflow may read (MMT19 / MMT20). */
    private static final String READ =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** Only the District Office raises and edits (MMT18 / MMT21). */
    private static final String ENTRY = "hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')";

    /** The three decision levels (MMT22 / MMT23 / MMT24). */
    private static final String DECIDE =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')";

    /** MMT21 manual status changes, including the Inactive right. */
    private static final String STATUS_CHANGE =
            "hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    private static final Map<String, String> EXPECTED = expected();

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();
        // Reads
        map.put("getCauseOfDeathOptions", READ);
        map.put("searchRequests", READ);
        map.put("getRecordsByMember", READ);
        map.put("getActiveRecordForMember", READ);
        map.put("getRecord", READ);
        map.put("getDocuments", READ);
        // Entry and editing
        map.put("saveRecord", ENTRY);
        map.put("updateRecord", ENTRY);
        map.put("submitRecord", ENTRY);
        map.put("markIncomplete", ENTRY);
        map.put("deleteDocument", ENTRY);
        map.put("forwardToDistrictCommittee", ENTRY);
        // Decisions
        map.put("approveRecord", DECIDE);
        map.put("rejectRecord", DECIDE);
        map.put("refreshDonationEntitlement", DECIDE);
        map.put("forwardToPdCommittee", "hasAnyRole('DISTRICT_COMMITTEE','SUPER_ADMIN')");
        // Status matrix
        map.put("changeStatus", STATUS_CHANGE);
        return map;
    }

    @Test
    void everyMemberDeathEndpointCarriesAPreAuthorize() {
        List<String> unguarded = handlerMethods(MemberDeathRecordController.class).stream()
                .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("every Record Member Death endpoint must state who may call it")
                .isEmpty();
    }

    @Test
    void theRoleMatrixMatchesTheSrs() {
        Map<String, String> actual = handlerMethods(MemberDeathRecordController.class).stream()
                .collect(Collectors.toMap(
                        Method::getName,
                        method -> normalise(method.getAnnotation(PreAuthorize.class).value()),
                        (a, b) -> a,
                        LinkedHashMap::new));

        assertThat(actual)
                .as("the annotations and the SRS role matrix must not drift apart")
                .containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    /**
     * MMT25 puts the final "Deceased" status change in the Finance Module's hands,
     * so its inbound edge must authenticate as a service user rather than sit open.
     */
    @Test
    void theFinanceCallbackIsRestrictedToAccounts() {
        PreAuthorize onClass =
                FinanceMemberDeathCallbackController.class.getAnnotation(PreAuthorize.class);

        assertThat(onClass).as("the Finance callback must not be open").isNotNull();
        assertThat(normalise(onClass.value())).isEqualTo("hasAnyRole('ACCOUNTS','SUPER_ADMIN')");
    }

    private static List<Method> handlerMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(MemberDeathEndpointSecurityTest::isHandler)
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
