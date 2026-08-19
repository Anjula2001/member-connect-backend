package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathMinorAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MemberDeathMinorAccountRepository extends JpaRepository<MemberDeathMinorAccount, Long> {

    List<MemberDeathMinorAccount> findByRecord_Id(Long recordId);

    // Bulk counterpart of findByRecord_Id, for the death-records list screen.
    List<MemberDeathMinorAccount> findByRecord_IdIn(Collection<Long> recordIds);
}
