package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.dto.DeathDonationResponseDTO;
import com.memberconnect.backend.dto.MarkIncompleteDTO;
import com.memberconnect.backend.service.DeathDonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/death-donations")
@CrossOrigin  // allow requests from the Next.js dev server
public class DeathDonationController {

    @Autowired
    private DeathDonationService deathDonationService;



     // Creates a new Death Donation Request

    @PostMapping
    public ResponseEntity<?> createRequest(@RequestBody DeathDonationRequestDTO dto) {
        try {
            DeathDonationResponseDTO response = deathDonationService.createRequest(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    // Updates an existing NEW request
    @PutMapping("/{id}/save")
    public ResponseEntity<?> saveRequest(@PathVariable Long id,
                                         @RequestBody DeathDonationRequestDTO dto) {
        try {
            DeathDonationResponseDTO response = deathDonationService.saveRequest(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    // Validates and moves to SUBMITTED_FOR_APPROVAL.
    @PutMapping("/{id}/submit")
    public ResponseEntity<?> submitRequest(@PathVariable Long id,
                                           @RequestBody(required = false) DeathDonationRequestDTO dto) {
        try {
            DeathDonationResponseDTO response = deathDonationService.submitRequest(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    // Marks the request as INCOMPLETE and records the reason.
    @PutMapping("/{id}/incomplete")
    public ResponseEntity<?> markIncomplete(@PathVariable Long id,
                                            @RequestBody MarkIncompleteDTO dto) {
        try {
            DeathDonationResponseDTO response = deathDonationService.markIncomplete(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    // Changes the status
    @PutMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id,
                                          @RequestBody com.memberconnect.backend.dto.ChangeStatusDTO dto) {
        try {
            DeathDonationResponseDTO response = deathDonationService.changeStatus(id, dto.getStatus());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    // Retrieves a single Death Donation Request by its database id.
    @GetMapping("/{id}")
    public ResponseEntity<?> getRequest(@PathVariable Long id) {
        try {
            DeathDonationResponseDTO response = deathDonationService.getRequest(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    //Retrieves all Death Donation Requests.
    @GetMapping
    public ResponseEntity<?> getAllRequests() {
        try {
            return ResponseEntity.ok(deathDonationService.getAllRequests());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
