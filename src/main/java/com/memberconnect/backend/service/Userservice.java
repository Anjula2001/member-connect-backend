package com.memberconnect.backend.service;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.UserDTO;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.UserRepo;

import jakarta.transaction.Transactional;

@Service
@Transactional// for data transaction
public class Userservice {
    @Autowired //inject dependencies
    private UserRepo userRepo;

    @Autowired
    private ModelMapper modelMapper;

    public List<UserDTO>getAllUsers(){
        List<User>userList = userRepo.findAll();
        return modelMapper.map(userList, new TypeToken<List<UserDTO>>(){}.getType());
    }

    public UserDTO saveUser(UserDTO UserDTO){
        userRepo.save(modelMapper.map(UserDTO,User.class));
        return UserDTO;
    }

}
