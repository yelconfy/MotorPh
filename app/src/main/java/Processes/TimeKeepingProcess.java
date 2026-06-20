package Processes;

import Core.Service.AttendanceCalculator;
import DataAccess.AttendanceDAO;
import Interface.ITimeKeepingProcess;
import Objects.models.Attendance;
import Objects.models.DailyAttendanceRecord;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class TimeKeepingProcess implements ITimeKeepingProcess {

  private final AttendanceDAO attendanceDAO;
  private final AttendanceCalculator calculator = new AttendanceCalculator();

  public TimeKeepingProcess(AttendanceDAO attendanceDAO) {
    this.attendanceDAO = attendanceDAO;
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
    return calculator.ComputeAll(GetEmpAttendance(query, fromDate, toDate));
  }

  @Override
  public AttendanceCalculator.Summary Summarize(
    List<DailyAttendanceRecord> records
  ) {
    return calculator.Summarize(records);
  }
}
