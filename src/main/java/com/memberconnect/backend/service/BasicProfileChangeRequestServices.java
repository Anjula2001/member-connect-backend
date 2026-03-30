package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.model.BasicProfileChangeRequest;
import com.memberconnect.backend.repository.BasicProfileChangeRequestRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class BasicProfileChangeRequestServices {



    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private BasicProfileChangeRequestRepo basicProfileChangeRequestRepo;

    public List<BasicProfileChangeRequestDTO> getBasicProfileChangeRequests(){
        List<BasicProfileChangeRequest> basicProfileChangeRequests = basicProfileChangeRequestRepo.findAll();
        return modelMapper.map(basicProfileChangeRequests,new TypeToken<List<BasicProfileChangeRequestDTO>>(){}.getType());
    }

    public String saveBasicProfileChangeRequest(BasicProfileChangeRequestDTO basicProfileChangeRequestDTO){
        BasicProfileChangeRequest entity = modelMapper.map(basicProfileChangeRequestDTO,BasicProfileChangeRequest.class);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }



}
