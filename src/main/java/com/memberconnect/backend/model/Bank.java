package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank")
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id")
    private String bankId;

    @Column(name = "name")
    private String name;

    public Long getId() { 
        return id; 
    }

    public String getBankId() {
        return bankId; 
    }

    public void setBankId(String bankId) { 
        this.bankId = bankId; 
    }

    public String getName() { 
        return name; 
    }
    public void setName(String name) { 
        this.name = name; 
    }
}