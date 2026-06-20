-- 1. SWITCH TO MASTER & RESET DB
USE master;

-- Force disconnect and drop
IF EXISTS (SELECT name FROM sys.databases WHERE name = N'MotorPH_ERP')
BEGIN
    ALTER DATABASE MotorPH_ERP SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE MotorPH_ERP;
END;

CREATE DATABASE MotorPH_ERP;
USE MotorPH_ERP;

-- 2. CREATE TABLES
CREATE TABLE Positions (
    PositionID BIGINT PRIMARY KEY IDENTITY(1,1),
    PositionName NVARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE Employees (
   	EmployeeID BIGINT PRIMARY KEY IDENTITY(10001, 1),
    LastName NVARCHAR(100) NOT NULL,
    FirstName NVARCHAR(100) NOT NULL,
    Birthday DATE,
    Email NVARCHAR(150),
    PhoneNo NVARCHAR(50),
    EmploymentStatus INT NOT NULL DEFAULT 0, 
    PositionID BIGINT FOREIGN KEY REFERENCES Positions(PositionID),
    SupervisorID BIGINT NULL, 
    DateHired DATE,
    Status BIT DEFAULT 1,
    CONSTRAINT FK_Employee_Supervisor FOREIGN KEY (SupervisorID) REFERENCES Employees(EmployeeID)
);

CREATE TABLE StatutoryDetails (
    EmployeeID BIGINT PRIMARY KEY FOREIGN KEY REFERENCES Employees(EmployeeID),
    SssNo NVARCHAR(20),
    PhilHealthNo NVARCHAR(20),
    TinNo NVARCHAR(20),
    PagIbigNo NVARCHAR(20)
);

CREATE TABLE EmployeeAddresses (
    AddressID BIGINT PRIMARY KEY IDENTITY(1,1),
    EmployeeID BIGINT FOREIGN KEY REFERENCES Employees(EmployeeID),
    HouseBlockLot NVARCHAR(255),
    Street NVARCHAR(255),
    Barangay NVARCHAR(100),
    CityMunicipality NVARCHAR(100),
    Province NVARCHAR(100),
    ZipCode NVARCHAR(10)
);

CREATE TABLE EmployeeSalary (
    SalaryID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    EmployeeID BIGINT FOREIGN KEY REFERENCES Employees(EmployeeID),
    BasicSalary DECIMAL(18,2) NULL,
    RiceSubsidy DECIMAL(18,2) NULL,
    PhoneAllowance DECIMAL(18,2) NULL,
    ClothingAllowance DECIMAL(18,2) NULL,
    HourlyRate DECIMAL(18,2) NULL,
    EffectiveDate DATE DEFAULT GETDATE() NULL,
    GrossSemiMonthlyRate AS (
        (ISNULL(BasicSalary, 0) + ISNULL(RiceSubsidy, 0) + 
         ISNULL(PhoneAllowance, 0) + ISNULL(ClothingAllowance, 0)) / 2
    ) 
);

-- 3. CREATE VIEW (At the bottom, separated by a semicolon)
-- In DBeaver, highlight the whole script and press Alt+X
CREATE VIEW vw_EmployeeCompleteDetails AS
SELECT 
    e.EmployeeID, e.LastName, e.FirstName, e.Birthday, e.Email, e.PhoneNo, 
    e.EmploymentStatus, e.DateHired, e.Status, e.SupervisorID,
    p.PositionID, p.PositionName,
    s.SssNo, s.PhilHealthNo, s.TinNo, s.PagIbigNo,
    a.AddressID, a.HouseBlockLot, a.Street, a.Barangay, a.CityMunicipality, a.Province, a.ZipCode,
    sal.SalaryID, sal.BasicSalary, sal.RiceSubsidy, sal.PhoneAllowance, sal.ClothingAllowance, 
    sal.HourlyRate, sal.GrossSemiMonthlyRate
FROM Employees e
LEFT JOIN Positions p ON e.PositionID = p.PositionID
LEFT JOIN StatutoryDetails s ON e.EmployeeID = s.EmployeeID
LEFT JOIN EmployeeAddresses a ON e.EmployeeID = a.EmployeeID
LEFT JOIN EmployeeSalary sal ON e.EmployeeID = sal.EmployeeID;