USE MotorPH_ERP;

-- 1. Clean Slate (Respecting Foreign Key constraints)
DELETE FROM EmployeeAddresses;
DELETE FROM EmployeeSalary;
DELETE FROM StatutoryDetails;
DELETE FROM Employees;
-- Note: Positions are usually kept, but added here to ensure sync if needed
DELETE FROM Positions WHERE PositionID NOT IN (SELECT PositionID FROM Employees);

-- 2. Prepare Staging
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
FROM 'C:\Users\Rafael Organo\Work\Personal\Teacheron\Arielle Confesor\NetBeans\Prog2JavaChip\app\files\EmpDetailsCSV.csv'
WITH (
    FORMAT = 'CSV',
    FIRSTROW = 2,
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n',
    TABLOCK
);

-- 4. Populate Positions
INSERT INTO Positions (PositionName)
SELECT DISTINCT LTRIM(RTRIM(Position)) 
FROM #StagingEmployees 
WHERE Position IS NOT NULL 
AND LTRIM(RTRIM(Position)) NOT IN (SELECT PositionName FROM Positions);

-- 5. Populate Employees (Manual ID override needed for IDENTITY columns)
SET IDENTITY_INSERT Employees ON; -- <--- ADD THIS

INSERT INTO Employees (EmployeeID, LastName, FirstName, Birthday, Email, PhoneNo, EmploymentStatus, PositionID, DateHired, Status)
SELECT 
    CAST(CAST(S.EmpNo AS FLOAT) AS BIGINT),
    S.LName, 
    S.FName,
    CAST(S.BDay AS DATE),
    S.Email, 
    S.Phone, 
    CASE WHEN LTRIM(RTRIM(S.EmpStatus)) = 'Regular' THEN 1 ELSE 0 END,
    P.PositionID,
    CAST(S.DateHired AS DATE),
    1 
FROM #StagingEmployees S
LEFT JOIN Positions P ON LTRIM(RTRIM(S.Position)) = LTRIM(RTRIM(P.PositionName))
WHERE S.EmpNo IS NOT NULL;

SET IDENTITY_INSERT Employees OFF; -- <--- AND ADD THIS

-- 6. Populate Addresses
INSERT INTO EmployeeAddresses (EmployeeID, HouseBlockLot, Street, Barangay, CityMunicipality, Province, ZipCode)
SELECT 
    CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
    HouseNo, Street, Brgy, City, Province, Zip
FROM #StagingEmployees;

-- 7. Populate Statutory (Mapped to SssNo, PhilHealthNo, TinNo, PagIbigNo)
INSERT INTO StatutoryDetails (EmployeeID, SssNo, PhilHealthNo, TinNo, PagIbigNo)
SELECT 
    CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
    LTRIM(RTRIM(SSS)), 
    CASE 
        WHEN Philhealth LIKE '%E+%' THEN CAST(CAST(Philhealth AS FLOAT) AS DECIMAL(20,0)) 
        ELSE LTRIM(RTRIM(Philhealth)) 
    END,
    LTRIM(RTRIM(TIN)), 
    CASE 
        WHEN Pagibig LIKE '%E+%' THEN CAST(CAST(Pagibig AS FLOAT) AS DECIMAL(20,0)) 
        ELSE LTRIM(RTRIM(Pagibig)) 
    END
FROM #StagingEmployees;

-- 8. Populate Salary
INSERT INTO EmployeeSalary (EmployeeID, BasicSalary, RiceSubsidy, PhoneAllowance, ClothingAllowance, HourlyRate)
SELECT 
    CAST(CAST(EmpNo AS FLOAT) AS BIGINT), 
    CAST(REPLACE(BasicSalary, ',', '') AS DECIMAL(18,2)),
    CAST(REPLACE(RiceSubsidy, ',', '') AS DECIMAL(18,2)),
    CAST(REPLACE(PhoneAllow, ',', '') AS DECIMAL(18,2)),
    CAST(REPLACE(ClothingAllow, ',', '') AS DECIMAL(18,2)),
    CAST(REPLACE(HourlyRate, ',', '') AS DECIMAL(18,2))
FROM #StagingEmployees;

-- 9. Update Supervisor Linkage
UPDATE E
SET E.SupervisorID = S.EmployeeID
FROM Employees E
JOIN #StagingEmployees St ON E.EmployeeID = CAST(CAST(St.EmpNo AS FLOAT) AS BIGINT)
JOIN Employees S ON (S.LastName + ', ' + S.FirstName) = LTRIM(RTRIM(St.Supervisor))
WHERE St.Supervisor <> 'N/A';

-- 10. Clean up
DROP TABLE #StagingEmployees;
PRINT 'ETL Refresh Successful.';