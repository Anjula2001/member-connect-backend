package com.memberconnect.backend.repository;


import com.memberconnect.backend.model.RemittanceAmountChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RemittanceAmountChangeRepo extends JpaRepository<RemittanceAmountChange,Integer> {

}
