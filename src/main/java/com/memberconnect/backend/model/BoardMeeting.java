package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Board_Meeting")
public class BoardMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String boardMeetingId;

    @Column(name = "ScheduledDate")
    private LocalDate scheduledDate;

    @Column(name = "ActualDate")
    private LocalDate actualDate;
}