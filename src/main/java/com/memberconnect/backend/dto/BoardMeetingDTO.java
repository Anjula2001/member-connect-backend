package com.memberconnect.backend.dto;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BoardMeetingDTO {

  private Long id;
  private String boardMeetingId;
  private LocalDate scheduledDate;
  private LocalDate actualDate;
  
}
