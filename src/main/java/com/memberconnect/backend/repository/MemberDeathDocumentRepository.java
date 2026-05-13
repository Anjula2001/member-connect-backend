package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberDeathDocumentRepository extends JpaRepository<MemberDeathDocument, Long> {
}
