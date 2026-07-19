package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import Objects.enums.Status.AuditAction;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Shared scaffolding for the read-only report/print processes (Payroll Summary,
 * 13th Month, BIR 2316, Statutory Remittance).
 *
 * Consolidates the two patterns those processes all repeated:
 *
 *   DegradingRead — a one-shot report read that self-opens via its DAO and
 *     degrades to an empty list on SQLException, logging a context-tagged
 *     message. Reads are inherently read-only; no transaction needed.
 *
 *   LogPrint — a reprint-aware print audit on a SINGLE Connection: a print whose
 *     prior PRINT count is > 0 is a reprint. Named LogPrint (not RecordPrint) so
 *     it never collides with each subclass's differently-typed public
 *     RecordPrint(...), which builds the report's recordId and delegates here.
 *
 * The audit table name is fixed per report and supplied at construction; the
 * recordId is a generic NVARCHAR key (Audit_Log.RecordID), so no FK is needed.
 */
public abstract class BaseReportProcess {

  /** Supplier variant that may throw SQLException — the shape of a DAO read. */
  @FunctionalInterface
  protected interface SqlSupplier<T> {
    T Get() throws SQLException;
  }

  private final String auditTable;
  private final AuditLogDAO auditLogDAO;

  protected BaseReportProcess(String auditTable, AuditLogDAO auditLogDAO) {
    this.auditTable = auditTable;
    this.auditLogDAO = auditLogDAO;
  }

  /** Run a report read; on failure log "{context}: {msg}" and return an empty list. */
  protected <T> List<T> DegradingRead(String context, SqlSupplier<List<T>> read) {
    try {
      return read.Get();
    } catch (SQLException e) {
      System.err.println(context + ": " + e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Reprint-aware print audit on one Connection. Returns true if a PRINT for this
   * recordId already existed (i.e. this is a reprint).
   */
  protected boolean LogPrint(String recordId, String username, String reason)
      throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      boolean isReprint =
        auditLogDAO.CountActions(conn, auditTable, recordId, AuditAction.PRINT) > 0;
      auditLogDAO.Log(
        conn, username, auditTable, recordId, AuditAction.PRINT, null, reason
      );
      return isReprint;
    }
  }
}