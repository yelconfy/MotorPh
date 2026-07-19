-- =============================================================
-- 11 - Create SQL Login
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
--
-- FRESH-MACHINE NOTE (mixed-mode auth):
--   A SQL login ('yel') only works if the SERVER allows SQL authentication.
--   A brand-new SQL Server install often defaults to Windows-Authentication-
--   only, in which case CREATE LOGIN below SUCCEEDS but every later SQL-auth
--   connection still fails ("Login failed for user 'yel'"). Step 0 flips the
--   server to mixed mode when needed. IMPORTANT: that change is a registry
--   write that does NOT take effect until the SQL Server service is RESTARTED,
--   and a T-SQL script cannot restart a service. If step 0 reports it made the
--   change, you MUST restart the service before the login will work:
--       Restart-Service -Name MSSQLSERVER -Force
--   (named instance: MSSQL$INSTANCENAME). Step 0 is idempotent - it no-ops if
--   the server is already in mixed mode.
-- =============================================================
USE master;
GO

-- 0. Ensure the server permits SQL authentication (mixed mode).
--    Guarded so re-runs are safe; prints a loud RESTART REQUIRED notice only
--    when it actually changed something.
IF SERVERPROPERTY('IsIntegratedSecurityOnly') = 1
BEGIN
    -- xp_instance_regwrite needs elevated/sysadmin rights. If it errors with a
    -- permissions message, run this script (or SSMS) "as administrator", or set
    -- mixed mode via SSMS: right-click server > Properties > Security >
    -- "SQL Server and Windows Authentication mode".
    BEGIN TRY
        EXEC xp_instance_regwrite
            N'HKEY_LOCAL_MACHINE',
            N'Software\Microsoft\MSSQLServer\MSSQLServer',
            N'LoginMode',
            REG_DWORD,
            2;

        PRINT '***********************************************************';
        PRINT '*** 11 - Mixed-mode authentication has been ENABLED.';
        PRINT '*** You MUST restart the SQL Server service NOW:';
        PRINT '***     Restart-Service -Name MSSQLSERVER -Force';
        PRINT '*** (named instance: MSSQL$INSTANCENAME)';
        PRINT '*** SQL login [yel] will NOT authenticate until you do.';
        PRINT '***********************************************************';
    END TRY
    BEGIN CATCH
        PRINT '!!! 11 - Could not enable mixed mode automatically: '
              + ERROR_MESSAGE();
        PRINT '!!! Enable it manually (SSMS > Server Properties > Security >';
        PRINT '!!! "SQL Server and Windows Authentication mode"), then restart';
        PRINT '!!! the SQL Server service and re-run this script.';
    END CATCH
END
ELSE
BEGIN
    PRINT '11 - Mixed-mode auth already enabled; no service restart needed.';
END
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

PRINT '11 - Login [yel] reset and granted db_owner on MotorPH_ERP.';