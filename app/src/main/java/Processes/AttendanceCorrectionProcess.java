package Processes;

import DataAccess.AttendanceDAO;
import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import Interface.IAttendanceCorrectionProcess;
import Objects.enums.Status.AuditAction;
import Objects.models.Attendance;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Punch Correction (Phase 7c) — audited add/edit of attendance rows.
 *
 * Each write goes through BaseMaintenanceProcess.ExecuteAtomic, and the
 * Audit_Log row is written ON THE SAME CONNECTION inside that transaction, so a
 * correction and its audit trail commit or roll back together — the trail can
 * never drift from the data.
 *
 * Audit shape (TableName = "Attendance", RecordID = AttendanceID):
 *   EditPunch -> UPDATE, OldValue = old "in-out", NewValue = new "in-out" [+ reason]
 *   AddPunch  -> INSERT, OldValue = null,         NewValue = new "in-out" [+ reason]
 *
 * BKL-35 B-rollout (step 1): EditPunch/AddPunch now report through SaveResult
 * instead of a bare boolean / magic-number long. ExecuteAtomic itself stays
 * boolean (shared by processes that never learn about SaveResult), so each
 * method captures the real outcome in an AtomicReference set at every exit
 * point inside the lambda, then returns it after the transaction resolves —
 * defaulting to a generic failed() if nothing overwrote it (e.g. an
 * unexpected SQLException caught by ExecuteAtomic itself).
 */
public class AttendanceCorrectionProcess
  extends BaseMaintenanceProcess
  implements IAttendanceCorrectionProcess {

  private static final String AUDIT_TABLE = "Attendance";
  private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

  private final AttendanceDAO attendanceDAO;
  private final AuditLogDAO auditLogDAO;

  public AttendanceCorrectionProcess(
    AttendanceDAO attendanceDAO,
    AuditLogDAO auditLogDAO
  ) {
    this.attendanceDAO = attendanceDAO;
    this.auditLogDAO = auditLogDAO;
  }

  @Override
  public List<Attendance> GetAttendance(
    long employeeId,
    LocalDate from,
    LocalDate to
  ) {
    try {
      return attendanceDAO.GetByDateRange(employeeId, from, to);
    } catch (SQLException e) {
      System.err.println("AttendanceCorrectionProcess.GetAttendance: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public boolean ExistsForDate(long employeeId, LocalDate date) {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return attendanceDAO.ExistsForDate(conn, employeeId, date);
    } catch (SQLException e) {
      // On error, report "exists" so the UI errs toward blocking a possible duplicate.
      System.err.println("AttendanceCorrectionProcess.ExistsForDate: " + e.getMessage());
      return true;
    }
  }

  @Override
  public SaveResult<Void> EditPunch(
    long attendanceId,
    LocalTime newTimeIn,
    LocalTime newTimeOut,
    String reason,
    String username
  ) {
    AtomicReference<SaveResult<Void>> outcome = new AtomicReference<>(SaveResult.failed());

    ExecuteAtomic(conn -> {
      Attendance current = attendanceDAO.GetById(conn, attendanceId);
      if (current == null) {
        outcome.set(SaveResult.failed("That punch no longer exists."));
        return false;
      }
      String oldValue = span(current.GetTimeIn(), current.GetTimeOut());

      if (!attendanceDAO.Update(conn, attendanceId, newTimeIn, newTimeOut)) {
        outcome.set(SaveResult.failed());
        return false;
      }

      auditLogDAO.Log(
        conn,
        username,
        AUDIT_TABLE,
        String.valueOf(attendanceId),
        AuditAction.UPDATE,
        oldValue,
        withReason(span(newTimeIn, newTimeOut), reason)
      );
      outcome.set(SaveResult.success());
      return true;
    });

    return outcome.get();
  }

  @Override
  public SaveResult<Long> AddPunch(
    long employeeId,
    LocalDate date,
    LocalTime timeIn,
    LocalTime timeOut,
    String reason,
    String username
  ) {
    AtomicReference<SaveResult<Long>> outcome = new AtomicReference<>(SaveResult.failed());

    ExecuteAtomic(conn -> {
      // Authoritative duplicate guard inside the transaction (no DB constraint).
      if (attendanceDAO.ExistsForDate(conn, employeeId, date)) {
        outcome.set(SaveResult.invalid(
          "That employee already has a record on " + date + ".\n" +
          "Use Edit Punch to change the existing row."
        ));
        return false;
      }
      long newId = attendanceDAO.Insert(conn, employeeId, date, timeIn, timeOut);
      if (newId <= 0) {
        outcome.set(SaveResult.failed());
        return false;
      }
      auditLogDAO.Log(
        conn,
        username,
        AUDIT_TABLE,
        String.valueOf(newId),
        AuditAction.INSERT,
        null,
        withReason(span(timeIn, timeOut), reason)
      );
      outcome.set(SaveResult.success(newId));
      return true;
    });

    return outcome.get();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static String span(LocalTime in, LocalTime out) {
    return t(in) + "-" + t(out);
  }

  private static String t(LocalTime time) {
    return time != null ? time.format(T) : "\u2014";
  }

  private static String withReason(String value, String reason) {
    return (reason != null && !reason.isBlank())
      ? value + " | reason: " + reason.trim()
      : value;
  }
}