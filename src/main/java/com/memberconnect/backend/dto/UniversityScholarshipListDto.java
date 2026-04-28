package com.memberconnect.backend.dto;

public class UniversityScholarshipListDto {

    private Long id;
    private String requestId;
    private String studentName;
    private String memberName;
    private String universityName;
    private String status;

    public UniversityScholarshipListDto(
            Long id,
            String requestId,
            String studentName,
            String memberName,
            String universityName,
            String status
    ) {
        this.id = id;
        this.requestId = requestId;
        this.studentName = studentName;
        this.memberName = memberName;
        this.universityName = universityName;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getStudentName() { return studentName; }
    public String getMemberName() { return memberName; }
    public String getUniversityName() { return universityName; }
    public String getStatus() { return status; }
}
