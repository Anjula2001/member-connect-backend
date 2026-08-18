package com.memberconnect.backend.service;

import com.memberconnect.backend.model.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Member notifications for the registration flow.
 *
 * Email is real (SMTP via spring-boot-starter-mail). SMS is a deliberate STUB: no
 * gateway is integrated yet, so messages are logged rather than sent. Both paths
 * are best-effort — a member's activation or dispatch must never fail because a
 * notification could not be delivered, so every send is caught and logged.
 */
@Service
public class NotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${notifications.email.from:no-reply@ffi.lk}")
    private String fromAddress;

    @Value("${notifications.email.enabled:false}")
    private boolean emailEnabled;

    /** MR12 — sent when the Finance Module activates a member. */
    public void sendMembershipActivated(Member member) {
        String name = displayName(member);
        sendEmail(member.getEmailAddress(),
                "Your Future Finance Institute membership is now active",
                "Dear " + name + ",\n\n"
                        + "Your membership (" + member.getMemberId() + ") is now active.\n"
                        + "Your membership documentation will be posted to you shortly.\n\n"
                        + "Future Finance Institute");
        sendSms(member.getMobileNumber(),
                "FFI: Your membership " + member.getMemberId()
                        + " is now active. Your documentation will be posted soon.");
    }

    /** MR18 — sent when the member's documentation is put in the post. */
    public void sendDocumentationDispatched(Member member) {
        String name = displayName(member);
        sendEmail(member.getEmailAddress(),
                "Your Future Finance Institute membership documentation has been posted",
                "Dear " + name + ",\n\n"
                        + "The membership documentation for " + member.getMemberId()
                        + " has been posted to your registered address.\n"
                        + "Please sign the enclosed Signature Card and hand it in at any District Office.\n\n"
                        + "Future Finance Institute");
        sendSms(member.getMobileNumber(),
                "FFI: Documentation for membership " + member.getMemberId()
                        + " has been posted. Please sign and return the Signature Card.");
    }

    private String displayName(Member member) {
        if (member.getNameWithInitials() != null && !member.getNameWithInitials().isBlank()) {
            return member.getNameWithInitials();
        }
        return member.getFullName() == null ? "Member" : member.getFullName();
    }

    private void sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return; // email is optional on the application
        }
        if (!emailEnabled || mailSender == null) {
            System.out.println("[notification][email disabled] to=" + to + " subject=" + subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("[notification][email sent] to=" + to);
        } catch (Exception error) {
            // Never fail the business action because a notification bounced.
            System.err.println("[notification][email FAILED] to=" + to + " : " + error.getMessage());
        }
    }

    /**
     * STUB. Replace the body with a real gateway call when one is procured; the
     * call sites and message text do not need to change.
     */
    private void sendSms(String to, String text) {
        if (to == null || to.isBlank()) {
            return; // mobile is optional on the application
        }
        System.out.println("[notification][sms STUB - not sent] to=" + to + " text=" + text);
    }
}
