-- =============================================================
-- 04 - Leave & Compensation Tables
-- MotorPH_ERP  |  Build phase 2-3 (depends on 01, 02, 03)
-- =============================================================
USE MotorPH_ERP;
GO

-- Standing per-employee recurring amounts (current-only).
CREATE TABLE Employee_Allowance (
    EmployeeAllowanceID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID          BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    AllowanceTypeID     INT    NOT NULL FOREIGN KEY REFERENCES Allowance_Type(AllowanceTypeID),
    Amount              DECIMAL(18,2) NOT NULL,
    Status              BIT    NOT NULL DEFAULT 1,
    CONSTRAINT UQ_EmployeeAllowance UNIQUE (EmployeeID, AllowanceTypeID)
);

CREATE TABLE Employee_Deduction (
    EmployeeDeductionID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID          BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    DeductionTypeID     INT    NOT NULL FOREIGN KEY REFERENCES Deduction_Type(DeductionTypeID),
    Amount              DECIMAL(18,2) NOT NULL,
    Status              BIT    NOT NULL DEFAULT 1,
    CONSTRAINT UQ_EmployeeDeduction UNIQUE (EmployeeID, DeductionTypeID)
);

-- Balance derived via vw_LoanBalance (no stored balance).
CREATE TABLE Employee_Loan (
    LoanID            BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID        BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    DeductionTypeID   INT    NOT NULL FOREIGN KEY REFERENCES Deduction_Type(DeductionTypeID),
    PrincipalAmount   DECIMAL(18,2) NOT NULL,
    InterestRate      DECIMAL(5,2)  NULL,
    TotalPayable      DECIMAL(18,2) NOT NULL,
    InstallmentAmount DECIMAL(18,2) NOT NULL,
    NumberOfTerms     INT           NOT NULL,
    StartDate         DATE          NOT NULL,
    Status            TINYINT       NOT NULL DEFAULT 0,   -- 0=Active,1=Fully Paid,2=Cancelled
    LastUpdatedBy     NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate   DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- Annual lump-sum grant. TotalEntitled = base + carried over.
CREATE TABLE Leave_Entitlement (
    EntitlementID   BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID      BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    LeaveTypeID     INT    NOT NULL FOREIGN KEY REFERENCES Leave_Type(LeaveTypeID),
    [Year]          SMALLINT NOT NULL,
    EntitledDays    DECIMAL(5,2) NOT NULL,
    CarriedOverDays DECIMAL(5,2) NOT NULL DEFAULT 0,
    TotalEntitled   AS (EntitledDays + CarriedOverDays),
    CONSTRAINT UQ_LeaveEntitlement UNIQUE (EmployeeID, LeaveTypeID, [Year])
);

-- Cross-year requests are split at the boundary (app logic). Only Status=1 counts.
CREATE TABLE Leave_Request (
    LeaveRequestID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID     BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    LeaveTypeID    INT    NOT NULL FOREIGN KEY REFERENCES Leave_Type(LeaveTypeID),
    StartDate      DATE   NOT NULL,
    EndDate        DATE   NOT NULL,
    NumberOfDays   DECIMAL(5,2) NOT NULL,
    Reason         NVARCHAR(255) NULL,
    Status         TINYINT NOT NULL DEFAULT 0,   -- 0=Pending,1=Approved,2=Rejected,3=Cancelled
    ActionedBy     BIGINT  NULL FOREIGN KEY REFERENCES Users(UserID),
    DateFiled      DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    DateActioned   DATETIME2 NULL
);

-- Start/end times support night-differential computation at payroll time.
CREATE TABLE Overtime_Request (
    OvertimeRequestID BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID        BIGINT NOT NULL FOREIGN KEY REFERENCES Employees(EmployeeID),
    OvertimeDate      DATE   NOT NULL,
    OvertimeStart     TIME   NOT NULL,
    OvertimeEnd       TIME   NOT NULL,
    Reason            NVARCHAR(255) NULL,
    Status            TINYINT NOT NULL DEFAULT 0,   -- 0=Pending,1=Approved,2=Rejected,3=Cancelled
    ActionedBy        BIGINT  NULL FOREIGN KEY REFERENCES Users(UserID),
    DateFiled         DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    DateActioned      DATETIME2 NULL
);
GO

PRINT '04 - Leave & compensation tables created.';
