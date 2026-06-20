-- =============================================================
-- 10 - Create SQL Login
-- MotorPH_ERP  |  Run any time AFTER 01 (database must exist).
-- Creates/resets the application login 'yel' and grants db_owner.
--
-- SECURITY NOTES (see IAM_Module.md / DB_Centralization_Plan.md):
--   * This is the shared application credential. Anyone holding it can
--     bypass app-level IAM, so keep the connection string in protected,
--     non-committed config.
--   * Change 'Password1' to a strong secret before any real use.
--   * On Azure SQL (future), server-level CREATE LOGIN differs - use
--     contained database users or Entra (Azure AD) instead.
-- =============================================================
USE master;
GO

-- 1. Kill sessions, drop user + login if present, then recreate the login
BEGIN
    DECLARE @kill VARCHAR(8000) = '';
    SELECT @kill = @kill + 'KILL ' + CONVERT(VARCHAR(5), session_id) + ';'
    FROM sys.dm_exec_sessions
    WHERE login_name = 'yel';
    EXEC(@kill);

    IF EXISTS (SELECT 1 FROM MotorPH_ERP.sys.database_principals WHERE name = 'yel')
        EXEC('USE MotorPH_ERP; DROP USER [yel];');

    IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'yel')
        DROP LOGIN [yel];

    CREATE LOGIN [yel] WITH PASSWORD = 'Password1', CHECK_EXPIRATION = OFF, CHECK_POLICY = OFF;
END;
GO

-- 2. Map the login into MotorPH_ERP as db_owner (separate batch)
USE MotorPH_ERP;
GO
CREATE USER [yel] FOR LOGIN [yel];
ALTER ROLE [db_owner] ADD MEMBER [yel];
GO

PRINT '10 - Login [yel] reset and granted db_owner on MotorPH_ERP.';
