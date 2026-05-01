package com.memberconnect.backend.dto;

public class Grade5RequestListDTO {
    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String nameWithInitials;
    private String nic;
    private String requestedDate;
    private String studentName;
    private String examinationNumber;
    private Integer examYear;
    private String status;
    private String location;
    private boolean hasDeviation;


    public Grade5RequestListDTO(Long id, String requestNo, String memberId,
            String memberFullName, String nameWithInitials, String nic,
            String requestedDate, String studentName, String examinationNumber,
            Integer examYear, String status, String location, boolean hasDeviation) {
        this.id = id;
        this.requestNo = requestNo;
        this.memberId = memberId;
        this.memberFullName = memberFullName;
        this.nameWithInitials = nameWithInitials;
        this.nic = nic;
        this.requestedDate = requestedDate;
        this.studentName = studentName;
        this.examinationNumber = examinationNumber;
        this.examYear = examYear;
        this.status = status;
        this.location = location;
        this.hasDeviation = hasDeviation;
    }

    public Long getId() { return id; }
    public String getRequestNo() { return requestNo; }
    public String getMemberId() { return memberId; }
    public String getMemberFullName() { return memberFullName; }
    public String getNameWithInitials() { return nameWithInitials; }
    public String getNic() { return nic; }
    public String getRequestedDate() { return requestedDate; }
    public String getStudentName() { return studentName; }
    public String getExaminationNumber() { return examinationNumber; }
    public Integer getExamYear() { return examYear; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public boolean isHasDeviation() { return hasDeviation; }

}