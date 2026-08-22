package com.memberconnect.backend.service;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberStatusHistory;
import com.memberconnect.backend.repository.MemberStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Records and reads a member's status over time.
 *
 * Every flow that changes Member.status calls {@link #record} straight after saving the
 * member, so the history is written in the same transaction as the change it describes:
 * unlike an audit row, this history decides whether later requests are accepted, so a
 * row that describes a change that was rolled back would be worse than none at all.
 *
 * Reads are deliberately permissive. {@link #statusOn} returns null - "not known" -
 * rather than guessing when a member has no row covering the date asked about, which is
 * the case for every status change made before this table existed. Callers decide what
 * to do with that; the scholarship validation treats it as no evidence of inactivity
 * and lets the request through, so missing history cannot reject a legitimate request.
 */
@Service
public class MemberStatusHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MemberStatusHistoryService.class);

    private final MemberStatusHistoryRepository historyRepository;

    public MemberStatusHistoryService(MemberStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * Records a status transition.
     *
     * @param effectiveDate the date the change took effect in the business sense - a
     *                      termination's effective date, a deceased date. Pass null
     *                      where the flow has no such date and today is the truth.
     */
    @Transactional
    public void record(
            Member member,
            MemberStatus from,
            MemberStatus to,
            LocalDate effectiveDate,
            String source
    ) {
        record(member, from, to, effectiveDate, source, null);
    }

    @Transactional
    public void record(
            Member member,
            MemberStatus from,
            MemberStatus to,
            LocalDate effectiveDate,
            String source,
            String remarks
    ) {
        if (member == null || member.getId() == null || to == null) {
            log.warn("Member status history row skipped: member or target status missing. source={}", source);
            return;
        }

        // Nothing moved, so there is nothing to record. Saving it would add a row that
        // statusOn would have to step over for no gain.
        if (from == to) {
            return;
        }

        MemberStatusHistory history = new MemberStatusHistory();
        history.setMember(member);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        history.setSource(source);
        history.setRemarks(remarks);

        historyRepository.save(history);
    }

    /**
     * The status the member held on the given date, or null when nothing is recorded on
     * or before it - which means "not known", never "was not active".
     */
    @Transactional(readOnly = true)
    public MemberStatus statusOn(String memberId, LocalDate date) {
        if (memberId == null || memberId.isBlank() || date == null) {
            return null;
        }

        List<MemberStatusHistory> rows =
                historyRepository.findStatusAsAt(memberId, date, PageRequest.of(0, 1));

        return rows.isEmpty() ? null : rows.get(0).getToStatus();
    }

    /**
     * Whether the member is known to have held a status other than ACTIVE on the given
     * date. False when the history says ACTIVE and false when it says nothing at all -
     * only recorded inactivity counts against a member.
     */
    @Transactional(readOnly = true)
    public boolean wasNotActiveOn(String memberId, LocalDate date) {
        MemberStatus status = statusOn(memberId, date);
        return status != null && status != MemberStatus.ACTIVE;
    }

    /** A member's full status history, oldest first. */
    @Transactional(readOnly = true)
    public List<MemberStatusHistory> getHistory(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return List.of();
        }
        return historyRepository.findByMember_MemberIdOrderByEffectiveDateAscRecordedAtAsc(memberId);
    }
}
