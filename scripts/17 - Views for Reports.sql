-- =============================================================
-- 17 - Views for Reports
-- MotorPH_ERP  |  Reporting layer (depends on 01-05, 12)
--
-- Read-only reporting views consumed by the report screens. Each is
-- idempotent (drop-if-exists guard) so this script is safe to re-run.
--
--   vw_MonthlyPayrollSummary  -> Payroll Summary Report   (per employee x month)
--   vw_ThirteenthMonth        -> 13th Month Pay Report     (per employee x year)
--   vw_StatutoryRemittance    -> SSS R-3 / PhilHealth RF-1 / Pag-IBIG M1-1
--                                                          (per employee x month)
--   vw_Bir2316                -> BIR Form 2316 certificate  (per employee x year)
--   vw_LeaveBalanceReport     -> Leave Balance Report    (per employee x type x year)
--   vw_LoanLedgerReport       -> Loan Ledger Report      (per loan)
--   vw_Bir2316                -> BIR Form 2316 certificate  (per employee x year)
--   vw_LeaveBalanceReport     -> Leave Balance Report    (per employee x type x year)
--   vw_LoanLedgerReport       -> Loan Ledger Report      (per loan)
--
-- All read the FROZEN Payslip snapshot, never a recompute, so the reports
-- always match the finalized slips. (vw_StatutoryRemittance additionally
-- re-derives the employer share, which is not persisted on the payslip.)
-- =============================================================

-- =============================================================
-- vw_MonthlyPayrollSummary
--
-- Grain: (Employee x Year x Month).  The payroll engine runs
-- SEMI-MONTHLY (PayFrequency = 2); full statutory contributions
-- post on the 2nd cutoff only.  Summing the (up to two) payslips
-- in a calendar month therefore yields the correct MONTHLY totals.
--
-- Deductions are pre-aggregated PER PAYSLIP in a derived table so
-- the row fan-out cannot double-count GrossPay / NetPay.
-- =============================================================
USE MotorPH_ERP;
GO

IF OBJECT_ID('vw_MonthlyPayrollSummary', 'V') IS NOT NULL
    DROP VIEW vw_MonthlyPayrollSummary;
GO

CREATE VIEW vw_MonthlyPayrollSummary AS
SELECT
    -- ---- identifiers ----
    e.EmployeeID                                   AS EmployeeNo,
    CONCAT(e.LastName, ', ', e.FirstName)          AS EmployeeFullName,
    p.PositionName                                 AS Position,
    d.DepartmentName                               AS Department,

    -- ---- pay period (month) ----
    YEAR(pp.EndDate)                               AS PayYear,
    MONTH(pp.EndDate)                              AS PayMonth,
    DATENAME(MONTH, MIN(pp.EndDate))               AS PayMonthName,
    MIN(pp.StartDate)                              AS PeriodStart,
    MAX(pp.EndDate)                                AS PeriodEnd,
    COUNT(ps.PayslipID)                            AS PayslipsIncluded,  -- cutoffs rolled up (1 or 2)

    -- ---- statutory identifiers ----
    sd.SssNo                                       AS SocialSecurityNo,
    sd.PhilHealthNo                                AS PhilHealthNo,
    sd.PagIbigNo                                   AS PagIbigNo,
    sd.TinNo                                        AS TIN,

    -- ---- money: aggregated across the month's cutoffs ----
    SUM(ps.GrossPay)                               AS GrossIncome,
    SUM(ISNULL(ded.SssAmount,        0))           AS SocialSecurityContribution,
    SUM(ISNULL(ded.PhilHealthAmount, 0))           AS PhilHealthContribution,
    SUM(ISNULL(ded.PagIbigAmount,    0))           AS PagIbigContribution,
    SUM(ISNULL(ded.WithholdingTax,   0))           AS WithholdingTax,
    SUM(ps.NetPay)                                 AS NetPay
