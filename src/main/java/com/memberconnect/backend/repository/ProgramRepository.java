package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Program;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {
}