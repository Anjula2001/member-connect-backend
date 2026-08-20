-- Removes everything created by MemberDemoSeeder.
--
-- Safe to run repeatedly. Targets ONLY rows whose IDs carry the demo prefixes
-- (MEM-DEMO-% / APP-DEMO-%), so genuine members and applications are never touched.
--
-- Run with:
--   set -a; . ./backend/.env; set +a
--   PGPASSWORD="$DB_PASSWORD" psql "$JDBC_HOST_URI" -f backend/src/main/resources/db/undo-demo-seed.sql
--
-- NOTE ON CHILD RECORDS
-- If contributors have attached work to a demo member (a termination request, a
-- scholarship, a death donation, a transfer, a loan account), the DELETE FROM member
-- below will fail on a foreign key. That is deliberate: it stops this script from
-- silently destroying someone's test work. The optional block further down clears
-- those children if you genuinely want the demo members gone regardless.

BEGIN;

-- 1. Remittances (created by the seeder itself)
DELETE FROM member_remittance
WHERE member_id IN (SELECT id FROM member WHERE member_id LIKE 'MEM-DEMO-%');

-- 2. Audit entries pointing at demo members / applications
DELETE FROM audit
WHERE (module_name = 'MEMBER'
       AND reference_id IN (SELECT id FROM member WHERE member_id LIKE 'MEM-DEMO-%'))
   OR (module_name = 'MEMBER_APPLICATION'
       AND reference_id IN (SELECT id FROM member_application WHERE applicationid LIKE 'APP-DEMO-%'));

-- 3. Minor savings accounts created by MinorSavingsDemoSeeder.
--
-- Keyed on the account number rather than on member_id LIKE 'MEM-DEMO-%',
-- deliberately: that seeder writes against every ACTIVE member, real ones
-- included, and a member-prefix filter would leave those rows behind. Every row
-- it creates is MSA-DEMO-, and nothing else uses that prefix, so this removes
-- exactly the seeded rows and never a genuine minor savings account.
--
-- Must run before the member delete below - minor_savings_account references
-- the member.
DELETE FROM minor_savings_account WHERE minor_account_no LIKE 'MSA-DEMO-%';

-- 4. The members themselves (releases the FK on member_application)
DELETE FROM member WHERE member_id LIKE 'MEM-DEMO-%';

-- 5. The applications behind them
DELETE FROM member_application WHERE applicationid LIKE 'APP-DEMO-%';

COMMIT;

-- Verify: both should return 0
-- SELECT count(*) FROM member WHERE member_id LIKE 'MEM-DEMO-%';
-- SELECT count(*) FROM member_application WHERE applicationid LIKE 'APP-DEMO-%';


-- ============================================================================
-- OPTIONAL: force removal when contributors have attached records to demo members.
-- Uncomment and run BEFORE the block above. This destroys their test work.
-- ============================================================================
-- BEGIN;
-- CREATE TEMP TABLE demo_ids AS
--   SELECT id, member_id FROM member WHERE member_id LIKE 'MEM-DEMO-%';
--
-- DELETE FROM scholarship_month_settlement     WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM scholarship_remittance           WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM member_document_dispatch_members WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM dormant_approval_list_members    WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM member_death_record              WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM death_donation_request           WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM member_termination               WHERE member_id IN (SELECT id FROM demo_ids);
-- DELETE FROM member_account                   WHERE member_id IN (SELECT id FROM demo_ids);
-- -- these two reference member(member_id), the business key, not the numeric id
-- DELETE FROM member_transfer_request          WHERE member_id IN (SELECT member_id FROM demo_ids);
-- DELETE FROM university_scholarship_request   WHERE member_id IN (SELECT member_id FROM demo_ids);
-- COMMIT;
