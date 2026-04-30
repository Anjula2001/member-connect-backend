package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.repository.NameChangeRequestRepo;
import com.memberconnect.backend.service.NameChangeRequstServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api5/namechange")
@CrossOrigin(origins = "http://localhost:3000")

public class NameChangeRequestController {
    @Autowired
    public NameChangeRequestRepo nameChangeRequestRepo;
    @Autowired
    public NameChangeRequstServices nameChangeRequstServices;

    @GetMapping("/getnamechange")
    public List<NameChangeRequestDTO> getNameChangeRequests(){
        return nameChangeRequstServices.NameChangeRequestgetAll();
    }
    @GetMapping("/getnamebyid/{id}")
    public NameChangeRequestDTO getNameChangeRequestsById(@PathVariable Integer id){
        NameChangeRequestDTO entity = nameChangeRequstServices.getRequestById(id);
        return entity;
    }
    @PostMapping("/savenamechange")
    public NameChangeRequestDTO saveNameChangeRequest(@RequestBody NameChangeRequestDTO nameChangeRequestDTO){
        return nameChangeRequstServices.addNameChangeRequestService(nameChangeRequestDTO);
    }
    @PutMapping("/updatenamechange")
    public NameChangeRequestDTO updateNameChangeRequest(@PathVariable Integer id , @RequestBody NameChangeRequestDTO nameChangeRequestDTO){
        return nameChangeRequstServices.updateNameChangeRequestService(id,nameChangeRequestDTO);
    }

    @DeleteMapping("/deletnameChange/{id}")
    public String deleteNameChangeRequest(@PathVariable Integer id){
        return nameChangeRequstServices.deleteNameChangeRequestService(id);
    }

}
