package com.memberconnect.backend.service.notification;

/**
 * Masks member contact details before they reach a log file.
 *
 * Logs are retained and shipped far more freely than the database, so no full
 * email address and no full mobile number is ever written to them. Enough of the
 * value is kept to make a delivery problem diagnosable, and no more.
 */
final class ContactMasking {

    private ContactMasking() {
    }

    /**
     * "chamara.perera@example.com" becomes "c***@example.com".
     * A value that does not look like an address is masked completely.
     */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }

        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }

        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * "0771234567" becomes "*******567". Anything too short to mask safely is
     * replaced entirely rather than partially revealed.
     */
    static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return "<none>";
        }

        String trimmed = mobile.trim();
        if (trimmed.length() <= 3) {
            return "***";
        }

        return "*".repeat(trimmed.length() - 3) + trimmed.substring(trimmed.length() - 3);
    }
}
