package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MemberDeathDocumentRepository extends JpaRepository<MemberDeathDocument, Long> {

    List<MemberDeathDocument> findByRecord_RecordId(String recordId);

    // Bulk counterpart of findByRecord_RecordId, for the death-records list screen.
    List<MemberDeathDocument> findByRecord_RecordIdIn(Collection<String> recordIds);

    List<MemberDeathDocument> findByRecord_RecordIdAndDocumentType(String recordId, String documentType);
}
