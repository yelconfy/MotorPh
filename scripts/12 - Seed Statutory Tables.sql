-- =============================================================
-- 12 - Seed Statutory Tables
-- MotorPH_ERP  |  Seed phase (depends on 01 Reference Tables)
--
-- Populates the statutory reference tables that ship EMPTY from schema
-- creation, plus the four statutory Deduction_Type rows. Without this,
-- StatutoryDAO returns zero rows and all government deductions compute to 0.
--
-- BASIS: Philippine statutory rates EFFECTIVE 2024 (matches the sample
--        attendance/payslip data). Figures verified against:
--          SSS  - 14% total (EE 4.5% / ER 9.5%), MSC 4,000-30,000
--          PHIC - 5% (50/50), floor 10,000 / ceiling 100,000  [eff 2024-01-01]
--          HDMF - <=1,500 EE 1%; >1,500 EE 2%; MFS cap 10,000  [eff 2024-02-01, Circ.460]
--          BIR  - Semi-monthly TRAIN table (annual brackets / 24), eff 2023-onward
--
-- NOTE on SSS EmployerShare: stores the SS+WISP employer portion (9.5% x MSC).
--      Employees' Compensation (EC, employer-only: P10/P30) is intentionally
--      excluded because it is never an employee payslip deduction.
--
-- Run order: ... 09 RBAC seed -> 10 ETL Attendance -> 11 SQL Login -> 12 (this)
-- (12 only reads/writes reference data; placing it after 11 is safe.)
-- =============================================================
USE MotorPH_ERP;
GO

SET NOCOUNT ON;

-- -------------------------------------------------------------
-- 1) Deduction_Type : four canonical statutory rows (idempotent)
-- -------------------------------------------------------------
MERGE Deduction_Type AS t
USING (VALUES
    ('SSS',             0),
    ('PhilHealth',      0),
    ('Pag-IBIG',        0),
    ('Withholding Tax', 0)
) AS s(DeductionName, Category)
   ON t.DeductionName = s.DeductionName
WHEN NOT MATCHED THEN
    INSERT (DeductionName, Category, Status) VALUES (s.DeductionName, s.Category, 1);
PRINT '12 - Deduction_Type statutory rows ensured.';
GO

-- -------------------------------------------------------------
-- 2) SSS_Contribution_Table : EE 4.5% / ER 9.5% of MSC (2024)
--    Lookup rule (app): pick row where MonthlyBasic between RangeFrom/RangeTo
--    (RangeTo NULL = open top); deduct EmployeeShare.
-- -------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM SSS_Contribution_Table WHERE EffectiveDate = '2024-01-01')
BEGIN
INSERT INTO SSS_Contribution_Table
    (RangeFrom, RangeTo, MonthlySalaryCredit, EmployeeShare, EmployerShare, EffectiveDate, Status)
