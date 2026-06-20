-- =============================================================
-- 06 - Views
-- MotorPH_ERP  |  Build phase 5 (depends on all prior scripts)
-- =============================================================
USE MotorPH_ERP;
GO

-- Current (latest effective) salary row per employee.
CREATE VIEW vw_CurrentSalary AS
SELECT s.SalaryID, s.EmployeeID, s.BasicSalary, s.HourlyRate, s.EffectiveDate
FROM EmployeeSalary s
WHERE s.EffectiveDate = (
    SELECT MAX(s2.EffectiveDate)
    FROM EmployeeSalary s2
    WHERE s2.EmployeeID = s.EmployeeID
      AND s2.EffectiveDate <= CAST(GETDATE() AS DATE)
);
GO

-- Leave balance = entitlement (base + carried) minus approved request days, per year.
CREATE VIEW vw_LeaveBalance AS
SELECT
    ent.EmployeeID,
    ent.LeaveTypeID,
    ent.[Year],
    ent.TotalEntitled,
    ISNULL(used.UsedDays, 0)                       AS UsedDays,
    ent.TotalEntitled - ISNULL(used.UsedDays, 0)   AS RemainingDays
FROM Leave_Entitlement ent
LEFT JOIN (
    SELECT EmployeeID, LeaveTypeID,
           YEAR(StartDate) AS LeaveYear,
           SUM(NumberOfDays) AS UsedDays
    FROM Leave_Request
    WHERE Status = 1                 -- approved only
    GROUP BY EmployeeID, LeaveTypeID, YEAR(StartDate)
) used
    ON  used.EmployeeID  = ent.EmployeeID
    AND used.LeaveTypeID = ent.LeaveTypeID
    AND used.LeaveYear   = ent.[Year];
GO

-- Loan balance = total payable minus loan-tagged payroll deductions.
CREATE VIEW vw_LoanBalance AS
SELECT
    l.LoanID,
    l.EmployeeID,
    l.TotalPayable,
    ISNULL(paid.AmountPaid, 0)                     AS AmountPaid,
    l.TotalPayable - ISNULL(paid.AmountPaid, 0)    AS OutstandingBalance
FROM Employee_Loan l
LEFT JOIN (
    SELECT SourceID AS LoanID, SUM(Amount) AS AmountPaid
    FROM Payroll_Deduction
    WHERE SourceType = 2 AND SourceID IS NOT NULL   -- 2 = Loan
    GROUP BY SourceID
) paid ON paid.LoanID = l.LoanID;
GO

-- Flat employee record (rebuilt: allowances now live in Employee_Allowance;
-- salary comes from the current-salary view).
CREATE VIEW vw_EmployeeCompleteDetails AS
SELECT
    e.EmployeeID, e.LastName, e.FirstName, e.Birthday, e.Email, e.PhoneNo,
    e.EmploymentStatus, e.DateHired, e.Status, e.SupervisorID,
    p.PositionID, p.PositionName,
    d.DepartmentID, d.DepartmentCode, d.DepartmentName,
    ws.ScheduleID, ws.ScheduleName,
    s.SssNo, s.PhilHealthNo, s.TinNo, s.PagIbigNo,
    a.AddressID, a.HouseBlockLot, a.Street, a.Barangay, a.CityMunicipality, a.Province, a.ZipCode,
    cs.SalaryID, cs.BasicSalary, cs.HourlyRate, cs.EffectiveDate
FROM Employees e
LEFT JOIN Positions       p  ON e.PositionID     = p.PositionID
LEFT JOIN Departments     d  ON e.DepartmentID   = d.DepartmentID
LEFT JOIN Work_Schedule   ws ON e.WorkScheduleID = ws.ScheduleID
LEFT JOIN StatutoryDetails s  ON e.EmployeeID     = s.EmployeeID
LEFT JOIN EmployeeAddresses a ON e.EmployeeID     = a.EmployeeID
LEFT JOIN vw_CurrentSalary cs ON e.EmployeeID     = cs.EmployeeID;
GO

-- Optional: single activity timeline (audit + access merged).
CREATE VIEW vw_SystemActivity AS
SELECT 'AUDIT'  AS Source, Username, ActionTimestamp AS EventTime,
       CONCAT('Action ', ActionType, ' on ', TableName, ' #', RecordID) AS Detail
FROM Audit_Log
UNION ALL
SELECT 'ACCESS' AS Source, Username, LoginTimestamp AS EventTime,
       CASE LoginStatus WHEN 1 THEN 'Login success'
                        ELSE CONCAT('Login failed: ', ISNULL(FailureReason, '')) END AS Detail
FROM User_Access_Log;
GO

PRINT '06 - Views created. Schema build complete.';
