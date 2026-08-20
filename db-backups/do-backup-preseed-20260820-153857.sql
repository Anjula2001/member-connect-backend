--
-- PostgreSQL database dump
--

\restrict 21eKpkb4s3GSRCeetUgxozI2EOSdisIXvotbmZl0HcZjsubh5GKFEDWDFY4H1VE

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.0

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.users (id, name, assigned_district, created_at, full_name, is_active, password, profile_picture_url, role, username) FROM stdin;
3	\N	\N	2026-08-20 11:57:45.738902	Anjula	t	$2a$10$Qw7amUOQ/D8O/zRW1ogTiuVwYfvkfjg/PfPSUfzJCaFIgRFuePR2G	\N	HEAD_OFFICE	anjup
5	\N	\N	2026-08-20 12:01:13.568211	Amila pathum	t	$2a$10$G.Zsj8CRwnol2sh75uGcau9udIO7rLJyE7uQLrwAf5NZ2SfXLOKKO	/api/documents/file/a243e1c8-eeec-44d6-af17-a7bed848e4e3_WhatsApp Image 2025-06-21 at 21.19.57_6e8dfdfdfdceb44.jpg	SUPER_ADMIN	amilap
6	\N	Gampaha	2026-08-20 12:03:01.771334	Amila district	t	$2a$10$fs0Ql9cg7CE3Cr15jb/XleW1OIw1odEeGivawj3AoIL1llHCwc/Cu	\N	DISTRICT_OFFICE	amilad
7	\N	\N	2026-08-20 12:03:43.098811	Amila HO	t	$2a$10$GQ2m/4mcSz17ubHADGmcCO8xI8Hr045AcTfiP8td/aXkXWf9Zhbe.	\N	HEAD_OFFICE	amilah
1	\N	\N	2026-08-20 11:52:51.090421	Super Administrator	t	$2a$10$78gmllL9CVnuPuWAeZi9sOhz1LUBXQFHR6M3YaSDN9fG9sFhm.3YC	/api/documents/file/0e92f856-bce1-43b7-bf04-4975b7785214_f0a10584b34efbc0cd32d1ebd4c32fcb.jpg	SUPER_ADMIN	superadmin
2	\N	Colombo	2026-08-20 11:56:46.364875	Anjula	t	$2a$10$XqHbVF5QudMHc2.9A4xMq.88an5V1B77VycUuMSc8Ks3ryxL/YLMq	/api/documents/file/5e261377-59ab-49b3-ba95-cfa5c96f9ff5_2025-03-17 13.55.46.jpg	DISTRICT_OFFICE	anjulap
4	\N	\N	2026-08-20 11:58:15.905824	Anjula	t	$2a$10$b.GE./SdEpz8tU9wtvf1ie/SF7WZyZfMF5N5kpDOww/ld6Fo4LXwq	\N	BOARD_SECRETARY	anup
\.


--
-- Data for Name: audit; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.audit (id, action, audit_id, changed_at, changed_by, details, entity_id, entity_name, action_at, action_name, module_name, new_value, old_value, reference_id, remarks, action_by) FROM stdin;
1	\N	\N	\N	\N	\N	\N	\N	2026-08-20 12:13:12.047011	Application Created	MEMBER_APPLICATION	APP-2026-001	\N	1	\N	1
2	\N	\N	\N	\N	\N	\N	\N	2026-08-20 13:07:19.532054	Status Changed	MEMBER_APPLICATION	SUBMITTED_FOR_APPROVAL	NEW	1	\N	2
3	\N	\N	\N	\N	\N	\N	\N	2026-08-20 13:43:52.067367	Added to Board Approval List	MEMBER_APPLICATION	BAL-1787213631904	\N	1	\N	3
4	\N	\N	\N	\N	\N	\N	\N	2026-08-20 13:44:57.523771	Member Record Created	MEMBER	MEM-2026-001	\N	1	Created from approved application	3
5	\N	\N	\N	\N	\N	\N	\N	2026-08-20 13:46:07.702674	Status Changed	MEMBER	ACTIVE	INACTIVE	1	\N	1
6	\N	\N	\N	\N	\N	\N	\N	2026-08-20 14:23:18.160422	Documentation Printed	MEMBER	Membership Card	\N	1	\N	3
7	\N	\N	\N	\N	\N	\N	\N	2026-08-20 14:29:18.08369	Documentation Printed	MEMBER	Signature Card	\N	1	\N	3
8	\N	\N	\N	\N	\N	\N	\N	2026-08-20 14:29:30.9794	Documentation Printed	MEMBER	Passbook	\N	1	\N	3
9	\N	\N	\N	\N	\N	\N	\N	2026-08-20 14:30:49.39293	Documentation Dispatched	MEMBER	DSP-2026-001	\N	1	\N	3
\.


