package com.memberconnect.backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping("api/v1/")

public class AuthController {
    @GetMapping("/auth")
    public String auth() {
        return "Auth endpoint is working!";
    }
}
