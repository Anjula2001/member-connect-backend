package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Member_Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberApplicationRepository extends JpaRepository<Member_Application, Long> {

    Optional<Member_Application> findByApplicationID(String applicationID);

    Optional<Member_Application> findByNicNumber(String nicNumber);
}