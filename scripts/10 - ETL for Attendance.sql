-- =============================================================
-- 09 - ETL for Attendance
-- MotorPH_ERP  |  Loads EmpAttendanceRecordCSV.csv  (run AFTER 07)
-- Destination: Attendance  (Last/First name columns are discarded)
-- >>> UPDATE the BULK INSERT path before running. <<<
-- =============================================================
USE MotorPH_ERP;
GO

-- 1. Staging (Last/First loaded but not moved - EmployeeID is the key)
DROP TABLE IF EXISTS #StagingAttendance;
CREATE TABLE #StagingAttendance (
    EmpNo NVARCHAR(MAX), LName NVARCHAR(MAX), FName NVARCHAR(MAX),
    AttDate NVARCHAR(MAX), LogIn NVARCHAR(MAX), LogOut NVARCHAR(MAX)
);

-- 2. Load CSV
BULK INSERT #StagingAttendance
FROM 'C:\Users\eyell\OneDrive\Documents\School\Arielle Master\MotorPh\app\files\EmpAttendanceRecordCSV.csv'   -- <<< UPDATE PATH
WITH (FORMAT = 'CSV', FIRSTROW = 2, FIELDTERMINATOR = ',', ROWTERMINATOR = '\n');

-- 3. Populate Attendance
INSERT INTO Attendance (EmployeeID, AttendanceDate, TimeIn, TimeOut)
SELECT
    CAST(CAST(EmpNo AS FLOAT) AS BIGINT),
    TRY_CAST(AttDate AS DATE),
    TRY_CAST(LogIn  AS TIME),
    TRY_CAST(LogOut AS TIME)
FROM #StagingAttendance
WHERE EmpNo IS NOT NULL;

DROP TABLE #StagingAttendance;
PRINT '09 - Attendance ETL complete.';
