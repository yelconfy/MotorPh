-- =============================================================
-- Hotfix - Employee Module
-- For an ALREADY-LOADED MotorPH_ERP (your current testing DB).
-- Use this instead of re-running 07, which would collide with
-- existing rows (IDENTITY_INSERT). Safe to run more than once.
-- =============================================================
USE MotorPH_ERP;
GO

-- 1. Seed the standard work schedule if it's missing  (MPH-15)
INSERT INTO Work_Schedule (ScheduleName, TimeStart, TimeEnd, BreakMinutes, GracePeriodMinutes)
SELECT 'Standard 8:00-17:00', '08:00:00', '17:00:00', 60, 10
WHERE NOT EXISTS (SELECT 1 FROM Work_Schedule WHERE ScheduleName = 'Standard 8:00-17:00');

-- 2. Assign that schedule to any employee that doesn't have one  (MPH-13)
UPDATE Employees
SET WorkScheduleID = (SELECT TOP 1 ScheduleID FROM Work_Schedule WHERE ScheduleName = 'Standard 8:00-17:00')
WHERE WorkScheduleID IS NULL;

-- 3. Normalize statutory IDs to bare digits, in case any row still has dashes
--    (MPH-08 - no-op if you already ran this on the DB).
UPDATE StatutoryDetails
SET SssNo        = REPLACE(REPLACE(SssNo, '-', ''), ' ', ''),
    TinNo        = REPLACE(REPLACE(TinNo, '-', ''), ' ', ''),
    PhilHealthNo = REPLACE(REPLACE(PhilHealthNo, '-', ''), ' ', ''),
    PagIbigNo    = REPLACE(REPLACE(PagIbigNo, '-', ''), ' ', '');

PRINT 'Hotfix complete: work schedule seeded + assigned; statutory normalized.';