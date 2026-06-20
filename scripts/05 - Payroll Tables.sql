-- =============================================================
-- 05 - Payroll Tables
-- MotorPH_ERP  |  Build phase 4 (depends on 01, 02, 03)
-- Note: finalize-lock (read-only when Status >= 1) is enforced in APP LOGIC.
-- =============================================================
USE MotorPH_ERP;
GO

CREATE TABLE Payroll_Period (
    PayrollPeriodID BIGINT IDENTITY(1,1) PRIMARY KEY,
    PeriodName      NVARCHAR(50) NULL,
    StartDate       DATE NOT NULL,
    EndDate         DATE NOT NULL,
    PayDate         DATE NULL,
    Status          TINYINT NOT NULL DEFAULT 0     -- 0=Open,1=Processing,2=Closed,3=Paid
);

-- Snapshot totals written at finalization; one payslip per employee per period.
CREATE TABLE Payslip (
    PayslipID        BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID       BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    PayrollPeriodID  BIGINT NOT NULL FOREIGN KEY REFERENCES Payroll_Period(PayrollPeriodID),
    BasicPay         DECIMAL(18,2) NULL,
    TotalAllowances  DECIMAL(18,2) NULL,
    GrossPay         DECIMAL(18,2) NULL,
    TotalDeductions  DECIMAL(18,2) NULL,
    TotalAdjustments DECIMAL(18,2) NULL,
    NetPay           DECIMAL(18,2) NULL,
    DaysWorked       DECIMAL(9,2)  NULL,
    HoursWorked      DECIMAL(9,2)  NULL,
    Status           TINYINT       NOT NULL DEFAULT 0,   -- 0=Draft,1=Finalized,2=Paid
    GeneratedBy      BIGINT        NULL FOREIGN KEY REFERENCES Users(UserID),
    GeneratedDate    DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT UQ_Payslip UNIQUE (EmployeeID, PayrollPeriodID)
);

CREATE TABLE Payroll_Allowance (
    PayrollAllowanceID BIGINT IDENTITY(1,1) PRIMARY KEY,
    PayslipID          BIGINT NOT NULL FOREIGN KEY REFERENCES Payslip(PayslipID),
    AllowanceTypeID    INT    NOT NULL FOREIGN KEY REFERENCES Allowance_Type(AllowanceTypeID),
    Amount             DECIMAL(18,2) NOT NULL,
    Remarks            NVARCHAR(255) NULL,
    CONSTRAINT UQ_PayrollAllowance UNIQUE (PayslipID, AllowanceTypeID)
);

-- Polymorphic source: SourceType + SourceID (no FK on SourceID).
CREATE TABLE Payroll_Deduction (
    PayrollDeductionID BIGINT IDENTITY(1,1) PRIMARY KEY,
    PayslipID          BIGINT NOT NULL FOREIGN KEY REFERENCES Payslip(PayslipID),
    DeductionTypeID    INT    NOT NULL FOREIGN KEY REFERENCES Deduction_Type(DeductionTypeID),
    SourceType         TINYINT NOT NULL DEFAULT 0,   -- 0=Manual,1=Statutory,2=Loan,3=Voluntary
    SourceID           BIGINT  NULL,                 -- PK in the table named by SourceType
    Amount             DECIMAL(18,2) NOT NULL,
    Remarks            NVARCHAR(255) NULL
);
-- Single-instance deductions (statutory/manual) cannot duplicate on one payslip:
CREATE UNIQUE INDEX UX_PayrollDeduction_Single
    ON Payroll_Deduction (PayslipID, DeductionTypeID)
    WHERE SourceID IS NULL;
-- A given source record (e.g. a loan) cannot be charged twice on one payslip:
CREATE UNIQUE INDEX UX_PayrollDeduction_Source
    ON Payroll_Deduction (PayslipID, SourceType, SourceID)
    WHERE SourceID IS NOT NULL;

-- Maker-checker (CreatedBy <> ApprovedBy enforced in app). CorrectsPayslipID = retro trail.
CREATE TABLE Payroll_Adjustment (
    PayrollAdjustmentID BIGINT IDENTITY(1,1) PRIMARY KEY,
    PayslipID           BIGINT NOT NULL FOREIGN KEY REFERENCES Payslip(PayslipID),
    CorrectsPayslipID   BIGINT NULL FOREIGN KEY REFERENCES Payslip(PayslipID),
    AdjustmentType      TINYINT NOT NULL,            -- 0=Addition,1=Deduction
    Amount              DECIMAL(18,2) NOT NULL,
    Reason              NVARCHAR(255) NOT NULL,
    CreatedBy           BIGINT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    ApprovedBy          BIGINT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    AdjustmentDate      DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
GO

PRINT '05 - Payroll tables created.';
