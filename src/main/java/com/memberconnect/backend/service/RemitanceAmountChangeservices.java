package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.model.RemittanceAmountChange;
import com.memberconnect.backend.repository.RemittanceAmountChangeRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@Transactional

public class RemitanceAmountChangeservices {
    @Autowired
    public RemittanceAmountChangeRepo remittanceAmountChangeRepo;
    @Autowired
    public ModelMapper modelMapper;

    public List<RemittanceAmountChangeDTO> getRemitanceRequests(){
        List<RemittanceAmountChange> remittanceAmountChanges = remittanceAmountChangeRepo.findAll();
        return modelMapper.map(remittanceAmountChanges,new TypeToken<List<RemittanceAmountChangeDTO>>(){}.getType());
    }

    public String saveRemittanceRequest(RemittanceAmountChangeDTO remittanceAmountChangeDTO){
        modelMapper.map(remittanceAmountChangeDTO,RemittanceAmountChange.class);
        return  "success";
    }
    public String DeleteRemittanceRequest(Integer id){
        if(!remittanceAmountChangeRepo.existsById(id)){
            throw new RuntimeException("RemittanceAmountChange not found");
        }
        remittanceAmountChangeRepo.deleteById(id);
        return "Deleted succesfully";

    }

    public RemittanceAmountChangeDTO updateRemittanceRequest(Integer id,RemittanceAmountChangeDTO dto){
        RemittanceAmountChange existingAmount = remittanceAmountChangeRepo.findById(id).orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        modelMapper.map(dto,existingAmount);
        RemittanceAmountChange updatedAmount = remittanceAmountChangeRepo.save(existingAmount);
        return modelMapper.map(updatedAmount,RemittanceAmountChangeDTO.class);
    }


}
