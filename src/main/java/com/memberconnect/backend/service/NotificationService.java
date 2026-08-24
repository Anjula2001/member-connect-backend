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

    /**
     * MMT11 - sent once the Finance Module has closed the accounts and the member
     * has moved to TERMINATED.
     *
     * This is deliberately not sent at board approval. Approval only stops the
     * monthly remittance; telling a member their membership is terminated while
     * their savings accounts are still open would be untrue.
     */
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

    // ------------------------------------------------------------------
    // Record Member Death (SRS section 4).
    //
    // These differ from every other notification in this class in who they are
    // addressed to: the member is dead, so the recipient is the NOMINEE, whose
    // contact details live on the death record rather than on the Member. The
    // record is therefore resolved here, at delivery time, instead of the
    // contact details being carried on the event - a number captured at approval
    // and delivered days later could easily be stale.
    // ------------------------------------------------------------------

    /**
     * Tells the nominee that the Member Death Record has been marked INCOMPLETE,
     * with the reason (MMT18).
     */
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

    /**
     * Tells the nominee that the Member Death Record was rejected, at whichever
     * level rejected it (MMT22 / MMT23 / MMT24). The member profile has already
     * been put back to Active by the time this runs.
     */
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

    /**
     * Tells the nominee that the Finance Module has closed every account and the
     * membership is now terminated due to death, with the disbursement details
     * (MMT25).
     */
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

    // ------------------------------------------------------------------
    // Death Donations for Members (SRS Requirement 05, MMD01 / MMD05-07).
    //
    // Unlike Record Member Death above, the recipient is the REQUESTING MEMBER
    // themselves - they are alive and it is their relative who died - so these
    // read contact details from the Member profile, not from a nominee.
    // ------------------------------------------------------------------

    /**
     * Tells the member their Death Donation Request was marked INCOMPLETE, with
     * the reason (SRS MMD01, p.15).
     */
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

    /**
     * Tells the member their Death Donation Request was rejected, naming the
     * level that rejected it (MMD05 / MMD06 / MMD07).
     */
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

    /**
     * Tells the member their Death Donation Request was approved (SRS p.5).
     *
     * Deliberately says the funds are "being processed" rather than paid: the
     * approval hands off to the Finance Module (MMD08), which does the actual
     * disbursement afterwards.
     */
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

    // ------------------------------------------------------------------
    // University Scholarship
    //
    // Addressed to the member rather than the student: the member holds the
    // membership the scholarship is granted against, and it is their contact
    // details the profile carries. The student is named in the body so a member
    // with more than one child knows which request the message is about.
    // ------------------------------------------------------------------

    /** Tells the member their University Scholarship request was marked INCOMPLETE, with the reason. */
    public void notifyUniversityScholarshipMarkedIncomplete(
            String memberId, String requestNo, String studentName, String reason) {
        Member member = findMemberFor(memberId, requestNo, "university-scholarship-incomplete");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "University Scholarship Request " + requestNo + " \u2014 Incomplete",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The University Scholarship request below has been reviewed and marked as INCOMPLETE.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudentName(studentName) + "\n"
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
                "university-scholarship-incomplete"
        );
    }

    /** Tells the member their University Scholarship request was rejected, with the reason. */
    public void notifyUniversityScholarshipRejected(
            String memberId, String requestNo, String studentName, String reason) {
        Member member = findMemberFor(memberId, requestNo, "university-scholarship-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "University Scholarship Request " + requestNo + " \u2014 Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The University Scholarship request below has been reviewed and has not been\n"
                        + "approved.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudentName(studentName) + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you would like to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "university-scholarship-rejected"
        );
    }

    /**
     * Tells the member their University Scholarship request was approved.
     *
     * No amount or payment date is quoted: approval grants the scholarship, and each
     * instalment is released later through its own fund request.
     */
    public void notifyUniversityScholarshipApproved(
            String memberId, String requestNo, String studentName) {
        Member member = findMemberFor(memberId, requestNo, "university-scholarship-approved");
        if (member == null) {
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "University Scholarship Request " + requestNo + " \u2014 Approved",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The University Scholarship request below has been APPROVED.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Student        : " + safeStudentName(studentName) + "\n"
                        + "Status         : Approved\n"
                        + "\n"
                        + "Scholarship payments are released through fund requests raised against this\n"
                        + "scholarship. Please contact your District Office for any further information.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "university-scholarship-approved"
        );
    }

    /**
     * Tells the member a Fund Request against their scholarship was marked INCOMPLETE,
     * with the reason.
     *
     * The scholarship's request number is quoted alongside the fund request's own, so
     * a member holding more than one scholarship can tell which is meant.
     */
    public void notifyFundRequestMarkedIncomplete(
            String memberId, String fundRequestNo, String scholarshipRequestNo,
            String studentName, String period, String reason) {
        Member member = findMemberFor(memberId, fundRequestNo, "fund-request-incomplete");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Scholarship Fund Request " + fundRequestNo + " \u2014 Incomplete",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Fund Request below has been reviewed and marked as INCOMPLETE.\n"
                        + "\n"
                        + "Fund Request Number : " + fundRequestNo + "\n"
                        + "Scholarship Number  : " + safeValue(scholarshipRequestNo) + "\n"
                        + "Member Number       : " + memberId + "\n"
                        + "Student             : " + safeStudentName(studentName) + "\n"
                        + "Period              : " + safeValue(period) + "\n"
                        + "Status              : Incomplete\n"
                        + "Reason              : " + safeReason + "\n"
                        + "\n"
                        + "Please visit your District Office with the details described above so the\n"
                        + "fund request can be completed and submitted for approval.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "fund-request-incomplete"
        );
    }

    /** Tells the member a Fund Request against their scholarship was rejected, with the reason. */
    public void notifyFundRequestRejected(
            String memberId, String fundRequestNo, String scholarshipRequestNo,
            String studentName, String period, String reason) {
        Member member = findMemberFor(memberId, fundRequestNo, "fund-request-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Scholarship Fund Request " + fundRequestNo + " \u2014 Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Fund Request below has been reviewed and has not been approved.\n"
                        + "\n"
                        + "Fund Request Number : " + fundRequestNo + "\n"
                        + "Scholarship Number  : " + safeValue(scholarshipRequestNo) + "\n"
                        + "Member Number       : " + memberId + "\n"
                        + "Student             : " + safeStudentName(studentName) + "\n"
                        + "Period              : " + safeValue(period) + "\n"
                        + "Status              : Rejected\n"
                        + "Reason              : " + safeReason + "\n"
                        + "\n"
                        + "The scholarship itself is unaffected. Please contact your District Office if\n"
                        + "you would like to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "fund-request-rejected"
        );
    }

    /**
     * Tells the member a Fund Request against their scholarship was approved.
     *
     * The requested amount is quoted rather than a disbursed one: approval authorises
     * the payment, and the Finance Division releases it afterwards.
     */
    public void notifyFundRequestApproved(
            String memberId, String fundRequestNo, String scholarshipRequestNo,
            String studentName, String period, Double requestedAmount) {
        Member member = findMemberFor(memberId, fundRequestNo, "fund-request-approved");
        if (member == null) {
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Scholarship Fund Request " + fundRequestNo + " \u2014 Approved",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "The Fund Request below has been APPROVED.\n"
                        + "\n"
                        + "Fund Request Number : " + fundRequestNo + "\n"
                        + "Scholarship Number  : " + safeValue(scholarshipRequestNo) + "\n"
                        + "Member Number       : " + memberId + "\n"
                        + "Student             : " + safeStudentName(studentName) + "\n"
                        + "Period              : " + safeValue(period) + "\n"
                        + "Requested Amount    : " + formatFundAmount(requestedAmount) + "\n"
                        + "Status              : Approved\n"
                        + "\n"
                        + "The request has been passed to the Finance Division, which will process the\n"
                        + "payment. Please contact your District Office if you need any further\n"
                        + "information.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "fund-request-approved"
        );
    }

    // ------------------------------------------------------------------
    // Member Transfers (MMC30)
    // ------------------------------------------------------------------

    /**
     * Tells the member their transfer request was approved and their profile updated.
     *
     * The new working location and designation are quoted back so the member can check
     * that what was applied is what they asked for - a transfer changes where their
     * membership is administered, so a wrong value matters to them immediately.
     */
    public void notifyMemberTransferApproved(
            String memberId, String requestNo, String newWorkingLocation, String newDesignation) {
        Member member = findMemberFor(memberId, requestNo, "member-transfer-approved");
        if (member == null) {
            return;
        }

        dispatchEmail(
                member.getEmailAddress(),
                "Member Transfer Request " + requestNo + " \u2014 Approved",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your Member Transfer request has been APPROVED and your membership profile\n"
                        + "has been updated with the requested changes.\n"
                        + "\n"
                        + "Request Number   : " + requestNo + "\n"
                        + "Member Number    : " + memberId + "\n"
                        + "Status           : Approved\n"
                        + "Working Location : " + safeValue(newWorkingLocation) + "\n"
                        + "Designation      : " + safeValue(newDesignation) + "\n"
                        + "\n"
                        + "If your District has changed, your loans and savings accounts are being\n"
                        + "moved to the new District Office. Please contact your District Office if any\n"
                        + "of the details above are not what you requested.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "member-transfer-approved"
        );
    }

    /** Tells the member their transfer request was rejected, with the reason. */
    public void notifyMemberTransferRejected(String memberId, String requestNo, String reason) {
        Member member = findMemberFor(memberId, requestNo, "member-transfer-rejected");
        if (member == null) {
            return;
        }

        String safeReason = reason == null ? "" : reason.trim();

        dispatchEmail(
                member.getEmailAddress(),
                "Member Transfer Request " + requestNo + " \u2014 Rejected",
                "Dear " + resolveMemberName(member) + ",\n"
                        + "\n"
                        + "Your Member Transfer request has been reviewed and has not been approved.\n"
                        + "No changes have been made to your membership profile.\n"
                        + "\n"
                        + "Request Number : " + requestNo + "\n"
                        + "Member Number  : " + memberId + "\n"
                        + "Status         : Rejected\n"
                        + "Reason         : " + safeReason + "\n"
                        + "\n"
                        + "Please contact your District Office if you would like to discuss this decision.\n"
                        + "\n"
                        + "This is an automatically generated message. Please do not reply.\n"
                        + "\n"
                        + "MemberConnect\n",
                memberId,
                "member-transfer-rejected"
        );
    }

    /** Keeps a missing optional detail from printing as "null" in the body. */
    private String safeValue(String value) {
        return trimToNull(value) == null ? "-" : value.trim();
    }

    private String formatFundAmount(Double amount) {
        return amount == null ? "-" : String.format("LKR %,.2f", amount);
    }

    /** A request saved before the student name was mandatory still has to address someone. */
    private String safeStudentName(String studentName) {
        return trimToNull(studentName) == null ? "-" : studentName.trim();
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

    /**
     * A missing record is logged and swallowed, like every other failure here: the
     * approval it relates to is already committed and must not be undone by a
     * notification that cannot be addressed.
     */
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

    // ------------------------------------------------------------------
    // Inactivating Dormant Membership Profiles (SRS section 4)
    //
    // Two member-facing messages, not three. There is deliberately no
    // notification when the board REJECTS an inactivation: the member never knew
    // they were on a list, so "the Board declined to deactivate you" is noise
    // with no action attached, and it would disclose an internal deliberation
    // they were never party to.
    // ------------------------------------------------------------------

    /**
     * MMD10 - the identification process has flagged this member as dormant.
     *
     * Sent while the member can still do something about it: a single
     * transaction before the next board meeting clears the flag automatically.
     * Without this the first they hear of it is the inactivation notice, by
     * which point reversing it needs Head Office.
     */
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

    /** MMD17 / SRS 4.2.6 - the Board approved the inactivation and it is applied. */
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
