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

 @PutMapping("/updateRequest/{id}")
    public BasicProfileChangeRequestDTO updateRequest(@PathVariable Integer id, @RequestBody BasicProfileChangeRequestDTO dto) {
        return basicProfileChangeRequestsServices.updateProfileRequest(id, dto);
    }
 @DeleteMapping("/deletRequest/{id}")
    public String deleteRequest(@PathVariable Integer id) {
     return basicProfileChangeRequestsServices.deleteProfileRequest(id);
 }



}
