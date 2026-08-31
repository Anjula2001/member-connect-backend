package com.memberconnect.backend.event;

public record RetirementMarkedIncompleteEvent(
        String memberId,
        String requestNo,
        String reason
) {
}