FROM Payslip ps
JOIN      Payroll_Period   pp ON ps.PayrollPeriodID = pp.PayrollPeriodID
JOIN      Employees        e  ON ps.EmployeeID       = e.EmployeeID
LEFT JOIN Positions        p  ON e.PositionID        = p.PositionID
LEFT JOIN Departments      d  ON e.DepartmentID      = d.DepartmentID
LEFT JOIN StatutoryDetails sd ON e.EmployeeID        = sd.EmployeeID
LEFT JOIN (
    -- One row per payslip; statutory deductions pivoted into columns.
    -- Matched by the UNIQUE DeductionName values seeded in script 12.
    SELECT
        pd.PayslipID,
        SUM(CASE WHEN dt.DeductionName = 'SSS'             THEN pd.Amount ELSE 0 END) AS SssAmount,
        SUM(CASE WHEN dt.DeductionName = 'PhilHealth'      THEN pd.Amount ELSE 0 END) AS PhilHealthAmount,
        SUM(CASE WHEN dt.DeductionName = 'Pag-IBIG'        THEN pd.Amount ELSE 0 END) AS PagIbigAmount,
        SUM(CASE WHEN dt.DeductionName = 'Withholding Tax' THEN pd.Amount ELSE 0 END) AS WithholdingTax
    FROM Payroll_Deduction pd
    JOIN Deduction_Type    dt ON pd.DeductionTypeID = dt.DeductionTypeID
    GROUP BY pd.PayslipID
) ded ON ded.PayslipID = ps.PayslipID
WHERE e.Status = 1          -- employee-status filter: active employees only
  AND ps.Status >= 1        -- payslip filter: finalized (1) or paid (2); excludes drafts (0)
                            --   >>> relax to  ps.Status >= 0  if testing against draft runs <<<
GROUP BY
    e.EmployeeID, e.LastName, e.FirstName,
    p.PositionName, d.DepartmentName,
    YEAR(pp.EndDate), MONTH(pp.EndDate),
    sd.SssNo, sd.PhilHealthNo, sd.PagIbigNo, sd.TinNo;
GO

PRINT '17 - vw_MonthlyPayrollSummary created.';
GO

-- =============================================================
-- vw_ThirteenthMonth
--
-- Grain: (Employee x Year).  Basis: Presidential Decree 851 — total BASIC
-- salary EARNED in the calendar year divided by 12.
--
--   * "Earned" basic = SUM(Payslip.BasicPay), the frozen per-cutoff snapshot,
--     so mid-year raises (versioned salary rows) and unpaid absences are
--     already reflected — no recompute here.
--   * Only FINALIZED (Status=1) and PAID (Status=2) payslips count; Drafts
--     (Status=0) are unfinalized pay and excluded — same rule as the summary
--     view above (ps.Status >= 1).
--   * The divisor is always 12 (PD 851), NOT the number of cutoffs — a mid-year
--     hire is prorated naturally by having fewer cutoffs to sum, not by changing
--     the divisor.
--   * Year is taken from Payroll_Period.EndDate (the cutoff the pay belongs to),
--     consistent with vw_MonthlyPayrollSummary's PayYear.
--   * TotalBasicEarned and PayslipsIncluded are exposed so the figure is
--     auditable (show-the-work for DOLE / employee disputes).
--
-- NOTE (managerial exclusion): PD 851 legally entitles only RANK-AND-FILE
-- employees. A position-level IsManagerial flag is a planned follow-on; once it
-- exists, add  AND p.IsManagerial = 0  to the WHERE clause below. Until then
-- this view intentionally includes ALL employees.
--
-- NOTE: the employee-status filter (e.Status = 1) is intentionally OMITTED here
-- so a separated employee's 13th month can still be reported for a year they
-- earned basic pay in (final-pay use case); the summary view filters active-only
-- because it is a current-payroll report. Change if your policy differs.
-- =============================================================

IF OBJECT_ID('vw_ThirteenthMonth', 'V') IS NOT NULL
    DROP VIEW vw_ThirteenthMonth;
GO

