-- =============================================================
-- 07 - ETL for Employees
-- MotorPH_ERP  |  Loads EmpDetailsCSV.csv  (run AFTER 01-06)
-- Destinations: Positions (seed), Allowance_Type (seed),
--   Work_Schedule (seed), Employees, EmployeeAddresses,
--   StatutoryDetails, EmployeeSalary, Employee_Allowance
-- >>> UPDATE the BULK INSERT path before running. <<<
-- =============================================================
USE MotorPH_ERP;
GO

-- 1. Seed the three allowance types (if not already present)
INSERT INTO Allowance_Type (AllowanceName, IsTaxable, IsRecurring)
SELECT v.Name, 0, 1
FROM (VALUES ('Rice Subsidy'), ('Phone Allowance'), ('Clothing Allowance')) AS v(Name)
WHERE NOT EXISTS (SELECT 1 FROM Allowance_Type a WHERE a.AllowanceName = v.Name);

-- 2. Staging (matches CSV columns exactly; Gross is loaded but NOT moved)
DROP TABLE IF EXISTS #StagingEmployees;
CREATE TABLE #StagingEmployees (
    EmpNo NVARCHAR(MAX), LName NVARCHAR(MAX), FName NVARCHAR(MAX), BDay NVARCHAR(MAX),
    Email NVARCHAR(MAX), Phone NVARCHAR(MAX), HouseNo NVARCHAR(MAX), Street NVARCHAR(MAX),
    Brgy NVARCHAR(MAX), City NVARCHAR(MAX), Province NVARCHAR(MAX), Zip NVARCHAR(MAX),
    SSS NVARCHAR(MAX), Philhealth NVARCHAR(MAX), TIN NVARCHAR(MAX), Pagibig NVARCHAR(MAX),
    EmpStatus NVARCHAR(MAX), Position NVARCHAR(MAX), Supervisor NVARCHAR(MAX),
    DateHired NVARCHAR(MAX), BasicSalary NVARCHAR(MAX), RiceSubsidy NVARCHAR(MAX),
    PhoneAllow NVARCHAR(MAX), ClothingAllow NVARCHAR(MAX), GrossRate NVARCHAR(MAX),
    HourlyRate NVARCHAR(MAX), RecordStatus NVARCHAR(MAX)
);

-- 3. Load CSV
BULK INSERT #StagingEmployees
FROM 'C:\Users\eyell\OneDrive\Documents\School\Arielle Master\MotorPh\app\files\EmpDetailsCSV.csv'         -- <<< UPDATE PATH
WITH (FORMAT = 'CSV', FIRSTROW = 2, FIELDTERMINATOR = ',', ROWTERMINATOR = '\n', TABLOCK);

-- 4. Seed Positions (distinct)
INSERT INTO Positions (PositionName)
SELECT DISTINCT LTRIM(RTRIM(Position))
FROM #StagingEmployees
WHERE Position IS NOT NULL
  AND LTRIM(RTRIM(Position)) NOT IN (SELECT PositionName FROM Positions);

-- 4b. Seed the default company work schedule  (MPH-13 / MPH-15)
--     Idempotent: ScheduleName is UNIQUE. Mon-Fri 08:00-17:00, 60-min break,
--     10-min grace. Remaining columns use their table defaults.
INSERT INTO Work_Schedule (ScheduleName, TimeStart, TimeEnd, BreakMinutes, GracePeriodMinutes)
SELECT 'Standard 8:00-17:00', '08:00:00', '17:00:00', 60, 10
WHERE NOT EXISTS (SELECT 1 FROM Work_Schedule WHERE ScheduleName = 'Standard 8:00-17:00');

-- 5. Employees (fixed IDs => IDENTITY_INSERT)
--    WorkScheduleID is now assigned to the standard schedule  (MPH-13).
SET IDENTITY_INSERT Employees ON;
INSERT INTO Employees (EmployeeID, LastName, FirstName, Birthday, Email, PhoneNo,
                       EmploymentStatus, PositionID, WorkScheduleID, DateHired, Status)
SELECT
    CAST(CAST(S.EmpNo AS FLOAT) AS BIGINT),
    S.LName, S.FName,
    TRY_CAST(S.BDay AS DATE),
    S.Email, S.Phone,
    CASE WHEN LTRIM(RTRIM(S.EmpStatus)) = 'Regular' THEN 1 ELSE 0 END,
    P.PositionID,
    (SELECT TOP 1 ScheduleID FROM Work_Schedule WHERE ScheduleName = 'Standard 8:00-17:00'),
    TRY_CAST(S.DateHired AS DATE),
    1
FROM #StagingEmployees S
LEFT JOIN Positions P ON LTRIM(RTRIM(S.Position)) = P.PositionName
WHERE S.EmpNo IS NOT NULL;
SET IDENTITY_INSERT Employees OFF;

