package com.memberconnect.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
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
    private final MemberDeathRecordRepository memberDeathRecordRepository;
    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public NotificationService(
            MemberRepository memberRepository,
            MemberDeathRecordRepository memberDeathRecordRepository,
            EmailSender emailSender,
            SmsSender smsSender
    ) {
        this.memberRepository = memberRepository;
        this.memberDeathRecordRepository = memberDeathRecordRepository;
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

    /**
     * MMT09 - sent when the board rejects a termination request.
     *
     * The member's profile has already gone back to Active by the time this
     * runs, so the message is written as "your membership continues" rather than
     * as a bare failure notice.
     */
    public void notifyTerminationRejected(String memberId, String requestNo, String reason) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);

        if (member == null) {
            log.warn(
                    "Termination rejected notification skipped: member not found. memberId={}, requestNo={}",
                    memberId, requestNo
            );
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Termination Request " + requestNo + " — Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Board has reviewed your membership termination request and has not\n"
                        + "approved it. Your membership remains active.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you wish to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "termination rejected"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Your membership termination request " + requestNo
                        + " was not approved by the Board. Reason: " + truncateForSms(safeReason)
                        + ". Your membership remains active.",
                memberId,
                "termination rejected"
        );
    }

   
    public void notifyMembershipTerminated(String memberId, String requestNo) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);

        if (member == null) {
            log.warn(
                    "Membership terminated notification skipped: member not found. memberId={}, requestNo={}",
                    memberId, requestNo
            );
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership has been terminated",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your membership (" + memberId + ") has been terminated and your\n"
                        + "accounts have been closed.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "\n"
                        + "Thank you for your membership with the Future Finance Institute.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "membership terminated"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Your membership " + memberId + " has been terminated and your accounts closed.",
                memberId,
                "membership terminated"
        );
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

    // profile change approved notifications
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

    // profile change rejected notifications
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
                    // Handed to the notification executor; SmtpEmailSender logs the
                    // actual SENT/FAILED outcome once Gmail has answered.
                    "Notification channel EMAIL accepted for delivery. memberId={}, requestNo={}",
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
            // Handed to the notification executor; SmtpEmailSender logs the actual
            // SENT/FAILED outcome once Gmail has answered.
            log.info("Notification channel EMAIL accepted for delivery. memberId={}, purpose={}", memberId, purpose);
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

    // Member Death
    public void notifyMemberDeathMarkedIncomplete(String memberId, String recordNo, String reason) {
        MemberDeathRecord record = findDeathRecord(recordNo, "member-death-incomplete");
        if (record == null) {
            return;
        }

        String subject = "Member Death Record " + recordNo + " — Incomplete";
        String body = "Dear " + nomineeName(record) + ",\n"
                + "\n"
                + "The member death record you submitted has been reviewed and marked as INCOMPLETE.\n"
                + "\n"
                + "Record Number : " + recordNo + "\n"
                + "Member Number : " + memberId + "\n"
                + "Status        : Incomplete\n"
                + "Reason        : " + reason + "\n"
                + "\n"
                + "Please visit your District Office with the details described above so the\n"
                + "record can be completed and submitted for approval.\n"
                + "\n"
                + "This is an automatically generated message. Please do not reply.\n"
                + "\n"
                + "MemberConnect\n";

        String sms = "Member death record " + recordNo + " has been marked INCOMPLETE. Reason: "
                + truncateForSms(reason) + ". Please contact your District Office.";

        dispatchEmail(record.getNomineeEmail(), subject, body, memberId, "member-death-incomplete");
        dispatchSms(record.getNomineeMobile(), sms, memberId, "member-death-incomplete");
    }

    // Member Death rejected
    public void notifyMemberDeathRejected(String memberId, String recordNo, String reason, String level) {
        MemberDeathRecord record = findDeathRecord(recordNo, "member-death-rejected");
        if (record == null) {
            return;
        }

        String subject = "Member Death Record " + recordNo + " — Rejected";
        String body = "Dear " + nomineeName(record) + ",\n"
                + "\n"
                + "The member death record you submitted has been rejected.\n"
                + "\n"
                + "Record Number : " + recordNo + "\n"
                + "Member Number : " + memberId + "\n"
                + "Status        : Rejected\n"
                + "Rejected by   : " + level + "\n"
                + "Reason        : " + reason + "\n"
                + "\n"
                + "Please contact your District Office if you would like to discuss this decision.\n"
                + "\n"
                + "This is an automatically generated message. Please do not reply.\n"
                + "\n"
                + "MemberConnect\n";

        String sms = "Member death record " + recordNo + " was rejected by " + level
                + ". Reason: " + truncateForSms(reason) + ". Please contact your District Office.";

        dispatchEmail(record.getNomineeEmail(), subject, body, memberId, "member-death-rejected");
        dispatchSms(record.getNomineeMobile(), sms, memberId, "member-death-rejected");
    }

    // Member Death completed
    public void notifyMemberDeceased(String memberId, String recordNo) {
        MemberDeathRecord record = findDeathRecord(recordNo, "member-deceased");
        if (record == null) {
            return;
        }

        String subject = "Member Death Record " + recordNo + " — Completed";
        String body = "Dear " + nomineeName(record) + ",\n"
                + "\n"
                + "The membership has now been terminated due to death and the balance funds\n"
                + "have been disbursed.\n"
                + "\n"
                + "Record Number     : " + recordNo + "\n"
                + "Member Number     : " + memberId + "\n"
                + "Status            : Deceased\n"
                + formatAmountLine("Death Donation    ", record.getDisburseDonationAmount())
                + formatAmountLine("Credited to Fund  ", record.getCreditedToSpecialFixedAccount())
                + formatDisbursementAccount(record)
                + "\n"
                + "Please contact your District Office if any of the details above are incorrect.\n"
                + "\n"
                + "This is an automatically generated message. Please do not reply.\n"
                + "\n"
                + "MemberConnect\n";

        String sms = "Membership " + memberId + " has been terminated due to death (record "
                + recordNo + ") and the balance funds have been disbursed.";

        dispatchEmail(record.getNomineeEmail(), subject, body, memberId, "member-deceased");
        dispatchSms(record.getNomineeMobile(), sms, memberId, "member-deceased");
    }

    // Retirement notifications (mark as Incomplete)
    public void notifyRetirementMarkedIncomplete(String memberId, String requestNo, String reason) {
        Member member = findMemberFor(memberId, requestNo, "retirement-incomplete");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Retirement Request " + requestNo + " — Incomplete",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your membership retirement request has been reviewed and marked as INCOMPLETE.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Incomplete\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please visit your District Office with the details described above so the\n"
                        + "request can be completed and submitted for approval.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "retirement-incomplete"
        );
    }

   // Retirement notifications (rejected)
    public void notifyRetirementRejected(String memberId, String requestNo, String reason) {
        Member member = findMemberFor(memberId, requestNo, "retirement-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Retirement Request " + requestNo + " — Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your membership retirement request has been reviewed and has NOT been\n"
                        + "approved. Your membership remains active and no further action is required\n"
                        + "unless you wish to reapply.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you wish to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "retirement-rejected"
        );
    }

    // Retirement notifications (completed)
    public void notifyMemberRetired(String memberId, String requestNo) {
        Member member = findMemberFor(memberId, requestNo, "member-retired");
        if (member == null) {
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership has ended on retirement",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your retirement has been completed and your membership (" + memberId + ")\n"
                        + "is now terminated due to retirement. Your accounts have been settled by the\n"
                        + "Finance Department.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Retired\n"
                        + "\n"
                        + "Thank you for your membership with the Future Finance Institute.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "member-retired"
        );
    }

   // Grade 5 Scholarship notifications (mark as Incomplete)
    public void notifyGrade5MarkedIncomplete(
            String memberId, String requestNo, String studentName, String reason) {

        Member member = findMemberFor(memberId, requestNo, "grade5-incomplete");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();
        String safeStudent = trimToNull(studentName) == null ? "your child" : studentName.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Grade 5 Scholarship Request " + requestNo + " — Incomplete",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Grade 5 scholarship request submitted for " + safeStudent + " has been\n"
                        + "reviewed and marked as INCOMPLETE. It has not been rejected - it cannot be\n"
                        + "sent for approval until the point below is resolved.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudent + "\n"
                        + "Status         : Incomplete\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please visit your District Office with the details described above so the\n"
                        + "request can be completed and submitted for approval.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "grade5-incomplete"
        );
    }

    // Grade 5 Scholarship notifications (rejected)
    public void notifyGrade5Rejected(
            String memberId, String requestNo, String studentName, String reason) {

        Member member = findMemberFor(memberId, requestNo, "grade5-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();
        String safeStudent = trimToNull(studentName) == null ? "your child" : studentName.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Grade 5 Scholarship Request " + requestNo + " — Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Board has reviewed the Grade 5 scholarship request submitted for\n"
                        + safeStudent + " and has NOT approved it.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudent + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you wish to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "grade5-rejected"
        );
    }

    // Grade 5 Scholarship notifications (approved)
    public void notifyGrade5Approved(String memberId, String requestNo, String studentName) {
        Member member = findMemberFor(memberId, requestNo, "grade5-approved");
        if (member == null) {
            return;
        }

        String safeStudent = trimToNull(studentName) == null ? "your child" : studentName.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Grade 5 Scholarship Request " + requestNo + " — Approved",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Board has approved the Grade 5 scholarship request submitted for\n"
                        + safeStudent + ". The necessary fund disbursement is now underway.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudent + "\n"
                        + "Status         : Approved\n"
                        + "\n"
                        + "You will be informed once the funds have been released. Please contact\n"
                        + "your District Office if you have any questions.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "grade5-approved"
        );
    }

    // Death Donation notifications (mark as Incomplete)
    public void notifyDeathDonationMarkedIncomplete(String memberId, String requestNo, String reason) {
        Member member = findMemberFor(memberId, requestNo, "death-donation-incomplete");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Death Donation Request " + requestNo + " — Incomplete",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your death donation request has been reviewed and marked as INCOMPLETE.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Incomplete\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please visit your District Office with the details described above so the\n"
                        + "request can be completed and submitted for approval.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "death-donation-incomplete"
        );

        dispatchSms(
                member.getMobileNumber(),
                "Death donation request " + requestNo + " has been marked INCOMPLETE. Reason: "
                        + truncateForSms(safeReason) + ". Please contact your District Office.",
                memberId,
                "death-donation-incomplete"
        );
    }

    // Death Donation notifications (rejected)
    public void notifyDeathDonationRejected(
            String memberId,
            String requestNo,
            String reason,
            String level
    ) {
        Member member = findMemberFor(memberId, requestNo, "death-donation-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Death Donation Request " + requestNo + " — Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your death donation request has been reviewed and has not been approved.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Rejected\n"
                        + "Rejected by    : " + level + "\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you would like to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "death-donation-rejected"
        );

        dispatchSms(
                member.getMobileNumber(),
                "Death donation request " + requestNo + " was rejected by " + level
                        + ". Reason: " + truncateForSms(safeReason)
                        + ". Please contact your District Office.",
                memberId,
                "death-donation-rejected"
        );
    }

    // Death Donation notifications (approved)
    public void notifyDeathDonationApproved(String memberId, String requestNo, String level) {
        Member member = findMemberFor(memberId, requestNo, "death-donation-approved");
        if (member == null) {
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Death Donation Request " + requestNo + " — Approved",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your death donation request has been APPROVED.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Approved\n"
                        + "Approved by    : " + level + "\n"
                        + "\n"
                        + "The request has been passed to the Finance Division, which will process the\n"
                        + "disbursement of the donation. Please contact your District Office if you\n"
                        + "need any further information.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "death-donation-approved"
        );

        dispatchSms(
                member.getMobileNumber(),
                "Your death donation request " + requestNo + " has been APPROVED by " + level
                        + ". The Finance Division will process the disbursement.",
                memberId,
                "death-donation-approved"
        );
    }

    /** A missing member is logged and swallowed, like every other failure here. */
    private Member findMemberFor(String memberId, String requestNo, String purpose) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);
        if (member == null) {
            log.warn(
                    "Notification skipped: member not found. memberId={}, requestNo={}, purpose={}",
                    memberId, requestNo, purpose
            );
        }
        return member;
    }

    // A missing death record is logged and swallowed, like every other failure here.
    private MemberDeathRecord findDeathRecord(String recordNo, String purpose) {
        MemberDeathRecord record = memberDeathRecordRepository.findByRecordId(recordNo).orElse(null);
        if (record == null) {
            log.error("Notification skipped: member death record not found. recordNo={}, purpose={}",
                    recordNo, purpose);
        }
        return record;
    }

    private String nomineeName(MemberDeathRecord record) {
        String name = trimToNull(record.getNomineeFullName());
        return name != null ? name : "Nominee";
    }

    private String formatAmountLine(String label, java.math.BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return label + ": LKR " + amount.toPlainString() + "\n";
    }

    private String formatDisbursementAccount(MemberDeathRecord record) {
        String accountNo = trimToNull(record.getNomineeAccountNo());
        if (accountNo == null) {
            return "";
        }
        String bank = trimToNull(record.getBank());
        return "Disbursed To      : " + accountNo + (bank != null ? " (" + bank + ")" : "") + "\n";
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

    // Dormant membership notifications (selected for dormant)
    public void notifySelectedForDormant(String memberId, int dormantPeriodMonths) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);

        if (member == null) {
            log.warn("Dormant selection notification skipped: member not found. memberId={}", memberId);
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership has been flagged as dormant",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your membership (" + memberId + ") has been flagged as dormant because\n"
                        + "no activity has been recorded on your accounts for "
                        + dormantPeriodMonths + " month(s).\n"
                        + "\n"
                        + "Dormant memberships are placed before the Board for inactivation. If you\n"
                        + "wish to keep your membership active, please make a transaction or contact\n"
                        + "your District Office before the next Board meeting.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "dormant selected"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Membership " + memberId + " has been flagged dormant after "
                        + dormantPeriodMonths + " month(s) without activity. Transact to stay active.",
                memberId,
                "dormant selected"
        );
    }

    /**the Board approved the inactivation and it is applied. */
    public void notifyMembershipInactivatedDormant(String memberId, String listId) {
        Member member = memberRepository.findByMemberId(memberId).orElse(null);

        if (member == null) {
            log.warn(
                    "Dormant inactivation notification skipped: member not found. memberId={}, listId={}",
                    memberId, listId
            );
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Your Future Finance Institute membership is now inactive (dormant)",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your membership (" + memberId + ") has been made Inactive (Dormant)\n"
                        + "following approval by the Board.\n"
                        + "\n"
                        + "Your accounts have been flagged as dormant. To reactivate your\n"
                        + "membership, please contact your District Office.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "dormant inactivated"
        );

        dispatchSms(
                member.getMobileNumber(),
                "FFI: Membership " + memberId + " is now Inactive (Dormant). "
                        + "Contact your District Office to reactivate.",
                memberId,
                "dormant inactivated"
        );
    }
}
