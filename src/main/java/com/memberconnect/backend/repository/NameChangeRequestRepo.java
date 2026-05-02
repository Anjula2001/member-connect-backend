package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.NameChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NameChangeRequestRepo extends JpaRepository<NameChangeRequest,Integer> {
}
