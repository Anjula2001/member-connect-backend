package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.BasicProfileChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BasicProfileChangeRequestRepo extends JpaRepository<BasicProfileChangeRequest,Integer> {
}