-- Ten additional mock members for the DEVELOPMENT database (MEM-DEMO-031 .. 040).
--
-- Continues the numbering MemberDemoSeeder stops at (030) and reuses the same
-- MEM-DEMO- / APP-DEMO- prefixes, so undo-demo-seed.sql already removes these too -
-- there is no second cleanup script to remember.
--
-- Safe to run repeatedly: both inserts are ON CONFLICT DO NOTHING on the natural key.
-- Wrapped in a transaction, so a failure anywhere leaves the database untouched.
--
-- MemberDemoSeeder will not fight this. Its guard skips the whole seeder as soon as
-- any MEM-DEMO- member exists, which is already the case.
--
-- WHY EVERY MEMBER IS ACTIVE
-- Statuses like RETIREMENT_REQUESTED or TERMINATION_APPROVED are only half the truth:
-- the real system also holds a matching retirement_request / termination_request row,
-- and a member parked in one of those states with no request behind it produces
-- screens that contradict themselves and test results that cannot be trusted. ACTIVE
-- is the one status that is complete on its own, and it is also the status every
-- workflow starts from - so these ten can be driven into any state through the UI.
--
-- Membership start dates are spread from 2008 to 2026 on purpose: retirement and
-- scholarship eligibility both count months of service, so a set of members who all
-- joined this year cannot exercise those rules.
--
-- Email addresses are deliberately @edu.lk placeholders. Nothing here is a real
-- mailbox, so switching NOTIFY_EMAIL_ENABLED on cannot mail a real person by accident.

BEGIN;

-- 1. The applications. Members are created FROM these, so they must land first.
INSERT INTO member_application (
    applicationid, application_date, status, submission_location,
    title, full_name, name_with_initials, name_as_in_payroll,
    gender, date_of_birth, nic_number, identification, identification_number,
    mobile_number, office_telephone, private_telephone, email_address,
    permanent_private_address, preferred_language,
    designation, nature_of_occupation, educational_district, educational_zone,
    working_location, working_location_address, working_location_type,
    salary_paying_office, computer_no_in_payslip,
    nominee_full_name, nominee_address, nominee_relationship,
    share_account_amount, fixed_deposit_amount, special_deposit_amount,
    scholarship_death_donation_pension_amount, rejoin_flag
)
SELECT
    v.app_no, '2026-08-22', 'APPROVED', v.district,
    v.title, v.full_name, v.initials, v.payroll,
    v.gender, v.dob::date, v.nic, 'NIC', v.nic,
    v.mobile, '', '', v.email,
    v.address, v.lang,
    v.designation, 'PERMANENT', v.district, v.zone,
    v.work_loc, v.work_addr, v.work_type,
    v.salary_office, v.empno,
    v.nominee, v.address, v.nominee_rel,
    14000.00, 50000.00, 35000.00,
    3000.00, false