CREATE VIEW vw_ThirteenthMonth AS
SELECT
    -- ---- identifiers ----
    e.EmployeeID                                   AS EmployeeNo,
    CONCAT(e.LastName, ', ', e.FirstName)          AS EmployeeFullName,
    p.PositionName                                 AS Position,
    d.DepartmentName                               AS Department,

    -- ---- basis year ----
    YEAR(pp.EndDate)                               AS PayYear,

    -- ---- computation (auditable) ----
    SUM(ps.BasicPay)                               AS TotalBasicEarned,
    COUNT(ps.PayslipID)                            AS PayslipsIncluded,   -- cutoffs summed
    CAST(SUM(ps.BasicPay) / 12.0 AS DECIMAL(18,2)) AS ThirteenthMonthPay
FROM Payslip ps
JOIN      Payroll_Period pp ON ps.PayrollPeriodID = pp.PayrollPeriodID
JOIN      Employees      e  ON ps.EmployeeID       = e.EmployeeID
LEFT JOIN Positions      p  ON e.PositionID        = p.PositionID
LEFT JOIN Departments    d  ON e.DepartmentID      = d.DepartmentID
WHERE ps.Status >= 1        -- finalized (1) or paid (2); excludes drafts (0)
GROUP BY
    e.EmployeeID, e.LastName, e.FirstName,
    p.PositionName, d.DepartmentName,
    YEAR(pp.EndDate);
GO

PRINT '17 - vw_ThirteenthMonth created.';
GO

-- =============================================================
-- vw_StatutoryRemittance
--
-- Grain: (Employee x Year x Month).  Backs the three government remittance
-- reports: SSS R-3, PhilHealth RF-1, Pag-IBIG M1-1.
--
-- EMPLOYEE share  = the amount ACTUALLY deducted (frozen Payroll_Deduction on
--                   Finalized/Paid payslips), summed over the calendar month.
--                   Statutory posts full-monthly on the 2nd cutoff only, so the
--                   month's sum equals the monthly contribution.
-- EMPLOYER share  = re-derived (Payroll_Deduction stores employee-side only):
--                   * SSS  -> SSS_Contribution_Table.EmployerShare, bracketed on
--                            the employee's CURRENT monthly basic (vw_CurrentSalary).
--                            Both EE and ER are absolute pesos in that table, so
--                            no percentage math / no dependency on Contribution_Rate
--                            (see BKL-21 rate-storage flag).
--                   * PhilHealth -> ER = EE (statutory 50/50, 2024).
--                   * Pag-IBIG   -> ER = EE (2%/2% for all basics > P1,500; the
--                            entire 2024 dataset qualifies). If a sub-1,500 earner
--                            is ever added, revisit (ER would be 2% vs EE 1%).
--
-- Only FINALIZED (1) / PAID (2) payslips; Drafts (0) excluded.
-- SSS ER note: bracketed on CURRENT basic, not the period-historical rate — a
-- reporting simplification; acceptable for a monthly remittance list.
-- =============================================================

IF OBJECT_ID('vw_StatutoryRemittance', 'V') IS NOT NULL
    DROP VIEW vw_StatutoryRemittance;
GO

