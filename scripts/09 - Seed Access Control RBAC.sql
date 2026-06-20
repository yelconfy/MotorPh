-- =============================================================
-- Seed Access Control (RBAC)
-- MotorPH_ERP  |  Seeds Module, Permission, Role_Permission
-- Run order: AFTER "08 - ETL for Credentials" (needs Account_Role).
--            Independent of the attendance ETL and the SQL-login script.
--
-- Why this exists:
--   The schema CREATEs Module / Permission / Role_Permission but no ETL
--   step ever populates them. Until they hold grants, any role->module
--   lookup returns zero rows and the app would lock every module.
--
-- Idempotent: re-running inserts only what's missing (safe to repeat).
-- =============================================================
USE MotorPH_ERP;
GO

-- 1. Seed Module (stable ModuleCode = app key, ModuleName = display label)
INSERT INTO Module (ModuleCode, ModuleName)
SELECT v.Code, v.Name
FROM (VALUES
    ('PAYROLL',     'Payroll'),
    ('EMPMGMT',     'Employee Management'),
    ('TIMEKEEPING', 'Timekeeping')
) AS v(Code, Name)
WHERE NOT EXISTS (SELECT 1 FROM Module m WHERE m.ModuleCode = v.Code);
GO

-- 2. Seed Permission (the action verbs referenced by the schema comment)
INSERT INTO Permission (PermissionCode)
SELECT v.Code
FROM (VALUES ('VIEW'), ('ADD'), ('EDIT'), ('DELETE'), ('APPROVE')) AS v(Code)
WHERE NOT EXISTS (SELECT 1 FROM Permission p WHERE p.PermissionCode = v.Code);
GO

-- 3. Seed Role_Permission grants
--    Mapping (by RoleCode -> ModuleCode -> PermissionCode):
--      PR (Payroll Officer) -> PAYROLL     : VIEW, EDIT, APPROVE
--      HR (HR Admin)        -> EMPMGMT     : VIEW, ADD, EDIT, DELETE
--      TK (Timekeeper)      -> TIMEKEEPING : VIEW
--    Resolved to IDs via code lookups so it survives any IDENTITY values.
WITH Grants (RoleCode, ModuleCode, PermissionCode) AS (
    SELECT * FROM (VALUES
        ('PR', 'PAYROLL',     'VIEW'),
        ('PR', 'PAYROLL',     'EDIT'),
        ('PR', 'PAYROLL',     'APPROVE'),
        ('HR', 'EMPMGMT',     'VIEW'),
        ('HR', 'EMPMGMT',     'ADD'),
        ('HR', 'EMPMGMT',     'EDIT'),
        ('HR', 'EMPMGMT',     'DELETE'),
        ('TK', 'TIMEKEEPING', 'VIEW')
    ) AS g(RoleCode, ModuleCode, PermissionCode)
)
INSERT INTO Role_Permission (RoleID, PermissionID, ModuleID)
SELECT r.RoleID, p.PermissionID, m.ModuleID
FROM Grants g
JOIN Account_Role r ON r.RoleCode      = g.RoleCode
JOIN Module       m ON m.ModuleCode    = g.ModuleCode
JOIN Permission   p ON p.PermissionCode = g.PermissionCode
WHERE NOT EXISTS (
    SELECT 1 FROM Role_Permission rp
    WHERE rp.RoleID = r.RoleID
      AND rp.ModuleID = m.ModuleID
      AND rp.PermissionID = p.PermissionID
);
GO

PRINT 'RBAC seed complete: Module, Permission, Role_Permission populated.';
GO

-- 4. Verification — eyeball the resulting grant matrix
SELECT  r.RoleCode,
        r.RoleName,
        m.ModuleCode,
        m.ModuleName,
        p.PermissionCode
FROM Role_Permission rp
JOIN Account_Role r ON r.RoleID       = rp.RoleID
JOIN Module       m ON m.ModuleID     = rp.ModuleID
JOIN Permission   p ON p.PermissionID = rp.PermissionID
WHERE rp.Status = 1
ORDER BY r.RoleCode, m.ModuleName, p.PermissionCode;
GO