FROM (VALUES
 ('APP-DEMO-031','Mr','Nuwan Perera','N. Perera','N Perera','MALE','1972-04-18','721104518V','0771045180','nuwan.perera@edu.lk','No 22, Temple Road, Nugegoda','SINHALA','Principal','Colombo','Sri Jayawardenepura','Nugegoda Maha Vidyalaya','Nugegoda Maha Vidyalaya, Colombo','National School','Zonal Education Office - Sri Jayawardenepura','EMP20031','Kanthi Perera','Spouse'),
 ('APP-DEMO-032','Mrs','Sanduni Ekanayake','S. Ekanayake','S Ekanayake','FEMALE','1980-11-02','805071122V','0712233445','sanduni.e@edu.lk','No 8, Station Road, Ja-Ela','SINHALA','Deputy Principal','Gampaha','Gampaha','Ja-Ela Central College','Ja-Ela Central College, Gampaha','1AB School','Zonal Education Office - Gampaha','EMP20032','Ruwan Ekanayake','Spouse'),
 ('APP-DEMO-033','Mr','Kanagaratnam Selvam','K. Selvam','K Selvam','MALE','1968-07-25','681872533V','0775566778','k.selvam@edu.lk','No 41, Hospital Road, Chavakachcheri','TAMIL','Principal','Jaffna','Thenmarachchi','Chavakachcheri Hindu College','Chavakachcheri Hindu College, Jaffna','National School','Zonal Education Office - Thenmarachchi','EMP20033','Sivakami Selvam','Spouse'),
 ('APP-DEMO-034','Ms','Malithi Senanayake','M. Senanayake','M Senanayake','FEMALE','1994-03-30','947093012V','0763344556','malithi.s@edu.lk','No 15, Peradeniya Road, Kandy','SINHALA','Teacher','Kandy','Kandy','Kandy Girls High School','Kandy Girls High School, Kandy','1AB School','Zonal Education Office - Kandy','EMP20034','Sunil Senanayake','Father'),
 ('APP-DEMO-035','Mr','Ajith Wickramasinghe','A. Wickramasinghe','A Wickramasinghe','MALE','1974-09-12','742564412V','0779988776','ajith.w@edu.lk','No 63, Wackwella Road, Galle','SINHALA','Sectional Head','Galle','Galle','Richmond College','Richmond College, Galle','National School','Zonal Education Office - Galle','EMP20035','Nayana Wickramasinghe','Spouse'),
 ('APP-DEMO-036','Mrs','Fathima Nusrath','F. Nusrath','F Nusrath','FEMALE','1986-12-08','863421208V','0752211334','fathima.n@edu.lk','No 7, Central Road, Kattankudy','TAMIL','Teacher','Batticaloa','Kalmunai','Kattankudy Central College','Kattankudy Central College, Batticaloa','1AB School','Zonal Education Office - Kalmunai','EMP20036','Mohamed Rizwan','Spouse'),
 ('APP-DEMO-037','Mr','Rohana Athukorala','R. Athukorala','R Athukorala','MALE','1966-05-19','663351930V','0714455667','rohana.a@edu.lk','No 129, Negombo Road, Kuliyapitiya','SINHALA','Principal','Kurunegala','Kuliyapitiya','Kuliyapitiya Central College','Kuliyapitiya Central College, Kurunegala','National School','Zonal Education Office - Kuliyapitiya','EMP20037','Sriyani Athukorala','Spouse'),
 ('APP-DEMO-038','Ms','Tharushi Dilhara','T. Dilhara','T Dilhara','FEMALE','1996-08-24','962371524V','0768877665','tharushi.d@edu.lk','No 30, Beach Road, Matara','SINHALA','Teacher','Matara','Matara','St Servatius College','St Servatius College, Matara','1AB School','Zonal Education Office - Matara','EMP20038','Nimal Dilhara','Father'),
 ('APP-DEMO-039','Mr','Mahinda Ratnayake','M. Ratnayake','M Ratnayake','MALE','1970-02-14','700451422V','0726677889','mahinda.r@edu.lk','No 52, Maithripala Senanayake Mawatha, Anuradhapura','SINHALA','Deputy Principal','Anuradhapura','Anuradhapura','Central College Anuradhapura','Central College Anuradhapura, Anuradhapura','National School','Zonal Education Office - Anuradhapura','EMP20039','Chandra Ratnayake','Spouse'),
 ('APP-DEMO-040','Mrs','Vijayaluxmi Kandasamy','V. Kandasamy','V Kandasamy','FEMALE','1983-06-07','832581607V','0773322119','vijaya.k@edu.lk','No 19, Dockyard Road, Trincomalee','TAMIL','Sectional Head','Trincomalee','Trincomalee','Sri Shanmuga Hindu Ladies College','Sri Shanmuga Hindu Ladies College, Trincomalee','1AB School','Zonal Education Office - Trincomalee','EMP20040','Kandasamy Suresh','Spouse')
) AS v(app_no, title, full_name, initials, payroll, gender, dob, nic, mobile, email,
       address, lang, designation, district, zone, work_loc, work_addr, work_type,
       salary_office, empno, nominee, nominee_rel)
ON CONFLICT (applicationid) DO NOTHING;

-- 2. The members. Every shared column is copied straight off the application rather
--    than retyped, so the two rows cannot disagree with each other. Only the
--    genuinely member-only columns are supplied here.
INSERT INTO member (
    member_id, member_type, status, membership_start_date, last_activity_date,
    application_id, submission_location,
    title, full_name, name_with_initials, name_as_in_payroll,
    gender, date_of_birth, nic, identification, identification_number,
    mobile_number, office_telephone, private_telephone, email_address,
    permanent_private_address, preferred_language,
    designation, nature_of_occupation, educational_district, educational_zone,
    working_location, working_location_address, working_location_type,
    salary_paying_office, computer_no_in_payslip,
    nominee_full_name, nominee_address, nominee_relationship,
    is_remittance, is_settlement
)
SELECT
    'MEM-DEMO-' || right(a.applicationid, 3),
    'Member', 'ACTIVE', s.start_date::date, '2026-08-01'::date,
    a.id, a.submission_location,
    a.title, a.full_name, a.name_with_initials, a.name_as_in_payroll,
    a.gender, a.date_of_birth, a.nic_number, a.identification, a.identification_number,
    a.mobile_number, a.office_telephone, a.private_telephone, a.email_address,
    a.permanent_private_address, a.preferred_language,
    a.designation, a.nature_of_occupation, a.educational_district, a.educational_zone,
    a.working_location, a.working_location_address, a.working_location_type,
    a.salary_paying_office, a.computer_no_in_payslip,
    a.nominee_full_name, a.nominee_address, a.nominee_relationship,
    false, false
FROM member_application a
JOIN (VALUES
 ('APP-DEMO-031','2012-04-01'),
 ('APP-DEMO-032','2015-06-15'),
 ('APP-DEMO-033','2009-01-20'),
 ('APP-DEMO-034','2026-05-10'),
 ('APP-DEMO-035','2011-09-05'),
 ('APP-DEMO-036','2018-03-12'),
 ('APP-DEMO-037','2008-11-30'),
 ('APP-DEMO-038','2026-01-15'),
 ('APP-DEMO-039','2013-07-22'),
 ('APP-DEMO-040','2016-02-28')
) AS s(app_no, start_date) ON s.app_no = a.applicationid
ON CONFLICT (member_id) DO NOTHING;

COMMIT;
