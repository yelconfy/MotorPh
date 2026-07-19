package Processes;

import Core.Service.AttendanceCalculator;
import DataAccess.AttendanceDAO;
import DataAccess.DatabaseConnector;
import DataAccess.HolidayDAO;
import DataAccess.WorkScheduleDAO;
import Interface.ITimeKeepingProcess;
import Objects.models.Attendance;
import Objects.models.AttendanceContext;
import Objects.models.DailyAttendanceRecord;
import Objects.models.HolidayCalendar;
import Objects.models.WorkScheduleInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TimeKeepingProcess implements ITimeKeepingProcess {

  private final AttendanceDAO attendanceDAO;
  private final WorkScheduleDAO scheduleDAO;
  private final HolidayDAO holidayDAO;
  private final AttendanceCalculator calculator = new AttendanceCalculator();

  public TimeKeepingProcess(
    AttendanceDAO attendanceDAO,
    WorkScheduleDAO scheduleDAO,
    HolidayDAO holidayDAO
  ) {
    this.attendanceDAO = attendanceDAO;
    this.scheduleDAO = scheduleDAO;
    this.holidayDAO = holidayDAO;
  }

  @Override
  public List<Attendance> GetEmpAttendance(
    Optional<String> query,
    Optional<LocalDate> fromDate,
    Optional<LocalDate> toDate
  ) {
    try {
      return attendanceDAO.SearchAttendance(query.orElse(""), fromDate, toDate);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public List<DailyAttendanceRecord> GetTimeRecords(
    Optional<String> query,
    Optional<LocalDate> fromDate,
    Optional<LocalDate> toDate
  ) {
    List<Attendance> rows = GetEmpAttendance(query, fromDate, toDate);
    return calculator.ComputeAll(rows, BuildContext(rows));
  }

  /**
   * Phase 7b — exact single-employee DTR read. Uses AttendanceDAO.GetByDateRange
   * (WHERE EmployeeID = ?, the same exact path Payroll uses) rather than the
   * fuzzy LIKE search, so the report is keyed to exactly one employee. The rows
   * are then run through the SAME calculator + context as the grid.
   */
  @Override
  public List<DailyAttendanceRecord> GetTimeRecordsForEmployee(
    long employeeId,
    LocalDate fromDate,
    LocalDate toDate
  ) {
    try {
      List<Attendance> rows = attendanceDAO.GetByDateRange(
        employeeId,
        fromDate,
        toDate
      );
      return calculator.ComputeAll(rows, BuildContext(rows));
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public AttendanceCalculator.Summary Summarize(
    List<DailyAttendanceRecord> records
  ) {
    return calculator.Summarize(records);
  }

  /**
   * Builds the run context for the rows on screen: a per-employee schedule map
   * (one lookup per distinct employee) plus the holiday calendar spanning the
   * rows' date range. Both reads share a single connection. On a DB error the
   * context degrades gracefully (default schedule / no holidays).
   */
  private AttendanceContext BuildContext(List<Attendance> rows) {
    Map<Long, WorkScheduleInfo> schedules = new HashMap<>();
    HolidayCalendar holidays = HolidayCalendar.Empty();
    if (rows == null || rows.isEmpty()) {
      return new AttendanceContext(
        schedules,
        WorkScheduleInfo.Default(),
        holidays
      );
    }

    LocalDate min = null, max = null;
    for (Attendance a : rows) {
      LocalDate d = a.GetAttendanceDate();
      if (d != null) {
        if (min == null || d.isBefore(min)) min = d;
        if (max == null || d.isAfter(max)) max = d;
      }
    }

    try (Connection conn = DatabaseConnector.GetConnection()) {
      for (Attendance a : rows) {
        long empId = a.GetEmployeeId();
        if (schedules.containsKey(empId)) continue;
        WorkScheduleInfo sched = scheduleDAO.GetByEmployee(conn, empId);
        schedules.put(empId, sched != null ? sched : WorkScheduleInfo.Default());
      }
      if (min != null && max != null) {
        holidays = new HolidayCalendar(holidayDAO.GetByDateRange(conn, min, max));
      }
    } catch (SQLException e) {
      e.printStackTrace(); // context degrades to defaults
    }
    return new AttendanceContext(schedules, WorkScheduleInfo.Default(), holidays);
  }
}