--
-- Data for Name: bank; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.bank (id, bank_id, name) FROM stdin;
\.


--
-- Data for Name: bank_branch; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.bank_branch (id, bank_id, branch_id, name) FROM stdin;
\.


--
-- Data for Name: banks; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.banks (id, bank_code, name) FROM stdin;
1	BOC	Bank of Ceylon
2	PB	People's Bank
3	HNB	Hatton National Bank
\.


--
-- Data for Name: basic_profile_change_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.basic_profile_change_request (id, new_date_of_birth, new_designation_id, new_email_address, new_gender, new_mobile_number, newnic, new_nature_of_occupation, new_permanent_private_address, new_preferred_language, new_private_telephone, new_status) FROM stdin;
\.


--
-- Data for Name: board_approval_list; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.board_approval_list (id, application_ids, board_meeting_date, board_meeting_id, created_at, list_id, processed_at, processed_by, status, actual_meeting_date, board_remarks, decision, reject_reason, approved_list_document) FROM stdin;
1	\N	2026-08-30	1	2026-08-20 13:43:51.90502	BAL-1787213631904	2026-08-20 13:44:58.644571	Super Admin User	PROCESSED	2026-08-20	ok	Approve	\N	\N
\.


--
-- Data for Name: member_application; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_application (id, application_date, applicationid, computer_no_in_payslip, date_of_birth, designation, educational_district, educational_zone, email_address, fixed_deposit_amount, full_name, gender, identification, identification_details, identification_number, mobile_number, name_as_in_payroll, name_with_initials, nature_of_occupation, nic_number, nominee_address, nominee_full_name, nominee_relationship, office_telephone, permanent_private_address, preferred_language, private_telephone, rejoin_flag, salary_paying_office, scholarship_death_donation_pension_amount, share_account_amount, special_deposit_amount, status, title, working_location, working_location_address, working_location_type, board_decision_reason, submission_location) FROM stdin;
1	2026-08-20	APP-2026-001	12	2001-11-03	Teacher	Galle	Ambalangoda	prasadanjula1@gmail.com	8000.00	Anjula Prasad Amarakoon	MALE	NIC	[{"type":"NIC","number":"200130802088"}]	200130802088	0771950486	Anjula	A.M.D.A.P.Amarakoon	PERMANENT	200130802079	Ratgaga, Ratnapura	Amila Pathum Ranasinghe	Sibling	0771950486	Delwala, Ratnapura	SINHALA	0712091643	f	office1	9000.00	8000.00	7000.00	APPROVED	Mr	Elapatha m.v	galle	Teacher	\N	Colombo
\.


--
-- Data for Name: board_approval_list_applications; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.board_approval_list_applications (board_approval_list_id, member_application_id) FROM stdin;
1	1
\.


--
-- Data for Name: board_meeting; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.board_meeting (id, actual_date, board_meeting_id, scheduled_date) FROM stdin;
1	\N	BM-1787213620877	2026-08-30
\.


--
-- Data for Name: branch; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.branch (id, name, bank_id) FROM stdin;
1	Colombo Main	1
2	Kandy	1
3	Galle	1
4	Colombo	2
5	Matara	2
6	Jaffna	2
7	Colombo	3
8	Negombo	3
9	Kurunegala	3
\.


--
-- Data for Name: cause_of_death; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.cause_of_death (id, active, code, name, max_death_donation_amount) FROM stdin;
1	t	NATURAL	Natural Causes	\N
2	t	ACCIDENT	Accident	\N
3	t	ILLNESS	Illness	\N
4	t	OTHER	Other	\N
\.


