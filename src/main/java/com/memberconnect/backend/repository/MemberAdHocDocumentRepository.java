package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.MemberAdHocDocument;

public interface MemberAdHocDocumentRepository extends JpaRepository<MemberAdHocDocument, Long> {

    /**
     * Oldest first.
     *
     * MMD09 shows the Ad-hoc Documents folder dated by "the date of the first document
     * uploaded", so the first element of this list is that date - no separate min()
     * query, and no risk of the two disagreeing.
     */
    List<MemberAdHocDocument> findByMemberIdOrderByUploadedAtAsc(String memberId);
}
