package com.memberconnect.backend.event;


public record Grade5ApprovedEvent(
        String memberId,
        String requestNo,
        String studentName
) {
}
