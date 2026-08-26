package com.memberconnect.backend.event;

public record Grade5MarkedIncompleteEvent(
        String memberId,
        String requestNo,
        String studentName,
        String reason
) {
}
