-- =============================================================
-- 16 - Premium Rate Table (+ seed)
-- MotorPH_ERP  |  Schema + seed (extends 01 Reference Tables)
--
-- Introduces a VERSIONED premium-rate table so labour-premium multipliers
-- (overtime, rest-day, holiday, special-holiday, night differential) and the
-- night-differential WINDOW are DB-driven and effective-dated — the same
-- pattern already used for Contribution_Rate / SSS_Contribution_Table /
-- WithholdingTax_Table on the statutory-deduction side.
--
-- Replaces the hardcoded Constants.OvertimeRateMultiplier /
-- PremiumRateMultiplier enums as the SOURCE OF TRUTH. The enums remain in code
-- only as provider FALLBACKS (so payroll still runs if this table is empty).
--
-- Seed values equal the current enum constants, so existing payslip output is
-- UNCHANGED by Phase 6a (pure refactor). Night-diff defaults: 22:00-06:00 @ 1.10.
--
-- Idempotent: table guarded by IF NOT EXISTS; seed is delete-then-insert for
-- the 2024-01-01 effective row set.
-- =============================================================
USE MotorPH_ERP;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'Premium_Rate')
BEGIN
    CREATE TABLE Premium_Rate (
        PremiumRateID   INT IDENTITY(1,1) PRIMARY KEY,
        PremiumType     NVARCHAR(30)  NOT NULL,   -- REGULAR_OT, WEEKEND_OT, HOLIDAY_OT,
                                                  -- REST_DAY, REGULAR_HOLIDAY, SPECIAL_HOLIDAY, NIGHT_DIFF
        Multiplier      DECIMAL(5,2)  NOT NULL,   -- e.g. 1.25, 1.30, 2.00, 1.10
        WindowStart     TIME          NULL,       -- NIGHT_DIFF only (else NULL)
        WindowEnd       TIME          NULL,
        EffectiveDate   DATE          NOT NULL,
        Status          BIT           NOT NULL DEFAULT 1,
        LastUpdatedBy   NVARCHAR(50)  NOT NULL DEFAULT 'System',
        LastUpdatedDate DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT UQ_PremiumRate UNIQUE (PremiumType, EffectiveDate)
    );
    PRINT '16 - Premium_Rate table created.';
END
ELSE
    PRINT '16 - Premium_Rate table already exists.';
GO

SET NOCOUNT ON;

-- Self-correcting seed for the 2024-01-01 effective set.
DELETE FROM Premium_Rate WHERE EffectiveDate = '2024-01-01';

INSERT INTO Premium_Rate (PremiumType, Multiplier, WindowStart, WindowEnd, EffectiveDate) VALUES
    ('REGULAR_OT',      1.25, NULL,    NULL,    '2024-01-01'),
    ('WEEKEND_OT',      1.50, NULL,    NULL,    '2024-01-01'),
    ('HOLIDAY_OT',      2.00, NULL,    NULL,    '2024-01-01'),
    ('REST_DAY',        1.30, NULL,    NULL,    '2024-01-01'),
    ('REGULAR_HOLIDAY', 2.00, NULL,    NULL,    '2024-01-01'),
    ('SPECIAL_HOLIDAY', 1.30, NULL,    NULL,    '2024-01-01'),
    ('NIGHT_DIFF',      1.10, '22:00', '06:00', '2024-01-01');
GO

-- Verify (expect 7 rows; NIGHT_DIFF carries the 22:00-06:00 window).
SELECT PremiumType, Multiplier, WindowStart, WindowEnd, EffectiveDate
FROM Premium_Rate WHERE Status = 1
ORDER BY EffectiveDate, PremiumType;
GO