package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.dto.ProfileChangeDecisionDTO;
import com.memberconnect.backend.service.BasicProfileChangeRequestServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authorization: the class-level rule keeps the three specialist roles (Accounts,
 * Scholarship Officer, Death Donation Officer) out of the module entirely; individual
 * methods narrow it further.
 *
 * These annotations are the enforcement. The role checks on the screens decide what is
 * shown and can be bypassed by calling the API directly, so every rule in the matrix
 * that matters is repeated here.
 */
@RestController
@RequestMapping("/api/v2")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE')")
public class MemberRequestController {
 @Autowired
 public BasicProfileChangeRequestServices basicProfileChangeRequestsServices;
 @GetMapping("/getAllRequsts")
public String getAllRequsts(){
     return "getAllRequsts";
 }

 @GetMapping("/getRequests")
    public List<BasicProfileChangeRequestDTO> getRequests(){
     return basicProfileChangeRequestsServices.getBasicProfileChangeRequests();
 }
 @GetMapping("getRequest/{id}")
 public ResponseEntity<BasicProfileChangeRequestDTO> getRequestById(@PathVariable Integer id) {
     BasicProfileChangeRequestDTO dto = basicProfileChangeRequestsServices.getRequestById(id);

     if (dto != null) {
         return ResponseEntity.ok(dto);
     } else {
         return ResponseEntity.notFound().build();
     }
 }

 /** MMC01: raised by District Office. Board Secretary decides but never opens one. */
 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
 @PostMapping("/saveRequests")
    public String saveRequest(@jakarta.validation.Valid @RequestBody BasicProfileChangeRequestDTO dto){
      return basicProfileChangeRequestsServices.saveBasicProfileChangeRequest(dto);
 }

 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
 @PostMapping(value = "/saveRequestWithDocument", consumes = {"multipart/form-data"})
    public String saveRequestWithDocument(
            @jakarta.validation.Valid @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.saveWithDocument(dto, file);
    }

 /**
  * Editing a submitted request is not an SRS function - MMC01 forbids it outright - so
  * it is held to the roles that can decide the request. A District Office user cannot
  * revise what they have already sent for approval.
  */
 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
 @PutMapping("/updateRequest/{id}")
    public BasicProfileChangeRequestDTO updateRequest(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody BasicProfileChangeRequestDTO dto) {
        return basicProfileChangeRequestsServices.updateProfileRequest(id, dto);
    }

 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
 @PutMapping(value = "/updateRequestWithDocument/{id}", consumes = {"multipart/form-data"})
    public BasicProfileChangeRequestDTO updateRequestWithDocument(
            @PathVariable Integer id,
            @jakarta.validation.Valid @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.updateWithDocument(id, dto, file);
    }

 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
 @PutMapping("/updateRequestStatus/{id}")
    public BasicProfileChangeRequestDTO updateRequestStatus(
            @PathVariable Integer id, 
            @RequestParam("status") com.memberconnect.backend.enums.ApplicationStatus status) {
        return basicProfileChangeRequestsServices.updateStatus(id, status);
    }

 /**
  * MMC04 - approve or reject, in one transaction that also updates the Member Profile.
  *
  * Replaces the browser-side sequence of "PUT the member, then PUT the request status",
  * which was neither atomic nor correct: it addressed the member endpoint by the
  * membership number rather than the Member table's numeric id, so every approval
  * failed converting "MEM-2026-001" to a Long.
  */
 /**
  * MMC04. District Office is deliberately excluded: it raises Basic Profile changes
  * but does not decide them. ProfileChangeStatusPolicy still enforces which statuses
  * may be decided; this decides who may do the deciding.
  */
 @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
 @PutMapping("/requests/{id}/decision")
 public BasicProfileChangeRequestDTO decide(
         @PathVariable Integer id,
         @RequestBody ProfileChangeDecisionDTO decision) {
     return basicProfileChangeRequestsServices.decide(id, decision);
 }

 @DeleteMapping("/deletRequest/{id}")
    public String deleteRequest(@PathVariable Integer id) {
     return basicProfileChangeRequestsServices.deleteProfileRequest(id);
 }



}
