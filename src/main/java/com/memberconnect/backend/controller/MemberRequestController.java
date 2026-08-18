package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
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
    public String saveRequest(@RequestBody BasicProfileChangeRequestDTO dto){
      return basicProfileChangeRequestsServices.saveBasicProfileChangeRequest(dto);
 }

 @PostMapping(value = "/saveRequestWithDocument", consumes = {"multipart/form-data"})
    public String saveRequestWithDocument(
            @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.saveWithDocument(dto, file);
    }

 @PutMapping("/updateRequest/{id}")
    public BasicProfileChangeRequestDTO updateRequest(@PathVariable Integer id, @RequestBody BasicProfileChangeRequestDTO dto) {
        return basicProfileChangeRequestsServices.updateProfileRequest(id, dto);
    }

 @PutMapping(value = "/updateRequestWithDocument/{id}", consumes = {"multipart/form-data"})
    public BasicProfileChangeRequestDTO updateRequestWithDocument(
            @PathVariable Integer id,
            @RequestPart("request") BasicProfileChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return basicProfileChangeRequestsServices.updateWithDocument(id, dto, file);
    }

 @PutMapping("/updateRequestStatus/{id}")
    public BasicProfileChangeRequestDTO updateRequestStatus(
            @PathVariable Integer id, 
            @RequestParam("status") com.memberconnect.backend.enums.ApplicationStatus status) {
        return basicProfileChangeRequestsServices.updateStatus(id, status);
    }

 @DeleteMapping("/deletRequest/{id}")
    public String deleteRequest(@PathVariable Integer id) {
     return basicProfileChangeRequestsServices.deleteProfileRequest(id);
 }



}