--
-- Data for Name: death_donation_config; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_config (id, config_key, config_value, description) FROM stdin;
1	DEFAULT_MAX_DONATION	100000.00	General Maximum Death Donation Amount, when the cause of death has no override
2	FUNERAL_ACCOUNT_MULTIPLIER	2.00	Multiplier applied to the maximum and eligible amounts when a Special Fixed Account for Funerals exists
3	FUNERAL_ACCOUNT_MAXIMUM	200000.00	Maximum credit the Special Fixed Account for Funerals may hold, excluding interest
4	DONATION_ELIGIBLE_PERIOD_DAYS	90.00	Days after the deceased date within which the death should be informed
\.


--
-- Data for Name: member; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member (id, computer_no_in_payslip, date_of_birth, email_address, full_name, gender, member_id, member_type, membership_start_date, mobile_number, name_as_in_payroll, name_with_initials, nic, permanent_private_address, preferred_language, private_telephone, salary_paying_office, status, title, application_id, designation, educational_district, educational_zone, identification, identification_details, identification_number, nature_of_occupation, nominee_address, nominee_full_name, nominee_relationship, office_telephone, profile_picture_url, signature_url, working_location, working_location_address, working_location_type, dormant_selection_date, last_activity_date, documents_dispatched_at, membership_card_printed_at, passbook_printed_at, signature_card_printed_at, submission_location) FROM stdin;
1	12	2001-11-03	prasadanjula1@gmail.com	Anjula Prasad Amarakoon	MALE	MEM-2026-001	\N	2026-08-20	0771950486	Anjula	A.M.D.A.P.Amarakoon	200130802079	Delwala, Ratnapura	SINHALA	0712091643	office1	ACTIVE	Mr	1	Teacher	Galle	Ambalangoda	NIC	[{"type":"NIC","number":"200130802088"}]	200130802088	PERMANENT	Ratgaga, Ratnapura	Amila Pathum Ranasinghe	Sibling	0771950486	\N	\N	Elapatha m.v	galle	Teacher	\N	2026-08-20	2026-08-20 14:30:49.20054	2026-08-20 14:23:17.998603	2026-08-20 14:29:30.772237	2026-08-20 14:29:17.892618	Colombo
\.


--
-- Data for Name: death_donation_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_request (id, concerns_identified, created_at, death_certificate_number, deceased_date, deceased_member_id, deceased_name, incomplete_reason, is_deceased_member, maiden_name, place_of_work, relationship_to_deceased, request_id, requested_date, status, updated_at, member_id, deceased_place_of_work, maiden_name_if_married, reject_reason, created_by, credited_to_special_fixed_account, credited_to_special_fixed_edited, disburse_donation_amount, donation_multiplier_applied, eligible_donation_amount, funeral_account_credited, funeral_account_maximum, funeral_account_no, level1_decided_at, level1_decided_by, level2_decided_at, level2_decided_by, level3_decided_at, level3_decided_by, maximum_donation_amount, months_remitted, months_remitted_edited, received_past_12_months, received_past_12_months_edited, submission_location) FROM stdin;
\.


--
-- Data for Name: death_donation_document; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_document (id, document_type, file_name, mandatory, mime_type, uploaded_at, death_donation_request_id, file_path, file_type, request_no) FROM stdin;
\.


--
-- Data for Name: death_donation_eligibility_tier; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_eligibility_tier (id, min_months, percentage) FROM stdin;
1	0	0.00
2	12	25.00
3	24	50.00
4	60	100.00
\.


--
-- Data for Name: death_donation_relationship; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_relationship (id, active, code, display_order, name) FROM stdin;
1	t	FATHER	1	Father
2	t	MOTHER	2	Mother
3	t	SPOUSE	3	Spouse
4	t	SON	4	Son
5	t	DAUGHTER	5	Daughter
6	t	BROTHER	6	Brother
7	t	SISTER	7	Sister
8	t	GRANDFATHER	8	Grandfather
9	t	GRANDMOTHER	9	Grandmother
10	t	OTHER	10	Other
\.


--
-- Data for Name: death_donation_relative; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.death_donation_relative (id, is_auto, member_id, relationship_to_deceased, death_donation_request_id, auto_populated, relative_member_id, request_id) FROM stdin;
\.


