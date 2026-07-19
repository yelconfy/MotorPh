package Objects.enums;

/**
 * Severity levels for the diagnostic trace log (MPH_TRACE.Trace_Log), lowest to
 * highest. Mirrors the conventional SLF4J-style ladder so it reads familiarly.
 *
 * This is the DIAGNOSTIC axis — it is NOT the audit/compliance trail. Audit
 * events (data changes, sign-ins) still go to Audit_Log / User_Access_Log in
 * the main database via AuditLogDAO; those are transactional and durable. Trace
 * is fire-and-forget diagnostics.
 *
 * Ordering matters: LoggingService uses ordinal() to apply a minimum threshold,
 * so declare these least-severe first and never reorder them.
 */
public enum LogLevel {
  TRACE,
  DEBUG,
  INFO,
  WARN,
  ERROR;

  /** True when this level is at or above the given minimum threshold. */
  public boolean AtLeast(LogLevel min) {
    return this.ordinal() >= min.ordinal();
  }
}