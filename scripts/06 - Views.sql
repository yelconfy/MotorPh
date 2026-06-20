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

-- Employee payslip report: one row per payslip, reproducing the MotorPH
-- payslip layout (identity, period, earnings, itemised benefits, itemised
-- statutory deductions, gross/deductions/net summary). Source of truth is the
-- frozen Payslip snapshot; the line tables are pivoted into named columns and
-- two reconciliation columns re-derive gross/net to verify the snapshot.
-- NOTE: MonthlyRate / DailyRate come from vw_CurrentSalary (LATEST effective
-- rate), not the rate as-of the historical period. BasicPay (snapshot) is the
-- period-accurate earned basic and remains authoritative.
CREATE VIEW vw_EmployeePayslipReport AS
SELECT
    -- Payslip / period identity
    ps.PayslipID,
    ps.PayrollPeriodID,
    pp.PeriodName,
    pp.StartDate                                        AS PeriodStart,
    pp.EndDate                                          AS PeriodEnd,
    pp.PayDate,

    -- Employee identity (JOINs)
    e.EmployeeID,
    (e.FirstName + N' ' + e.LastName)                   AS EmployeeName,
    p.PositionName,
    d.DepartmentName,
    sd.SssNo,
    sd.PhilHealthNo,
    sd.TinNo,
    sd.PagIbigNo,

    -- Attendance snapshot
    ps.DaysWorked,
    ps.HoursWorked,

    -- Earnings
    cs.BasicSalary                                      AS MonthlyRate,
    CAST(cs.BasicSalary / 21.75 AS DECIMAL(18,2))       AS DailyRate,
    cs.HourlyRate,
    ps.BasicPay,

    -- Benefits (allowance lines pivoted to columns)
    ISNULL(al.Rice,     0)                              AS RiceSubsidy,
    ISNULL(al.Phone,    0)                              AS PhoneAllowance,
    ISNULL(al.Clothing, 0)                              AS ClothingAllowance,
    ps.TotalAllowances,

    -- Deductions (deduction lines pivoted to columns)
    ISNULL(dl.SSS,         0)                           AS SSS,
    ISNULL(dl.PhilHealth,  0)                           AS PhilHealth,
    ISNULL(dl.PagIBIG,     0)                           AS PagIBIG,
    ISNULL(dl.Withholding, 0)                           AS WithholdingTax,
    ISNULL(dl.OtherDed,    0)                           AS OtherDeductions,
    ps.TotalDeductions,

    -- Summary (from snapshot)
    ps.GrossPay,
    ps.TotalAdjustments,
    ps.NetPay,

    -- Reconciliation (re-derived to verify the snapshot)
    (ps.BasicPay + ISNULL(ps.TotalAllowances, 0))                                  AS GrossPay_Check,
    (ps.GrossPay - ISNULL(ps.TotalDeductions, 0) + ISNULL(ps.TotalAdjustments, 0)) AS NetPay_Check,

    ps.Status                                           AS PayslipStatus  -- 0=Draft,1=Finalized,2=Paid
FROM Payslip ps
    INNER JOIN Payroll_Period   pp ON pp.PayrollPeriodID = ps.PayrollPeriodID
    INNER JOIN Employees        e  ON e.EmployeeID       = ps.EmployeeID
    LEFT  JOIN Positions        p  ON p.PositionID       = e.PositionID
    LEFT  JOIN Departments      d  ON d.DepartmentID     = e.DepartmentID
    LEFT  JOIN StatutoryDetails sd ON sd.EmployeeID      = e.EmployeeID
    LEFT  JOIN vw_CurrentSalary cs ON cs.EmployeeID      = e.EmployeeID
    LEFT JOIN (
        SELECT
            pa.PayslipID,
            SUM(CASE WHEN at.AllowanceName = 'Rice Subsidy'       THEN pa.Amount END) AS Rice,
            SUM(CASE WHEN at.AllowanceName = 'Phone Allowance'    THEN pa.Amount END) AS Phone,
            SUM(CASE WHEN at.AllowanceName = 'Clothing Allowance' THEN pa.Amount END) AS Clothing
        FROM Payroll_Allowance pa
            INNER JOIN Allowance_Type at ON at.AllowanceTypeID = pa.AllowanceTypeID
        GROUP BY pa.PayslipID
    ) al ON al.PayslipID = ps.PayslipID
    LEFT JOIN (
        SELECT
            pd.PayslipID,
            SUM(CASE WHEN dt.DeductionName = 'SSS'             THEN pd.Amount END) AS SSS,
            SUM(CASE WHEN dt.DeductionName = 'PhilHealth'      THEN pd.Amount END) AS PhilHealth,
            SUM(CASE WHEN dt.DeductionName = 'Pag-IBIG'        THEN pd.Amount END) AS PagIBIG,
            SUM(CASE WHEN dt.DeductionName = 'Withholding Tax' THEN pd.Amount END) AS Withholding,
            SUM(CASE WHEN dt.DeductionName NOT IN
                     ('SSS','PhilHealth','Pag-IBIG','Withholding Tax')
                     THEN pd.Amount END)                                           AS OtherDed
        FROM Payroll_Deduction pd
            INNER JOIN Deduction_Type dt ON dt.DeductionTypeID = pd.DeductionTypeID
        GROUP BY pd.PayslipID
    ) dl ON dl.PayslipID = ps.PayslipID;
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