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
}
