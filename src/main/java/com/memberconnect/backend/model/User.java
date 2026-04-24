package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "Users")

public class User {
    @Id   //define primary key
    @Column(name = "Id")
    private int id;

    @Column(name = "name")
    private String name;
}
