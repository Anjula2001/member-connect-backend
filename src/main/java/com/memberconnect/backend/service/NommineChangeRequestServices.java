package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.model.NameChangeRequest;
import com.memberconnect.backend.model.NommineChangeRequests;
import com.memberconnect.backend.repository.NominneChangeRequestRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Transactional

public class NommineChangeRequestServices {
    @Autowired
    public NominneChangeRequestRepo nominneChangeRequestRepo;
    @Autowired
    public ModelMapper modelMapper;

 public List<NommineChangeRequestDTO> nommineChangeRequestFindService(){
 List<NommineChangeRequests> nommineChangeRequests = nominneChangeRequestRepo.findAll();
 return modelMapper.map(nommineChangeRequests,new TypeToken<List<NommineChangeRequestDTO>>(){}.getType());
 }

 public NommineChangeRequestDTO getNommineChangeRequestById(Integer id){
     Optional <NommineChangeRequests> optionalEntity =  nominneChangeRequestRepo.findById(id);

     if(optionalEntity.isPresent()){
         return modelMapper.map(optionalEntity.get(),NommineChangeRequestDTO.class);
     }else {
         return null;
     }
 }

 public NommineChangeRequestDTO NommineChangeRequestaddService(NommineChangeRequestDTO nommineChangeRequestDTO){
nominneChangeRequestRepo.save(modelMapper.map(nommineChangeRequestDTO,NommineChangeRequests.class));
return nommineChangeRequestDTO;

 }

 public String deleteNommineChangeRequestService(Integer id){
     if(!nominneChangeRequestRepo.existsById(id)){
         throw new RuntimeException("Request not found with id: " + id);
     }
     nominneChangeRequestRepo.deleteById(id);
     return "succesfully deleted";
 }

 public NommineChangeRequestDTO updateNommineChange(Integer id,NommineChangeRequestDTO nommineChangeRequestDTO){
     NommineChangeRequests exsitingnommineChangeRequests = nominneChangeRequestRepo.findById(id).orElseThrow(() -> new RuntimeException("Request not found with id: " + id) );
     modelMapper.map(nommineChangeRequestDTO, exsitingnommineChangeRequests);
     NommineChangeRequests updatedNommineChangeRequest = nominneChangeRequestRepo.save(exsitingnommineChangeRequests);
     return modelMapper.map(updatedNommineChangeRequest,NommineChangeRequestDTO.class);

 }



}
