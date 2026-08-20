package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeathDonationConfigRepository extends JpaRepository<DeathDonationConfig, Long> {

    Optional<DeathDonationConfig> findByConfigKey(String configKey);

    List<DeathDonationConfig> findByConfigKeyIn(List<String> configKeys);
}
