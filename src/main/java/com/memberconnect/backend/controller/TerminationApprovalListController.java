package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.service.TerminationApprovalListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terminationApprovalLists")
@CrossOrigin
public class TerminationApprovalListController {

    @Autowired
    private TerminationApprovalListService terminationApprovalListService;

    // Create a new termination approval list + board meeting.
    @PostMapping("/create")
    public ResponseEntity<?> createTerminationApprovalList(@RequestBody TerminationApprovalListDTO dto) {
        try {
            TerminationApprovalListDTO result = terminationApprovalListService.createTerminationApprovalList(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Retrieve all termination approval lists. 
    @GetMapping("/getAll")
    public ResponseEntity<List<TerminationApprovalListDTO>> getAllTerminationApprovalLists() {
        List<TerminationApprovalListDTO> result = terminationApprovalListService.getAllTerminationApprovalLists();
        return ResponseEntity.ok(result);
    }

    // Retrieve approval list byId.
    @GetMapping("/getByListId/{listId}")
    public ResponseEntity<?> getTerminationApprovalListByListId(@PathVariable String listId) {
        try {
            TerminationApprovalListDTO result = terminationApprovalListService.getTerminationApprovalListByListId(listId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Delete approval list
    @DeleteMapping("/delete/{listId}")
    public ResponseEntity<?> deleteTerminationApprovalList(@PathVariable String listId) {
        try {
            String message = terminationApprovalListService.deleteTerminationApprovalList(listId);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