VALUES
    (0.00, 4249.99, 4000.00, 180.00, 380.00, '2024-01-01', 1),
    (4250.00, 4749.99, 4500.00, 202.50, 427.50, '2024-01-01', 1),
    (4750.00, 5249.99, 5000.00, 225.00, 475.00, '2024-01-01', 1),
    (5250.00, 5749.99, 5500.00, 247.50, 522.50, '2024-01-01', 1),
    (5750.00, 6249.99, 6000.00, 270.00, 570.00, '2024-01-01', 1),
    (6250.00, 6749.99, 6500.00, 292.50, 617.50, '2024-01-01', 1),
    (6750.00, 7249.99, 7000.00, 315.00, 665.00, '2024-01-01', 1),
    (7250.00, 7749.99, 7500.00, 337.50, 712.50, '2024-01-01', 1),
    (7750.00, 8249.99, 8000.00, 360.00, 760.00, '2024-01-01', 1),
    (8250.00, 8749.99, 8500.00, 382.50, 807.50, '2024-01-01', 1),
    (8750.00, 9249.99, 9000.00, 405.00, 855.00, '2024-01-01', 1),
    (9250.00, 9749.99, 9500.00, 427.50, 902.50, '2024-01-01', 1),
    (9750.00, 10249.99, 10000.00, 450.00, 950.00, '2024-01-01', 1),
    (10250.00, 10749.99, 10500.00, 472.50, 997.50, '2024-01-01', 1),
    (10750.00, 11249.99, 11000.00, 495.00, 1045.00, '2024-01-01', 1),
    (11250.00, 11749.99, 11500.00, 517.50, 1092.50, '2024-01-01', 1),
    (11750.00, 12249.99, 12000.00, 540.00, 1140.00, '2024-01-01', 1),
    (12250.00, 12749.99, 12500.00, 562.50, 1187.50, '2024-01-01', 1),
    (12750.00, 13249.99, 13000.00, 585.00, 1235.00, '2024-01-01', 1),
    (13250.00, 13749.99, 13500.00, 607.50, 1282.50, '2024-01-01', 1),
    (13750.00, 14249.99, 14000.00, 630.00, 1330.00, '2024-01-01', 1),
    (14250.00, 14749.99, 14500.00, 652.50, 1377.50, '2024-01-01', 1),
    (14750.00, 15249.99, 15000.00, 675.00, 1425.00, '2024-01-01', 1),
    (15250.00, 15749.99, 15500.00, 697.50, 1472.50, '2024-01-01', 1),
    (15750.00, 16249.99, 16000.00, 720.00, 1520.00, '2024-01-01', 1),
    (16250.00, 16749.99, 16500.00, 742.50, 1567.50, '2024-01-01', 1),
    (16750.00, 17249.99, 17000.00, 765.00, 1615.00, '2024-01-01', 1),
    (17250.00, 17749.99, 17500.00, 787.50, 1662.50, '2024-01-01', 1),
    (17750.00, 18249.99, 18000.00, 810.00, 1710.00, '2024-01-01', 1),
    (18250.00, 18749.99, 18500.00, 832.50, 1757.50, '2024-01-01', 1),
    (18750.00, 19249.99, 19000.00, 855.00, 1805.00, '2024-01-01', 1),
    (19250.00, 19749.99, 19500.00, 877.50, 1852.50, '2024-01-01', 1),
    (19750.00, 20249.99, 20000.00, 900.00, 1900.00, '2024-01-01', 1),
    (20250.00, 20749.99, 20500.00, 922.50, 1947.50, '2024-01-01', 1),
    (20750.00, 21249.99, 21000.00, 945.00, 1995.00, '2024-01-01', 1),
    (21250.00, 21749.99, 21500.00, 967.50, 2042.50, '2024-01-01', 1),
    (21750.00, 22249.99, 22000.00, 990.00, 2090.00, '2024-01-01', 1),
    (22250.00, 22749.99, 22500.00, 1012.50, 2137.50, '2024-01-01', 1),
    (22750.00, 23249.99, 23000.00, 1035.00, 2185.00, '2024-01-01', 1),
    (23250.00, 23749.99, 23500.00, 1057.50, 2232.50, '2024-01-01', 1),
    (23750.00, 24249.99, 24000.00, 1080.00, 2280.00, '2024-01-01', 1),
    (24250.00, 24749.99, 24500.00, 1102.50, 2327.50, '2024-01-01', 1),
    (24750.00, 25249.99, 25000.00, 1125.00, 2375.00, '2024-01-01', 1),
    (25250.00, 25749.99, 25500.00, 1147.50, 2422.50, '2024-01-01', 1),
    (25750.00, 26249.99, 26000.00, 1170.00, 2470.00, '2024-01-01', 1),
    (26250.00, 26749.99, 26500.00, 1192.50, 2517.50, '2024-01-01', 1),
    (26750.00, 27249.99, 27000.00, 1215.00, 2565.00, '2024-01-01', 1),
    (27250.00, 27749.99, 27500.00, 1237.50, 2612.50, '2024-01-01', 1),
    (27750.00, 28249.99, 28000.00, 1260.00, 2660.00, '2024-01-01', 1),
    (28250.00, 28749.99, 28500.00, 1282.50, 2707.50, '2024-01-01', 1),
    (28750.00, 29249.99, 29000.00, 1305.00, 2755.00, '2024-01-01', 1),
    (29250.00, 29749.99, 29500.00, 1327.50, 2802.50, '2024-01-01', 1),
    (29750.00, NULL, 30000.00, 1350.00, 2850.00, '2024-01-01', 1);
END
PRINT '12 - SSS_Contribution_Table seeded (2024).';
GO

