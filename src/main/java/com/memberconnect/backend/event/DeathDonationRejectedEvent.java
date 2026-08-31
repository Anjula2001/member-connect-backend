package com.memberconnect.backend.event;

/**
 * A Death Donation Request was rejected at one of the three approval levels
 * (MMD05 / MMD06 / MMD07). Each says an SMS and an Email go to the Member
 * "indicating that the requested Death Donation is rejected with the entered
 * reason".
 *
 * @param level human-readable level the rejection came from, for the message body
 */
public record DeathDonationRejectedEvent(String memberId, String requestNo, String reason, String level) {
}
