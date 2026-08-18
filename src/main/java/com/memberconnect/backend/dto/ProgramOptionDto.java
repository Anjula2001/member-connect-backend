package com.memberconnect.backend.dto;

public class ProgramOptionDto {

    private Long programId;
    private String programName;
    private Integer duration;

    public ProgramOptionDto() {}

    public ProgramOptionDto(Long programId, String programName, Integer duration) {
        this.programId = programId;
        this.programName = programName;
        this.duration = duration;
    }

    public Long getProgramId() {
        return programId;
    }

    public void setProgramId(Long programId) {
        this.programId = programId;
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}