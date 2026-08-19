package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.service.RemitanceAmountChangeservices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api4/remitance")
@CrossOrigin("http://localhost:3000")
public class RemittanceAmountChangeRequestsController {
    @Autowired
    public RemitanceAmountChangeservices remittanceAmountChangeservices;

    @GetMapping("/getRemitance")
    public List<RemittanceAmountChangeDTO> getRemitance(){
        return remittanceAmountChangeservices.getRemitanceRequests();
    }
    @GetMapping("/getRemitanceById/{id}")
    public RemittanceAmountChangeDTO getRemittanceById(@PathVariable Integer id){
        return remittanceAmountChangeservices.remitanceRequestgetBhyID(id);
    }
    @PostMapping("/saveRemitance")
    public String saveRemitance(@jakarta.validation.Valid @RequestBody RemittanceAmountChangeDTO dto){
        return remittanceAmountChangeservices.saveRemittanceRequest(dto);
    }
    @PutMapping("/updateRemitance/{id}")
    public RemittanceAmountChangeDTO updateRemitance(@PathVariable Integer id,@jakarta.validation.Valid @RequestBody RemittanceAmountChangeDTO dto){
        return remittanceAmountChangeservices.updateRemittanceRequest(id,dto);
    }
    @DeleteMapping("/deleteRemitance/{id}")
    public String deleteRemitance(@PathVariable Integer id){
        return remittanceAmountChangeservices.DeleteRemittanceRequest(id);
    }




}
