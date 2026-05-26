package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UniversityProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniversityProgramRepository extends JpaRepository<UniversityProgram, Long> {

    List<UniversityProgram> findByUniversityId(Long universityId);

    Optional<UniversityProgram> findByUniversityIdAndProgramId(Long universityId, Long programId);
}