-- Grade 5 district cut-off marks for exam years 2021-2025, all 25 districts (125 rows).
-- DEVELOPMENT database only - mock data for testing.
--
-- The table already holds 2026 for all 25 districts. grade5_exam_master already holds
-- 2021-2026, so these five years are exactly the missing complement: every cut-off row
-- created here has a real exam year behind it, and no exam year is left half-populated.
--
-- Safe to run repeatedly: ON CONFLICT DO NOTHING on the (district, exam_year) unique
-- key. Wrapped in a transaction, so a failure anywhere leaves the table untouched.
--
-- WHY THE MARKS ARE DERIVED FROM 2026 RATHER THAN TYPED OUT
-- A real cut-off is a property of the district as much as of the year - Colombo and
-- Kegalle sit high every year, Gampaha and Batticaloa lower - so 125 independently
-- invented numbers would destroy the very pattern that makes the data useful for
-- testing district filters and eligibility rules. Each district's 2026 mark is used as
-- its baseline and moved by a small deterministic amount per year:
--
--   * a year offset, so every district shifts with the national trend, and
--   * a district-specific wobble from length(district), so they do not all move in
--     lockstep, which would look obviously synthetic.
--
-- Deterministic on purpose: re-running produces identical numbers, and a teammate
-- rebuilding the database gets the same figures rather than a fresh random set that
-- silently invalidates a recorded test result.
--
-- Clamped to 130..200, the plausible range for this examination.

BEGIN;

INSERT INTO district_cutoff (district, exam_year, cutoff_marks)
SELECT
    base.district,
    y.exam_year,
    GREATEST(130, LEAST(200,
        base.cutoff_marks
        + y.year_offset
        + ((length(base.district) + y.exam_year) % 7) - 3
    ))
FROM (
    -- The 2026 row is the baseline for each district, so the seed cannot drift out of
    -- step with whatever is actually in the table.
    SELECT district, cutoff_marks
    FROM district_cutoff
    WHERE exam_year = 2026
) AS base
CROSS JOIN (VALUES
    (2025, -3),
    (2024,  2),
    (2023, -6),
    (2022,  4),
    (2021, -8)
) AS y(exam_year, year_offset)
ON CONFLICT (district, exam_year) DO NOTHING;

COMMIT;
