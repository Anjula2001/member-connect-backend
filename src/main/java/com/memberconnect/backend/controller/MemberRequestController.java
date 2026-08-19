package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.dto.ProfileChangeDecisionDTO;
import com.memberconnect.backend.service.BasicProfileChangeRequestServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2")
@CrossOrigin(origins = "http://localhost:3000")
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

 @PostMapping("/saveRequests")
    public String saveRequest(@jakarta.validation.Valid @RequestBody BasicProfileChangeRequestDTO dto){
      return basicProfileChangeRequestsServices.saveBasicProfileChangeRequest(dto);
 }

 @PostMapping(value = "/saveRequestWithDocument", consumes = {"multipart/form-data"})
    public String saveRequestWithDocument(
            @jakarta.validation.Valid @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.saveWithDocument(dto, file);
    }

 @PutMapping("/updateRequest/{id}")
    public BasicProfileChangeRequestDTO updateRequest(@PathVariable Integer id, @jakarta.validation.Valid @RequestBody BasicProfileChangeRequestDTO dto) {
        return basicProfileChangeRequestsServices.updateProfileRequest(id, dto);
    }

 @PutMapping(value = "/updateRequestWithDocument/{id}", consumes = {"multipart/form-data"})
    public BasicProfileChangeRequestDTO updateRequestWithDocument(
            @PathVariable Integer id,
            @jakarta.validation.Valid @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.updateWithDocument(id, dto, file);
    }

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
