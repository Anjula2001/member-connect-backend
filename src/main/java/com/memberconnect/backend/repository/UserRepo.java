package com.memberconnect.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.memberconnect.backend.model.User;

@Repository
public interface UserRepo extends JpaRepository<User, Integer> {
    
}
