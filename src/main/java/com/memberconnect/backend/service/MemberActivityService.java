package com.memberconnect.backend.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * The single writer of {@link Member#getLastActivityDate()}, which is the field
 * the whole of MMD10 pivots on.
 *
 * <h2>Why this class exists</h2>
 *
 * Before it, {@code lastActivityDate} was written in exactly one place -
 * DormantDataSeeder, which set it to <em>today</em> for every member whose value
 * was null, on every single startup. No business flow touched it. That is not
 * merely a gap: on a database where the column was never populated it actively
 * suppressed the feature, because every member's clock reset on each restart and
 * the dormancy cutoff could never be crossed. The identification process was
 * correct and could never fire.
 *
 * Funnelling every writer through one method keeps the rule in one place. The
 * alternative - stamping the column from a dozen call sites - is the version that
 * the next feature forgets.
 *
 * <h2>What counts as activity, and the honest caveat</h2>
 *
 * SRS 4.2.1 says "accounts not updated for the specified period", and the
 * evaluation notes phrase it as no <em>remittance</em>. There is no
 * remittance-posting flow anywhere in this codebase: RemittanceMasterAccount
 * holds amount rules, Loan and LoanObligation are read-only stubs, and the
 * Finance Module does not exist. So every in-app caller of this method is a
 * <em>proxy</em> for the signal the SRS actually names, and the authoritative
 * input has to arrive from Finance - see FinanceMemberActivityController.
 *
 * Deliberately excluded as callers: card printing and document dispatch. Those
 * are office actions performed <em>on</em> a member rather than by them, and
 * counting them would keep a genuinely dormant member perpetually fresh every
 * time Head Office reprinted a card.
 */
@Service
public class MemberActivityService {

    private static final Logger log = LoggerFactory.getLogger(MemberActivityService.class);

    private final MemberRepository memberRepository;
    private final MemberStatusHistoryService memberStatusHistoryService;

    public MemberActivityService(
            MemberRepository memberRepository,
            MemberStatusHistoryService memberStatusHistoryService) {
        this.memberRepository = memberRepository;
        this.memberStatusHistoryService = memberStatusHistoryService;
    }

    /** Records activity for a member id, doing nothing if no such member exists. */
    @Transactional
    public void recordActivity(String memberId, String source) {
        recordActivity(memberId, LocalDate.now(), source);
    }

    /** Records activity for a member id on a given date. */
    @Transactional
    public void recordActivity(String memberId, LocalDate activityDate, String source) {
        if (memberId == null || memberId.isBlank()) {
            return;
        }

        memberRepository.findByMemberId(memberId)
                .ifPresent(member -> recordActivity(member, activityDate, source));
    }

    /** Records activity as of today. */
    @Transactional
    public void recordActivity(Member member, String source) {
        recordActivity(member, LocalDate.now(), source);
    }

    /**
     * Records activity on a given date.
     *
     * Never moves the date backwards, so Finance can safely replay a month
     * without un-ageing an account that has transacted since.
     */
    @Transactional
    public void recordActivity(Member member, LocalDate activityDate, String source) {
        if (member == null || activityDate == null) {
            return;
        }

        LocalDate current = member.getLastActivityDate();
        boolean moved = false;

        if (current == null || current.isBefore(activityDate)) {
            member.setLastActivityDate(activityDate);
            moved = true;
        }

        // MMD10: an account just touched is not dormant. Clearing the flag here
        // makes runIdentification's re-validation loop a safety net rather than
        // the only route back to Active - a member who transacts should not have
        // to wait until the 25th to stop being listed.
        if (member.getStatus() == MemberStatus.SELECTED_FOR_DORMANT) {
            member.setStatus(MemberStatus.ACTIVE);
            member.setDormantSelectionDate(null);
            moved = true;
            memberStatusHistoryService.record(member, MemberStatus.SELECTED_FOR_DORMANT,
                    MemberStatus.ACTIVE, activityDate, "DORMANT_FLAG_CLEARED_BY_ACTIVITY",
                    "Account activity from " + source);

            log.info(
                    "Dormant flag cleared by activity. memberId={}, source={}",
                    member.getMemberId(), source
            );
        }

        // Deliberately does NOT clear SENT_FOR_DORMANT_APPROVAL. That member is
        // already on a board list, and silently pulling them out would leave the
        // list inconsistent behind the Secretariat's back - the printed sheet
        // would no longer match the system. The date is still stamped, and
        // DormantMemberDTO.activitySinceListing surfaces it to the board and on
        // the printed sheet so a human decides.

        if (moved) {
            memberRepository.save(member);
        }
    }
}
