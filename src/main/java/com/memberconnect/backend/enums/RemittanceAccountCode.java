package com.memberconnect.backend.enums;

/**
 * The remittance accounts collected on a New Member Registration.
 *
 * These codes map 1:1 onto the existing amount columns on Member_Application
 * (shareAccountAmount, specialDepositAmount, fixedDepositAmount,
 * scholarshipDeathDonationPensionAmount). The Remittance Master drives each
 * account's label, fixed/minimum amount and mandatory flag; it does not currently
 * add or remove accounts, since the application table stores them as fixed columns.
 */
public enum RemittanceAccountCode {
    SHARE,
    SPECIAL_DEPOSIT,
    FIXED_DEPOSIT,
    SCHOLARSHIP_DEATH_DONATION_PENSION
}
