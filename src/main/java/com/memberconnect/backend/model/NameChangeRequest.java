package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Table(name = "NameChangeRequestsTable")
@Entity
@Data

public class NameChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NameChangeRequestID")
    private Integer nameChangeRequestID;
    @Column(name = "title")
    private String newTitle;
    @Column(name = "fullname")
    private String newFullName;
    @Column(name = "nameAszPayroll")
    private String newNameAsInPayroll;
    @Column(name = "Name with initials")
    private String newNameWithInitials;


}
