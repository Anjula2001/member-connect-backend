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

    // Sentinel NIC belonging to the new dummy batch. If a member with this NIC
    // already exists, this batch has been seeded and we skip. Existing members
    // and records are always left untouched.
    private static final String SEED_SENTINEL_NIC = "902512345V";

    // Sentinel NIC for the dormant-candidate batch (Active members with an old
    // last-activity date so the identification process can flag them).
    private static final String DORMANT_SENTINEL_NIC = "800112301V";

    private final MemberRepository memberRepository;

    public MemberDataSeeder(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(String... args) {
        seedActiveBatch();
        seedDormantCandidateBatch();
    }

    private void seedActiveBatch() {
        if (memberRepository.findByNic(SEED_SENTINEL_NIC).isPresent()) {
            return;
        }

        List<Member> members = buildDummyMembers();

        // Vary last-activity dates so the dormant identification process has
        // realistic data: the first six are long-inactive (dormant candidates)
        // and the rest were active recently.
        LocalDate today = LocalDate.now();
        for (int i = 0; i < members.size(); i++) {
            LocalDate lastActivity = i < 6 ? today.minusMonths(20 + i) : today.minusMonths(1);
            members.get(i).setLastActivityDate(lastActivity);
        }

        memberRepository.saveAll(members);
        System.out.println("Seeded " + members.size()
                + " new ACTIVE members (MEM-DUMMY-011 to MEM-DUMMY-020). Existing members left untouched.");
    }

    /**
     * Seeds a batch of ACTIVE members whose last-activity date is well beyond the
     * default dormant period. They stay Active until the Dormant Identification
     * Process is run, at which point they will be flagged 'Selected for Dormant'.
     */
    private void seedDormantCandidateBatch() {
        if (memberRepository.findByNic(DORMANT_SENTINEL_NIC).isPresent()) {
            return;
        }

        List<Member> members = buildDormantCandidateMembers();
        LocalDate longAgo = LocalDate.now().minusMonths(24);
        for (Member member : members) {
            member.setLastActivityDate(longAgo);
        }

        memberRepository.saveAll(members);
        System.out.println("Seeded " + members.size()
                + " ACTIVE dormant-candidate members (MEM-DUMMY-021 to MEM-DUMMY-026). "
                + "Run the identification process to flag them as dormant.");
    }

    private List<Member> buildDormantCandidateMembers() {
        List<Member> members = new ArrayList<>();

        members.add(buildMember(
                "MEM-DUMMY-021", "800112301V", "Mr.", "Wasantha Kumara", "W. Kumara",
                LocalDate.of(1980, 1, 12), Gender.MALE, Language.SINHALA,
                "No. 7, Old Road, Maharagama", "0112856742", "0712300021",
                "wasantha.kumara@email.lk", "PSL-30001", "Colombo District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Colombo", "Colombo Zone 1",
                "Maharagama Maha Vidyalaya", "Station Road, Maharagama", "0112856701",
                "Kanthi Kumara", "Spouse", "No. 7, Old Road, Maharagama"
        ));

        members.add(buildMember(
                "MEM-DUMMY-022", "812233402V", "Mrs.", "Renuka Fernando", "R. Fernando",
                LocalDate.of(1981, 6, 23), Gender.FEMALE, Language.SINHALA,
                "No. 19, Church Street, Negombo", "0312244557", "0722300022",
                "renuka.fernando@email.lk", "PSL-30002", "Gampaha District Education Office",
                "school", "Principal", NatureOfOccupation.PERMANENT, "Gampaha", "Negombo Zone 1",
                "Maris Stella College", "Colombo Road, Negombo", "0312244501",
                "Lalith Fernando", "Spouse", "No. 19, Church Street, Negombo"
        ));

        members.add(buildMember(
                "MEM-DUMMY-023", "823344503V", "Mr.", "Sunil Rathnayake", "S. Rathnayake",
                LocalDate.of(1982, 3, 4), Gender.MALE, Language.ENGLISH,
                "No. 88, Main Street, Kandy", "0812223345", "0732300023",
                "sunil.rathnayake@email.lk", "PSL-30003", "Kandy District Education Office",
                "school", "Vice Principal", NatureOfOccupation.PERMANENT, "Kandy", "Kandy Zone 2",
                "Dharmaraja College", "Peradeniya Road, Kandy", "0812223301",
                "Manel Rathnayake", "Spouse", "No. 88, Main Street, Kandy"
        ));

        members.add(buildMember(
                "MEM-DUMMY-024", "834455604V", "Ms.", "Deepika Alwis", "D. Alwis",
                LocalDate.of(1983, 9, 15), Gender.FEMALE, Language.SINHALA,
                "No. 3, Sea Street, Galle", "0912244557", "0742300024",
                "deepika.alwis@email.lk", "PSL-30004", "Galle District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Galle", "Galle Zone 1",
                "Richmond College", "Closenberg Road, Galle", "0912244501",
                "Ranjith Alwis", "Father", "No. 3, Sea Street, Galle"
        ));

        members.add(buildMember(
                "MEM-DUMMY-025", "845566705V", "Mr.", "Bandula Jayasuriya", "B. Jayasuriya",
                LocalDate.of(1984, 11, 8), Gender.MALE, Language.SINHALA,
                "No. 26, Temple Road, Kurunegala", "0372227789", "0752300025",
                "bandula.jayasuriya@email.lk", "PSL-30005", "Kurunegala District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Kurunegala", "Kurunegala Zone 2",
                "Maliyadeva College", "Dambulla Road, Kurunegala", "0372227701",
                "Chandra Jayasuriya", "Spouse", "No. 26, Temple Road, Kurunegala"
        ));

        members.add(buildMember(
                "MEM-DUMMY-026", "856677806V", "Mrs.", "Nirmala Peiris", "N. Peiris",
                LocalDate.of(1985, 5, 30), Gender.FEMALE, Language.SINHALA,
                "No. 12, Hill Street, Matara", "0412230012", "0762300026",
                "nirmala.peiris@email.lk", "PSL-30006", "Matara District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Matara", "Matara Zone 1",
                "Rahula College", "Uyanwatta, Matara", "0412230011",
                "Gamini Peiris", "Spouse", "No. 12, Hill Street, Matara"
        ));

        return members;
    }

    private List<Member> buildDummyMembers() {
        List<Member> members = new ArrayList<>();

        members.add(buildMember(
                "MEM-DUMMY-011", "902512345V", "Mr.", "Chathura Weerasinghe", "C. Weerasinghe",
                LocalDate.of(1990, 9, 8), Gender.MALE, Language.SINHALA,
                "No. 24, Flower Road, Panadura", "0382233445", "0712233445",
                "chathura.weerasinghe@email.lk", "PSL-20001", "Kalutara District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Kalutara", "Kalutara Zone 1",
                "Sri Sumangala College", "Galle Road, Panadura", "0382233400",
                "Dinusha Weerasinghe", "Spouse", "No. 24, Flower Road, Panadura"
        ));

        members.add(buildMember(
                "MEM-DUMMY-012", "917623456V", "Mrs.", "Amaya Senanayake", "A. Senanayake",
                LocalDate.of(1991, 3, 17), Gender.FEMALE, Language.SINHALA,
                "No. 61, Cross Street, Negombo", "0312244556", "0722244556",
                "amaya.senanayake@email.lk", "PSL-20002", "Gampaha District Education Office",
                "school", "Principal", NatureOfOccupation.PERMANENT, "Gampaha", "Negombo Zone 1",
                "Maris Stella College", "Colombo Road, Negombo", "0312244500",
                "Ruwan Senanayake", "Spouse", "No. 61, Cross Street, Negombo"
        ));

        members.add(buildMember(
                "MEM-DUMMY-013", "883034567V", "Mr.", "Isuru Madushanka", "I. Madushanka",
                LocalDate.of(1988, 10, 30), Gender.MALE, Language.ENGLISH,
                "No. 5, Hospital Road, Batticaloa", "0652255667", "0732255667",
                "isuru.madushanka@email.lk", "PSL-20003", "Batticaloa District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Batticaloa", "Batticaloa Zone 1",
                "St. Michael's College", "Trinco Road, Batticaloa", "0652255600",
                "Shanika Madushanka", "Spouse", "No. 5, Hospital Road, Batticaloa"
        ));

        members.add(buildMember(
                "MEM-DUMMY-014", "946145678V", "Ms.", "Thilini Rajapaksha", "T. Rajapaksha",
                LocalDate.of(1994, 6, 14), Gender.FEMALE, Language.SINHALA,
                "No. 40, Market Lane, Hambantota", "0472266778", "0742266778",
                "thilini.rajapaksha@email.lk", "PSL-20004", "Hambantota District Education Office",
                "school", "Teacher", NatureOfOccupation.PROBATION, "Hambantota", "Hambantota Zone 1",
                "Debarawewa Central College", "Tissa Road, Hambantota", "0472266700",
                "Nimal Rajapaksha", "Father", "No. 40, Market Lane, Hambantota"
        ));

        members.add(buildMember(
                "MEM-DUMMY-015", "897256789V", "Mr.", "Dilan Herath", "D. Herath",
                LocalDate.of(1989, 8, 12), Gender.MALE, Language.SINHALA,
                "No. 14, Kandy Road, Matale", "0662277889", "0752277889",
                "dilan.herath@email.lk", "PSL-20005", "Matale District Education Office",
                "school", "Vice Principal", NatureOfOccupation.PERMANENT, "Matale", "Matale Zone 1",
                "St. Thomas College", "Trinco Street, Matale", "0662277800",
                "Nadeeka Herath", "Spouse", "No. 14, Kandy Road, Matale"
        ));

        members.add(buildMember(
                "MEM-DUMMY-016", "928367890V", "Mrs.", "Kumudu Ekanayake", "K. Ekanayake",
                LocalDate.of(1992, 12, 3), Gender.FEMALE, Language.SINHALA,
                "No. 78, Lake Road, Polonnaruwa", "0272288990", "0762288990",
                "kumudu.ekanayake@email.lk", "PSL-20006", "Polonnaruwa District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Polonnaruwa", "Polonnaruwa Zone 1",
                "Royal Central College", "Batticaloa Road, Polonnaruwa", "0272288900",
                "Sampath Ekanayake", "Spouse", "No. 78, Lake Road, Polonnaruwa"
        ));

        members.add(buildMember(
                "MEM-DUMMY-017", "958478901V", "Mr.", "Roshan Perera", "R. Perera",
                LocalDate.of(1995, 4, 25), Gender.MALE, Language.ENGLISH,
                "No. 2, Fort Road, Trincomalee", "0262299001", "0772299001",
                "roshan.perera@email.lk", "PSL-20007", "Trincomalee District Education Office",
                "school", "Teacher", NatureOfOccupation.TEMPORARY, "Trincomalee", "Trincomalee Zone 1",
                "T/ St. Joseph's College", "Dockyard Road, Trincomalee", "0262299000",
                "Malsha Perera", "Mother", "No. 2, Fort Road, Trincomalee"
        ));

        members.add(buildMember(
                "MEM-DUMMY-018", "936589012V", "Mrs.", "Sachini Liyanage", "S. Liyanage",
                LocalDate.of(1993, 5, 19), Gender.FEMALE, Language.SINHALA,
                "No. 33, Temple Lane, Kegalle", "0352200112", "0782200112",
                "sachini.liyanage@email.lk", "PSL-20008", "Kegalle District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Kegalle", "Kegalle Zone 1",
                "Kegalu Vidyalaya", "Kandy Road, Kegalle", "0352200100",
                "Buddhika Liyanage", "Spouse", "No. 33, Temple Lane, Kegalle"
        ));

        members.add(buildMember(
                "MEM-DUMMY-019", "907690123V", "Mr.", "Lahiru Dissanayake", "L. Dissanayake",
                LocalDate.of(1990, 7, 27), Gender.MALE, Language.SINHALA,
                "No. 19, Station Road, Vavuniya", "0242211223", "0792211223",
                "lahiru.dissanayake@email.lk", "PSL-20009", "Vavuniya District Education Office",
                "school", "Teacher", NatureOfOccupation.CASUAL, "Vavuniya", "Vavuniya Zone 1",
                "Vavuniya Tamil M.V.", "Kandy Road, Vavuniya", "0242211200",
                "Priyanka Dissanayake", "Mother", "No. 19, Station Road, Vavuniya"
        ));

        members.add(buildMember(
                "MEM-DUMMY-020", "961701234V", "Ms.", "Nadeesha Gamage", "N. Gamage",
                LocalDate.of(1996, 1, 7), Gender.FEMALE, Language.SINHALA,
                "No. 50, Beach Lane, Puttalam", "0322222334", "0702222334",
                "nadeesha.gamage@email.lk", "PSL-20010", "Puttalam District Education Office",
                "school", "Teacher", NatureOfOccupation.PERMANENT, "Puttalam", "Puttalam Zone 1",
                "Puttalam Central College", "Kurunegala Road, Puttalam", "0322222300",
                "Ajith Gamage", "Father", "No. 50, Beach Lane, Puttalam"
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
