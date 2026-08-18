package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.model.RemittanceAmountChange;
import com.memberconnect.backend.repository.RemittanceAmountChangeRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
    public RemittanceAmountChangeDTO remitanceRequestgetBhyID(Integer id){
        Optional <RemittanceAmountChange> entity = remittanceAmountChangeRepo.findById(id);
        if(entity.isPresent()){
            return modelMapper.map(entity.get(),RemittanceAmountChangeDTO.class);
        }else {
            return null;
        }
    }

    public String saveRemittanceRequest(RemittanceAmountChangeDTO remittanceAmountChangeDTO){
        RemittanceAmountChange entity = modelMapper.map(remittanceAmountChangeDTO, RemittanceAmountChange.class);
        remittanceAmountChangeRepo.save(entity);
        return  "success";
    }
    public String DeleteRemittanceRequest(@NonNull Integer id){
        if(!remittanceAmountChangeRepo.existsById(id)){
            throw new RuntimeException("RemittanceAmountChange not found");
        }
        remittanceAmountChangeRepo.deleteById(id);
        return "Deleted succesfully";

    }

    public RemittanceAmountChangeDTO updateRemittanceRequest(Integer id,RemittanceAmountChangeDTO dto){
        RemittanceAmountChange existingAmount = remittanceAmountChangeRepo.findById(id).orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        existingAmount.setNewStatus(dto.getNewStatus());
        existingAmount.setNewRemittanceAmount(dto.getNewRemittanceAmount());
        existingAmount.setNewRemittanceCurrency(dto.getNewRemittanceCurrency());
        existingAmount.setRemittanceAccountType(dto.getRemittanceAccountType());
        existingAmount.setMemberId(dto.getMemberId());
        RemittanceAmountChange updatedAmount = remittanceAmountChangeRepo.save(existingAmount);
        return modelMapper.map(updatedAmount,RemittanceAmountChangeDTO.class);
    }


}