CREATE VIEW vw_StatutoryRemittance AS
WITH MonthlyDeduction AS (
    -- Employee-side monthly contribution actually deducted, per agency.
    SELECT
        e.EmployeeID,
        YEAR(pp.EndDate)  AS PayYear,
        MONTH(pp.EndDate) AS PayMonth,
        SUM(CASE WHEN dt.DeductionName = 'SSS'        THEN pd.Amount ELSE 0 END) AS SssEmployee,
        SUM(CASE WHEN dt.DeductionName = 'PhilHealth' THEN pd.Amount ELSE 0 END) AS PhicEmployee,
        SUM(CASE WHEN dt.DeductionName = 'Pag-IBIG'   THEN pd.Amount ELSE 0 END) AS HdmfEmployee
    FROM Payslip ps
    JOIN Payroll_Period  pp ON ps.PayrollPeriodID = pp.PayrollPeriodID
    JOIN Employees       e  ON ps.EmployeeID       = e.EmployeeID
    JOIN Payroll_Deduction pd ON pd.PayslipID       = ps.PayslipID
    JOIN Deduction_Type  dt ON dt.DeductionTypeID  = pd.DeductionTypeID
    WHERE ps.Status >= 1
      AND dt.DeductionName IN ('SSS', 'PhilHealth', 'Pag-IBIG')
    GROUP BY e.EmployeeID, YEAR(pp.EndDate), MONTH(pp.EndDate)
)
SELECT
    -- ---- identifiers ----
    md.EmployeeID                                  AS EmployeeNo,
    CONCAT(e.LastName, ', ', e.FirstName)          AS EmployeeFullName,
    sd.SssNo                                       AS SssNo,
    sd.PhilHealthNo                                AS PhilHealthNo,
    sd.PagIbigNo                                   AS PagIbigNo,

    -- ---- period ----
    md.PayYear                                     AS PayYear,
    md.PayMonth                                    AS PayMonth,

    -- ---- SSS (EE actual; ER from table bracket) ----
    md.SssEmployee                                 AS SssEmployeeShare,
    ISNULL(sssER.EmployerShare, 0)                 AS SssEmployerShare,
    md.SssEmployee + ISNULL(sssER.EmployerShare,0) AS SssTotal,

    -- ---- PhilHealth (50/50) ----
    md.PhicEmployee                                AS PhicEmployeeShare,
    md.PhicEmployee                                AS PhicEmployerShare,
    md.PhicEmployee * 2                            AS PhicTotal,

    -- ---- Pag-IBIG (2%/2% for this dataset) ----
    md.HdmfEmployee                                AS HdmfEmployeeShare,
    md.HdmfEmployee                                AS HdmfEmployerShare,
    md.HdmfEmployee * 2                            AS HdmfTotal
FROM MonthlyDeduction md
JOIN      Employees        e  ON e.EmployeeID  = md.EmployeeID
LEFT JOIN StatutoryDetails sd ON sd.EmployeeID = md.EmployeeID
LEFT JOIN vw_CurrentSalary cs ON cs.EmployeeID = md.EmployeeID
OUTER APPLY (
    -- SSS employer share for the bracket containing the employee's monthly basic.
    SELECT TOP 1 t.EmployerShare
    FROM SSS_Contribution_Table t
    WHERE t.Status = 1
      AND cs.BasicSalary >= t.RangeFrom
      AND (t.RangeTo IS NULL OR cs.BasicSalary <= t.RangeTo)
    ORDER BY t.EffectiveDate DESC
) sssER;
GO

PRINT '17 - vw_StatutoryRemittance created.';
GO

-- =============================================================
-- vw_Bir2316
--
-- Grain: (Employee x Year).  Backs the BIR Form 2316 certificate
-- (Certificate of Compensation Payment / Tax Withheld), one per employee.
--
-- All money is from the FROZEN payslip snapshot (Finalized/Paid), never a
-- recompute:
--   GrossCompensation     = SUM(Payslip.GrossPay)            (basic + all allowances)
--   Taxable/NonTaxable    allowances split by Allowance_Type.IsTaxable
--                          (de minimis / non-taxable benefits are exempt)
--   Mandatory contribs    = SUM(SSS + PhilHealth + Pag-IBIG employee share)  [exempt]
--   ThirteenthMonth       from vw_ThirteenthMonth; exempt up to the P90,000 cap,
--                          excess is taxable
--   TaxWithheld           = SUM(Withholding Tax) actually deducted            [actual]
--
-- TaxableCompensation = Gross - NonTaxableAllowances - MandatoryContributions
--                       + ThirteenthMonthTaxable(excess over 90k)
--
-- TaxDue = 2024 TRAIN ANNUAL brackets applied to TaxableCompensation. The
-- brackets are stated DIRECTLY here (they equal the semi-monthly
-- WithholdingTax_Table x 24) so the certificate's tax-due figure does NOT depend
-- on the Contribution_Rate / WithholdingTax_Table rate-storage question (BKL-21).
--
-- OverUnderWithheld = TaxWithheld - TaxDue
--   ( > 0  => over-withheld, refund due to employee;
--     < 0  => under-withheld, collectible from employee. )
--
-- Depends on vw_ThirteenthMonth (defined above) and vw_EmployeeCompleteDetails
-- (script 06). De minimis limits are NOT individually enforced (any allowance
-- flagged IsTaxable=0 is treated fully exempt) - a documented simplification.
-- =============================================================