--
-- Data for Name: designation; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.designation (id, name) FROM stdin;
\.


--
-- Data for Name: district_cutoff; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.district_cutoff (id, cutoff_marks, district, exam_year) FROM stdin;
\.


--
-- Data for Name: dormant_approval_list; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.dormant_approval_list (id, actual_meeting_date, board_meeting_date, board_meeting_id, board_remarks, created_at, decision, inactivated_at, list_id, processed_at, processed_by, reject_reason, status) FROM stdin;
\.


--
-- Data for Name: dormant_approval_list_members; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.dormant_approval_list_members (dormant_approval_list_id, member_id) FROM stdin;
\.


--
-- Data for Name: dormant_config; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.dormant_config (id, dormant_period_months, enabled, schedule_day_of_month, schedule_hour, schedule_minute) FROM stdin;
1	12	t	25	0	0
\.


--
-- Data for Name: educational_district; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.educational_district (id, name) FROM stdin;
\.


--
-- Data for Name: educational_district_zone; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.educational_district_zone (id, district, zone) FROM stdin;
1	Colombo	Colombo South
2	Colombo	Colombo North
3	Colombo	Homagama
4	Kandy	Kandy
5	Kandy	Gampola
6	Kandy	Katugastota
7	Galle	Galle
8	Galle	Elpitiya
9	Galle	Ambalangoda
10	Matara	Matara
11	Matara	Akuressa
12	Matara	Weligama
13	Jaffna	Jaffna
14	Jaffna	Nallur
15	Jaffna	Chavakachcheri
16	Gampaha	Gampaha
17	Gampaha	Negombo
18	Gampaha	Minuwangoda
\.


--
-- Data for Name: educational_zone; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.educational_zone (id, name, educational_district_id) FROM stdin;
\.


--
-- Data for Name: grade5_exam_master; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.grade5_exam_master (year, exam_date) FROM stdin;
\.


--
-- Data for Name: grade5_scholarship_approval_list; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.grade5_scholarship_approval_list (id, actual_meeting_date, board_meeting_date, board_meeting_id, board_remarks, created_at, decision, list_id, processed_at, processed_by, scanned_report_path, status, type) FROM stdin;
\.


--
-- Data for Name: grade5scholarship_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.grade5scholarship_request (id, birth_certificate_number, disbursement_option, district_cut_off_mark, eligible_months, exam_year, examination_number, incomplete_reason, is_double_amount, marks_obtained, member_amount, member_id, minor_account_exists, minor_account_number, minor_amount, request_no, requested_date, school_district, status, student_name, student_school, approval_list_id, has_deviation, original_status, created_at, created_by, submission_location) FROM stdin;
\.


--
-- Data for Name: loan; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.loan (id, balance, member_id) FROM stdin;
\.


--
-- Data for Name: loan_obligation; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.loan_obligation (id, loan_id, member_id) FROM stdin;
\.


--
-- Data for Name: member_account; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_account (id, account_code, account_number, balance, last_synced_at, opened_date, source, updated_at, member_id) FROM stdin;
\.


--
-- Data for Name: member_bank_account; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_bank_account (id, account_number, bank_id, branch_id, member_id) FROM stdin;
\.


--
-- Data for Name: member_death_record; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_death_record (id, account_number, bank, bank_branch, cause_of_death, comment, concerns_identified, created_at, deceased_date, incomplete_reason, informed_date, nominee_address, nominee_email_address, nominee_full_name, nominee_identification_type_and_number, nominee_mobile_no, nominee_relationship, record_id, status, updated_at, member_id, death_donation_amount, nominee_account_no, nominee_bank_id, nominee_branch_id, nominee_email, nominee_mobile, reject_reason, created_by, credited_to_special_fixed_account, credited_to_special_fixed_edited, disburse_donation_amount, donation_multiplier_applied, eligible_donation_amount, funeral_account_credited, funeral_account_maximum, funeral_account_no, level1_decided_at, level1_decided_by, level2_decided_at, level2_decided_by, level3_decided_at, level3_decided_by, maximum_donation_amount, months_remitted, months_remitted_edited, received_past_12_months, received_past_12_months_edited, submission_location) FROM stdin;
\.


