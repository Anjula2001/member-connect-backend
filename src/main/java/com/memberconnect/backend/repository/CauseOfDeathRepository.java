package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.CauseOfDeath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CauseOfDeathRepository extends JpaRepository<CauseOfDeath, Long> {

    List<CauseOfDeath> findByActiveTrueOrderByNameAsc();
}