IF OBJECT_ID('vw_Bir2316', 'V') IS NOT NULL
    DROP VIEW vw_Bir2316;
GO

CREATE VIEW vw_Bir2316 AS
WITH PayYearBase AS (
    SELECT ps.EmployeeID, YEAR(pp.EndDate) AS PayYear,
           SUM(ps.GrossPay) AS GrossCompensation,
           SUM(ps.BasicPay) AS TotalBasicPay
    FROM Payslip ps
    JOIN Payroll_Period pp ON pp.PayrollPeriodID = ps.PayrollPeriodID
    WHERE ps.Status >= 1
    GROUP BY ps.EmployeeID, YEAR(pp.EndDate)
),
AllowanceSplit AS (
    SELECT ps.EmployeeID, YEAR(pp.EndDate) AS PayYear,
           SUM(CASE WHEN at.IsTaxable = 1 THEN pa.Amount ELSE 0 END) AS TaxableAllowances,
           SUM(CASE WHEN at.IsTaxable = 0 THEN pa.Amount ELSE 0 END) AS NonTaxableAllowances
    FROM Payroll_Allowance pa
    JOIN Payslip ps        ON ps.PayslipID       = pa.PayslipID
    JOIN Payroll_Period pp ON pp.PayrollPeriodID = ps.PayrollPeriodID
    JOIN Allowance_Type at ON at.AllowanceTypeID = pa.AllowanceTypeID
    WHERE ps.Status >= 1
    GROUP BY ps.EmployeeID, YEAR(pp.EndDate)
),
DeductionSplit AS (
    SELECT ps.EmployeeID, YEAR(pp.EndDate) AS PayYear,
           SUM(CASE WHEN dt.DeductionName IN ('SSS','PhilHealth','Pag-IBIG') THEN pd.Amount ELSE 0 END) AS MandatoryContributions,
           SUM(CASE WHEN dt.DeductionName = 'SSS'             THEN pd.Amount ELSE 0 END) AS SssContribution,
           SUM(CASE WHEN dt.DeductionName = 'PhilHealth'      THEN pd.Amount ELSE 0 END) AS PhilHealthContribution,
           SUM(CASE WHEN dt.DeductionName = 'Pag-IBIG'        THEN pd.Amount ELSE 0 END) AS PagIbigContribution,
           SUM(CASE WHEN dt.DeductionName = 'Withholding Tax' THEN pd.Amount ELSE 0 END) AS TaxWithheld
    FROM Payroll_Deduction pd
    JOIN Payslip ps        ON ps.PayslipID       = pd.PayslipID
    JOIN Payroll_Period pp ON pp.PayrollPeriodID = ps.PayrollPeriodID
    JOIN Deduction_Type dt ON dt.DeductionTypeID = pd.DeductionTypeID
    WHERE ps.Status >= 1
    GROUP BY ps.EmployeeID, YEAR(pp.EndDate)
),
Assembled AS (
    SELECT
        b.EmployeeID                                   AS EmployeeNo,
        CONCAT(ecd.LastName, ', ', ecd.FirstName)      AS EmployeeFullName,
        ecd.TinNo                                      AS TIN,
        ecd.PositionName                               AS Position,
        CONCAT_WS(', ',
            NULLIF(ecd.HouseBlockLot, ''), NULLIF(ecd.Street, ''),
            NULLIF(ecd.Barangay, ''), NULLIF(ecd.CityMunicipality, ''),
            NULLIF(ecd.Province, ''))                  AS RegisteredAddress,
        b.PayYear                                      AS PayYear,
        b.GrossCompensation                            AS GrossCompensation,
        ISNULL(a.TaxableAllowances, 0)                 AS TaxableAllowances,
        ISNULL(a.NonTaxableAllowances, 0)              AS NonTaxableAllowances,
        ISNULL(d.SssContribution, 0)                   AS SssContribution,
        ISNULL(d.PhilHealthContribution, 0)            AS PhilHealthContribution,
        ISNULL(d.PagIbigContribution, 0)               AS PagIbigContribution,
        ISNULL(d.MandatoryContributions, 0)            AS MandatoryContributions,
        ISNULL(t.ThirteenthMonthPay, 0)                AS ThirteenthMonthPay,
        CASE WHEN ISNULL(t.ThirteenthMonthPay,0) > 90000 THEN 90000
             ELSE ISNULL(t.ThirteenthMonthPay,0) END   AS ThirteenthMonthNonTaxable,
        CASE WHEN ISNULL(t.ThirteenthMonthPay,0) > 90000 THEN ISNULL(t.ThirteenthMonthPay,0) - 90000
             ELSE 0 END                                AS ThirteenthMonthTaxable,
        CAST(
            b.GrossCompensation
            - ISNULL(a.NonTaxableAllowances, 0)
            - ISNULL(d.MandatoryContributions, 0)
            + CASE WHEN ISNULL(t.ThirteenthMonthPay,0) > 90000 THEN ISNULL(t.ThirteenthMonthPay,0) - 90000 ELSE 0 END
        AS DECIMAL(18,2))                              AS TaxableCompensation,
        ISNULL(d.TaxWithheld, 0)                       AS TaxWithheld
    FROM PayYearBase b
    JOIN      vw_EmployeeCompleteDetails ecd ON ecd.EmployeeID = b.EmployeeID
    LEFT JOIN AllowanceSplit a ON a.EmployeeID = b.EmployeeID AND a.PayYear = b.PayYear
    LEFT JOIN DeductionSplit d ON d.EmployeeID = b.EmployeeID AND d.PayYear = b.PayYear
    LEFT JOIN vw_ThirteenthMonth t ON t.EmployeeNo = b.EmployeeID AND t.PayYear = b.PayYear
)
SELECT
    x.*,
    -- 2024 TRAIN ANNUAL tax due (brackets stated directly; = semi-monthly x 24)
    CAST(
        CASE
            WHEN x.TaxableCompensation <=  250000 THEN 0
            WHEN x.TaxableCompensation <=  400000 THEN (x.TaxableCompensation -  250000) * 0.15
            WHEN x.TaxableCompensation <=  800000 THEN   22500 + (x.TaxableCompensation -  400000) * 0.20
            WHEN x.TaxableCompensation <= 2000000 THEN  102500 + (x.TaxableCompensation -  800000) * 0.25
            WHEN x.TaxableCompensation <= 8000000 THEN  402500 + (x.TaxableCompensation - 2000000) * 0.30
            ELSE                                       2202500 + (x.TaxableCompensation - 8000000) * 0.35
        END
    AS DECIMAL(18,2))                                  AS TaxDue,
    CAST(
        x.TaxWithheld -
        CASE
            WHEN x.TaxableCompensation <=  250000 THEN 0
            WHEN x.TaxableCompensation <=  400000 THEN (x.TaxableCompensation -  250000) * 0.15
            WHEN x.TaxableCompensation <=  800000 THEN   22500 + (x.TaxableCompensation -  400000) * 0.20
            WHEN x.TaxableCompensation <= 2000000 THEN  102500 + (x.TaxableCompensation -  800000) * 0.25
            WHEN x.TaxableCompensation <= 8000000 THEN  402500 + (x.TaxableCompensation - 2000000) * 0.30
            ELSE                                       2202500 + (x.TaxableCompensation - 8000000) * 0.35
        END
    AS DECIMAL(18,2))                                  AS OverUnderWithheld