--
-- Data for Name: member_death_document; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_death_document (id, document_type, file_name, mandatory, mime_type, uploaded_at, member_death_record_id, file_path, file_type) FROM stdin;
\.


--
-- Data for Name: member_death_minor_account; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_death_minor_account (id, branch, disbursement_account_number, disbursement_bank, minor_account_holder_name, minor_account_number, member_death_record_id) FROM stdin;
\.


--
-- Data for Name: member_death_minor_disbursement; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_death_minor_disbursement (id, disbursement_account_no, disbursement_bank_id, disbursement_branch_id, holder_name, minor_account_no, member_death_record_id) FROM stdin;
\.


--
-- Data for Name: member_document_dispatch; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_document_dispatch (id, created_at, dispatch_date, dispatch_no, dispatched_by) FROM stdin;
1	2026-08-20 14:30:49.20054	2026-08-20	DSP-2026-001	Anjula
\.


--
-- Data for Name: member_document_dispatch_members; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_document_dispatch_members (dispatch_id, member_id) FROM stdin;
1	1
\.


--
-- Data for Name: member_remittance; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_remittance (id, account_code, amount, effective_from, updated_at, member_id) FROM stdin;
1	SHARE	8000.00	2026-08-20	2026-08-20 13:44:57.661333	1
2	SPECIAL_DEPOSIT	7000.00	2026-08-20	2026-08-20 13:44:57.852296	1
3	FIXED_DEPOSIT	8000.00	2026-08-20	2026-08-20 13:44:58.026035	1
4	SCHOLARSHIP_DEATH_DONATION_PENSION	9000.00	2026-08-20	2026-08-20 13:44:58.240944	1
\.


--
-- Data for Name: member_termination; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_termination (id, approved_by, approved_date, created_at, processed_by, processed_date, remarks, requested_date, termination_date, termination_id, termination_reason, termination_status, updated_at, member_id) FROM stdin;
\.


--
-- Data for Name: nature_of_occupation; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.nature_of_occupation (id, name) FROM stdin;
\.


--
-- Data for Name: working_location_type; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.working_location_type (id, name, uses_zone) FROM stdin;
\.


--
-- Data for Name: working_location; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.working_location (id, address, name, salary_paying_office, educational_district_id, educational_zone_id, working_location_type_id) FROM stdin;
\.


--
-- Data for Name: member_transfer_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.member_transfer_request (id, computer_no_in_payslip, salary_paying_office, working_location_address, request_id, requested_date, status, member_id, designation_id, educational_district_id, educational_zone_id, nature_of_occupation_id, working_location_id, working_location_type_id, decision_reason) FROM stdin;
\.


--
-- Data for Name: membership_eligibility_config; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.membership_eligibility_config (id, maximum_age, minimum_age) FROM stdin;
1	60	18
\.


--
-- Data for Name: minor_account_remittance; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.minor_account_remittance (id, minor_account_no, remittance_amount, remittance_month) FROM stdin;
\.


--
-- Data for Name: minor_accounts; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.minor_accounts (id, account_number, birth_certificate_number, created_date, is_active, remitted_months, bank_id, branch_id) FROM stdin;
\.


--
-- Data for Name: minor_savings_account; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.minor_savings_account (minor_account_no, balance, birth_certificate_no, holder_name, member_id, remitted_amount, remitted_date) FROM stdin;
\.


--
-- Data for Name: name_change_requests_table; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.name_change_requests_table (name_change_requestid, fullname, name_asz_payroll, "name with initials", status, title) FROM stdin;
\.


--
-- Data for Name: nommine_change_requests; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.nommine_change_requests (id, address, newnommine_name, nic, relationship, status) FROM stdin;
\.


--
-- Data for Name: programs; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.programs (id, name) FROM stdin;
\.


--
-- Data for Name: remittance_amount_change; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.remittance_amount_change (id, new_remittance_amount, new_remittance_currency, new_status) FROM stdin;
\.