-- 6. Addresses (ZIP came in as float -> strip trailing .0)
INSERT INTO EmployeeAddresses (EmployeeID, HouseBlockLot, Street, Barangay, CityMunicipality, Province, ZipCode)
SELECT CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
       HouseNo, Street, Brgy, City, Province,
       CASE WHEN Zip LIKE '%.0' THEN LEFT(Zip, LEN(Zip) - 2) ELSE LTRIM(RTRIM(Zip)) END
FROM #StagingEmployees WHERE EmpNo IS NOT NULL;

-- 7. Statutory — store BARE DIGITS (no dashes/spaces)  (MPH-08)
--    The app's masked fields re-add separators for display, so the DB holds
--    digits only. SSS/TIN arrive dashed in the CSV; PhilHealth/Pag-IBIG can
--    arrive as scientific-notation floats -> convert first, then strip.
INSERT INTO StatutoryDetails (EmployeeID, SssNo, PhilHealthNo, TinNo, PagIbigNo)
SELECT CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
       REPLACE(REPLACE(LTRIM(RTRIM(SSS)), '-', ''), ' ', ''),
       CASE WHEN Philhealth LIKE '%E+%'
            THEN CONVERT(NVARCHAR(20), CAST(CAST(Philhealth AS FLOAT) AS DECIMAL(20,0)))
            ELSE REPLACE(REPLACE(LTRIM(RTRIM(Philhealth)), '-', ''), ' ', '') END,
       REPLACE(REPLACE(LTRIM(RTRIM(TIN)), '-', ''), ' ', ''),
       CASE WHEN Pagibig LIKE '%E+%'
            THEN CONVERT(NVARCHAR(20), CAST(CAST(Pagibig AS FLOAT) AS DECIMAL(20,0)))
            ELSE REPLACE(REPLACE(LTRIM(RTRIM(Pagibig)), '-', ''), ' ', '') END
FROM #StagingEmployees WHERE EmpNo IS NOT NULL;

-- 8. Salary (basic + hourly only; allowances handled in step 9)
INSERT INTO EmployeeSalary (EmployeeID, BasicSalary, HourlyRate, EffectiveDate)
SELECT CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
       TRY_CAST(REPLACE(BasicSalary, ',', '') AS DECIMAL(18,2)),
       TRY_CAST(REPLACE(HourlyRate, ',', '') AS DECIMAL(18,2)),
       COALESCE(TRY_CAST(DateHired AS DATE), CAST(GETDATE() AS DATE))
FROM #StagingEmployees WHERE EmpNo IS NOT NULL;

-- 9. Allowances -> Employee_Allowance (one row per allowance type)
INSERT INTO Employee_Allowance (EmployeeID, AllowanceTypeID, Amount)
SELECT CAST(CAST(S.EmpNo AS FLOAT) AS BIGINT), A.AllowanceTypeID, TRY_CAST(REPLACE(S.RiceSubsidy, ',', '') AS DECIMAL(18,2))
FROM #StagingEmployees S JOIN Allowance_Type A ON A.AllowanceName = 'Rice Subsidy'
WHERE S.EmpNo IS NOT NULL AND TRY_CAST(REPLACE(S.RiceSubsidy, ',', '') AS DECIMAL(18,2)) IS NOT NULL;

INSERT INTO Employee_Allowance (EmployeeID, AllowanceTypeID, Amount)
SELECT CAST(CAST(S.EmpNo AS FLOAT) AS BIGINT), A.AllowanceTypeID, TRY_CAST(REPLACE(S.PhoneAllow, ',', '') AS DECIMAL(18,2))
FROM #StagingEmployees S JOIN Allowance_Type A ON A.AllowanceName = 'Phone Allowance'
WHERE S.EmpNo IS NOT NULL AND TRY_CAST(REPLACE(S.PhoneAllow, ',', '') AS DECIMAL(18,2)) IS NOT NULL;

INSERT INTO Employee_Allowance (EmployeeID, AllowanceTypeID, Amount)
SELECT CAST(CAST(S.EmpNo AS FLOAT) AS BIGINT), A.AllowanceTypeID, TRY_CAST(REPLACE(S.ClothingAllow, ',', '') AS DECIMAL(18,2))
FROM #StagingEmployees S JOIN Allowance_Type A ON A.AllowanceName = 'Clothing Allowance'
WHERE S.EmpNo IS NOT NULL AND TRY_CAST(REPLACE(S.ClothingAllow, ',', '') AS DECIMAL(18,2)) IS NOT NULL;

-- 10. Supervisor linkage (2nd pass: match "LastName, FirstName")
UPDATE E
SET E.SupervisorID = Sup.EmployeeID
FROM Employees E
JOIN #StagingEmployees St ON E.EmployeeID = CAST(CAST(St.EmpNo AS FLOAT) AS BIGINT)
JOIN Employees Sup ON (Sup.LastName + ', ' + Sup.FirstName) = LTRIM(RTRIM(St.Supervisor))
WHERE St.Supervisor IS NOT NULL AND St.Supervisor <> 'N/A';

DROP TABLE #StagingEmployees;
PRINT '07 - Employee ETL complete.';