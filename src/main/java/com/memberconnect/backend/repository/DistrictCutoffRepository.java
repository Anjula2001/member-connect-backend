package com.memberconnect.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.DistrictCutoff;

public interface DistrictCutoffRepository extends JpaRepository<DistrictCutoff, Long> {

    Optional<DistrictCutoff> findByDistrictAndExamYear(String district, int examYear);
}