-- -------------------------------------------------------------
-- 3) Contribution_Rate : PhilHealth + Pag-IBIG (FK -> Deduction_Type)
--    Lookup rule (app): match MonthlyBasic to RangeFrom/RangeTo, clamp the
--    base to [IncomeFloor, IncomeCeiling], deduct EmployeeRate x base.
-- -------------------------------------------------------------
-- No session variables: IDs are resolved inline so the script is safe
-- regardless of how the SQL client batches statements.

-- PhilHealth 2024: single band, 2.5% EE / 2.5% ER, floor 10k, ceiling 100k
IF NOT EXISTS (
    SELECT 1 FROM Contribution_Rate cr
    JOIN Deduction_Type dt ON dt.DeductionTypeID = cr.DeductionTypeID
    WHERE dt.DeductionName = 'PhilHealth' AND cr.EffectiveDate = '2024-01-01')
INSERT INTO Contribution_Rate
    (DeductionTypeID, RangeFrom, RangeTo, EmployeeRate, EmployerRate, IncomeFloor, IncomeCeiling, EffectiveDate, Status)
SELECT dt.DeductionTypeID, 0.00, NULL, 0.025, 0.025, 10000.00, 100000.00, '2024-01-01', 1
FROM Deduction_Type dt
WHERE dt.DeductionName = 'PhilHealth';

-- Pag-IBIG 2024 (Circular 460, eff Feb 2024): MFS cap 10k
--   band 1: MFS <= 1,500  -> EE 1%, ER 2%
--   band 2: MFS  > 1,500  -> EE 2%, ER 2%
IF NOT EXISTS (
    SELECT 1 FROM Contribution_Rate cr
    JOIN Deduction_Type dt ON dt.DeductionTypeID = cr.DeductionTypeID
    WHERE dt.DeductionName = 'Pag-IBIG' AND cr.EffectiveDate = '2024-02-01')
BEGIN
    INSERT INTO Contribution_Rate
        (DeductionTypeID, RangeFrom, RangeTo, EmployeeRate, EmployerRate, IncomeFloor, IncomeCeiling, EffectiveDate, Status)
    SELECT dt.DeductionTypeID, 0.00, 1500.00, 0.01, 0.02, NULL, 10000.00, '2024-02-01', 1
    FROM Deduction_Type dt WHERE dt.DeductionName = 'Pag-IBIG';

    INSERT INTO Contribution_Rate
        (DeductionTypeID, RangeFrom, RangeTo, EmployeeRate, EmployerRate, IncomeFloor, IncomeCeiling, EffectiveDate, Status)
    SELECT dt.DeductionTypeID, 1500.01, NULL, 0.02, 0.02, NULL, 10000.00, '2024-02-01', 1
    FROM Deduction_Type dt WHERE dt.DeductionName = 'Pag-IBIG';
END
PRINT '12 - Contribution_Rate seeded (PhilHealth + Pag-IBIG, 2024).';
GO

-- -------------------------------------------------------------
-- 4) WithholdingTax_Table : SEMI-MONTHLY (PayFrequency = 2)
--    TRAIN annual brackets / 24, effective 2023-onward (applies to 2024).
--    Base amounts derived arithmetically (the commonly-circulated table
--    that pairs 2023 RATES with 2018-2022 BASE figures is internally
--    inconsistent; these are recomputed correctly).
--    Lookup rule (app): pick row where TaxableIncome >= RangeFrom (highest
--    qualifying); tax = BaseTax + RateOnExcess x (TaxableIncome - RangeFrom).
-- -------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM WithholdingTax_Table WHERE PayFrequency = 2 AND EffectiveDate = '2023-01-01')
INSERT INTO WithholdingTax_Table
    (PayFrequency, RangeFrom, RangeTo, BaseTax, RateOnExcess, EffectiveDate, Status)
VALUES
    (2,      0.00,   10416.67,     0.00, 0.00, '2023-01-01', 1),
    (2,  10416.67,   16666.67,     0.00, 0.15, '2023-01-01', 1),
    (2,  16666.67,   33333.33,   937.50, 0.20, '2023-01-01', 1),
    (2,  33333.33,   83333.33,  4270.83, 0.25, '2023-01-01', 1),
    (2,  83333.33,  333333.33, 16770.83, 0.30, '2023-01-01', 1),
    (2, 333333.33,       NULL, 91770.83, 0.35, '2023-01-01', 1);
PRINT '12 - WithholdingTax_Table seeded (semi-monthly, 2023-onward).';
GO

SET NOCOUNT OFF;
PRINT '12 - Statutory seed complete.';
GO