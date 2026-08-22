package com.memberconnect.backend.event;

/**
 * A Death Donation Request was approved (MMD05 / MMD06 / MMD07). SRS p.5: "The
 * Member will receive an SMS and Email mentioning that the Death Donation
 * Request has been approved."
 *
 * Unlike Record Member Death - where the notification waits for the Finance
 * completion - the donation SRS notifies on the approval decision itself, so
 * this fires as soon as the record reaches APPROVED.
 *
 * @param level human-readable level that approved, for the message body
 */
public record DeathDonationApprovedEvent(String memberId, String requestNo, String level) {
}
