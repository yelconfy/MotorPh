package Interface;

import Objects.models.Attendance;
import Objects.results.SaveResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Contract for the audited Punch Correction module (Phase 7c).
 *
 * Admin-side correction of dirty clock data (missed scans, wrong times, a
 * worked day captured as no-row). Every write commits together with its
 * Audit_Log row in one transaction, stamped with the acting username and an
 * optional reason — so corrections are traceable, not silent edits.
 *
 * BKL-35 B-rollout (step 1): EditPunch and AddPunch now report their outcome
 * through SaveResult instead of a bare boolean / magic-number long.
 */
public interface IAttendanceCorrectionProcess {

  /** Raw attendance rows for one employee over a range (for the grid). */
  List<Attendance> GetAttendance(long employeeId, LocalDate from, LocalDate to);

  /** True if the employee already has a row on that date (UI pre-check for add). */
  boolean ExistsForDate(long employeeId, LocalDate date);

  /**
   * Edit an existing punch's TimeIn/TimeOut. Either may be null (incomplete).
   * Captures old -> new (plus reason) to Audit_Log as an UPDATE on "Attendance".
   * Returns SUCCESS, or FAILED with a reason (e.g. the row no longer exists).
   */
  SaveResult<Void> EditPunch(
    long attendanceId,
    LocalTime newTimeIn,
    LocalTime newTimeOut,
    String reason,
    String username
  );

  /**
   * Add a punch for a date with no existing row. Re-checks ExistsForDate inside
   * the transaction. Returns SUCCESS carrying the new AttendanceID,
   * VALIDATION_FAILED if the date already has a row, or FAILED on a technical
   * failure. Logs an INSERT on "Attendance".
   */
  SaveResult<Long> AddPunch(
    long employeeId,
    LocalDate date,
    LocalTime timeIn,
    LocalTime timeOut,
    String reason,
    String username
  );
}