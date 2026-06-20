USE MotorPH_ERP;

-- 1. Drop existing Attendance table
DROP TABLE IF EXISTS Attendance;

-- 2. Create optimized Attendance Table
CREATE TABLE Attendance (
    AttendanceID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID BIGINT NOT NULL,
    AttendanceDate DATE NOT NULL,
    TimeIn TIME NULL,
    TimeOut TIME NULL,
    CONSTRAINT FK_Attendance_Employees FOREIGN KEY (EmployeeID) REFERENCES Employees(EmployeeID)
);