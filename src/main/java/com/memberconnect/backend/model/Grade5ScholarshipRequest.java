package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "Grade5ScholarshipRequest")
public class Grade5ScholarshipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "StudentName")
    private String studentName;

    @Column(name = "BirthCertificateNumber")
    private String birthCertificateNumber;

    @Column(name = "StudentSchool")
    private String studentSchool;

    @Column(name = "SchoolDistrict")
    private String schoolDistrict;

    @Column(name = "ExamYear")
    private Integer examYear;

    @Column(name = "ExaminationNumber")
    private String examinationNumber;

    @Column(name = "MarksObtained")
    private Integer marksObtained;

    // Getters & Setters
    public Long getId() { 
        return id; 
    }

    public String getExaminationNumber() { 
        return examinationNumber; 
    }
    public void setExaminationNumber(String examinationNumber) {
        this.examinationNumber = examinationNumber;
    }

    public String getStudentName() { 
        return studentName;
    }
    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getSchool() {
         return studentSchool; 
    }
    public void setSchool(String school) { 
        this.studentSchool = school; 
    }

    public String getDistrict() {
         return schoolDistrict;
     }
    public void setDistrict(String district) { 
        this.schoolDistrict = district;
    }

    public int getExamYear() {
         return examYear; 
    }
    public void setExamYear(Integer examYear) {
         this.examYear = examYear; 
    }

    public String getBirthCertificateNumber() {
         return birthCertificateNumber;
    }
    public void setBirthCertificateNumber(String birthCertificateNumber) {
        this.birthCertificateNumber = birthCertificateNumber;
   }

    public int getMarksObtained() { 
        return marksObtained; 
    }
    public void setMarksObtained(Integer marksObtained) {
        this.marksObtained = marksObtained;
    }
}

