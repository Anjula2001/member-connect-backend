package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.model.NameChangeRequest;
import com.memberconnect.backend.repository.NameChangeRequestRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@Transactional

public class NameChangeRequstServices {
    @Autowired
    private NameChangeRequestRepo nameChangeRequestRepo;
    @Autowired
    private ModelMapper modelMapper;

    public List<NameChangeRequestDTO> NameChangeRequestgetAll(){
        List<NameChangeRequest> nameChangeRequests = nameChangeRequestRepo.findAll();
        return modelMapper.map(nameChangeRequests,new TypeToken<List<NameChangeRequestDTO>>(){}.getType());
    }

    public String addNameChangeRequestService(NameChangeRequestDTO nameChangeRequestDTO){
        modelMapper.map(nameChangeRequestDTO,NameChangeRequest.class);
        return "success";
    }

    public NameChangeRequestDTO updateNameChangeRequestService(Integer id,NameChangeRequestDTO dto){
        NameChangeRequest exsitingName = nameChangeRequestRepo.findById(id).orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        modelMapper.map(dto,exsitingName);
           exsitingName.setNameChangeRequestID(id);
           NameChangeRequest updatedName = nameChangeRequestRepo.save(exsitingName);
           return modelMapper.map(updatedName, NameChangeRequestDTO.class);

    }
    public String deleteNameChangeRequestService(Integer id){
        if(!nameChangeRequestRepo.existsById(id)){
            throw new RuntimeException("Request not found with id: " + id);
        }
        nameChangeRequestRepo.deleteById(id);
        return "delete Succesfully";
    }
}
