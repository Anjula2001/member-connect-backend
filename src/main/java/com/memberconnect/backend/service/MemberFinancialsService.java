package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.*;
import com.memberconnect.backend.enums.AccountDataSource;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.*;
import com.memberconnect.backend.repository.MemberAccountRepository;
import com.memberconnect.backend.repository.MemberRemittanceRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The member's Remittance & Savings data (spec 4.8).
 *
 * Two deliberately separate concerns:
 *  - Remittance amounts are OURS: seeded from the approved application, later
 *    changed through the change-remittance request flow.
 *  - Operative accounts belong to the FINANCE MODULE, which is outside this
 *    project. They are hand-entered here in the meantime and stamped MANUAL, so a
 *    later Finance sync can overwrite them and leave un-migrated rows obvious.
 */
@Service
@Transactional
public class MemberFinancialsService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberRemittanceRepository remittanceRepository;

    @Autowired
    private MemberAccountRepository accountRepository;

    @Autowired
    private RemittanceMasterService remittanceMasterService;

    @Autowired
    private AuditService auditService;

    /**
     * Copies the approved application's amounts onto the new member.
     *
     * Without this the amounts collected at registration were simply lost when the
     * board approved the application — Member has no remittance columns of its own.
     */
    public void seedFromApplication(Member member, Member_Application application) {
        if (application == null) {
            return;
        }
        Map<RemittanceAccountCode, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put(RemittanceAccountCode.SHARE, application.getShareAccountAmount());
        amounts.put(RemittanceAccountCode.SPECIAL_DEPOSIT, application.getSpecialDepositAmount());
        amounts.put(RemittanceAccountCode.FIXED_DEPOSIT, application.getFixedDepositAmount());
        amounts.put(RemittanceAccountCode.SCHOLARSHIP_DEATH_DONATION_PENSION,
                application.getScholarshipDeathDonationPensionAmount());

        amounts.forEach((code, amount) -> {
            if (amount == null) {
                return;
            }
            MemberRemittance row = new MemberRemittance();
            row.setMember(member);
            row.setAccountCode(code);
            row.setAmount(amount);
            row.setEffectiveFrom(member.getMembershipStartDate());
            row.setUpdatedAt(LocalDateTime.now());
            remittanceRepository.save(row);
        });
    }

    public MemberFinancialsDTO getFinancials(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        List<RemittanceMasterAccountDTO> master = remittanceMasterService.getActive();
        List<MemberRemittance> remittances = remittanceRepository.findByMemberIdOrderByAccountCodeAsc(memberId);
        List<MemberAccount> accounts = accountRepository.findByMemberIdOrderByAccountCodeAsc(memberId);

        MemberFinancialsDTO dto = new MemberFinancialsDTO();
        dto.setMemberId(member.getId());
        dto.setMemberCode(member.getMemberId());
        dto.setMemberName(member.getNameWithInitials() != null
                ? member.getNameWithInitials() : member.getFullName());

        // Always present a row per configured account, so a member with nothing
        // recorded still shows the full set rather than an empty tab.
        List<MemberRemittanceDTO> remittanceDtos = new ArrayList<>();
        List<MemberAccountDTO> accountDtos = new ArrayList<>();
        for (RemittanceMasterAccountDTO account : master) {
            MemberRemittance existing = remittances.stream()
                    .filter(r -> r.getAccountCode() == account.getAccountCode()).findFirst().orElse(null);
            MemberRemittanceDTO r = new MemberRemittanceDTO();
            r.setId(existing == null ? null : existing.getId());
            r.setAccountCode(account.getAccountCode());
            r.setAccountName(account.getAccountName());
            r.setAmount(existing == null ? null : existing.getAmount());
            r.setEffectiveFrom(existing == null ? null : existing.getEffectiveFrom());
            r.setFixedAmount(account.getFixedAmount());
            r.setMinimumAmount(account.getMinimumAmount());
            remittanceDtos.add(r);

            MemberAccount acc = accounts.stream()
                    .filter(a -> a.getAccountCode() == account.getAccountCode()).findFirst().orElse(null);
            MemberAccountDTO a = new MemberAccountDTO();
            a.setId(acc == null ? null : acc.getId());
            a.setAccountCode(account.getAccountCode());
            a.setAccountName(account.getAccountName());
            a.setAccountNumber(acc == null ? null : acc.getAccountNumber());
            a.setBalance(acc == null ? null : acc.getBalance());
            a.setOpenedDate(acc == null ? null : acc.getOpenedDate());
            a.setSource(acc == null ? null : acc.getSource());
            a.setLastSyncedAt(acc == null ? null : acc.getLastSyncedAt());
            accountDtos.add(a);
        }

        dto.setRemittances(remittanceDtos);
        dto.setAccounts(accountDtos);
        dto.setAwaitingFinanceIntegration(
                accounts.stream().noneMatch(a -> a.getSource() == AccountDataSource.FINANCE));
        dto.setIsRemittance(member.isRemittance());
        dto.setIsSettlement(member.isSettlement());
        return dto;
    }

    /** Manual entry from the admin screen. Validates against the Remittance Master. */
    public MemberFinancialsDTO updateFinancials(Long memberId, MemberFinancialsDTO request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        List<RemittanceMasterAccountDTO> master = remittanceMasterService.getActive();
        LocalDateTime now = LocalDateTime.now();

        if (request.getRemittances() != null) {
            for (MemberRemittanceDTO in : request.getRemittances()) {
                if (in.getAccountCode() == null || in.getAmount() == null) {
                    continue;
                }
                RemittanceMasterAccountDTO rule = master.stream()
                        .filter(m -> m.getAccountCode() == in.getAccountCode()).findFirst().orElse(null);
                if (rule == null) {
                    continue; // account no longer active in the master
                }
                BigDecimal amount = rule.getFixedAmount() != null ? rule.getFixedAmount() : in.getAmount();
                if (amount.signum() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            rule.getAccountName() + " cannot be negative.");
                }
                if (rule.getFixedAmount() == null && rule.getMinimumAmount() != null
                        && amount.compareTo(rule.getMinimumAmount()) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                            "%s must be at least %s.", rule.getAccountName(),
                            rule.getMinimumAmount().toPlainString()));
                }

                MemberRemittance row = remittanceRepository
                        .findByMemberIdAndAccountCode(memberId, in.getAccountCode())
                        .orElseGet(() -> {
                            MemberRemittance fresh = new MemberRemittance();
                            fresh.setMember(member);
                            fresh.setAccountCode(in.getAccountCode());
                            return fresh;
                        });
                row.setAmount(amount);
                row.setEffectiveFrom(in.getEffectiveFrom());
                row.setUpdatedAt(now);
                remittanceRepository.save(row);
            }
        }

        if (request.getAccounts() != null) {
            for (MemberAccountDTO in : request.getAccounts()) {
                if (in.getAccountCode() == null) {
                    continue;
                }
                boolean empty = (in.getAccountNumber() == null || in.getAccountNumber().isBlank())
                        && in.getBalance() == null;
                MemberAccount row = accountRepository
                        .findByMemberIdAndAccountCode(memberId, in.getAccountCode()).orElse(null);
                if (row == null && empty) {
                    continue; // nothing entered, nothing to create
                }
                if (row == null) {
                    row = new MemberAccount();
                    row.setMember(member);
                    row.setAccountCode(in.getAccountCode());
                }
                if (in.getBalance() != null && in.getBalance().signum() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Account balance cannot be negative.");
                }
                row.setAccountNumber(in.getAccountNumber());
                row.setBalance(in.getBalance());
                row.setOpenedDate(in.getOpenedDate());
                // Hand-entered by definition — only a Finance sync may set FINANCE.
                row.setSource(AccountDataSource.MANUAL);
                row.setUpdatedAt(now);
                accountRepository.save(row);
            }
        }

        // Temporary Scholarship finance eligibility. Null means "not sent", so a caller
        // that only edits remittances cannot silently clear a member's eligibility.
        if (request.getIsRemittance() != null || request.getIsSettlement() != null) {
            if (request.getIsRemittance() != null) {
                member.setRemittance(request.getIsRemittance());
            }
            if (request.getIsSettlement() != null) {
                member.setSettlement(request.getIsSettlement());
            }
            memberRepository.save(member);

            auditService.record(AuditService.MODULE_MEMBER, memberId,
                    "Scholarship Finance Eligibility Updated", null, null,
                    "Remittance=" + member.isRemittance() + ", Settlement=" + member.isSettlement());
        }

        auditService.record(AuditService.MODULE_MEMBER, memberId,
                "Remittance & Accounts Updated", null, null, "Manual entry (Finance Module not connected)");

        return getFinancials(memberId);
    }
}
