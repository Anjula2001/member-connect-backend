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


public class NameChangeRequestController {
    @Autowired
    public NameChangeRequestRepo nameChangeRequestRepo;
    @Autowired
    public NameChangeRequstServices nameChangeRequstServices;

    @GetMapping("/getnamechange")
    public List<NameChangeRequestDTO> getNameChangeRequests(){
        return nameChangeRequstServices.NameChangeRequestgetAll();
    }
    @PostMapping("/savenamechange")
    public String saveNameChangeRequest(@RequestBody NameChangeRequestDTO nameChangeRequestDTO){
        return nameChangeRequstServices.addNameChangeRequestService(nameChangeRequestDTO);
    }
    @PutMapping("/updateNommine")
    public NameChangeRequestDTO updateNameChangeRequest(@PathVariable Integer id , @RequestBody NameChangeRequestDTO nameChangeRequestDTO){
        return nameChangeRequstServices.updateNameChangeRequestService(id,nameChangeRequestDTO);
    }

    @DeleteMapping("/deletnameChange")
    public String deleteNameChangeRequest(@PathVariable Integer id){
        return nameChangeRequstServices.deleteNameChangeRequestService(id);
    }

}
