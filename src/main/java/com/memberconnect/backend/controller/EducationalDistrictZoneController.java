package com.memberconnect.backend.controller;

import com.memberconnect.backend.service.EducationalDistrictZoneService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/education")
@CrossOrigin(origins = "http://localhost:3000")
public class EducationalDistrictZoneController {

    private final EducationalDistrictZoneService service;

    public EducationalDistrictZoneController(EducationalDistrictZoneService service) {
        this.service = service;
    }

    @GetMapping("/districts")
    public List<String> getDistricts() {
        return service.getDistricts();
    }

    @GetMapping("/zones")
    public List<String> getZonesByDistrict(@RequestParam String district) {
        return service.getZonesByDistrict(district);
    }
}
