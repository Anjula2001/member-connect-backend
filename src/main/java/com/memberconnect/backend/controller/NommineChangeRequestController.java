package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.model.NommineChangeRequests;
import com.memberconnect.backend.service.NommineChangeRequestServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v3")
@CrossOrigin("http://localhost:3000")
public class NommineChangeRequestController {
    @Autowired
    public NommineChangeRequestServices nommineChangeRequestServices;

    @GetMapping("/getnommine")
    public List<NommineChangeRequestDTO>getNewNommine(){
        return nommineChangeRequestServices.nommineChangeRequestFindService();
    }

    @GetMapping("/getnommineById/{Id}")
    public NommineChangeRequestDTO getNewNommineById(@PathVariable Integer Id){
      NommineChangeRequestDTO entity = nommineChangeRequestServices.getNommineChangeRequestById(Id);
      return entity;
    }

    @PostMapping("/saveNommine")
    public NommineChangeRequestDTO saveNommineChangeRequest(@RequestBody NommineChangeRequestDTO dto){
        return nommineChangeRequestServices.NommineChangeRequestaddService(dto);
    }
    @PutMapping("/updateNommine")
    public NommineChangeRequestDTO updateNommineChangeRequest(@PathVariable Integer id , @RequestBody NommineChangeRequestDTO dto){
        return nommineChangeRequestServices.updateNommineChange(id, dto);
    }
    @DeleteMapping("/deleteNommine/{id}")
    public String deleteNommineChangeRequest(@PathVariable Integer id){
        return nommineChangeRequestServices.deleteNommineChangeRequestService(id);
    }
}
