package com.memberconnect.backend.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Identification;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberRemittance;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.MemberRemittanceRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;

/**
 * Demo membership data for the shared development database.
 *
 * Exists so contributors working on Loans, Scholarships, Termination, Dormancy and
 * Death Donation have members to work against, instead of each person hand-creating
 * records and ending up with a different database from everyone else.
 *
 * Three things about the design are deliberate:
 *
 * 1. Every member is created WITH a Member_Application behind it. Member has no
 *    remittance columns of its own — the contribution amounts live on the application
 *    and are copied into member_remittance at approval. Seeding members alone would
 *    produce a Remittance & Savings tab that is empty and scholarship eligibility
 *    checks (6 and 12 remitted months) that can never pass.
 *
 * 2. IDs use a DEMO prefix (MEM-DEMO-001 / APP-DEMO-001) rather than the real
 *    MEM-{year}-{seq} format. MemberService.generateMemberId() searches for the
 *    prefix "MEM-" + current year, so demo rows are invisible to it and genuine
 *    members created through the UI still begin at MEM-2026-001 with no collision.
 *    The prefix is also what makes the purge script in
 *    resources/db/undo-demo-seed.sql able to target exactly these rows.
 *
 * 3. @Profile("dev") plus the sentinel check below. The profile guard keeps this away
 *    from any production deployment; the sentinel makes re-running harmless.
 */
@Component
@Profile("dev")
@Order(10)
public class MemberDemoSeeder implements ApplicationRunner {

    /** Rows created here are identifiable — and removable — by these prefixes. */
    private static final String MEMBER_PREFIX = "MEM-DEMO-";
    private static final String APPLICATION_PREFIX = "APP-DEMO-";

    private final MemberRepository memberRepository;
    private final MemberApplicationRepository applicationRepository;
    private final MemberRemittanceRepository remittanceRepository;

    public MemberDemoSeeder(MemberRepository memberRepository,
                            MemberApplicationRepository applicationRepository,
                            MemberRemittanceRepository remittanceRepository) {
        this.memberRepository = memberRepository;
        this.applicationRepository = applicationRepository;
        this.remittanceRepository = remittanceRepository;
    }

    /** One row of the demo dataset. */
    private record Seed(
            String fullName, String initials, String payrollName, String title,
            Gender gender, Language language, String nic, LocalDate dob,
            String district, String zone, String workingLocation, String locationType,
            String designation, NatureOfOccupation occupation,
            String address, String mobile, String email, String payrollNo,
            MemberStatus status,
            BigDecimal share, BigDecimal special, BigDecimal fixed, BigDecimal scholarship,
            String nomineeName, String nomineeRelationship,
            /** 0 = nothing printed, 1 = partly printed, 2 = fully printed and dispatched. */
            int printStage) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (applicationRepository.count() > 0
                && memberRepository.findAll().stream()
                        .anyMatch(m -> m.getMemberId() != null && m.getMemberId().startsWith(MEMBER_PREFIX))) {
            System.out.println("[demo-seed] Demo members already present — skipping.");
            return;
        }

        List<Seed> seeds = buildSeeds();
        int index = 0;

        for (Seed seed : seeds) {
            index++;
            String suffix = String.format("%03d", index);

            Member_Application application = toApplication(seed, APPLICATION_PREFIX + suffix);
            application = applicationRepository.save(application);

            Member member = toMember(seed, MEMBER_PREFIX + suffix, application);
            member = memberRepository.save(member);

            saveRemittances(member, seed);
        }

