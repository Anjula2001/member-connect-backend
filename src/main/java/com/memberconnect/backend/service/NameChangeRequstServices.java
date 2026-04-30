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
import java.util.Optional;

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
    public NameChangeRequestDTO getRequestById(Integer id){
        Optional<NameChangeRequest> optionalEntity = nameChangeRequestRepo.findById(id);
        if(optionalEntity.isPresent()){
            return modelMapper.map(optionalEntity.get(),NameChangeRequestDTO.class);

        }else{
            return null;
        }

    }

    public NameChangeRequestDTO addNameChangeRequestService(NameChangeRequestDTO nameChangeRequestDTO){

        NameChangeRequest entity = modelMapper.map(nameChangeRequestDTO,NameChangeRequest.class);
        nameChangeRequestRepo.save(entity);
        return nameChangeRequestDTO;
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
