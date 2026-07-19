package Objects.models;

/**
 * Period-level worked-hours accumulator for payroll.
 *
 * In-memory computation object (like EmpDeductions / EmpPaySlip) — NOT a
 * DB-mapped entity, so it intentionally does not extend BaseObject and has no
 * GetIdentity().
 *
 * PayrollProcess fills it one attendance row at a time in CalculateHoursWorked /
 * ProcessDailyAttendance; ComputeEmployeeDeductions and GenerateEmpPaySlip then
 * read the totals. It is the return type of IPayrollProcess.CalculateHoursWorked.
 *
 * Hours are stored as WHOLE HOURS — the caller floors per day (regMin / 60),
 * matching the deliberate payroll flooring also mirrored in
 * Core.Service.AttendanceCalculator, so the Timekeeping screen and Payroll never
 * disagree on hours. Day types are bucketed separately (regular / weekend /
 * holiday) so the payroll engine can apply the correct premium per bucket;
 * overtime is tracked apart from the day-type buckets. Late time is kept in
 * minutes (penalty math needs the finer granularity); absences are whole days.
 */
public class WorkedHoursSummary {

  private int regularHours;
  private int weekendHours;
  private int holidayHours;
  private int overtimeHours;
  private int totalLateMinutes;
  private int totalAbsentDays;
  private int specialHolidayHours;
  private int paidLeaveDays;
  private int nightDiffHours;
  private int totalUndertimeMinutes;

  public WorkedHoursSummary() {}

  // -------------------------------------------------------------------------
  // Accumulators (called per attendance row)
  // -------------------------------------------------------------------------

  public void AddRegularHours(int hours) {
    this.regularHours += hours;
  }

  public void AddWeekendHours(int hours) {
    this.weekendHours += hours;
  }

  public void AddHolidayHours(int hours) {
    this.holidayHours += hours;
  }

  public void AddOvertimeHours(int hours) {
    this.overtimeHours += hours;
  }

  public void AddLateMinutes(int minutes) {
    this.totalLateMinutes += minutes;
  }

  public void AddAbsentDays(int days) {
    this.totalAbsentDays += days;
  }

  public void AddSpecialHolidayHours(int hours) {
    this.specialHolidayHours += hours;
  }

  public void AddPaidLeaveDays(int days) {
    this.paidLeaveDays += days;
  }

  public void AddNightDiffHours(int hours) {
    this.nightDiffHours += hours;
  }

  public void AddUndertimeMinutes(int minutes) {
    this.totalUndertimeMinutes += minutes;
  }

  // -------------------------------------------------------------------------
  // Totals
  // -------------------------------------------------------------------------

  public int GetRegularHours() {
    return regularHours;
  }

  public int GetWeekendHours() {
    return weekendHours;
  }

  public int GetHolidayHours() {
    return holidayHours;
  }

  public int GetOvertimeHours() {
    return overtimeHours;
  }

  public int GetTotalLateMinutes() {
    return totalLateMinutes;
  }

  public int GetTotalAbsentDays() {
    return totalAbsentDays;
  }

  public int GetSpecialHolidayHours() {
    return specialHolidayHours;
  }

  /** Convenience: all paid worked hours across buckets (excludes late / absent). */
  public int GetTotalWorkedHours() {
    return (
      regularHours +
      weekendHours +
      holidayHours +
      overtimeHours +
      specialHolidayHours
    );
  }

  public int GetNightDiffHours() {
    return nightDiffHours;
  }

  public int GetTotalUndertimeMinutes() {
    return totalUndertimeMinutes;
  }

  public int GetPaidLeaveDays() {
    return paidLeaveDays;
  }

  @Override
  public String toString() {
    return (
      "WorkedHoursSummary{" +
      "regular=" +
      regularHours +
      ", weekend=" +
      weekendHours +
      ", holiday=" +
      holidayHours +
      ", overtime=" +
      overtimeHours +
      ", lateMinutes=" +
      totalLateMinutes +
      ", absentDays=" +
      totalAbsentDays +
      "}"
    );
  }
}
