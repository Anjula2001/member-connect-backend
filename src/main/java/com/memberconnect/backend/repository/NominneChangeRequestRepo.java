package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.NommineChangeRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NominneChangeRequestRepo extends JpaRepository<NommineChangeRequests,Integer> {
}
