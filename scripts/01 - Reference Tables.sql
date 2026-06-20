-- =============================================================
-- 01 - Reference Tables
-- MotorPH_ERP  |  Build phase 0 (no dependencies)
-- Run order: 01 -> 02 -> 03 -> 04 -> 05 -> 06
-- =============================================================
USE master;
GO

IF EXISTS (SELECT name FROM sys.databases WHERE name = N'MotorPH_ERP')
BEGIN
    ALTER DATABASE MotorPH_ERP SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE MotorPH_ERP;
END;
GO

CREATE DATABASE MotorPH_ERP;
GO

USE MotorPH_ERP;
GO

-- ---------- Org structure ----------
CREATE TABLE Positions (
    PositionID      BIGINT IDENTITY(1,1) PRIMARY KEY,
    PositionName    NVARCHAR(100) NOT NULL UNIQUE,
    LastUpdatedBy   NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE Departments (
    DepartmentID    INT IDENTITY(1,1) PRIMARY KEY,
    DepartmentCode  NVARCHAR(10)  NOT NULL UNIQUE,
    DepartmentName  NVARCHAR(100) NULL,
    LastUpdatedBy   NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ---------- Access control reference ----------
CREATE TABLE Account_Role (
    RoleID          INT IDENTITY(1,1) PRIMARY KEY,
    RoleName        NVARCHAR(50) NOT NULL UNIQUE,
    RoleCode        NVARCHAR(10) NULL UNIQUE,
    Status          BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE Permission (
    PermissionID    INT IDENTITY(1,1) PRIMARY KEY,
    PermissionCode  NVARCHAR(20) NOT NULL UNIQUE,   -- ADD, EDIT, DELETE, VIEW, APPROVE
    Status          BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE Module (
    ModuleID        INT IDENTITY(1,1) PRIMARY KEY,
    ModuleCode      NVARCHAR(20) NOT NULL UNIQUE,   -- stable app key
    ModuleName      NVARCHAR(50) NOT NULL UNIQUE,   -- display label
    Status          BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

-- ---------- Compensation reference ----------
CREATE TABLE Allowance_Type (
    AllowanceTypeID INT IDENTITY(1,1) PRIMARY KEY,
    AllowanceName   NVARCHAR(50) NOT NULL UNIQUE,
    IsTaxable       BIT          NOT NULL DEFAULT 0,
    IsRecurring     BIT          NOT NULL DEFAULT 1,
    Status          BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE Deduction_Type (
    DeductionTypeID INT IDENTITY(1,1) PRIMARY KEY,
    DeductionName   NVARCHAR(50) NOT NULL UNIQUE,
    Category        TINYINT      NOT NULL,          -- 0=Statutory, 1=Loan, 2=Voluntary
    Status          BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

-- PhilHealth + Pag-IBIG rates (versioned by EffectiveDate)
CREATE TABLE Contribution_Rate (
    ContributionRateID INT IDENTITY(1,1) PRIMARY KEY,
    DeductionTypeID    INT NOT NULL FOREIGN KEY REFERENCES Deduction_Type(DeductionTypeID),
    RangeFrom          DECIMAL(18,2) NOT NULL,
    RangeTo            DECIMAL(18,2) NULL,
    EmployeeRate       DECIMAL(5,2)  NOT NULL,
    EmployerRate       DECIMAL(5,2)  NOT NULL,
    IncomeFloor        DECIMAL(18,2) NULL,
    IncomeCeiling      DECIMAL(18,2) NULL,
    EffectiveDate      DATE          NOT NULL,
    Status             BIT           NOT NULL DEFAULT 1,
    LastUpdatedBy      NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate    DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- ---------- Leave reference ----------
CREATE TABLE Leave_Type (
    LeaveTypeID        INT IDENTITY(1,1) PRIMARY KEY,
    LeaveTypeName      NVARCHAR(50) NOT NULL UNIQUE,
    IsPaid             BIT          NOT NULL DEFAULT 1,
    DefaultDaysPerYear DECIMAL(5,2) NULL,
    CarryOverAllowed   BIT          NOT NULL DEFAULT 0,
    MaxCarryOverDays   DECIMAL(5,2) NULL,
    Status             BIT          NOT NULL DEFAULT 1,
    LastUpdatedBy      NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate    DATETIME2    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT CK_LeaveType_CarryOver CHECK (CarryOverAllowed = 1 OR MaxCarryOverDays IS NULL)
);

-- ---------- Time reference ----------
CREATE TABLE Holiday (
    HolidayID       INT IDENTITY(1,1) PRIMARY KEY,
    HolidayDate     DATE          NOT NULL,
    HolidayName     NVARCHAR(100) NOT NULL,
    HolidayType     TINYINT       NOT NULL,          -- 0=Regular, 1=Special Non-Working
    IsRecurring     BIT           NOT NULL DEFAULT 0,
    Status          BIT           NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT UQ_Holiday UNIQUE (HolidayDate, HolidayName)
);

CREATE TABLE Work_Schedule (
    ScheduleID         INT IDENTITY(1,1) PRIMARY KEY,
    ScheduleName       NVARCHAR(50) NOT NULL UNIQUE,
    TimeStart          TIME NOT NULL,
    TimeEnd            TIME NOT NULL,                 -- TimeEnd < TimeStart => crosses midnight (app logic)
    BreakMinutes       INT  NOT NULL DEFAULT 60,
    GracePeriodMinutes INT  NOT NULL DEFAULT 0,
    WorksMon           BIT  NOT NULL DEFAULT 1,
    WorksTue           BIT  NOT NULL DEFAULT 1,
    WorksWed           BIT  NOT NULL DEFAULT 1,
    WorksThu           BIT  NOT NULL DEFAULT 1,
    WorksFri           BIT  NOT NULL DEFAULT 1,
    WorksSat           BIT  NOT NULL DEFAULT 0,
    WorksSun           BIT  NOT NULL DEFAULT 0,
    Status             BIT  NOT NULL DEFAULT 1,
    LastUpdatedBy      NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate    DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

-- ---------- Statutory contribution tables (versioned by EffectiveDate) ----------
CREATE TABLE SSS_Contribution_Table (
    SssContributionID   INT IDENTITY(1,1) PRIMARY KEY,
    RangeFrom           DECIMAL(18,2) NOT NULL,
    RangeTo             DECIMAL(18,2) NULL,
    MonthlySalaryCredit DECIMAL(18,2) NULL,
    EmployeeShare       DECIMAL(18,2) NOT NULL,
    EmployerShare       DECIMAL(18,2) NOT NULL,
    EffectiveDate       DATE          NOT NULL,
    Status              BIT           NOT NULL DEFAULT 1,
    LastUpdatedBy       NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate     DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE WithholdingTax_Table (
    TaxBracketID    INT IDENTITY(1,1) PRIMARY KEY,
    PayFrequency    TINYINT       NOT NULL,          -- 0=Daily,1=Weekly,2=Semi-Monthly,3=Monthly
    RangeFrom       DECIMAL(18,2) NOT NULL,
    RangeTo         DECIMAL(18,2) NULL,
    BaseTax         DECIMAL(18,2) NOT NULL,
    RateOnExcess    DECIMAL(5,2)  NOT NULL,
    EffectiveDate   DATE          NOT NULL,
    Status          BIT           NOT NULL DEFAULT 1,
    LastUpdatedBy   NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
GO

PRINT '01 - Reference tables created.';
