package com.memberconnect.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.notification.EmailSender;
import com.memberconnect.backend.service.notification.SmsSender;

/**
 * Builds and dispatches member notifications.
 *
 * Two rules govern everything in this class:
 *
 * 1. It never throws. Callers reach it from an after-commit hook or from inside
 *    a transaction, where an escaping exception would serve no purpose - the
 *    business record is already committed, or is about to be, and cannot be
 *    undone by failing to tell the member about it.
 *
 * 2. The two channels are fully isolated. A missing email address, or an SMTP
 *    server that is down, must not stop the SMS going out, and vice versa.
 *
 * Both channels go through the EmailSender / SmsSender abstractions rather than
 * touching JavaMailSender directly, so which transport is live is decided once,
 * by notification.email.enabled, instead of separately per notification.
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

    /** MR12 - sent when the Finance Module activates a member. */
    public void sendMembershipActivated(Member member) {
        String name = displayName(member);

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership is now active",
                "Dear " + name + ",\n\n"
                        + "Your membership (" + member.getMemberId() + ") is now active.\n"
                        + "Your membership documentation will be posted to you shortly.\n\n"
                        + "Future Finance Institute",
                member.getMemberId(),
                "membership activated"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Your membership " + member.getMemberId()
                        + " is now active. Your documentation will be posted soon.",
                member.getMemberId(),
                "membership activated"
        );
    }

    /** MR18 - sent when the membership documentation is put in the post. */
    public void sendDocumentationDispatched(Member member) {
        String name = displayName(member);

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership documentation has been posted",
                "Dear " + name + ",\n\n"
                        + "The membership documentation for " + member.getMemberId()
                        + " has been posted to your registered address.\n"
                        + "Please sign the enclosed Signature Card and hand it in at any District Office.\n\n"
                        + "Future Finance Institute",
                member.getMemberId(),
                "documentation dispatched"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Documentation for membership " + member.getMemberId()
                        + " has been posted. Please sign and return the Signature Card.",
                member.getMemberId(),
                "documentation dispatched"
        );
    }

    /**
     * MMC04 / MMC12 / MMC17 / MMC25 - sent when a profile change request is approved
     * and the Member Profile has been updated with the requested values.
     *
     * One method covers all four request types because the SRS wording is identical
     * for each; only the request type name and number differ.
     */
    public void sendProfileChangeApproved(Member member, ProfileChangeType type, String requestNo) {
        String name = displayName(member);
        String what = type.getLabel();

        dispatchEmail(
                member.getEmailAddress(),
                what + " approved — request " + requestNo,
                "Dear " + name + ",\n\n"
                        + "Your request to update your membership details has been approved and your\n"
                        + "Member Profile has been updated.\n\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Request Type   : " + what + "\n"
                        + "Status         : Approved\n\n"
                        + "If any of the updated details are not correct, please contact any District\n"
                        + "Office.\n\n"
                        + "This is an automatically generated message. Please do not reply.\n\n"
                        + "Future Finance Institute",
                member.getMemberId(),
                "profile change approved"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Your request " + requestNo + " (" + what
                        + ") has been approved and your member profile has been updated.",
                member.getMemberId(),
                "profile change approved"
        );
    }

    /**
     * MMC04 / MMC12 / MMC17 / MMC25 - sent when a profile change request is rejected.
     * The Member Profile is left untouched, and the reason entered by the approver is
     * carried in both channels (truncated for SMS only, as elsewhere in this class).
     */
    public void sendProfileChangeRejected(
            Member member,
            ProfileChangeType type,
            String requestNo,
            String reason
    ) {
        String name = displayName(member);
        String what = type.getLabel();
        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                what + " rejected — request " + requestNo,
                "Dear " + name + ",\n\n"
                        + "Your request to update your membership details has been reviewed and was not\n"
                        + "approved. Your Member Profile has not been changed.\n\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Request Type   : " + what + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n\n"
                        + "Please visit any District Office if you would like to submit a new request.\n\n"
                        + "This is an automatically generated message. Please do not reply.\n\n"
                        + "Future Finance Institute",
                member.getMemberId(),
                "profile change rejected"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Your request " + requestNo + " (" + what + ") was rejected. Reason: "
                        + truncateForSms(safeReason) + ". Contact any District Office for help.",
                member.getMemberId(),
                "profile change rejected"
        );
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

    /**
     * Email channel for the registration-flow notifications, which identify the
     * member by membership number rather than by a request number. Same contract
     * as sendEmail above: a blank address is skipped, a transport failure is
     * logged and never propagated to the caller's transaction.
     */
    private void dispatchEmail(String to, String subject, String body, String memberId, String purpose) {
        String address = trimToNull(to);

        if (address == null) {
            // Email is optional on the application, so this is expected, not a fault.
            log.warn(
                    "Notification channel EMAIL skipped: member has no email address. memberId={}, purpose={}",
                    memberId, purpose
            );
            return;
        }

        try {
            emailSender.send(address, subject, body);
            log.info("Notification channel EMAIL succeeded. memberId={}, purpose={}", memberId, purpose);
        } catch (Exception e) {
            log.error(
                    "Notification channel EMAIL failed. memberId={}, purpose={}, cause={}",
                    memberId, purpose, e.toString(), e
            );
        }
    }

    /** SMS counterpart of dispatchEmail. */
    private void dispatchSms(String toMobile, String message, String memberId, String purpose) {
        String mobile = trimToNull(toMobile);

        if (mobile == null) {
            // Mobile is optional on the application, so this is expected, not a fault.
            log.warn(
                    "Notification channel SMS skipped: member has no mobile number. memberId={}, purpose={}",
                    memberId, purpose
            );
            return;
        }

        try {
            smsSender.send(mobile, message);
            log.info("Notification channel SMS succeeded. memberId={}, purpose={}", memberId, purpose);
        } catch (Exception e) {
            log.error(
                    "Notification channel SMS failed. memberId={}, purpose={}, cause={}",
                    memberId, purpose, e.toString(), e
            );
        }
    }

    /**
     * Greeting used by the registration-flow notifications. It prefers the name
     * with initials, which is how the membership documentation addresses the
     * member; the termination notification uses resolveMemberName instead, which
     * prefers the full name. The two orders are deliberate, not an oversight.
     */
    private String displayName(Member member) {
        String nameWithInitials = trimToNull(member.getNameWithInitials());
        if (nameWithInitials != null) {
            return nameWithInitials;
        }

        String fullName = trimToNull(member.getFullName());
        return fullName == null ? "Member" : fullName;
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
