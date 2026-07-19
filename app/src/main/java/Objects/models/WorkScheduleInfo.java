package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;

/**
 * Maps to Work_Schedule (01 - Reference Tables).
 * Columns: ScheduleID, ScheduleName, TimeStart, TimeEnd,
 *          BreakMinutes, GracePeriodMinutes,
 *          WorksMon..WorksSun, Status
 */
public class WorkScheduleInfo extends BaseObject {

  private int ScheduleId;
  private String ScheduleName;
  private LocalTime TimeStart;
  private LocalTime TimeEnd;
  private int BreakMinutes;
  private int GracePeriodMinutes;
  private boolean WorksMon, WorksTue, WorksWed, WorksThu, WorksFri, WorksSat, WorksSun;

  public WorkScheduleInfo() {}

  public WorkScheduleInfo(ResultSet rs) throws SQLException {
    this.ScheduleId = rs.getInt("ScheduleID");
    this.ScheduleName = rs.getString("ScheduleName");

    java.sql.Time ts = rs.getTime("TimeStart");
    this.TimeStart = (ts != null) ? ts.toLocalTime() : null;

    java.sql.Time te = rs.getTime("TimeEnd");
    this.TimeEnd = (te != null) ? te.toLocalTime() : null;

    this.BreakMinutes = rs.getInt("BreakMinutes");
    this.GracePeriodMinutes = rs.getInt("GracePeriodMinutes");
    this.WorksMon = rs.getBoolean("WorksMon");
    this.WorksTue = rs.getBoolean("WorksTue");
    this.WorksWed = rs.getBoolean("WorksWed");
    this.WorksThu = rs.getBoolean("WorksThu");
    this.WorksFri = rs.getBoolean("WorksFri");
    this.WorksSat = rs.getBoolean("WorksSat");
    this.WorksSun = rs.getBoolean("WorksSun");
    SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetScheduleId();
  }

  @Override
  public String toString() {
    return ScheduleName != null ? ScheduleName : "";
  }

  /** Net working hours per day after break. Useful for hourly rate calculations. */
  public double GetNetHoursPerDay() {
    if (TimeStart == null || TimeEnd == null) return 8.0;
    long totalMinutes = java.time.Duration.between(
      TimeStart,
      TimeEnd
    ).toMinutes();
    if (totalMinutes < 0) totalMinutes += 24 * 60; // crosses midnight
    return (totalMinutes - BreakMinutes) / 60.0;
  }

  /**
   * Default fallback schedule — the standard MotorPH shift (Mon-Fri 08:00-17:00,
   * 60-min unpaid break, 10-min grace). Used by the attendance calculator when an
   * employee has no Work_Schedule assigned, so per-day math never NPEs. Mirrors
   * the 'Standard 8:00-17:00' seed (ETL 07 step 4b / script 13).
   */
  public static WorkScheduleInfo Default() {
    WorkScheduleInfo s = new WorkScheduleInfo();
    s.ScheduleId = 0;
    s.ScheduleName = "Standard 8:00-17:00";
    s.TimeStart = LocalTime.of(8, 0);
    s.TimeEnd = LocalTime.of(17, 0);
    s.BreakMinutes = 60;
    s.GracePeriodMinutes = 10;
    s.WorksMon = s.WorksTue = s.WorksWed = s.WorksThu = s.WorksFri = true;
    s.WorksSat = s.WorksSun = false;
    s.SetActive(true);
    return s;
  }

  public boolean WorksOn(java.time.LocalDate date) {
    if (date == null) return false;
    return switch (date.getDayOfWeek()) {
      case MONDAY    -> WorksMon;
      case TUESDAY   -> WorksTue;
      case WEDNESDAY -> WorksWed;
      case THURSDAY  -> WorksThu;
      case FRIDAY    -> WorksFri;
      case SATURDAY  -> WorksSat;
      case SUNDAY    -> WorksSun;
    };
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public int GetScheduleId() {
    return ScheduleId;
  }

  public void SetScheduleId(int v) {
    this.ScheduleId = v;
  }

  public String GetScheduleName() {
    return ScheduleName;
  }

  public void SetScheduleName(String v) {
    this.ScheduleName = v;
  }

  public LocalTime GetTimeStart() {
    return TimeStart;
  }

  public void SetTimeStart(LocalTime v) {
    this.TimeStart = v;
  }

  public LocalTime GetTimeEnd() {
    return TimeEnd;
  }

  public void SetTimeEnd(LocalTime v) {
    this.TimeEnd = v;
  }

  public int GetBreakMinutes() {
    return BreakMinutes;
  }

  public void SetBreakMinutes(int v) {
    this.BreakMinutes = v;
  }

  public int GetGracePeriodMinutes() {
    return GracePeriodMinutes;
  }

  public void SetGracePeriodMinutes(int v) {
    this.GracePeriodMinutes = v;
  }

  public boolean GetWorksMon() {
    return WorksMon;
  }

  public boolean GetWorksTue() {
    return WorksTue;
  }

  public boolean GetWorksWed() {
    return WorksWed;
  }

  public boolean GetWorksThu() {
    return WorksThu;
  }

  public boolean GetWorksFri() {
    return WorksFri;
  }

  public boolean GetWorksSat() {
    return WorksSat;
  }

  public boolean GetWorksSun() {
    return WorksSun;
  }

  public void SetWorksMon(boolean v) { this.WorksMon = v; }
  public void SetWorksTue(boolean v) { this.WorksTue = v; }
  public void SetWorksWed(boolean v) { this.WorksWed = v; }
  public void SetWorksThu(boolean v) { this.WorksThu = v; }
  public void SetWorksFri(boolean v) { this.WorksFri = v; }
  public void SetWorksSat(boolean v) { this.WorksSat = v; }
  public void SetWorksSun(boolean v) { this.WorksSun = v; }
}
