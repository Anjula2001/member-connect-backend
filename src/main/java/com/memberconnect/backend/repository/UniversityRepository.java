package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {

    /** Name uniqueness for the master screen; case-insensitive so "Colombo" and "colombo" collide. */
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}