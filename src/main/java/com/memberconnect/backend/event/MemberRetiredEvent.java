package com.memberconnect.backend.event;

public record MemberRetiredEvent(
        String memberId,
        String requestNo
) {
}
