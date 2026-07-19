/* ============================================================================
 * 18 - Create Trace Database.sql
 * ----------------------------------------------------------------------------
 * Creates MPH_TRACE: a SEPARATE physical database, on the same local SQL Server
 * instance, dedicated to diagnostic / trace logging only.
 *
 * WHY A SEPARATE DATABASE
 *   Trace logs are high-volume, disposable diagnostics (the System.out noise:
 *   timings, pool events, reconciler output, caught-and-logged errors). Keeping
 *   them out of the operational database means MPH_TRACE can be truncated,
 *   backed up, or dropped on its own schedule without ever touching operational
 *   or audit data.
 *
 * WHAT DOES *NOT* LIVE HERE  (important)
 *   Audit_Log and User_Access_Log stay in the MAIN database. They are
 *   COMPLIANCE data, written inside the same transaction as the change they
 *   record (see BaseMaintenanceProcess.ExecuteAtomic / MPH-40 / MPH-42). Moving
 *   them across a database boundary would break that atomicity (it would require
 *   distributed / MSDTC transactions). Trace_Log is the opposite: fire-and-
 *   forget, never transactional, no FK into the main DB. That is the whole
 *   reason a physical split is safe here but would NOT be for the audit tables.
 *
 * INDEPENDENCE RULE
 *   Trace_Log has NO foreign key into the main database. That is deliberate and
 *   must stay true: it is what lets MPH_TRACE be a genuinely standalone,
 *   separately-manageable database.
 *
 * RUN ORDER: standalone. Depends on nothing; nothing in scripts 01-17 depends
 * on it. Safe to (re-)run any time. The application also DEGRADES GRACEFULLY if
 * this database is absent — trace logging falls back to console-only — so a
 * missing MPH_TRACE never blocks the app from starting.
 * ============================================================================ */

IF DB_ID('MPH_TRACE') IS NULL
BEGIN
    CREATE DATABASE MPH_TRACE;
END
GO

USE MPH_TRACE;
GO

/* --------------------------------------------------------------------------
 * Trace_Log — one row per diagnostic event.
 *
 *   TraceLogID      identity PK
 *   EventTimestamp  when the event was recorded (app-side UTC-ish local time)
 *   LogLevel        TRACE / DEBUG / INFO / WARN / ERROR  (see LogLevel enum)
 *   Source          logical origin, e.g. "LoginProcess", "DatabaseConnector"
 *   Message         the human-readable line
 *   ThreadName      the thread that emitted it (EDT vs a worker vs db-warmup)
 *   Username        best-effort current user, or NULL (NOT an FK — see above)
 *   SessionId       best-effort User_Session id, or NULL (NOT an FK)
 *
 * No FK, by design. Username/SessionId are loose stamps copied from Session at
 * emit time, exactly like Audit_Log.Username in the main DB.
 * -------------------------------------------------------------------------- */
IF OBJECT_ID('dbo.Trace_Log', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Trace_Log (
        TraceLogID      BIGINT         IDENTITY(1,1) PRIMARY KEY,
        EventTimestamp  DATETIME2(3)   NOT NULL CONSTRAINT DF_TraceLog_Ts DEFAULT (SYSDATETIME()),
        LogLevel        VARCHAR(10)    NOT NULL,
        Source          NVARCHAR(128)  NULL,
        Message         NVARCHAR(MAX)  NOT NULL,
        ThreadName      NVARCHAR(128)  NULL,
        Username        NVARCHAR(128)  NULL,
        SessionId       BIGINT         NULL
    );

    /* Query patterns: "recent errors", "everything from this source lately".
       Newest-first on the timestamp is the common read. */
    CREATE INDEX IX_TraceLog_Ts    ON dbo.Trace_Log (EventTimestamp DESC);
    CREATE INDEX IX_TraceLog_Level ON dbo.Trace_Log (LogLevel, EventTimestamp DESC);
END
GO

/* --------------------------------------------------------------------------
 * Optional retention helper — delete trace rows older than N days.
 * Not scheduled here (no SQL Agent assumption); call manually or wire to a job.
 * Trace data is disposable, so a hard DELETE is fine.
 * -------------------------------------------------------------------------- */
IF OBJECT_ID('dbo.PurgeOldTrace', 'P') IS NOT NULL
    DROP PROCEDURE dbo.PurgeOldTrace;
GO

CREATE PROCEDURE dbo.PurgeOldTrace
    @RetainDays INT = 30
AS
BEGIN
    SET NOCOUNT ON;
    DELETE FROM dbo.Trace_Log
    WHERE EventTimestamp < DATEADD(DAY, -@RetainDays, SYSDATETIME());
END
GO