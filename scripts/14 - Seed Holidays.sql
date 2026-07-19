-- =============================================================
-- 14 - Seed Holidays
-- MotorPH_ERP  |  Seed phase (depends on 01 Reference Tables)
--
-- Populates the Holiday table, which ships EMPTY from schema creation.
-- Until this runs, HolidayDAO returns zero rows and every day classifies
-- as Regular/Weekend (no holiday premium is ever applied).
--
-- BASIS: Philippine holidays for CY2024 (the year the sample attendance
--        data covers, 2024-06-03 .. 2024-12-31). Sources:
--          - Proclamation No. 368, s. 2023  (base regular + special list)
--          - Proclamation No. 665, s. 2024  (MOVED Ninoy Aquino Day from
--            21 Aug [Wed] to 23 Aug 2024 [Fri])
--          - Eid'l Fitr  declared 10 Apr 2024 (regular)
--          - Eidul Adha  declared 17 Jun 2024 (regular)
--
-- HolidayType: 0 = Regular holiday (200% if worked),
--              1 = Special Non-Working day (130% if worked).
-- IsRecurring: 1 = fixed civil/religious date that repeats yearly,
--              0 = movable / one-off (Holy Week, Eid, National Heroes Day
--              [last Monday], the Ninoy-moved date, the additional specials).
--              NOTE: the app currently resolves holidays by EXACT date, so
--              IsRecurring is metadata only until year-expansion is added.
--
-- Self-correcting: deletes CY2024 rows then re-inserts (client-batch safe,
-- same pattern as 12 - Seed Statutory Tables).
-- =============================================================
USE MotorPH_ERP;
GO

SET NOCOUNT ON;

-- Clear only CY2024 so re-running is idempotent and doesn't touch other years.
DELETE FROM Holiday WHERE YEAR(HolidayDate) = 2024;

INSERT INTO Holiday (HolidayDate, HolidayName, HolidayType, IsRecurring) VALUES
    -- ---- Regular holidays (type 0) ----
    ('2024-01-01', 'New Year''s Day',                 0, 1),
    ('2024-03-28', 'Maundy Thursday',                 0, 0),
    ('2024-03-29', 'Good Friday',                     0, 0),
    ('2024-04-09', 'Araw ng Kagitingan',              0, 1),
    ('2024-04-10', 'Eid''l Fitr',                     0, 0),
    ('2024-05-01', 'Labor Day',                       0, 1),
    ('2024-06-12', 'Independence Day',                0, 1),
    ('2024-06-17', 'Eidul Adha',                      0, 0),
    ('2024-08-26', 'National Heroes Day',             0, 0),
    ('2024-11-30', 'Bonifacio Day',                   0, 1),
    ('2024-12-25', 'Christmas Day',                   0, 1),
    ('2024-12-30', 'Rizal Day',                       0, 1),
    -- ---- Special (Non-Working) days (type 1) ----
    ('2024-02-10', 'Chinese New Year',                1, 0),
    ('2024-03-30', 'Black Saturday',                  1, 0),
    ('2024-08-23', 'Ninoy Aquino Day',                1, 0),   -- moved from 21 Aug by Proc 665
    ('2024-11-01', 'All Saints'' Day',                1, 1),
    ('2024-11-02', 'All Souls'' Day (Additional)',    1, 0),
    ('2024-12-08', 'Feast of the Immaculate Conception', 1, 1),
    ('2024-12-24', 'Christmas Eve (Additional)',      1, 0),
    ('2024-12-31', 'Last Day of the Year',            1, 1);
GO

-- Verify: plain SELECT (no T-SQL variable, so it runs in any client batch mode).
-- Expect: CY2024_Holidays = 20, Regular = 12, Special = 8.
SELECT
    COUNT(*)                                          AS CY2024_Holidays,
    SUM(CASE WHEN HolidayType = 0 THEN 1 ELSE 0 END)  AS Regular,
    SUM(CASE WHEN HolidayType = 1 THEN 1 ELSE 0 END)  AS Special
FROM Holiday
WHERE YEAR(HolidayDate) = 2024;
GO