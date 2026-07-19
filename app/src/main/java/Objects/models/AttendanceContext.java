package Objects.models;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Map;

/**
 * Per-run context injected into AttendanceCalculator so the pure per-day math
 * has everything it needs without doing any JDBC itself.
 *
 * Carries, as of Phase 6b:
 *   - schedules        : employeeId -> WorkScheduleInfo (full row)              [P1]
 *   - holidays         : date -> HolidayType lookup                             [P3]
 *   - approvedOtMinutes: employeeId -> (date -> approved OT minutes)            [P4]
 *   - approvedLeave    : employeeId -> (date -> isPaid) for APPROVED leave      [P5]
 *   - night window     : start/end of the night-differential span              [P6b]
 *
 * Constructors layer up so each consumer passes only what it needs; all but the
 * fullest default the night window to 22:00-06:00 (PH Labor Code).
 */
public final class AttendanceContext {

  private static final LocalTime DEFAULT_NIGHT_START = LocalTime.of(22, 0);
  private static final LocalTime DEFAULT_NIGHT_END   = LocalTime.of(6, 0);

  private final Map<Long, WorkScheduleInfo> schedules;
  private final WorkScheduleInfo defaultSchedule;
  private final HolidayCalendar holidays;
  private final Map<Long, Map<LocalDate, Long>> approvedOtMinutes;
  private final Map<Long, Map<LocalDate, Boolean>> approvedLeave;
  private final LocalTime nightStart;
  private final LocalTime nightEnd;

  public AttendanceContext(
    Map<Long, WorkScheduleInfo> schedules,
    WorkScheduleInfo defaultSchedule,
    HolidayCalendar holidays
  ) {
    this(schedules, defaultSchedule, holidays, null, null, null, null);
  }

  public AttendanceContext(
    Map<Long, WorkScheduleInfo> schedules,
    WorkScheduleInfo defaultSchedule,
    HolidayCalendar holidays,
    Map<Long, Map<LocalDate, Long>> approvedOtMinutes
  ) {
    this(schedules, defaultSchedule, holidays, approvedOtMinutes, null, null, null);
  }

  public AttendanceContext(
    Map<Long, WorkScheduleInfo> schedules,
    WorkScheduleInfo defaultSchedule,
    HolidayCalendar holidays,
    Map<Long, Map<LocalDate, Long>> approvedOtMinutes,
    Map<Long, Map<LocalDate, Boolean>> approvedLeave
  ) {
    this(schedules, defaultSchedule, holidays, approvedOtMinutes, approvedLeave, null, null);
  }

  public AttendanceContext(
    Map<Long, WorkScheduleInfo> schedules,
    WorkScheduleInfo defaultSchedule,
    HolidayCalendar holidays,
    Map<Long, Map<LocalDate, Long>> approvedOtMinutes,
    Map<Long, Map<LocalDate, Boolean>> approvedLeave,
    LocalTime nightStart,
    LocalTime nightEnd
  ) {
    this.schedules = (schedules != null) ? schedules : Collections.emptyMap();
    this.defaultSchedule =
      (defaultSchedule != null) ? defaultSchedule : WorkScheduleInfo.Default();
    this.holidays = (holidays != null) ? holidays : HolidayCalendar.Empty();
    this.approvedOtMinutes =
      (approvedOtMinutes != null) ? approvedOtMinutes : Collections.emptyMap();
    this.approvedLeave =
      (approvedLeave != null) ? approvedLeave : Collections.emptyMap();
    this.nightStart = (nightStart != null) ? nightStart : DEFAULT_NIGHT_START;
    this.nightEnd   = (nightEnd != null)   ? nightEnd   : DEFAULT_NIGHT_END;
  }

  /** Empty context (default schedule, no holidays/OT/leave, default night window). */
  public static AttendanceContext Empty() {
    return new AttendanceContext(
      Collections.emptyMap(), WorkScheduleInfo.Default(), HolidayCalendar.Empty(),
      Collections.emptyMap(), Collections.emptyMap(), null, null
    );
  }

  /** The employee's schedule, or the default when none is mapped. */
  public WorkScheduleInfo ScheduleFor(long employeeId) {
    WorkScheduleInfo s = schedules.get(employeeId);
    return (s != null) ? s : defaultSchedule;
  }

  public HolidayCalendar Holidays() {
    return holidays;
  }

  /** Approved overtime minutes for an employee on a date (0 if none/unapproved). */
  public long ApprovedOvertimeMinutes(long employeeId, LocalDate date) {
    if (date == null) return 0;
    Map<LocalDate, Long> byDate = approvedOtMinutes.get(employeeId);
    if (byDate == null) return 0;
    Long v = byDate.get(date);
    return (v != null) ? v : 0;
  }

  /**
   * Approved-leave status for an employee on a date:
   *   TRUE  = approved PAID leave, FALSE = approved UNPAID leave, null = no leave.
   */
  public Boolean LeaveStatusFor(long employeeId, LocalDate date) {
    if (date == null) return null;
    Map<LocalDate, Boolean> byDate = approvedLeave.get(employeeId);
    return (byDate != null) ? byDate.get(date) : null;
  }

  public LocalTime NightWindowStart() { return nightStart; }
  public LocalTime NightWindowEnd()   { return nightEnd; }
}