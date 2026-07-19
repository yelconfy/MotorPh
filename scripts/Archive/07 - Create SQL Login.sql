USE master;

-- 1. KILL SESSIONS, DROP, AND RECREATE IN ONE SHOT
BEGIN
    -- Kill sessions for 'yel'
    DECLARE @kill VARCHAR(8000) = '';  
    SELECT @kill = @kill + 'KILL ' + CONVERT(VARCHAR(5), session_id) + ';'  
    FROM sys.dm_exec_sessions  
    WHERE login_name = 'yel';
    EXEC(@kill);

    -- Drop User from MotorPH_ERP if it exists
    IF EXISTS (SELECT * FROM MotorPH_ERP.sys.database_principals WHERE name = 'yel')
    BEGIN
        EXEC('USE MotorPH_ERP; DROP USER [yel];');
    END;

    -- Drop Login if it exists
    IF EXISTS (SELECT * FROM sys.server_principals WHERE name = 'yel')
    BEGIN
        DROP LOGIN [yel];
    END;

    -- Recreate Login
    CREATE LOGIN [yel] WITH PASSWORD = 'Password1', CHECK_EXPIRATION = OFF, CHECK_POLICY = OFF;
END;
GO

-- 2. ASSIGN TO DATABASE (This must be a separate batch)
USE MotorPH_ERP;
GO
CREATE USER [yel] FOR LOGIN [yel];
ALTER ROLE [db_owner] ADD MEMBER [yel];
GO

PRINT 'Final Success! Login [yel] is reset and ready.';