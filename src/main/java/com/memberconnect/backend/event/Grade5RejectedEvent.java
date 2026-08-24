package com.memberconnect.backend.event;

public record Grade5RejectedEvent(
        String memberId,
        String requestNo,
        String studentName,
        String reason
) {
}
