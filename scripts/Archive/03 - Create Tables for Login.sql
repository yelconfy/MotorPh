USE MotorPH_ERP;

-- 1. Drop tables in order of dependency (Users first because it links to Employees)
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Departments;

-- 2. Create Departments table
CREATE TABLE Departments (
    DepartmentID INT IDENTITY(1,1) PRIMARY KEY,
    DepartmentCode NVARCHAR(10) NOT NULL UNIQUE,
    DepartmentName NVARCHAR(100) NULL
);

-- 3. Add column to Employees if it doesn't exist (Simple check)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Employees') AND name = 'DepartmentID')
    ALTER TABLE Employees ADD DepartmentID INT;

-- 4. Create Users table (Links to Employees)
CREATE TABLE Users (
    UserID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID BIGINT NOT NULL,
    Username NVARCHAR(50) NOT NULL UNIQUE,
    Password NVARCHAR(255) NOT NULL, 
    Role NVARCHAR(10) NOT NULL,
    Status BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Users_Employees FOREIGN KEY (EmployeeID) REFERENCES Employees(EmployeeID)
);