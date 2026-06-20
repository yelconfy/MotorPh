-- =============================================================
-- 03 - Security & Audit Tables
-- MotorPH_ERP  |  Build phase 2-3 (depends on 01, 02)
-- =============================================================
USE MotorPH_ERP;
GO

-- One account per employee (UNIQUE EmployeeID); bcrypt/Argon2 hash in PasswordHash.
CREATE TABLE Users (
    UserID             BIGINT IDENTITY(1,1) PRIMARY KEY,
    EmployeeID         BIGINT NOT NULL UNIQUE FOREIGN KEY REFERENCES Employees(EmployeeID),
    Username           NVARCHAR(50)  NOT NULL UNIQUE,
    PasswordHash       NVARCHAR(255) NOT NULL,
    RoleID             INT           NOT NULL FOREIGN KEY REFERENCES Account_Role(RoleID),
    Status             BIT           NOT NULL DEFAULT 1,
    FailedLoginCount   INT           NOT NULL DEFAULT 0,
    LockoutUntil       DATETIME2     NULL,
    LastPasswordChange DATETIME2     NULL,
    MustChangePassword BIT           NOT NULL DEFAULT 0,
    LastUpdatedBy      NVARCHAR(50)  NOT NULL DEFAULT 'System',
    LastUpdatedDate    DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);

-- Role x Permission x Module grant matrix.
CREATE TABLE Role_Permission (
    RolePermissionID INT IDENTITY(1,1) PRIMARY KEY,
    RoleID           INT NOT NULL FOREIGN KEY REFERENCES Account_Role(RoleID),
    PermissionID     INT NOT NULL FOREIGN KEY REFERENCES Permission(PermissionID),
    ModuleID         INT NOT NULL FOREIGN KEY REFERENCES Module(ModuleID),
    Status           BIT NOT NULL DEFAULT 1,
    LastUpdatedBy    NVARCHAR(50) NOT NULL DEFAULT 'System',
    LastUpdatedDate  DATETIME2    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT UQ_RolePermission UNIQUE (RoleID, PermissionID, ModuleID)
);

-- Write-once password reuse trail.
CREATE TABLE Password_History (
    PasswordHistoryID BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserID            BIGINT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    PasswordHash      NVARCHAR(255) NOT NULL,
    ChangedBy         NVARCHAR(50)  NOT NULL DEFAULT 'System',
    ChangedDate       DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX IX_PasswordHistory_User ON Password_History (UserID, ChangedDate DESC);

-- Live sessions (single-session enforced in app). Store token HASH, not raw token.
CREATE TABLE User_Session (
    SessionID        BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserID           BIGINT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    SessionTokenHash NVARCHAR(255) NOT NULL UNIQUE,
    IssuedAt         DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    ExpiresAt        DATETIME2     NOT NULL,
    LastActivityAt   DATETIME2     NULL,
    IPAddress        NVARCHAR(45)  NULL,
    DeviceInfo       NVARCHAR(255) NULL,
    IsRevoked        BIT           NOT NULL DEFAULT 0,
    RevokedReason    NVARCHAR(100) NULL
);
CREATE INDEX IX_UserSession_User ON User_Session (UserID);

-- Append-only data-change trail. Username is a string stamp (no FK).
CREATE TABLE Audit_Log (
    AuditLogID      BIGINT IDENTITY(1,1) PRIMARY KEY,   -- clustered, append-only
    Username        NVARCHAR(50)  NOT NULL,
    TableName       NVARCHAR(100) NOT NULL,
    RecordID        NVARCHAR(50)  NOT NULL,
    ActionType      TINYINT       NOT NULL,             -- 0=Insert,1=Update,2=Delete
    OldValue        NVARCHAR(MAX) NULL,
    NewValue        NVARCHAR(MAX) NULL,
    ActionTimestamp DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX IX_AuditLog_Record ON Audit_Log (TableName, RecordID);
CREATE INDEX IX_AuditLog_Time   ON Audit_Log (ActionTimestamp);

-- Append-only authentication trail. Username is the attempted login (no FK).
CREATE TABLE User_Access_Log (
    AccessLogID     BIGINT IDENTITY(1,1) PRIMARY KEY,   -- clustered, append-only
    Username        NVARCHAR(50)  NOT NULL,
    LoginTimestamp  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    LogoutTimestamp DATETIME2     NULL,
    LoginStatus     TINYINT       NOT NULL,             -- 0=Failed,1=Success
    FailureReason   NVARCHAR(100) NULL,
    IPAddress       NVARCHAR(45)  NULL,
    DeviceInfo      NVARCHAR(255) NULL
);
CREATE INDEX IX_AccessLog_User   ON User_Access_Log (Username);
CREATE INDEX IX_AccessLog_Time   ON User_Access_Log (LoginTimestamp);
CREATE INDEX IX_AccessLog_Failed ON User_Access_Log (LoginStatus, IPAddress);
GO

PRINT '03 - Security & audit tables created.';
