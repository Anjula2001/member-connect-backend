package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MembershipEligibilityConfigDTO;
import com.memberconnect.backend.model.MembershipEligibilityConfig;
import com.memberconnect.backend.repository.MembershipEligibilityConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MembershipEligibilityService {

    @Autowired
    private MembershipEligibilityConfigRepository repository;

    public MembershipEligibilityConfig getConfig() {
        return repository.findAll().stream().findFirst()
                .orElseGet(() -> repository.save(new MembershipEligibilityConfig()));
    }

    public void seedDefaultIfEmpty() {
        if (repository.count() == 0) {
            repository.save(new MembershipEligibilityConfig());
            System.out.println("Seeded default membership eligibility configuration.");
        }
    }

    public MembershipEligibilityConfigDTO getConfigDto() {
        return toDto(getConfig());
    }

    public MembershipEligibilityConfigDTO updateConfig(MembershipEligibilityConfigDTO dto) {
        MembershipEligibilityConfig config = getConfig();

        int min = dto.getMinimumAge() != null ? dto.getMinimumAge() : config.getMinimumAge();
        int max = dto.getMaximumAge() != null ? dto.getMaximumAge() : config.getMaximumAge();

        if (min < 0 || max < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ages cannot be negative.");
        }
        if (min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum age cannot be greater than maximum age.");
        }
        if (max > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum age is unrealistic.");
        }

        config.setMinimumAge(min);
        config.setMaximumAge(max);
        return toDto(repository.save(config));
    }

    private MembershipEligibilityConfigDTO toDto(MembershipEligibilityConfig c) {
        MembershipEligibilityConfigDTO dto = new MembershipEligibilityConfigDTO();
        dto.setId(c.getId());
        dto.setMinimumAge(c.getMinimumAge());
        dto.setMaximumAge(c.getMaximumAge());
        return dto;
    }
}
