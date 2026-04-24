package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.BankBranch;

public interface BankBranchRepository extends JpaRepository<BankBranch, Long> {

    List<BankBranch> findByBankId(String bankId);

    Optional<BankBranch> findByBranchId(String branchId);

}