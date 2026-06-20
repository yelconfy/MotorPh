package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Maps to Attendance (02 - Core Employee Tables), JOINed with Employees for
 * the display name.
 *
 *   Attendance: AttendanceID, EmployeeID, AttendanceDate, TimeIn, TimeOut
 *   Employees (JOIN): FirstName, LastName
 *
 * CHANGE: Attendance now COMPOSES an EmployeeInfo instead of EXTENDING it.
 * An attendance record HAS an employee; it is not itself an employee. This
 * also removes the previous bug where Attendance redeclared its own
 * `EmployeeId` field, shadowing the inherited one from BaseEmployeeInfo and
 * leaving two sources of truth. Identity is now correctly AttendanceID
 * (the table's real primary key), not EmployeeID.
 *
 * The display lookup is now an explicit switch (matching EmpDetail) instead of
 * reflection, which broke once the name fields moved into the composed object.
 */
public class Attendance extends BaseObject {

  private long AttendanceId;
  private EmployeeInfo Employee; // composed reference (was inheritance)
  private LocalDate AttendanceDate;
  private LocalTime TimeIn;
  private LocalTime TimeOut;

  public static final String[] DISPLAY_FIELDS = {
    "EmployeeId",
    "LastName",
    "FirstName",
    "AttendanceDate",
    "TimeIn",
    "TimeOut",
  };

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public Attendance() {
    this.Employee = new EmployeeInfo();
  }

  /**
   * Smart Constructor — expects a row from the attendance JOIN:
   *   SELECT a.*, e.FirstName, e.LastName FROM Attendance a JOIN Employees e ...
   *
   * Birthday is intentionally NOT read here: the attendance query does not
   * select it, so `new EmployeeInfo(rs)` cannot be reused. The employee
   * reference is built from the three columns the query actually provides.
   */
  public Attendance(ResultSet rs) throws SQLException {
    this.AttendanceId = rs.getLong("AttendanceID");

    this.Employee = new EmployeeInfo();
    this.Employee.SetEmployeeId(rs.getLong("EmployeeID"));
    this.Employee.SetLastName(rs.getString("LastName"));
    this.Employee.SetFirstName(rs.getString("FirstName"));

    java.sql.Date date = rs.getDate("AttendanceDate");
    if (date != null) {
      this.AttendanceDate = date.toLocalDate();
    }

    java.sql.Time in = rs.getTime("TimeIn");
    if (in != null) {
      this.TimeIn = in.toLocalTime();
    }

    java.sql.Time out = rs.getTime("TimeOut");
    if (out != null) {
      this.TimeOut = out.toLocalTime();
    }
  }

  @Override
  public Object GetIdentity() {
    return GetAttendanceId();
  }

  // -------------------------------------------------------------------------
  // Display helper (used by AttendanceTableModel)
  // -------------------------------------------------------------------------

  public Object GetDisplayFieldValue(int index) {
    return switch (index) {
      case 0 -> GetEmployeeId();
      case 1 -> (Employee != null) ? Employee.GetLastName() : null;
      case 2 -> (Employee != null) ? Employee.GetFirstName() : null;
      case 3 -> AttendanceDate;
      case 4 -> TimeIn;
      case 5 -> TimeOut;
      default -> null;
    };
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public long GetAttendanceId() {
    return AttendanceId;
  }

  public void SetAttendanceId(long v) {
    this.AttendanceId = v;
  }

  public EmployeeInfo GetEmployee() {
    return Employee;
  }

  public void SetEmployee(EmployeeInfo v) {
    this.Employee = v;
  }

  // Convenience delegators so existing call sites keep working.
  public long GetEmployeeId() {
    return (Employee != null) ? Employee.GetEmployeeId() : 0L;
  }

  public void SetEmployeeId(long v) {
    if (Employee == null) {
      Employee = new EmployeeInfo();
    }
    Employee.SetEmployeeId(v);
  }

  public String GetFirstName() {
    return (Employee != null) ? Employee.GetFirstName() : null;
  }

  public String GetLastName() {
    return (Employee != null) ? Employee.GetLastName() : null;
  }

  public String GetFullName() {
    return (Employee != null) ? Employee.GetFullName() : null;
  }

  public LocalDate GetAttendanceDate() {
    return AttendanceDate;
  }

  public void SetAttendanceDate(LocalDate v) {
    this.AttendanceDate = v;
  }

  public LocalTime GetTimeIn() {
    return TimeIn;
  }

  public void SetTimeIn(LocalTime v) {
    this.TimeIn = v;
  }

  public LocalTime GetTimeOut() {
    return TimeOut;
  }

  public void SetTimeOut(LocalTime v) {
    this.TimeOut = v;
  }
}
