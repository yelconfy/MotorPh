-- =============================================================
-- 15 - Seed Approved Overtime & Leave  (TEST DATA)
-- MotorPH_ERP  |  Seed phase (depends on 01, 02, 04)
--
-- Makes Phases 4 (overtime authorization) and 5 (leave integration)
-- VERIFIABLE without a filing app, by inserting test request rows directly:
--   - APPROVED (Status=1) rows prove the payroll math (Phase 4/5 caps).
--   - PENDING  (Status=0) rows give the Phase 7a approval screens
--     (Leave Approvals / Overtime Approvals) something to action.
--
-- Idempotent: every seeded row is tagged with a 'SEED:' Reason prefix and
-- delete-then-inserted. (LIKE 'SEED:%' not '[SEED]%' — [ ] are LIKE wildcards
-- in T-SQL.)
--
-- NOTE on dates: the sample attendance set has a row for EVERY weekday, so
-- there are no natural absences. To create testable absences for the leave
-- seed, this script REMOVES a few attendance rows and approves leave over
-- those dates. Re-running 10 - ETL for Attendance restores those punches.
--
-- NOTE: PENDING rows (Status=0) do NOT affect payroll — only APPROVED rows are
-- read by PayrollProcess — so they need no attendance manipulation.
-- =============================================================
USE MotorPH_ERP;
GO

SET NOCOUNT ON;

-- -------------------------------------------------------------
-- 1) Leave_Type : ships UNSEEDED. Idempotent by unique name.
--    CHECK CK_LeaveType_CarryOver: CarryOverAllowed=0 => MaxCarryOverDays NULL.
-- -------------------------------------------------------------
INSERT INTO Leave_Type (LeaveTypeName, IsPaid, DefaultDaysPerYear, CarryOverAllowed, MaxCarryOverDays)
SELECT 'Vacation Leave', 1, 5, 0, NULL
WHERE NOT EXISTS (SELECT 1 FROM Leave_Type WHERE LeaveTypeName = 'Vacation Leave');

INSERT INTO Leave_Type (LeaveTypeName, IsPaid, DefaultDaysPerYear, CarryOverAllowed, MaxCarryOverDays)
SELECT 'Sick Leave', 1, 5, 0, NULL
WHERE NOT EXISTS (SELECT 1 FROM Leave_Type WHERE LeaveTypeName = 'Sick Leave');

INSERT INTO Leave_Type (LeaveTypeName, IsPaid, DefaultDaysPerYear, CarryOverAllowed, MaxCarryOverDays)
SELECT 'Unpaid Leave', 0, NULL, 0, NULL
WHERE NOT EXISTS (SELECT 1 FROM Leave_Type WHERE LeaveTypeName = 'Unpaid Leave');
GO

-- -------------------------------------------------------------
-- 2) Overtime requests. The single DELETE clears ALL 'SEED:' rows (approved
--    and pending) so the whole block stays idempotent.
--
--    APPROVED (Status=1) on REAL long-span rows (the cap is visible):
--      10001 2024-06-13 in 08:24 out 19:20 -> raw OT ~116m; approve 60m  => paid 60
--      10001 2024-06-20 in 09:07 out 20:01 -> raw OT ~114m; approve 120m => paid 114
--      10005 2024-07-02 in 08:50 out 19:49 -> raw OT ~119m; approve 90m  => paid 90
--      (Any worked-beyond-shift date WITHOUT an approval row pays 0 OT.)
--    PENDING (Status=0) for the Overtime Approvals screen (Phase 7a):
--      10004 / 10008 — distinct from the approved employees above.
-- -------------------------------------------------------------
DELETE FROM Overtime_Request WHERE Reason LIKE 'SEED:%';

INSERT INTO Overtime_Request (EmployeeID, OvertimeDate, OvertimeStart, OvertimeEnd, Reason, Status, DateActioned) VALUES
    (10001, '2024-06-13', '17:00', '18:00', 'SEED: approved 60m (< raw)',  1, SYSDATETIME()),
    (10001, '2024-06-20', '17:00', '19:00', 'SEED: approved 120m (> raw)', 1, SYSDATETIME()),
    (10005, '2024-07-02', '17:00', '18:30', 'SEED: approved 90m',          1, SYSDATETIME());

