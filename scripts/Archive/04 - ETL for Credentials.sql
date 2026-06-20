USE MotorPH_ERP;

-- 1. Staging
DROP TABLE IF EXISTS #StagingUsers;
CREATE TABLE #StagingUsers (
    UserName NVARCHAR(MAX), Pwd NVARCHAR(MAX), DeptCode NVARCHAR(MAX), FName NVARCHAR(MAX), LName NVARCHAR(MAX)
);

-- 2. Bulk Load
BULK INSERT #StagingUsers
FROM 'C:\Users\Rafael Organo\Work\Personal\Teacheron\Arielle Confesor\NetBeans\Prog2JavaChip\app\files\CredCSV.csv'
WITH (FORMAT = 'CSV', FIRSTROW = 2, FIELDTERMINATOR = ',', ROWTERMINATOR = '\n');

-- 3. Populate Departments
INSERT INTO Departments (DepartmentCode, DepartmentName)
SELECT DISTINCT LTRIM(RTRIM(DeptCode)), 
    CASE LTRIM(RTRIM(DeptCode)) 
        WHEN 'PR' THEN 'Payroll' 
        WHEN 'TK' THEN 'Timekeeping' 
        WHEN 'HR' THEN 'Human Resources' 
    END
FROM #StagingUsers;

-- 4. Link Employees to Departments
UPDATE E
SET E.DepartmentID = D.DepartmentID
FROM Employees E
JOIN #StagingUsers SU ON E.FirstName = SU.FName AND E.LastName = SU.LName
JOIN Departments D ON SU.DeptCode = D.DepartmentCode;

-- 5. Populate Users (Role gets the Code: PR, TK, or HR)
INSERT INTO Users (EmployeeID, Username, Password, Role)
SELECT E.EmployeeID, SU.UserName, SU.Pwd, SU.DeptCode
FROM #StagingUsers SU
JOIN Employees E ON LTRIM(RTRIM(E.FirstName)) = LTRIM(RTRIM(SU.FName)) 
                AND LTRIM(RTRIM(E.LastName)) = LTRIM(RTRIM(SU.LName));

DROP TABLE #StagingUsers;