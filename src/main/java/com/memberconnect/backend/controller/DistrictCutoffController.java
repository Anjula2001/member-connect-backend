package com.memberconnect.backend.controller;

import java.util.Optional;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.DistrictCutoffDTO;
import com.memberconnect.backend.model.DistrictCutoff;
import com.memberconnect.backend.service.DistrictCutoffService;

@RestController
@CrossOrigin(origins = "http://localhost:3000") // allow requests from your React app
public class DistrictCutoffController {

    private final DistrictCutoffService service;

    public DistrictCutoffController(DistrictCutoffService service) {
        this.service = service;
    }

    //fetch cutoff by district and exam year
    @GetMapping("/api/cutoff")
    public DistrictCutoffDTO getCutoff(
            @RequestParam String district,
            @RequestParam int year
    ) {
        Optional<DistrictCutoff> cutoff = service.getCutoff(district, year);

        return cutoff
                .map(dc -> new DistrictCutoffDTO(
                        dc.getId(),
                        dc.getDistrict(),
                        dc.getExamYear(),
                        dc.getCutoffMarks()
                ))
                .orElse(new DistrictCutoffDTO(0L, district, year, 0));
    }
}
