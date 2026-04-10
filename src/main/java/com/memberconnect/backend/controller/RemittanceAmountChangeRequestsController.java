package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.service.RemitanceAmountChangeservices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api4/remitance")
public class RemittanceAmountChangeRequestsController {
    @Autowired
    public RemitanceAmountChangeservices remittanceAmountChangeservices;

    @GetMapping("/getRemitance")
    public List<RemittanceAmountChangeDTO> getRemitance(){
        return remittanceAmountChangeservices.getRemitanceRequests();
    }
    @PostMapping("/saveRemitance")
    public String saveRemitance(@RequestBody RemittanceAmountChangeDTO dto){
        return remittanceAmountChangeservices.saveRemittanceRequest(dto);
    }
    @PutMapping("/updateRemitance")
    public RemittanceAmountChangeDTO updateRemitance(@PathVariable Integer id,@RequestBody RemittanceAmountChangeDTO dto){
        return remittanceAmountChangeservices.updateRemittanceRequest(id,dto);
    }
    @DeleteMapping("")
    public String deleteRemitance(@PathVariable Integer id){
        return remittanceAmountChangeservices.DeleteRemittanceRequest(id);
    }




}