FROM Assembled x;
GO

PRINT '17 - vw_Bir2316 created.';
GO

-- =============================================================
-- vw_LeaveBalanceReport
--
-- Grain: (Employee x LeaveType x Year).  Backs the Leave Balance Report.
-- Thin display wrapper over vw_LeaveBalance (script 06) — adds employee,
-- department and leave-type NAMES for presentation. The balance math
-- (TotalEntitled - approved UsedDays = RemainingDays) stays in vw_LeaveBalance,
-- so this view never re-derives it.
-- =============================================================

IF OBJECT_ID('vw_LeaveBalanceReport', 'V') IS NOT NULL
    DROP VIEW vw_LeaveBalanceReport;
GO

CREATE VIEW vw_LeaveBalanceReport AS
SELECT
    lb.EmployeeID                                  AS EmployeeNo,
    CONCAT(e.LastName, ', ', e.FirstName)          AS EmployeeFullName,
    d.DepartmentName                               AS Department,
    lt.LeaveTypeName                               AS LeaveType,
    lb.[Year]                                      AS PayYear,
    lb.TotalEntitled                               AS EntitledDays,
    lb.UsedDays                                    AS UsedDays,
    lb.RemainingDays                               AS RemainingDays
FROM vw_LeaveBalance lb
JOIN      Employees   e  ON e.EmployeeID    = lb.EmployeeID
JOIN      Leave_Type  lt ON lt.LeaveTypeID  = lb.LeaveTypeID
LEFT JOIN Departments d  ON d.DepartmentID  = e.DepartmentID;
GO

