package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.RemittanceMasterAccountDTO;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.RemittanceMasterAccount;
import com.memberconnect.backend.repository.RemittanceMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class RemittanceMasterService {

    @Autowired
    private RemittanceMasterRepository repository;

    /**
     * Seeds the four accounts the registration form collects. Amounts are left null
     * (free entry) so nothing is silently enforced until Accounts configures it.
     */
    public void seedDefaultsIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        seed(RemittanceAccountCode.SHARE, "Share Account", 1, true);
        seed(RemittanceAccountCode.SPECIAL_DEPOSIT, "Special Deposit Account", 2, true);
        seed(RemittanceAccountCode.FIXED_DEPOSIT, "Fixed Deposit Account", 3, true);
        // Per the spec this one is not user-editable on the application — it is auto
        // picked from the configured amount, so it is seeded as non-mandatory.
        seed(RemittanceAccountCode.SCHOLARSHIP_DEATH_DONATION_PENSION,
                "Scholarship / Death Donation / Pension", 4, false);
        System.out.println("Seeded default Remittance Master accounts.");
    }

    private void seed(RemittanceAccountCode code, String name, int order, boolean mandatory) {
        RemittanceMasterAccount a = new RemittanceMasterAccount();
        a.setAccountCode(code);
        a.setAccountName(name);
        a.setDisplayOrder(order);
        a.setMandatory(mandatory);
        a.setActive(true);
        repository.save(a);
    }

    public List<RemittanceMasterAccountDTO> getAll() {
        return repository.findAllByOrderByDisplayOrderAsc().stream().map(this::toDto).toList();
    }

    /** Only the accounts the registration form should actually render. */
    public List<RemittanceMasterAccountDTO> getActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream().map(this::toDto).toList();
    }

    public RemittanceMasterAccountDTO update(Long id, RemittanceMasterAccountDTO dto) {
        RemittanceMasterAccount account = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remittance account not found"));

        if (dto.getAccountName() != null) {
            if (dto.getAccountName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account name is required.");
            }
            account.setAccountName(dto.getAccountName().trim());
        }

        BigDecimal fixed = dto.getFixedAmount();
        BigDecimal minimum = dto.getMinimumAmount();
        if (fixed != null && fixed.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fixed amount cannot be negative.");
        }
        if (minimum != null && minimum.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum amount cannot be negative.");
        }
        // A fixed amount locks the field, so a minimum alongside it is contradictory —
        // reject rather than silently letting one of the two rules win at entry time.
        if (fixed != null && minimum != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Set either a fixed amount or a minimum amount for an account, not both.");
        }
        account.setFixedAmount(fixed);
        account.setMinimumAmount(minimum);

        if (dto.getMandatory() != null) account.setMandatory(dto.getMandatory());
        if (dto.getActive() != null) account.setActive(dto.getActive());
        if (dto.getDisplayOrder() != null) account.setDisplayOrder(dto.getDisplayOrder());

        return toDto(repository.save(account));
    }

    private RemittanceMasterAccountDTO toDto(RemittanceMasterAccount a) {
        RemittanceMasterAccountDTO dto = new RemittanceMasterAccountDTO();
        dto.setId(a.getId());
        dto.setAccountCode(a.getAccountCode());
        dto.setAccountName(a.getAccountName());
        dto.setFixedAmount(a.getFixedAmount());
        dto.setMinimumAmount(a.getMinimumAmount());
        dto.setMandatory(a.getMandatory());
        dto.setDisplayOrder(a.getDisplayOrder());
        dto.setActive(a.getActive());
        return dto;
    }
}
