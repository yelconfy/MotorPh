package Objects.models;

import Objects.enums.Status.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A computed, presentation-ready view of one attendance row.
 *
 * Wraps the raw {@link Attendance} and adds the metrics produced by
 * Core.Service.AttendanceCalculator. Minutes (not hours) are stored so the UI
 * can show H:MM precisely; period totals re-floor to whole hours to match
 * PayrollProcess.
 *
 * Overtime is two figures (Phase 4): overtimeMinutes is RAW (DTR truth);
 * approvedOvertimeMinutes is raw CAPPED by approved OT (what payroll pays).
 *
 * Phase 6b adds: nightDiffMinutes (minutes worked inside the night-diff window,
 * an ADD-ON paid on top of the day-type pay) and undertimeMinutes (early
 * departure when the day was underworked — docked like lateness).
 */
public class DailyAttendanceRecord {

  public enum DayType {
    REGULAR,
    WEEKEND,
    HOLIDAY,          // regular holiday (200%)
    HOLIDAY_SPECIAL,  // special non-working day (130%)
  }

  private final Attendance source;
  private final AttendanceStatus status;
  private final DayType dayType;
  private final long lateMinutes;
  private final long workedMinutes;          // raw TimeOut - TimeIn
  private final long regularMinutes;         // capped at one shift
  private final long overtimeMinutes;        // RAW beyond one shift
  private final long approvedOvertimeMinutes; // raw capped by approved OT (paid)
  private final long nightDiffMinutes;       // minutes inside the night-diff window
  private final long undertimeMinutes;       // early-departure shortfall (docked)

  public DailyAttendanceRecord(
    Attendance source,
    AttendanceStatus status,
    DayType dayType,
    long lateMinutes,
    long workedMinutes,
    long regularMinutes,
    long overtimeMinutes,
    long approvedOvertimeMinutes,
    long nightDiffMinutes,
    long undertimeMinutes
  ) {
    this.source = source;
    this.status = status;
    this.dayType = dayType;
    this.lateMinutes = lateMinutes;
    this.workedMinutes = workedMinutes;
    this.regularMinutes = regularMinutes;
    this.overtimeMinutes = overtimeMinutes;
    this.approvedOvertimeMinutes = approvedOvertimeMinutes;
    this.nightDiffMinutes = nightDiffMinutes;
    this.undertimeMinutes = undertimeMinutes;
  }

  // -- computed ------------------------------------------------------------
  public AttendanceStatus GetStatus() { return status; }
  public DayType GetDayType() { return dayType; }
  public long GetLateMinutes() { return lateMinutes; }
  public long GetWorkedMinutes() { return workedMinutes; }
  public long GetRegularMinutes() { return regularMinutes; }

  /** RAW overtime worked beyond the shift (Timekeeping/DTR truth). */
  public long GetOvertimeMinutes() { return overtimeMinutes; }

  /** Approved (paid) overtime: raw capped by approved Overtime_Request minutes. */
  public long GetApprovedOvertimeMinutes() { return approvedOvertimeMinutes; }

  /** Minutes worked inside the night-differential window (add-on basis). */
  public long GetNightDiffMinutes() { return nightDiffMinutes; }

  /** Early-departure shortfall on an underworked day (docked like lateness). */
  public long GetUndertimeMinutes() { return undertimeMinutes; }

  public boolean HasOvertime() { return overtimeMinutes > 0; }

  // -- delegated identity / raw values ------------------------------------
  public Attendance GetSource() { return source; }
  public long GetEmployeeId() { return source.GetEmployeeId(); }
  public String GetLastName() { return source.GetLastName(); }
  public String GetFirstName() { return source.GetFirstName(); }
  public String GetFullName() { return source.GetFullName(); }
  public LocalDate GetDate() { return source.GetAttendanceDate(); }
  public LocalTime GetTimeIn() { return source.GetTimeIn(); }
  public LocalTime GetTimeOut() { return source.GetTimeOut(); }
}