package com.memberconnect.backend.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Identification;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;

@Component
@Order(2)
public class MemberDataSeeder implements CommandLineRunner {

    private final MemberRepository memberRepository;

    public MemberDataSeeder(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(String... args) {
        if (memberRepository.findByMemberId("MEM-DUMMY-001").isPresent()) {
            return;
        }

        List<Member> members = buildDummyMembers();
        memberRepository.saveAll(members);
        System.out.println("Seeded " + members.size() + " dummy ACTIVE members (MEM-DUMMY-001 to MEM-DUMMY-010).");
    }

    private List<Member> buildDummyMembers() {
        List<Member> members = new ArrayList<>();

        members.add(buildMember(
                "MEM-DUMMY-001", "951101001V", "Mr.", "Ravindu Bandara", "R. Bandara",
                LocalDate.of(1995, 11, 1), Gender.MALE, Language.SINHALA,
                "No. 12, Temple Road, Maharagama", "0112856741", "0771234501",
                "ravindu.bandara@email.lk", "PSL-10001", "Colombo District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Colombo", "Colombo Zone 1",
                "Maharagama Maha Vidyalaya", "Station Road, Maharagama", "0112856700",
                "Nimali Bandara", "Spouse", "No. 12, Temple Road, Maharagama"
        ));

        members.add(buildMember(
                "MEM-DUMMY-002", "952202002V", "Mrs.", "Sanduni Perera", "S. Perera",
                LocalDate.of(1992, 2, 2), Gender.FEMALE, Language.SINHALA,
                "No. 45, Lake View, Kandy", "0812223344", "0772234502",
                "sanduni.perera@email.lk", "PSL-10002", "Kandy District Education Office",
                "school", "Principal", NatureOfOccupation.PERMANENT, "Kandy", "Kandy Zone 2",
                "Dharmaraja College", "Peradeniya Road, Kandy", "0812223300",
                "Kasun Perera", "Spouse", "No. 45, Lake View, Kandy"
        ));

        members.add(buildMember(
                "MEM-DUMMY-003", "953303003V", "Mr.", "Tharindu Fernando", "T. Fernando",
                LocalDate.of(1993, 3, 3), Gender.MALE, Language.ENGLISH,
                "No. 8, Beach Road, Galle", "0912244556", "0773234503",
                "tharindu.fernando@email.lk", "PSL-10003", "Galle District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Galle", "Galle Zone 1",
                "Richmond College", "Closenberg Road, Galle", "0912244500",
                "Anjali Fernando", "Spouse", "No. 8, Beach Road, Galle"
        ));

        members.add(buildMember(
                "MEM-DUMMY-004", "954404004V", "Ms.", "Dilani Jayawardena", "D. Jayawardena",
                LocalDate.of(1994, 4, 4), Gender.FEMALE, Language.SINHALA,
                "No. 22, Hill Street, Nuwara Eliya", "0522225566", "0774234504",
                "dilani.jayawardena@email.lk", "PSL-10004", "Nuwara Eliya District Education Office",
                "school", "Teacher", NatureOfOccupation.PROBATION, "Nuwara Eliya", "Nuwara Eliya Zone 1",
                "Gamini Central College", "Badulla Road, Nuwara Eliya", "0522225500",
                "Sunil Jayawardena", "Father", "No. 22, Hill Street, Nuwara Eliya"
        ));

        members.add(buildMember(
                "MEM-DUMMY-005", "955505005V", "Mr.", "Nuwan Silva", "N. Silva",
                LocalDate.of(1995, 5, 5), Gender.MALE, Language.SINHALA,
                "No. 3, Paddy Field Lane, Anuradhapura", "0252226677", "0775234505",
                "nuwan.silva@email.lk", "PSL-10005", "Anuradhapura District Education Office",
                "school", "Vice Principal", NatureOfOccupation.PERMANENT, "Anuradhapura", "Anuradhapura Zone 1",
                "Maliyadeva College", "Kurunegala Road, Anuradhapura", "0252226600",
                "Chamari Silva", "Spouse", "No. 3, Paddy Field Lane, Anuradhapura"
        ));

        members.add(buildMember(
                "MEM-DUMMY-006", "956606006V", "Mrs.", "Ishara Wickramasinghe", "I. Wickramasinghe",
                LocalDate.of(1996, 6, 6), Gender.FEMALE, Language.SINHALA,
                "No. 17, Green Park, Kurunegala", "0372227788", "0776234506",
                "ishara.wickramasinghe@email.lk", "PSL-10006", "Kurunegala District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Kurunegala", "Kurunegala Zone 2",
                "Maliyadeva Balika Vidyalaya", "Dambulla Road, Kurunegala", "0372227700",
                "Rohan Wickramasinghe", "Spouse", "No. 17, Green Park, Kurunegala"
        ));

        members.add(buildMember(
                "MEM-DUMMY-007", "957707007V", "Mr.", "Kavindu Rathnayake", "K. Rathnayake",
                LocalDate.of(1997, 7, 7), Gender.MALE, Language.ENGLISH,
                "No. 9, Riverside, Ratnapura", "0452228899", "0777234507",
                "kavindu.rathnayake@email.lk", "PSL-10007", "Ratnapura District Education Office",
                "school", "Teacher", NatureOfOccupation.TEMPORARY, "Ratnapura", "Ratnapura Zone 1",
                "Sri Sumangala College", "Colombo Road, Ratnapura", "0452228800",
                "Malini Rathnayake", "Mother", "No. 9, Riverside, Ratnapura"
        ));

        members.add(buildMember(
                "MEM-DUMMY-008", "958808008V", "Mrs.", "Nethmi Gunasekara", "N. Gunasekara",
                LocalDate.of(1998, 8, 8), Gender.FEMALE, Language.TAMIL,
                "No. 55, Lotus Avenue, Jaffna", "0212229900", "0778234508",
                "nethmi.gunasekara@email.lk", "PSL-10008", "Jaffna District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Jaffna", "Jaffna Zone 1",
                "Jaffna Hindu College", "Stanley Road, Jaffna", "0212229901",
                "Arun Gunasekara", "Spouse", "No. 55, Lotus Avenue, Jaffna"
        ));

        members.add(buildMember(
                "MEM-DUMMY-009", "959909009V", "Mr.", "Pasindu Amarasinghe", "P. Amarasinghe",
                LocalDate.of(1999, 9, 9), Gender.MALE, Language.SINHALA,
                "No. 31, Coconut Grove, Matara", "0412230011", "0779234509",
                "pasindu.amarasinghe@email.lk", "PSL-10009", "Matara District Education Office",
                "school", "Teacher", NatureOfOccupation.CASUAL, "Matara", "Matara Zone 1",
                "Rahula College", "Uyanwatta, Matara", "0412230010",
                "Kumari Amarasinghe", "Mother", "No. 31, Coconut Grove, Matara"
        ));

        members.add(buildMember(
                "MEM-DUMMY-010", "951010010V", "Ms.", "Hansani Karunaratne", "H. Karunaratne",
                LocalDate.of(1991, 10, 10), Gender.FEMALE, Language.SINHALA,
                "No. 6, School Lane, Badulla", "0552231122", "0770234510",
                "hansani.karunaratne@email.lk", "PSL-10010", "Badulla District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Badulla", "Badulla Zone 1",
                "Vishaka Vidyalaya", "Passara Road, Badulla", "0552231100",
                "Lal Karunaratne", "Father", "No. 6, School Lane, Badulla"
        ));

        return members;
    }

    private Member buildMember(
            String memberId,
            String nic,
            String title,
            String fullName,
            String nameWithInitials,
            LocalDate dateOfBirth,
            Gender gender,
            Language preferredLanguage,
            String permanentPrivateAddress,
            String privateTelephone,
            String mobileNumber,
            String emailAddress,
            String computerNoInPayslip,
            String salaryPayingOffice,
            String workingLocationType,
            String designation,
            NatureOfOccupation natureOfOccupation,
            String educationalDistrict,
            String educationalZone,
            String workingLocation,
            String workingLocationAddress,
            String officeTelephone,
            String nomineeFullName,
            String nomineeRelationship,
            String nomineeAddress
    ) {
        Member member = new Member();
        member.setMemberId(memberId);
        member.setMemberType("Ordinary Member");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipStartDate(LocalDate.of(2020, 1, 15));
        member.setNic(nic);
        member.setTitle(title);
        member.setFullName(fullName);
        member.setNameAsInPayroll(fullName.toUpperCase());
        member.setNameWithInitials(nameWithInitials);
        member.setDateOfBirth(dateOfBirth);
        member.setGender(gender);
        member.setPreferredLanguage(preferredLanguage);
        member.setPermanentPrivateAddress(permanentPrivateAddress);
        member.setPrivateTelephone(privateTelephone);
        member.setMobileNumber(mobileNumber);
        member.setEmailAddress(emailAddress);
        member.setComputerNoInPayslip(computerNoInPayslip);
        member.setSalaryPayingOffice(salaryPayingOffice);
        member.setProfilePictureUrl("https://placehold.co/200x200/png?text=" + memberId);
        member.setSignatureUrl("https://placehold.co/300x100/png?text=Signature");
        member.setWorkingLocationType(workingLocationType);
        member.setDesignation(designation);
        member.setNatureOfOccupation(natureOfOccupation);
        member.setEducationalDistrict(educationalDistrict);
        member.setEducationalZone(educationalZone);
        member.setWorkingLocation(workingLocation);
        member.setWorkingLocationAddress(workingLocationAddress);
        member.setOfficeTelephone(officeTelephone);
        member.setNomineeFullName(nomineeFullName);
        member.setNomineeRelationship(nomineeRelationship);
        member.setNomineeAddress(nomineeAddress);
        member.setIdentification(Identification.NIC);
        member.setIdentificationNumber(nic);
        member.setIdentificationDetails("National Identity Card issued by Department for Registration of Persons.");
        return member;
    }
}
