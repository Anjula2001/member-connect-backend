package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.UserDTO;
import com.memberconnect.backend.service.Userservice;

@RestController
@CrossOrigin
@RequestMapping(value="api/v1/")
public class user{
        @Autowired
        private Userservice userservice;
    private UserDTO UserDTO;

        @GetMapping("/getUsers")
        public List<UserDTO> getUsers() {
            return userservice.getAllUsers();
        }

        @PostMapping("/saveUser")
        public UserDTO saveUser(@RequestBody UserDTO userDTO){
            return userservice.saveUser(UserDTO);
        }
}
 