--
-- Data for Name: remittance_master; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.remittance_master (id, account_code, account_name, active, display_order, fixed_amount, mandatory, minimum_amount) FROM stdin;
1	SHARE	Share Account	t	1	\N	t	\N
2	SPECIAL_DEPOSIT	Special Deposit Account	t	2	\N	t	\N
3	FIXED_DEPOSIT	Fixed Deposit Account	t	3	\N	t	\N
4	SCHOLARSHIP_DEATH_DONATION_PENSION	Scholarship / Death Donation / Pension	t	4	\N	f	\N
\.


--
-- Data for Name: required_document; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.required_document (id, application_type, document_name, mandatory) FROM stdin;
1	TERMINATION	Termination Request Letter	t
2	TERMINATION	Membership Documents Handover Acknowledgement	t
3	TERMINATION	Other Supporting Documents	f
4	TERMINATION_MINOR	Minor Savings Account Disbursement Instruction	t
5	TERMINATION_MINOR	Minor Bank Account Confirmation	f
6	TERMINATION_APPROVAL_REPORT	Signed Termination Approval List	f
7	MEMBER_DEATH	Death Certificate	t
8	MEMBER_DEATH	Nominee Identification	t
9	MEMBER_DEATH	Membership Documents Handover Acknowledgement	f
10	MEMBER_DEATH	Other Supporting Documents	f
11	MEMBER_DEATH_MINOR	Minor Savings Account Disbursement Instruction	t
12	MEMBER_DEATH_MINOR	Minor Bank Account Confirmation	f
13	DEATH_DONATION	Death Certificate	t
14	DEATH_DONATION	NIC Copy	t
15	DEATH_DONATION	Other	f
\.


--
-- Data for Name: required_document_types; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.required_document_types (id, display_name, document_type, mandatory, request_type) FROM stdin;
\.


--
-- Data for Name: retirement_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.retirement_request (id, comment, effective_date, incomplete_reason, member_id, reject_reason, request_no, requested_date, status) FROM stdin;
\.


--
-- Data for Name: scholarship_config; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.scholarship_config (id, config_key, config_value) FROM stdin;
\.


--
-- Data for Name: scholarship_month_settlement; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.scholarship_month_settlement (id, settled, settlement_month, member_id) FROM stdin;
\.


--
-- Data for Name: scholarship_remittance; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.scholarship_remittance (id, remittance_month, remitted, member_id, remittance_amount) FROM stdin;
\.


--
-- Data for Name: termination_approval_list; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_approval_list (id, actual_meeting_date, board_meeting_date, board_meeting_id, board_remarks, created_at, decision, list_id, processed_at, processed_by, reject_reason, status, termination_ids, approved_list_document) FROM stdin;
\.


--
-- Data for Name: termination_reason; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_reason (id, active, code, display_order, name) FROM stdin;
1	t	RESIGNATION	1	Resignation from Post
2	t	DISCIPLINARY	2	Disciplinary Action
3	t	TRANSFER	3	Transfer to Another Organization
4	t	OTHER	4	Other
\.


--
-- Data for Name: termination_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_request (id, comment, effective_date, incomplete_reason, member_id, reject_reason, request_no, requested_date, status, termination_reason, termination_reason_id, termination_reason_ref_id, previous_status, submission_location) FROM stdin;
\.


--
-- Data for Name: termination_approval_list_item; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_approval_list_item (id, previous_status, list_id, termination_request_id) FROM stdin;
\.


--
-- Data for Name: termination_approval_list_requests; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_approval_list_requests (termination_approval_list_id, termination_request_id) FROM stdin;
\.


--
-- Data for Name: termination_minor_disbursement; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.termination_minor_disbursement (id, branch, disbursement_account_number, disbursement_bank, minor_account_holder_name, minor_account_no, termination_request_id, disbursement_bank_id, disbursement_branch_id) FROM stdin;
\.


--
-- Data for Name: universities; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.universities (id, name) FROM stdin;
\.


--
-- Data for Name: university_programs; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.university_programs (id, duration, program_id, university_id, scholarship_amount) FROM stdin;
\.


--
-- Data for Name: university_scholarship_exam_master; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.university_scholarship_exam_master (id, exam_last_date, exam_year) FROM stdin;
\.


