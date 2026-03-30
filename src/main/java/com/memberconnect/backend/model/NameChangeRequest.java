package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Table(name = "NameChangeRequestsTable")
public class NameChangeRequest {
    @Id
    @Column(name = "NameChangeRequestID")
    private String nameChangeRequestID;
    @Column(name = "title")
    private String newTitle;
    @Column(name = "fullname")
    private String newFullName;
    @Column(name = "nameAszPayroll")
    private String newNameAsInPayroll;
    @Column(name = "Name with initials")
    private String newNameWithInitials;


}
