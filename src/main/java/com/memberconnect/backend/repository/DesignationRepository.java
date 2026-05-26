package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
}