package Objects.models;

import Objects.enums.Status.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A computed, presentation-ready view of one attendance row.
 *
 * Wraps the raw {@link Attendance} and adds the metrics produced by
 * Core.Service.AttendanceCalculator (status, late/worked/overtime minutes,
 * day type). This is what the Timekeeping grid renders — the raw Attendance
 * stays untouched.
 *
 * Minutes (not hours) are stored so the UI can show H:MM precisely; period
 * totals re-floor to whole hours to stay identical to PayrollProcess.
 */
public class DailyAttendanceRecord {

  public enum DayType {
    REGULAR,
    WEEKEND,
    HOLIDAY,
  }

  private final Attendance source;
  private final AttendanceStatus status;
  private final DayType dayType;
  private final long lateMinutes;
  private final long workedMinutes; // raw TimeOut - TimeIn
  private final long regularMinutes; // capped at one shift, late removed
  private final long overtimeMinutes; // beyond one shift

  public DailyAttendanceRecord(
    Attendance source,
    AttendanceStatus status,
    DayType dayType,
    long lateMinutes,
    long workedMinutes,
    long regularMinutes,
    long overtimeMinutes
  ) {
    this.source = source;
    this.status = status;
    this.dayType = dayType;
    this.lateMinutes = lateMinutes;
    this.workedMinutes = workedMinutes;
    this.regularMinutes = regularMinutes;
    this.overtimeMinutes = overtimeMinutes;
  }

  // -- computed ------------------------------------------------------------
  public AttendanceStatus GetStatus() {
    return status;
  }

  public DayType GetDayType() {
    return dayType;
  }

  public long GetLateMinutes() {
    return lateMinutes;
  }

  public long GetWorkedMinutes() {
    return workedMinutes;
  }

  public long GetRegularMinutes() {
    return regularMinutes;
  }

  public long GetOvertimeMinutes() {
    return overtimeMinutes;
  }

  public boolean HasOvertime() {
    return overtimeMinutes > 0;
  }

  // -- delegated identity / raw values ------------------------------------
  public Attendance GetSource() {
    return source;
  }

  public long GetEmployeeId() {
    return source.GetEmployeeId();
  }

  public String GetLastName() {
    return source.GetLastName();
  }

  public String GetFirstName() {
    return source.GetFirstName();
  }

  public String GetFullName() {
    return source.GetFullName();
  }

  public LocalDate GetDate() {
    return source.GetAttendanceDate();
  }

  public LocalTime GetTimeIn() {
    return source.GetTimeIn();
  }

  public LocalTime GetTimeOut() {
    return source.GetTimeOut();
  }
}
