package com.memberconnect.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class DispatchRequestDTO {
    /** Member table ids selected on the dispatch screen. */
    private List<Long> memberIds;
}