-- Pending OT (Status defaults to 0; DateActioned NULL; DateFiled = now).
INSERT INTO Overtime_Request (EmployeeID, OvertimeDate, OvertimeStart, OvertimeEnd, Reason) VALUES
    (10004, '2024-08-07', '17:00', '19:00', 'SEED: pending 120m for review'),
    (10008, '2024-08-08', '17:00', '18:30', 'SEED: pending 90m for review'),
    (10004, '2024-08-14', '17:00', '20:00', 'SEED: pending 180m for review');
GO

-- -------------------------------------------------------------
-- 3) Leave requests. One DELETE clears ALL 'SEED:' leave rows.
--
--    APPROVED (Status=1, Phase 5) — punches removed so the calculator
--    synthesizes the leave days:
--      PAID  : 10010 Vacation Leave 2024-07-15..16  -> ON_LEAVE,        NOT docked
--      UNPAID: 10015 Unpaid Leave   2024-07-18..19  -> ON_LEAVE_UNPAID, docked 1 day each
--    PENDING (Status=0) for the Leave Approvals screen (Phase 7a):
--      10002 VL / 10006 SL / 10011 Unpaid — distinct employees, no punch removal.
-- -------------------------------------------------------------
DELETE FROM Attendance WHERE EmployeeID = 10010 AND AttendanceDate IN ('2024-07-15', '2024-07-16');
DELETE FROM Attendance WHERE EmployeeID = 10015 AND AttendanceDate IN ('2024-07-18', '2024-07-19');

DELETE FROM Leave_Request WHERE Reason LIKE 'SEED:%';

INSERT INTO Leave_Request (EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason, Status, DateActioned)
SELECT 10010, lt.LeaveTypeID, '2024-07-15', '2024-07-16', 2, 'SEED: paid VL over removed punches', 1, SYSDATETIME()
FROM Leave_Type lt WHERE lt.LeaveTypeName = 'Vacation Leave';

INSERT INTO Leave_Request (EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason, Status, DateActioned)
SELECT 10015, lt.LeaveTypeID, '2024-07-18', '2024-07-19', 2, 'SEED: unpaid leave over removed punches', 1, SYSDATETIME()
FROM Leave_Type lt WHERE lt.LeaveTypeName = 'Unpaid Leave';

-- Pending leave (Status defaults to 0; DateActioned NULL; DateFiled = now).
INSERT INTO Leave_Request (EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason)
SELECT 10002, lt.LeaveTypeID, '2024-08-05', '2024-08-06', 2, 'SEED: pending VL for review'
FROM Leave_Type lt WHERE lt.LeaveTypeName = 'Vacation Leave';

INSERT INTO Leave_Request (EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason)
SELECT 10006, lt.LeaveTypeID, '2024-08-12', '2024-08-12', 1, 'SEED: pending SL for review'
FROM Leave_Type lt WHERE lt.LeaveTypeName = 'Sick Leave';

INSERT INTO Leave_Request (EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason)
SELECT 10011, lt.LeaveTypeID, '2024-08-19', '2024-08-20', 2, 'SEED: pending unpaid leave for review'
FROM Leave_Type lt WHERE lt.LeaveTypeName = 'Unpaid Leave';
GO

-- -------------------------------------------------------------
-- Verify
-- -------------------------------------------------------------
SELECT 'OT approved (seeded)'       AS kind, COUNT(*) AS n FROM Overtime_Request WHERE Status = 1 AND Reason LIKE 'SEED:%'
UNION ALL
SELECT 'OT pending (seeded)',                COUNT(*)      FROM Overtime_Request WHERE Status = 0 AND Reason LIKE 'SEED:%'
UNION ALL
SELECT 'Leave approved (seeded)',            COUNT(*)      FROM Leave_Request    WHERE Status = 1 AND Reason LIKE 'SEED:%'
UNION ALL
SELECT 'Leave pending (seeded)',             COUNT(*)      FROM Leave_Request    WHERE Status = 0 AND Reason LIKE 'SEED:%'
UNION ALL
SELECT 'Leave types total',                  COUNT(*)      FROM Leave_Type;
GO