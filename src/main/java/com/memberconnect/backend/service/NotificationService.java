package com.memberconnect.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.notification.EmailSender;
import com.memberconnect.backend.service.notification.SmsSender;

/**
 * Builds and dispatches member notifications.
 *
 * Two rules govern everything in this class:
 *
 * 1. It never throws. Callers reach it from an after-commit hook, where an
 *    escaping exception would serve no purpose - the business record is already
 *    committed and cannot be undone by failing to tell the member about it.
 *
 * 2. The two channels are fully isolated. A missing email address, or an SMTP
 *    server that is down, must not stop the SMS going out, and vice versa.
 *
 * Contact details are read from the Member entity, which MemberService keeps
 * current, and are never logged in full.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // An SMS segment is 160 characters and the surrounding template already uses
    // roughly half of a two-segment message, so a long incomplete reason is
    // trimmed rather than allowed to fan out into many billable segments. The
    // email always carries the reason in full.
    private static final int MAX_SMS_REASON_LENGTH = 160;

    private final MemberRepository memberRepository;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public NotificationService(
            MemberRepository memberRepository,
            EmailSender emailSender,
            SmsSender smsSender
    ) {
        this.memberRepository = memberRepository;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
    }

    /**
     * Notifies a member that their termination request has been marked INCOMPLETE,
     * by email and by SMS, including the reason (SRS steps 5-7).
     *
     * Both channels are best effort. Every outcome is logged with the request
     * number and member id so a missed notification can be traced later.
     */
    public void notifyTerminationMarkedIncomplete(String memberId, String requestNo, String reason) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);

        if (member == null) {
            log.warn(
                    "Termination incomplete notification skipped: member not found. memberId={}, requestNo={}",
                    memberId, requestNo
            );
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        sendEmail(member, memberId, requestNo, safeReason);
        sendSms(member, memberId, requestNo, safeReason);
    }

    private void sendEmail(Member member, String memberId, String requestNo, String reason) {
        String address = trimToNull(member.getEmailAddress());

        if (address == null) {
            log.warn(
                    "Notification channel EMAIL skipped: member has no email address. memberId={}, requestNo={}",
                    memberId, requestNo
            );
            return;
        }

        try {
            emailSender.send(
                    address,
                    buildEmailSubject(requestNo),
                    buildEmailBody(member, requestNo, reason)
            );
            log.info(
                    "Notification channel EMAIL succeeded. memberId={}, requestNo={}",
                    memberId, requestNo
            );
        } catch (Exception e) {
            // Swallowed deliberately: see the class comment. The SMS attempt below
            // must still happen, and the termination request stays INCOMPLETE.
            log.error(
                    "Notification channel EMAIL failed. memberId={}, requestNo={}, cause={}",
                    memberId, requestNo, e.toString(), e
            );
        }
    }

    private void sendSms(Member member, String memberId, String requestNo, String reason) {
        String mobile = trimToNull(member.getMobileNumber());

        if (mobile == null) {
            log.warn(
                    "Notification channel SMS skipped: member has no mobile number. memberId={}, requestNo={}",
                    memberId, requestNo
            );
            return;
        }

        try {
            smsSender.send(mobile, buildSmsMessage(requestNo, reason));
            log.info(
                    "Notification channel SMS succeeded. memberId={}, requestNo={}",
                    memberId, requestNo
            );
        } catch (Exception e) {
            log.error(
                    "Notification channel SMS failed. memberId={}, requestNo={}, cause={}",
                    memberId, requestNo, e.toString(), e
            );
        }
    }

    private String buildSmsMessage(String requestNo, String reason) {
        return "Your membership termination request " + requestNo
                + " has been marked INCOMPLETE. Reason: " + truncateForSms(reason)
                + ". Please log in to MemberConnect to update it.";
    }

    private String buildEmailSubject(String requestNo) {
        // The separator is a literal em dash; the build pins source encoding to
        // UTF-8 (project.build.sourceEncoding), so it survives compilation intact.
        return "Termination Request " + requestNo + " — Incomplete";
    }

    private String buildEmailBody(Member member, String requestNo, String reason) {
        return "Dear " + resolveMemberName(member) + ",\n"
                + "\n"
                + "Your membership termination request has been reviewed and marked as INCOMPLETE.\n"
                + "\n"
                + "Request Number : " + requestNo + "\n"
                + "Status         : Incomplete\n"
                + "Reason         : " + reason + "\n"
                + "\n"
                + "Please log in to MemberConnect, open the termination request, correct the\n"
                + "details described above and submit it again for approval.\n"
                + "\n"
                + "This is an automatically generated message. Please do not reply.\n"
                + "\n"
                + "MemberConnect\n";
    }

    private String resolveMemberName(Member member) {
        String fullName = trimToNull(member.getFullName());
        if (fullName != null) {
            return fullName;
        }

        String nameWithInitials = trimToNull(member.getNameWithInitials());
        if (nameWithInitials != null) {
            return nameWithInitials;
        }

        return "Member";
    }

    private String truncateForSms(String reason) {
        if (reason.length() <= MAX_SMS_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_SMS_REASON_LENGTH) + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
