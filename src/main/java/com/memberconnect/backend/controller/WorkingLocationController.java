package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.repository.WorkingLocationRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/working-locations")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkingLocationController {

    private final WorkingLocationRepository workingLocationRepository;

    public WorkingLocationController(
            WorkingLocationRepository workingLocationRepository
    ) {
        this.workingLocationRepository = workingLocationRepository;
    }

    @GetMapping("/{id}")
    public WorkingLocation getWorkingLocationById(@PathVariable Long id) {
        return workingLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Working location not found"));
    }
}
