package com.memberconnect.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.model.DistrictCutoff;
import com.memberconnect.backend.repository.DistrictCutoffRepository;

@Service
public class DistrictCutoffService {

    private final DistrictCutoffRepository repository;

    public DistrictCutoffService(DistrictCutoffRepository repository) {
        this.repository = repository;
    }

    public Optional<DistrictCutoff> getCutoff(String district, int year) {
        return repository.findByDistrictAndExamYear(district, year);
    }
}