--
-- Data for Name: university_scholarship_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.university_scholarship_request (id, academic_year_start_date, account_number, address, applicant_type, birth_certificate_number, duration, exam_number, examyear, follow_deviation_process, has_minor_account, incomplete_reason, minor_account_months, mobile, nicnumber, reject_reason, request_date, status, student_name, university_scholarship_requestid, zscore, bank_id, branch_id, member_id, program_id, university_id, special_degree, total_scholarship_amount, board_meeting_id, actual_board_meeting_date, approval_list_id, processed_at, processed_by, scanned_report_path, committee_decision_at, committee_decision_by, created_at, created_by, submission_location) FROM stdin;
\.


--
-- Data for Name: university_scholarship_fund_request; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.university_scholarship_fund_request (id, disbursed_amount, disbursement_date, fund_request_id, incomplete_reason, requested_amount, requested_date, requested_period, status, university_scholarship_request_id, decision_reason) FROM stdin;
\.


--
-- Data for Name: upload_document; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.upload_document (id, application_id, document_id, document_type, file_name, file_size, file_type, storage_path, uploaded_at) FROM stdin;
1	1	DOC-af6ba767-c7e8-4739-8772-00e35b49d6cc	NIC_COPY	University_ID.jpg	181573	image/jpeg	89cceba5-61c4-4814-8821-f9ec208ae86e_University_ID.jpg	2026-08-20 12:13:34.741361
2	1	DOC-12c0cfc7-18a7-46cc-b7c6-d5350285b65d	APPOINTMENT_LETTER	2025-03-17_13.55.46.jpg	184531	image/jpeg	2baf1152-9cbc-4157-b4ed-a91f8969b625_2025-03-17_13.55.46.jpg	2026-08-20 12:13:48.436673
3	1	DOC-b563c368-bfc3-4fdb-9079-c7e4ef6fd729	PAYSLIP_COPY	1711802953356.jpg	103210	image/jpeg	45ed5e78-56cb-4085-b7f7-d096149c3c90_1711802953356.jpg	2026-08-20 12:13:55.398061
\.


--
-- Data for Name: uploaded_document; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.uploaded_document (id, file_name, file_path, file_type, request_id, required_document_id, uploaded_at) FROM stdin;
\.


--
-- Data for Name: uploaded_documents; Type: TABLE DATA; Schema: public; Owner: doadmin
--

COPY public.uploaded_documents (id, file_name, file_path, file_type, request_id, request_no, required_document_id, uploaded_at) FROM stdin;
\.


--
-- Name: audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.audit_id_seq', 9, true);


--
-- Name: bank_branch_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.bank_branch_id_seq', 1, false);


--
-- Name: bank_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.bank_id_seq', 1, false);


--
-- Name: banks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.banks_id_seq', 3, true);


--
-- Name: basic_profile_change_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.basic_profile_change_request_id_seq', 1, false);


--
-- Name: board_approval_list_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.board_approval_list_id_seq', 1, true);


--
-- Name: board_meeting_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.board_meeting_id_seq', 1, true);


--
-- Name: branch_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.branch_id_seq', 9, true);


--
-- Name: cause_of_death_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.cause_of_death_id_seq', 4, true);


--
-- Name: death_donation_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_config_id_seq', 4, true);


--
-- Name: death_donation_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_document_id_seq', 1, false);


--
-- Name: death_donation_eligibility_tier_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_eligibility_tier_id_seq', 4, true);


--
-- Name: death_donation_relationship_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_relationship_id_seq', 10, true);


--
-- Name: death_donation_relative_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_relative_id_seq', 1, false);


--
-- Name: death_donation_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.death_donation_request_id_seq', 1, false);


--
-- Name: designation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.designation_id_seq', 1, false);


--
-- Name: district_cutoff_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.district_cutoff_id_seq', 1, false);


--
-- Name: dormant_approval_list_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.dormant_approval_list_id_seq', 1, false);


--
-- Name: dormant_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.dormant_config_id_seq', 1, true);


--
-- Name: educational_district_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.educational_district_id_seq', 1, false);


--
-- Name: educational_district_zone_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.educational_district_zone_id_seq', 18, true);


--
-- Name: educational_zone_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.educational_zone_id_seq', 1, false);


--
-- Name: grade5_scholarship_approval_list_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.grade5_scholarship_approval_list_id_seq', 1, false);