        System.out.println("=================================================");
        System.out.println("  Demo membership data seeded: " + seeds.size() + " members");
        System.out.println("  IDs: " + MEMBER_PREFIX + "001 .. " + MEMBER_PREFIX
                + String.format("%03d", seeds.size()));
        System.out.println("  Remove with: backend/src/main/resources/db/undo-demo-seed.sql");
        System.out.println("=================================================");
    }

    private Member_Application toApplication(Seed seed, String applicationId) {
        Member_Application a = new Member_Application();
        a.setApplicationID(applicationId);
        // Approved: these applications already became members. Keeping them out of
        // ApplicationStatus.NEW also keeps them off the New Member Registration List,
        // which by spec shows only registrations not yet approved as Members.
        a.setStatus(ApplicationStatus.APPROVED);
        a.setApplicationDate(seed.status() == MemberStatus.INACTIVE ? "2026-06-15" : "2026-02-10");
        a.setSubmissionLocation(seed.district());
        a.setTitle(seed.title());
        a.setFullName(seed.fullName());
        a.setNameAsInPayroll(seed.payrollName());
        a.setNameWithInitials(seed.initials());
        a.setNicNumber(seed.nic());
        a.setDateOfBirth(seed.dob());
        a.setGender(seed.gender());
        a.setPreferredLanguage(seed.language());
        a.setPermanentPrivateAddress(seed.address());
        a.setWorkingLocationType(seed.locationType());
        a.setDesignation(seed.designation());
        a.setNatureOfOccupation(seed.occupation());
        a.setEducationalDistrict(seed.district());
        a.setEducationalZone(seed.zone());
        a.setWorkingLocation(seed.workingLocation());
        a.setWorkingLocationAddress(seed.workingLocation() + ", " + seed.district());
        a.setComputerNoInPayslip(seed.payrollNo());
        a.setSalaryPayingOffice("Zonal Education Office - " + seed.zone());
        a.setMobileNumber(seed.mobile());
        a.setEmailAddress(seed.email());
        a.setShareAccountAmount(seed.share());
        a.setSpecialDepositAmount(seed.special());
        a.setFixedDepositAmount(seed.fixed());
        a.setScholarshipDeathDonationPensionAmount(seed.scholarship());
        a.setNomineeFullName(seed.nomineeName());
        a.setNomineeRelationship(seed.nomineeRelationship());
        a.setNomineeAddress(seed.address());
        a.setIdentification(Identification.NIC);
        a.setIdentificationNumber(seed.nic());
        a.setRejoinFlag(false);
        return a;
    }

    private Member toMember(Seed seed, String memberId, Member_Application application) {
        Member m = new Member();
        m.setApplication(application);
        m.setMemberId(memberId);
        m.setMemberType("Member");
        m.setStatus(seed.status());
        m.setSubmissionLocation(seed.district());
        // Per MR14 the start date is the date the Board approved the application.
        m.setMembershipStartDate(seed.status() == MemberStatus.INACTIVE
                ? LocalDate.of(2026, 7, 20)
                : LocalDate.of(2026, 3, 15));
        m.setNic(seed.nic());
        m.setTitle(seed.title());
        m.setFullName(seed.fullName());
        m.setNameAsInPayroll(seed.payrollName());
        m.setNameWithInitials(seed.initials());
        m.setDateOfBirth(seed.dob());
        m.setGender(seed.gender());
        m.setPreferredLanguage(seed.language());
        m.setPermanentPrivateAddress(seed.address());
        m.setMobileNumber(seed.mobile());
        m.setEmailAddress(seed.email());
        m.setComputerNoInPayslip(seed.payrollNo());
        m.setSalaryPayingOffice("Zonal Education Office - " + seed.zone());
        m.setWorkingLocationType(seed.locationType());
        m.setDesignation(seed.designation());
        m.setNatureOfOccupation(seed.occupation());
        m.setEducationalDistrict(seed.district());
        m.setEducationalZone(seed.zone());
        m.setWorkingLocation(seed.workingLocation());
        m.setWorkingLocationAddress(seed.workingLocation() + ", " + seed.district());
        m.setNomineeFullName(seed.nomineeName());
        m.setNomineeRelationship(seed.nomineeRelationship());
        m.setNomineeAddress(seed.address());
        m.setIdentification(Identification.NIC);
        m.setIdentificationNumber(seed.nic());
        m.setLastActivityDate(LocalDate.of(2026, 8, 1));

        // Print and dispatch flags are mixed on purpose. All-null leaves the Dispatch
        // screen empty; all-populated leaves the Print screens empty, since those
        // default to "Members without <document>". A spread gives both something.
        LocalDateTime printedAt = LocalDateTime.of(2026, 4, 2, 10, 30);
        if (seed.printStage() >= 1) {
            m.setMembershipCardPrintedAt(printedAt);
            m.setSignatureCardPrintedAt(printedAt);
        }
        if (seed.printStage() >= 2) {
            m.setPassbookPrintedAt(printedAt);
            m.setDocumentsDispatchedAt(LocalDateTime.of(2026, 4, 5, 14, 0));
        }
        return m;
    }

    private void saveRemittances(Member member, Seed seed) {
        record Row(RemittanceAccountCode code, BigDecimal amount) {
        }
        List<Row> rows = List.of(
                new Row(RemittanceAccountCode.SHARE, seed.share()),
                new Row(RemittanceAccountCode.SPECIAL_DEPOSIT, seed.special()),
                new Row(RemittanceAccountCode.FIXED_DEPOSIT, seed.fixed()),
                new Row(RemittanceAccountCode.SCHOLARSHIP_DEATH_DONATION_PENSION, seed.scholarship()));

        for (Row row : rows) {
            if (row.amount() == null) {
                continue;
            }
            MemberRemittance remittance = new MemberRemittance();
            remittance.setMember(member);
            remittance.setAccountCode(row.code());
            remittance.setAmount(row.amount());
            remittanceRepository.save(remittance);
        }
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    /**
     * 30 members over 10 districts: 20 ACTIVE, 5 INACTIVE (awaiting Finance
     * activation), 5 TERMINATED (so the Rejoin branch in new registration has real
     * NICs to detect).
     */
    private List<Seed> buildSeeds() {
        List<Seed> s = new ArrayList<>();

        // ---- ACTIVE (20) ----
        s.add(new Seed("Nimal Rajapaksha Perera", "N.R. Perera", "N R Perera", "Mr", Gender.MALE, Language.SINHALA,
                "199812304567", LocalDate.of(1988, 4, 17), "Colombo", "Homagama", "Homagama Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 45, Temple Road, Homagama", "0771234567", "nimal.perera@edu.lk", "EMP10234",
                MemberStatus.ACTIVE, amount("5000"), amount("10000"), amount("25000"), amount("1500"),
                "Kamala Perera", "Spouse", 2));
        s.add(new Seed("Sanduni Bandara Ekanayake", "S.B. Ekanayake", "S B Ekanayake", "Mrs", Gender.FEMALE, Language.SINHALA,
                "927451234V", LocalDate.of(1992, 9, 3), "Kandy", "Gampola", "Gampola Balika Vidyalaya",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 12/A, Peradeniya Road, Gampola", "0763345120", "sanduni.b@edu.lk", "EMP10877",
                MemberStatus.ACTIVE, amount("5000"), amount("7500"), amount("18000"), amount("1500"),
                "Sunil Ekanayake", "Father", 2));
        s.add(new Seed("Thevan Rajendran", "T. Rajendran", "T Rajendran", "Mr", Gender.MALE, Language.TAMIL,
                "198535609876", LocalDate.of(1985, 12, 22), "Jaffna", "Nallur", "Nallur Hindu College",
                "National School", "Principal", NatureOfOccupation.PERMANENT,
                "No 8, Kandy Road, Nallur", "0771988456", "thevan.raj@edu.lk", "EMP11002",
                MemberStatus.ACTIVE, amount("10000"), amount("25000"), amount("40000"), amount("2500"),
                "Vasanthi Rajendran", "Spouse", 2));
        s.add(new Seed("Asela Gunawardena", "A. Gunawardena", "A Gunawardena", "Mr", Gender.MALE, Language.SINHALA,
                "791234567V", LocalDate.of(1979, 3, 30), "Kurunegala", "Kuliyapitiya", "Kuliyapitiya Maha Vidyalaya",
                "National School", "Deputy Principal", NatureOfOccupation.PERMANENT,
                "No 101, Station Road, Kuliyapitiya", "0777001122", "asela.g@edu.lk", "EMP11555",
                MemberStatus.ACTIVE, amount("15000"), amount("40000"), amount("55000"), amount("3000"),
                "Nayana Gunawardena", "Spouse", 2));
        s.add(new Seed("Harsha Samarasinghe", "H. Samarasinghe", "H Samarasinghe", "Mr", Gender.MALE, Language.SINHALA,
                "901567890V", LocalDate.of(1990, 11, 11), "Matara", "Akuressa", "Akuressa Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 7, Deniyaya Road, Akuressa", "0729988776", "harsha.s@edu.lk", "EMP11890",
                MemberStatus.ACTIVE, amount("5000"), amount("12000"), amount("22000"), amount("1500"),
                "Malini Samarasinghe", "Mother", 1));
        s.add(new Seed("Ruwan Dissanayake", "R. Dissanayake", "R Dissanayake", "Mr", Gender.MALE, Language.SINHALA,
                "831902345V", LocalDate.of(1983, 1, 19), "Anuradhapura", "Kekirawa", "Kekirawa Central College",
                "National School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 56, Dambulla Road, Kekirawa", "0714455667", "ruwan.d@edu.lk", "EMP12244",
                MemberStatus.ACTIVE, amount("7500"), amount("20000"), amount("30000"), amount("2000"),
                "Chamari Dissanayake", "Spouse", 1));
        s.add(new Seed("Nadeeka Priyadarshani", "N. Priyadarshani", "N Priyadarshani", "Mrs", Gender.FEMALE, Language.SINHALA,
                "875150987V", LocalDate.of(1987, 8, 2), "Ratnapura", "Balangoda", "Balangoda Maha Vidyalaya",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 91, Colombo Road, Balangoda", "0783344556", "nadeeka.p@edu.lk", "EMP12712",
                MemberStatus.ACTIVE, amount("5000"), amount("15000"), amount("26000"), amount("1500"),
                "Sarath Priyadarshana", "Spouse", 1));
        s.add(new Seed("Sachini Fernando", "S. Fernando", "S Fernando", "Miss", Gender.FEMALE, Language.ENGLISH,
                "915830456V", LocalDate.of(1991, 2, 27), "Gampaha", "Negombo", "Negombo Girls High School",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 5, Sea Street, Negombo", "0774411223", "sachini.f@edu.lk", "EMP12980",
                MemberStatus.ACTIVE, amount("5000"), amount("11000"), amount("19500"), amount("1500"),
                "Anton Fernando", "Father", 1));
        s.add(new Seed("Mohamed Irfan Careem", "M.I. Careem", "M I Careem", "Mr", Gender.MALE, Language.TAMIL,
                "813400765V", LocalDate.of(1981, 12, 5), "Trincomalee", "Kinniya", "Kinniya Muslim Central College",
                "National School", "Principal", NatureOfOccupation.PERMANENT,
                "No 3, Harbour Road, Kinniya", "0776677889", "mohamed.i@edu.lk", "EMP13355",
                MemberStatus.ACTIVE, amount("12500"), amount("30000"), amount("48000"), amount("2500"),
                "Rizana Careem", "Spouse", 1));
        s.add(new Seed("Sivakumar Thangarajah", "S. Thangarajah", "S Thangarajah", "Mr", Gender.MALE, Language.TAMIL,
                "761120987V", LocalDate.of(1976, 7, 30), "Batticaloa", "Kaluwanchikudy", "Batticaloa Central College",
                "National School", "Chief Clerk", NatureOfOccupation.PERMANENT,
                "No 14, Kandy Road, Batticaloa", "0779900112", "sivakumar.t@edu.lk", "EMP13845",
                MemberStatus.ACTIVE, amount("20000"), amount("50000"), amount("60000"), amount("3000"),
                "Meena Thangarajah", "Spouse", 0));
        s.add(new Seed("Chathura Wijesinghe", "C. Wijesinghe", "C Wijesinghe", "Mr", Gender.MALE, Language.SINHALA,
                "860445123V", LocalDate.of(1986, 5, 14), "Colombo", "Maharagama", "Maharagama Central College",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 22, High Level Road, Maharagama", "0712233445", "chathura.w@edu.lk", "EMP14001",
                MemberStatus.ACTIVE, amount("6000"), amount("14000"), amount("24000"), amount("1500"),
                "Iresha Wijesinghe", "Spouse", 0));
        s.add(new Seed("Dilrukshi Jayawardena", "D. Jayawardena", "D Jayawardena", "Mrs", Gender.FEMALE, Language.SINHALA,
                "893310456V", LocalDate.of(1989, 3, 8), "Galle", "Elpitiya", "Elpitiya Central College",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 33, Lake Road, Elpitiya", "0755512340", "dilrukshi.j@edu.lk", "EMP14210",
                MemberStatus.ACTIVE, amount("5000"), amount("9000"), amount("17000"), amount("1500"),
                "Ranjith Jayawardena", "Father", 0));
        s.add(new Seed("Pradeep Kumara Silva", "P.K. Silva", "P K Silva", "Mr", Gender.MALE, Language.SINHALA,
                "800922345V", LocalDate.of(1980, 6, 21), "Gampaha", "Ja-Ela", "Ja-Ela Maha Vidyalaya",
                "Government School", "Deputy Principal", NatureOfOccupation.PERMANENT,
                "No 67, Negombo Road, Ja-Ela", "0718899001", "pradeep.s@edu.lk", "EMP14488",
                MemberStatus.ACTIVE, amount("11000"), amount("28000"), amount("42000"), amount("2500"),
                "Sujatha Silva", "Spouse", 2));
        s.add(new Seed("Kumudu Herath", "K. Herath", "K Herath", "Mrs", Gender.FEMALE, Language.SINHALA,
                "902244567V", LocalDate.of(1990, 10, 2), "Kandy", "Katugastota", "Katugastota Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 18, Matale Road, Katugastota", "0762211334", "kumudu.h@edu.lk", "EMP14655",
                MemberStatus.ACTIVE, amount("5500"), amount("13000"), amount("21000"), amount("1500"),
                "Nuwan Herath", "Spouse", 2));
        s.add(new Seed("Anura Bandara Rathnayake", "A.B. Rathnayake", "A B Rathnayake", "Mr", Gender.MALE, Language.SINHALA,
                "775533210V", LocalDate.of(1977, 9, 28), "Badulla", "Bandarawela", "Bandarawela Central College",
                "National School", "Principal", NatureOfOccupation.PERMANENT,
                "No 4, Welimada Road, Bandarawela", "0773344221", "anura.r@edu.lk", "EMP14877",
                MemberStatus.ACTIVE, amount("14000"), amount("35000"), amount("50000"), amount("3000"),
                "Padma Rathnayake", "Spouse", 1));
        s.add(new Seed("Thilini Abeywardena", "T. Abeywardena", "T Abeywardena", "Miss", Gender.FEMALE, Language.ENGLISH,
                "945566789V", LocalDate.of(1994, 1, 25), "Colombo", "Dehiwala", "Dehiwala Girls College",
                "National School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 88, Galle Road, Dehiwala", "0705566778", "thilini.a@edu.lk", "EMP15020",
                MemberStatus.ACTIVE, amount("5000"), amount("8500"), amount("16000"), amount("1500"),
                "Nihal Abeywardena", "Father", 1));
        s.add(new Seed("Suresh Balasubramaniam", "S. Balasubramaniam", "S Balasubramaniam", "Mr", Gender.MALE, Language.TAMIL,
                "845577123V", LocalDate.of(1984, 4, 9), "Jaffna", "Chavakachcheri", "Chavakachcheri Hindu College",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 27, Point Pedro Road, Chavakachcheri", "0771122998", "suresh.b@edu.lk", "EMP15233",
                MemberStatus.ACTIVE, amount("7000"), amount("18000"), amount("29000"), amount("2000"),
                "Kamala Balasubramaniam", "Spouse", 0));
        s.add(new Seed("Ishara Madushani", "I. Madushani", "I Madushani", "Miss", Gender.FEMALE, Language.SINHALA,
                "965588234V", LocalDate.of(1996, 7, 16), "Matara", "Weligama", "Weligama Maha Vidyalaya",
                "Government School", "Teacher Grade III", NatureOfOccupation.PROBATION,
                "No 51, Matara Road, Weligama", "0761234098", "ishara.m@edu.lk", "EMP15477",
                MemberStatus.ACTIVE, amount("3000"), amount("6000"), amount("10000"), amount("1000"),
                "Wasantha Madushani", "Mother", 0));
        s.add(new Seed("Gayan Weerasinghe", "G. Weerasinghe", "G Weerasinghe", "Mr", Gender.MALE, Language.SINHALA,
                "882299876V", LocalDate.of(1988, 2, 4), "Kurunegala", "Wariyapola", "Wariyapola Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 73, Puttalam Road, Wariyapola", "0714477889", "gayan.w@edu.lk", "EMP15690",
                MemberStatus.ACTIVE, amount("6500"), amount("16000"), amount("27000"), amount("2000"),
                "Nadeesha Weerasinghe", "Spouse", 1));
        s.add(new Seed("Fathima Rizna Nazeer", "F.R. Nazeer", "F R Nazeer", "Mrs", Gender.FEMALE, Language.TAMIL,
                "925511678V", LocalDate.of(1992, 11, 30), "Batticaloa", "Eravur", "Eravur Muslim Ladies College",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 9, Main Street, Eravur", "0767788112", "fathima.n@edu.lk", "EMP15912",
                MemberStatus.ACTIVE, amount("5000"), amount("10500"), amount("20000"), amount("1500"),
                "Ahamed Nazeer", "Spouse", 1));

        // ---- INACTIVE (5) : approved by the Board, awaiting Finance activation ----
        s.add(new Seed("Kasun Liyanage", "K. Liyanage", "K Liyanage", "Mr", Gender.MALE, Language.SINHALA,
                "199944501234", LocalDate.of(1999, 8, 12), "Colombo", "Piliyandala", "Piliyandala Central College",
                "Government School", "Teacher Grade III", NatureOfOccupation.PROBATION,
                "No 15, Horana Road, Piliyandala", "0701199220", "kasun.l@edu.lk", "EMP16100",
                MemberStatus.INACTIVE, amount("3000"), amount("5000"), amount("9000"), amount("1000"),
                "Sumana Liyanage", "Mother", 0));
        s.add(new Seed("Hasini Rathnasekara", "H. Rathnasekara", "H Rathnasekara", "Miss", Gender.FEMALE, Language.SINHALA,
                "199755612345", LocalDate.of(1997, 5, 6), "Galle", "Ambalangoda", "Ambalangoda Dharmasoka College",
                "National School", "Teacher Grade III", NatureOfOccupation.PROBATION,
                "No 62, Galle Road, Ambalangoda", "0762244880", "hasini.r@edu.lk", "EMP16233",
                MemberStatus.INACTIVE, amount("3000"), amount("5500"), amount("9500"), amount("1000"),
                "Piyal Rathnasekara", "Father", 0));
        s.add(new Seed("Dinesh Kariyawasam", "D. Kariyawasam", "D Kariyawasam", "Mr", Gender.MALE, Language.SINHALA,
                "199866723456", LocalDate.of(1998, 3, 21), "Kandy", "Peradeniya", "Peradeniya Maha Vidyalaya",
                "Government School", "Teacher Grade III", NatureOfOccupation.TEMPORARY,
                "No 30, Galaha Road, Peradeniya", "0713366990", "dinesh.k@edu.lk", "EMP16388",
                MemberStatus.INACTIVE, amount("2500"), amount("4500"), amount("8000"), amount("1000"),
                "Ranjani Kariyawasam", "Mother", 0));
        s.add(new Seed("Nilakshi Weerakoon", "N. Weerakoon", "N Weerakoon", "Miss", Gender.FEMALE, Language.SINHALA,
                "199977834567", LocalDate.of(1999, 12, 2), "Badulla", "Haputale", "Haputale Maha Vidyalaya",
                "Government School", "Teacher Grade III", NatureOfOccupation.PROBATION,
                "No 11, Station Road, Haputale", "0755577220", "nilakshi.w@edu.lk", "EMP16544",
                MemberStatus.INACTIVE, amount("3000"), amount("5000"), amount("8500"), amount("1000"),
                "Ariyawathi Weerakoon", "Mother", 0));
        s.add(new Seed("Arun Sivanesan", "A. Sivanesan", "A Sivanesan", "Mr", Gender.MALE, Language.TAMIL,
                "199688945678", LocalDate.of(1996, 6, 18), "Trincomalee", "Muttur", "Muttur Central College",
                "Government School", "Teacher Grade III", NatureOfOccupation.TEMPORARY,
                "No 6, Beach Road, Muttur", "0778811003", "arun.s@edu.lk", "EMP16701",
                MemberStatus.INACTIVE, amount("2500"), amount("4000"), amount("7500"), amount("1000"),
                "Vijaya Sivanesan", "Father", 0));

        // ---- TERMINATED (5) : gives the Rejoin branch real NICs to detect ----
        s.add(new Seed("Bandula Senanayake", "B. Senanayake", "B Senanayake", "Mr", Gender.MALE, Language.SINHALA,
                "701122334V", LocalDate.of(1970, 2, 14), "Anuradhapura", "Medawachchiya", "Medawachchiya Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 40, Jaffna Road, Medawachchiya", "0772200114", "bandula.s@edu.lk", "EMP09011",
                MemberStatus.TERMINATED, amount("5000"), amount("12000"), amount("20000"), amount("1500"),
                "Leela Senanayake", "Spouse", 2));
        s.add(new Seed("Chandrika Amarasekara", "C. Amarasekara", "C Amarasekara", "Mrs", Gender.FEMALE, Language.SINHALA,
                "715544667V", LocalDate.of(1971, 9, 9), "Ratnapura", "Embilipitiya", "Embilipitiya Maha Vidyalaya",
                "Government School", "Teacher Grade I", NatureOfOccupation.PERMANENT,
                "No 25, Ratnapura Road, Embilipitiya", "0763311220", "chandrika.a@edu.lk", "EMP09244",
                MemberStatus.TERMINATED, amount("5000"), amount("11000"), amount("19000"), amount("1500"),
                "Gamini Amarasekara", "Spouse", 2));
        s.add(new Seed("Rajan Ponnambalam", "R. Ponnambalam", "R Ponnambalam", "Mr", Gender.MALE, Language.TAMIL,
                "689900112V", LocalDate.of(1968, 11, 23), "Jaffna", "Point Pedro", "Point Pedro Hartley College",
                "National School", "Deputy Principal", NatureOfOccupation.PERMANENT,
                "No 2, Main Road, Point Pedro", "0774455661", "rajan.p@edu.lk", "EMP08790",
                MemberStatus.TERMINATED, amount("9000"), amount("22000"), amount("35000"), amount("2500"),
                "Sarojini Ponnambalam", "Spouse", 2));
        s.add(new Seed("Wimala Gunathilake", "W. Gunathilake", "W Gunathilake", "Mrs", Gender.FEMALE, Language.SINHALA,
                "726677889V", LocalDate.of(1972, 4, 5), "Kurunegala", "Nikaweratiya", "Nikaweratiya Maha Vidyalaya",
                "Government School", "Teacher Grade II", NatureOfOccupation.PERMANENT,
                "No 58, Puttalam Road, Nikaweratiya", "0715599003", "wimala.g@edu.lk", "EMP09455",
                MemberStatus.TERMINATED, amount("4500"), amount("9500"), amount("16000"), amount("1500"),
                "Somapala Gunathilake", "Spouse", 1));
        s.add(new Seed("Lalith Kodikara", "L. Kodikara", "L Kodikara", "Mr", Gender.MALE, Language.SINHALA,
                "693344556V", LocalDate.of(1969, 7, 31), "Gampaha", "Minuwangoda", "Minuwangoda Maha Vidyalaya",
                "Government School", "Chief Clerk", NatureOfOccupation.PERMANENT,
                "No 19, Veyangoda Road, Minuwangoda", "0776622110", "lalith.k@edu.lk", "EMP08933",
                MemberStatus.TERMINATED, amount("8000"), amount("20000"), amount("32000"), amount("2500"),
                "Kanthi Kodikara", "Spouse", 1));

        return s;
    }
}
