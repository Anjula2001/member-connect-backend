package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberDeathDocumentRepository extends JpaRepository<MemberDeathDocument, Long> {

    List<MemberDeathDocument> findByRecord_RecordId(String recordId);

    List<MemberDeathDocument> findByRecord_RecordIdAndDocumentType(String recordId, String documentType);
}