PRINT '17 - vw_LeaveBalanceReport created.';
GO

-- =============================================================
-- vw_LoanLedgerReport
--
-- Grain: (Loan).  Backs the Loan Ledger / Loan Balance Report — one row per
-- employee loan with its current outstanding balance. Balance math lives in
-- vw_LoanBalance (script 06: TotalPayable - loan-tagged payroll deductions);
-- this view adds the loan terms (Employee_Loan) and display names.
--
-- StatusCode/StatusLabel from Employee_Loan.Status (0=Active,1=Fully Paid,
-- 2=Cancelled) drive the screen's status filter.
-- =============================================================

IF OBJECT_ID('vw_LoanLedgerReport', 'V') IS NOT NULL
    DROP VIEW vw_LoanLedgerReport;
GO

CREATE VIEW vw_LoanLedgerReport AS
SELECT
    el.LoanID                                      AS LoanID,
    el.EmployeeID                                  AS EmployeeNo,
    CONCAT(e.LastName, ', ', e.FirstName)          AS EmployeeFullName,
    d.DepartmentName                               AS Department,
    dt.DeductionName                               AS LoanType,
    el.PrincipalAmount                             AS Principal,
    lb.TotalPayable                                AS TotalPayable,
    lb.AmountPaid                                  AS AmountPaid,
    lb.OutstandingBalance                          AS OutstandingBalance,
    el.InstallmentAmount                           AS Installment,
    el.NumberOfTerms                               AS Terms,
    el.StartDate                                   AS StartDate,
    el.Status                                      AS StatusCode,
    CASE el.Status
        WHEN 0 THEN 'Active'
        WHEN 1 THEN 'Fully Paid'
        WHEN 2 THEN 'Cancelled'
        ELSE 'Unknown'
    END                                            AS StatusLabel
FROM Employee_Loan el
JOIN      vw_LoanBalance lb ON lb.LoanID        = el.LoanID
JOIN      Employees      e  ON e.EmployeeID     = el.EmployeeID
JOIN      Deduction_Type dt ON dt.DeductionTypeID = el.DeductionTypeID
LEFT JOIN Departments    d  ON d.DepartmentID   = e.DepartmentID;
GO

PRINT '17 - vw_LoanLedgerReport created.';
GO
