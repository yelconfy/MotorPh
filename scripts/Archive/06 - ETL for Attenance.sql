USE MotorPH_ERP;

-- 1. Temporary Staging (Matching the CSV columns exactly)
DROP TABLE IF EXISTS #StagingAttendance;
CREATE TABLE #StagingAttendance (
    EmpNo NVARCHAR(MAX),
    LName NVARCHAR(MAX), -- We load it into staging...
    FName NVARCHAR(MAX), -- ...but we won't move it to the final table.
    AttDate NVARCHAR(MAX),
    LogIn NVARCHAR(MAX),
    LogOut NVARCHAR(MAX)
);

-- 2. Bulk Load from CSV
BULK INSERT #StagingAttendance
FROM 'C:\Users\Rafael Organo\Work\Personal\Teacheron\Arielle Confesor\NetBeans\Prog2JavaChip\app\files\EmpAttendanceRecordCSV.csv'
WITH (
    FORMAT = 'CSV',
    FIRSTROW = 2,
    FIELDTERMINATOR = ',',
    ROWTERMINATOR = '\n'
);

-- 3. Populate final table (Selecting only the ID and the logs)
INSERT INTO Attendance (EmployeeID, AttendanceDate, TimeIn, TimeOut)
SELECT 
    CAST(CAST(EmpNo AS FLOAT) AS BIGINT), 
    CAST(AttDate AS DATE),
    TRY_CAST(LogIn AS TIME),
    TRY_CAST(LogOut AS TIME)
FROM #StagingAttendance
WHERE EmpNo IS NOT NULL;

-- 4. Cleanup
DROP TABLE #StagingAttendance;