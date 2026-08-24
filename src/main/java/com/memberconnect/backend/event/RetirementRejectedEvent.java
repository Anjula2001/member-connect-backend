package com.memberconnect.backend.event;


public record RetirementRejectedEvent(
        String memberId,
        String requestNo,
        String reason
) {
}
