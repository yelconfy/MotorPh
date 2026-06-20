-- =============================================================
-- 08 - ETL for Credentials
-- MotorPH_ERP  |  Loads CredCSV.csv  (run AFTER 07)
-- Destinations: Departments (seed), Account_Role (seed),
--   Employees.DepartmentID (link), Users
--
-- *** PASSWORDS ARE NOT HASHED HERE ***
-- T-SQL cannot produce bcrypt/Argon2 hashes. This script inserts a
-- placeholder hash and sets MustChangePassword = 1. The application
-- MUST replace PasswordHash with a real hash (seeding routine / first login).
-- >>> UPDATE the BULK INSERT path before running. <<<
-- =============================================================
USE MotorPH_ERP;
GO

-- 1. Staging
DROP TABLE IF EXISTS #StagingUsers;
CREATE TABLE #StagingUsers (
    UserName NVARCHAR(MAX), Pwd NVARCHAR(MAX), DeptCode NVARCHAR(MAX),
    FName NVARCHAR(MAX), LName NVARCHAR(MAX)
);

-- 2. Load CSV
BULK INSERT #StagingUsers
FROM 'C:\Users\eyell\OneDrive\Documents\School\Arielle Master\MotorPh\app\files\CredCSV.csv'              -- <<< UPDATE PATH
WITH (FORMAT = 'CSV', FIRSTROW = 2, FIELDTERMINATOR = ',', ROWTERMINATOR = '\n');

-- 3. Seed Departments (PR / TK / HR)
INSERT INTO Departments (DepartmentCode, DepartmentName)
SELECT DISTINCT LTRIM(RTRIM(DeptCode)),
       CASE LTRIM(RTRIM(DeptCode))
            WHEN 'PR' THEN 'Payroll'
            WHEN 'TK' THEN 'Timekeeping'
            WHEN 'HR' THEN 'Human Resources' END
FROM #StagingUsers
WHERE LTRIM(RTRIM(DeptCode)) NOT IN (SELECT DepartmentCode FROM Departments);

-- 4. Seed Account_Role (legacy code -> role; RoleCode preserves the mapping)
INSERT INTO Account_Role (RoleName, RoleCode)
SELECT DISTINCT
       CASE LTRIM(RTRIM(DeptCode))
            WHEN 'PR' THEN 'Payroll Officer'
            WHEN 'TK' THEN 'Timekeeper'
            WHEN 'HR' THEN 'HR Admin' END,
       LTRIM(RTRIM(DeptCode))
FROM #StagingUsers
WHERE LTRIM(RTRIM(DeptCode)) NOT IN (SELECT RoleCode FROM Account_Role WHERE RoleCode IS NOT NULL);

-- 5. Link Employees -> Departments (match on name)
UPDATE E
SET E.DepartmentID = D.DepartmentID
FROM Employees E
JOIN #StagingUsers SU ON LTRIM(RTRIM(E.FirstName)) = LTRIM(RTRIM(SU.FName))
                     AND LTRIM(RTRIM(E.LastName))  = LTRIM(RTRIM(SU.LName))
JOIN Departments D ON LTRIM(RTRIM(SU.DeptCode)) = D.DepartmentCode;

-- 6. Users (placeholder hash; role via RoleCode; matched to employee by name)
INSERT INTO Users (EmployeeID, Username, PasswordHash, RoleID, MustChangePassword)
SELECT E.EmployeeID,
       LTRIM(RTRIM(SU.UserName)),
       'PLACEHOLDER_REHASH_IN_APP',          -- app must overwrite with real hash
       R.RoleID,
       1
FROM #StagingUsers SU
JOIN Employees E ON LTRIM(RTRIM(E.FirstName)) = LTRIM(RTRIM(SU.FName))
                AND LTRIM(RTRIM(E.LastName))  = LTRIM(RTRIM(SU.LName))
JOIN Account_Role R ON R.RoleCode = LTRIM(RTRIM(SU.DeptCode));

DROP TABLE #StagingUsers;
PRINT '08 - Credentials ETL complete.  NOTE: PasswordHash values are placeholders - rehash in the app.';