--
-- Name: grade5scholarship_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.grade5scholarship_request_id_seq', 1, false);


--
-- Name: loan_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.loan_id_seq', 1, false);


--
-- Name: loan_obligation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.loan_obligation_id_seq', 1, false);


--
-- Name: member_account_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_account_id_seq', 1, false);


--
-- Name: member_application_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_application_id_seq', 1, true);


--
-- Name: member_bank_account_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_bank_account_id_seq', 1, false);


--
-- Name: member_death_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_death_document_id_seq', 1, false);


--
-- Name: member_death_minor_account_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_death_minor_account_id_seq', 1, false);


--
-- Name: member_death_minor_disbursement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_death_minor_disbursement_id_seq', 1, false);


--
-- Name: member_death_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_death_record_id_seq', 1, false);


--
-- Name: member_document_dispatch_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_document_dispatch_id_seq', 1, true);


--
-- Name: member_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_id_seq', 1, true);


--
-- Name: member_remittance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_remittance_id_seq', 4, true);


--
-- Name: member_termination_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_termination_id_seq', 1, false);


--
-- Name: member_transfer_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.member_transfer_request_id_seq', 1, false);


--
-- Name: membership_eligibility_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.membership_eligibility_config_id_seq', 1, true);


--
-- Name: minor_account_remittance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.minor_account_remittance_id_seq', 1, false);


--
-- Name: minor_accounts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.minor_accounts_id_seq', 1, false);


--
-- Name: name_change_requests_table_name_change_requestid_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.name_change_requests_table_name_change_requestid_seq', 1, false);


--
-- Name: nature_of_occupation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.nature_of_occupation_id_seq', 1, false);


--
-- Name: nommine_change_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.nommine_change_requests_id_seq', 1, false);


--
-- Name: programs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.programs_id_seq', 1, false);


--
-- Name: remittance_amount_change_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.remittance_amount_change_id_seq', 1, false);


--
-- Name: remittance_master_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.remittance_master_id_seq', 4, true);


--
-- Name: required_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.required_document_id_seq', 15, true);


--
-- Name: required_document_types_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.required_document_types_id_seq', 1, false);


--
-- Name: retirement_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.retirement_request_id_seq', 1, false);


--
-- Name: scholarship_config_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.scholarship_config_id_seq', 1, false);


--
-- Name: scholarship_month_settlement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.scholarship_month_settlement_id_seq', 1, false);


--
-- Name: scholarship_remittance_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.scholarship_remittance_id_seq', 1, false);


--
-- Name: termination_approval_list_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.termination_approval_list_id_seq', 1, false);


--
-- Name: termination_approval_list_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.termination_approval_list_item_id_seq', 1, false);


--
-- Name: termination_minor_disbursement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.termination_minor_disbursement_id_seq', 1, false);


--
-- Name: termination_reason_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.termination_reason_id_seq', 4, true);


--
-- Name: termination_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.termination_request_id_seq', 1, false);


--
-- Name: universities_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.universities_id_seq', 1, false);


--
-- Name: university_programs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.university_programs_id_seq', 1, false);


--
-- Name: university_scholarship_exam_master_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.university_scholarship_exam_master_id_seq', 1, false);


--
-- Name: university_scholarship_fund_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.university_scholarship_fund_request_id_seq', 1, false);


--
-- Name: university_scholarship_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.university_scholarship_request_id_seq', 1, false);


--
-- Name: upload_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.upload_document_id_seq', 3, true);


--
-- Name: uploaded_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.uploaded_document_id_seq', 1, false);


--
-- Name: uploaded_documents_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.uploaded_documents_id_seq', 1, false);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.users_id_seq', 7, true);


--
-- Name: working_location_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.working_location_id_seq', 1, false);


--
-- Name: working_location_type_id_seq; Type: SEQUENCE SET; Schema: public; Owner: doadmin
--

SELECT pg_catalog.setval('public.working_location_type_id_seq', 1, false);


--
-- PostgreSQL database dump complete
--

\unrestrict 21eKpkb4s3GSRCeetUgxozI2EOSdisIXvotbmZl0HcZjsubh5GKFEDWDFY4H1VE

