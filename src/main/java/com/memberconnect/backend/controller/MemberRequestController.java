package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.service.AuthServices;
import com.memberconnect.backend.service.BasicProfileChangeRequestServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2")
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

 @PostMapping("/saveRequests")
    public String saveRequest(@RequestBody BasicProfileChangeRequestDTO dto){
     return basicProfileChangeRequestsServices.saveBasicProfileChangeRequest(dto);
 }


}
