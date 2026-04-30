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
import java.util.Optional;

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
    public BasicProfileChangeRequestDTO getRequestById(Integer id) {
        // Find the entity by ID
        Optional<BasicProfileChangeRequest> optionalEntity = basicProfileChangeRequestRepo.findById(id);

        if (optionalEntity.isPresent()) {
            // Map the entity to DTO if found
            return modelMapper.map(optionalEntity.get(), BasicProfileChangeRequestDTO.class);
        } else {
            // Return null or throw a custom exception if not found
            return null;
        }
    }

    public String saveBasicProfileChangeRequest(BasicProfileChangeRequestDTO basicProfileChangeRequestDTO){
        BasicProfileChangeRequest entity = modelMapper.map(basicProfileChangeRequestDTO,BasicProfileChangeRequest.class);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }

    public BasicProfileChangeRequestDTO updateProfileRequest(Integer id, BasicProfileChangeRequestDTO dto) {

        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        modelMapper.map(dto, existingEntity);
        existingEntity.setId(id);
        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return modelMapper.map(updatedEntity, BasicProfileChangeRequestDTO.class);
    }

    public String deleteProfileRequest(Integer id) {
        if (!basicProfileChangeRequestRepo.existsById(id)) {
            throw new RuntimeException("Cannot delete: Request not found with id: " + id);
        } basicProfileChangeRequestRepo.deleteById(id);
        return "Succefully deleted request";
    }




}
