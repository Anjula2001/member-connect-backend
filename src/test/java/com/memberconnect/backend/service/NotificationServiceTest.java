package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.notification.EmailSender;
import com.memberconnect.backend.service.notification.SmsSender;

/**
 * Unit tests for the MMT01 "marked incomplete" notification.
 *
 * Plain Mockito tests with no Spring context, so they need no database and no
 * mail server.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String MEMBER_ID = "M-001";
    private static final String REQUEST_NO = "T-2026-001";
    private static final String REASON = "Bank passbook copy is not clear";
    private static final String EMAIL = "chamara@example.com";
    private static final String MOBILE = "0771234567";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private SmsSender smsSender;

    @InjectMocks
    private NotificationService notificationService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setMemberId(MEMBER_ID);
        member.setFullName("Chamara Perera");
        member.setEmailAddress(EMAIL);
        member.setMobileNumber(MOBILE);
    }

    private void givenMemberExists() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    private void markIncomplete(String reason) {
        notificationService.notifyTerminationMarkedIncomplete(MEMBER_ID, REQUEST_NO, reason);
    }

    @Test
    void sendsBothChannelsWhenEmailAndMobileArePresent() {
        givenMemberExists();

        markIncomplete(REASON);

        verify(emailSender, times(1)).send(eq(EMAIL), anyString(), anyString());
        verify(smsSender, times(1)).send(eq(MOBILE), anyString());
    }

    @Test
    void skipsEmailButStillSendsSmsWhenEmailAddressIsMissing() {
        member.setEmailAddress(null);
        givenMemberExists();

        markIncomplete(REASON);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(smsSender, times(1)).send(eq(MOBILE), anyString());
    }

    @Test
    void skipsSmsButStillSendsEmailWhenMobileNumberIsMissing() {
        member.setMobileNumber(null);
        givenMemberExists();

        markIncomplete(REASON);

        verify(smsSender, never()).send(anyString(), anyString());
        verify(emailSender, times(1)).send(eq(EMAIL), anyString(), anyString());
    }

    /**
     * A blank string is treated the same as a missing value - a member record with
     * "   " in the email column must not produce a send attempt to an empty address.
     */
    @Test
    void treatsBlankContactDetailsAsMissing() {
        member.setEmailAddress("   ");
        member.setMobileNumber("");
        givenMemberExists();

        markIncomplete(REASON);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void stillSendsSmsAndDoesNotThrowWhenEmailSenderFails() {
        givenMemberExists();
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        assertThatCode(() -> markIncomplete(REASON)).doesNotThrowAnyException();

        verify(smsSender, times(1)).send(eq(MOBILE), anyString());
    }

    @Test
    void stillSendsEmailAndDoesNotThrowWhenSmsSenderFails() {
        givenMemberExists();
        doThrow(new RuntimeException("SMS gateway unavailable"))
                .when(smsSender).send(anyString(), anyString());

        assertThatCode(() -> markIncomplete(REASON)).doesNotThrowAnyException();

        verify(emailSender, times(1)).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void doesNotThrowWhenBothChannelsFail() {
        givenMemberExists();
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        doThrow(new RuntimeException("SMS gateway unavailable"))
                .when(smsSender).send(anyString(), anyString());

        assertThatCode(() -> markIncomplete(REASON)).doesNotThrowAnyException();

        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
        verify(smsSender, times(1)).send(anyString(), anyString());
    }

    /**
     * SRS step 7: the communication must carry the reason the request was marked
     * incomplete, on both channels.
     */
    @Test
    void bothMessagesContainRequestNumberAndReason() {
        givenMemberExists();

        markIncomplete(REASON);

        ArgumentCaptor<String> emailSubject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailBody = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(anyString(), emailSubject.capture(), emailBody.capture());

        assertThat(emailSubject.getValue()).contains(REQUEST_NO);
        assertThat(emailBody.getValue())
                .contains(REQUEST_NO)
                .contains(REASON)
                .contains("Chamara Perera")
                .contains("Incomplete");

        ArgumentCaptor<String> smsMessage = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(anyString(), smsMessage.capture());

        assertThat(smsMessage.getValue())
                .contains(REQUEST_NO)
                .contains(REASON)
                .contains("INCOMPLETE");
    }

    /**
     * A very long reason must not blow the SMS out into many billable segments,
     * while the email still carries it in full.
     */
    @Test
    void truncatesLongReasonInSmsButNotInEmail() {
        givenMemberExists();
        String longReason = "x".repeat(400);

        markIncomplete(longReason);

        ArgumentCaptor<String> smsMessage = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(anyString(), smsMessage.capture());
        assertThat(smsMessage.getValue()).doesNotContain(longReason);
        assertThat(smsMessage.getValue()).contains("...");

        ArgumentCaptor<String> emailBody = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(anyString(), anyString(), emailBody.capture());
        assertThat(emailBody.getValue()).contains(longReason);
    }

    /**
     * The listener hands over whatever member id the request carried. If no such
     * member exists the notification is abandoned quietly rather than blowing up
     * an after-commit callback.
     */
    @Test
    void sendsNothingAndDoesNotThrowWhenMemberIsNotFound() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> markIncomplete(REASON)).doesNotThrowAnyException();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void toleratesNullReason() {
        givenMemberExists();

        assertThatCode(() -> markIncomplete(null)).doesNotThrowAnyException();

        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
        verify(smsSender, times(1)).send(anyString(), anyString());
    }
}
