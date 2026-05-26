package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.RequiredDocumentType;
import com.memberconnect.backend.repository.RequiredDocumentTypeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/required-document-types")
@CrossOrigin(origins = "http://localhost:3000")
public class RequiredDocumentTypeController {

    private final RequiredDocumentTypeRepository repository;

    public RequiredDocumentTypeController(RequiredDocumentTypeRepository repository) {
        this.repository = repository;
    }

    // Endpoint to get required document types by request type
    @GetMapping("/{requestType}")
    public List<RequiredDocumentType> getByRequestType(@PathVariable String requestType) {
        return repository.findByRequestType(requestType);
    }
}