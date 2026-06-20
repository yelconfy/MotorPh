-- =============================================================
-- 02 - Core Employee Tables
-- MotorPH_ERP  |  Build phase 1-2 (depends on 01)
-- =============================================================
USE MotorPH_ERP;
GO

CREATE TABLE Employees (
    EmployeeID       BIGINT IDENTITY(10001,1) PRIMARY KEY,
    LastName         NVARCHAR(100) NOT NULL,
    FirstName        NVARCHAR(100) NOT NULL,
    Birthday         DATE          NULL,
    Email            NVARCHAR(150) NULL,
    PhoneNo          NVARCHAR(50)  NULL,
    EmploymentStatus INT           NOT NULL DEFAULT 0,
    PositionID       BIGINT        NULL FOREIGN KEY REFERENCES Positions(PositionID),
    SupervisorID     BIGINT        NULL,
    DepartmentID     INT           NULL FOREIGN KEY REFERENCES Departments(DepartmentID),
    WorkScheduleID   INT           NULL FOREIGN KEY REFERENCES Work_Schedule(ScheduleID),
    DateHired        DATE          NULL,
    Status           BIT           NOT NULL DEFAULT 1,
    CONSTRAINT FK_Employee_Supervisor FOREIGN KEY (SupervisorID) REFERENCES Employees(EmployeeID)
);

CREATE TABLE StatutoryDetails (
    EmployeeID    BIGINT PRIMARY KEY FOREIGN KEY REFERENCES Employees(EmployeeID),
    SssNo         NVARCHAR(20) NULL,
    PhilHealthNo  NVARCHAR(20) NULL,
    TinNo         NVARCHAR(20) NULL,
    PagIbigNo     NVARCHAR(20) NULL
);

CREATE TABLE EmployeeAddresses (
    AddressID        BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID       BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    HouseBlockLot    NVARCHAR(255) NULL,
    Street           NVARCHAR(255) NULL,
    Barangay         NVARCHAR(100) NULL,
    CityMunicipality NVARCHAR(100) NULL,
    Province         NVARCHAR(100) NULL,
    ZipCode          NVARCHAR(10)  NULL
);

-- Versioned: one row per rate change; current = latest EffectiveDate.
CREATE TABLE EmployeeSalary (
    SalaryID      BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID    BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    BasicSalary   DECIMAL(18,2) NULL,
    HourlyRate    DECIMAL(18,2) NULL,
    EffectiveDate DATE NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Attendance (
    AttendanceID   BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID     BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    AttendanceDate DATE   NOT NULL,
    TimeIn         TIME   NULL,
    TimeOut        TIME   NULL
);
GO

PRINT '02 - Core employee tables created.';
