package DataAccess;

import Objects.models.EmpDetail;
import Objects.models.EmployeeInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Employees table and vw_EmployeeCompleteDetails.
 *
 * SCHEMA (02 - Core Employee Tables):
 *   Employees: EmployeeID, LastName, FirstName, Birthday, Email, PhoneNo,
 *              EmploymentStatus, PositionID, SupervisorID, DepartmentID,
 *              WorkScheduleID, DateHired, Status
 */
public class EmployeeDAO {

  /**
   * Insert — adds a new employee row; returns the generated EmployeeID
   * (IDENTITY starts at 10001).
   */
  public long Insert(Connection conn, EmpDetail emp) throws SQLException {
    String sql =
      "INSERT INTO Employees " +
      "(FirstName, LastName, Birthday, Email, PhoneNo, EmploymentStatus, " +
      " PositionID, SupervisorID, DepartmentID, WorkScheduleID, DateHired, Status) " +
      "OUTPUT INSERTED.EmployeeID " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, emp.GetFirstName());
      pstmt.setString(2, emp.GetLastName());

      pstmt.setDate(
        3,
        emp.GetBirthday() != null
          ? java.sql.Date.valueOf(emp.GetBirthday())
          : null
      );

      pstmt.setString(4, emp.GetEmail());
      pstmt.setString(5, emp.GetPhoneNo());

      pstmt.setInt(
        6,
        emp.GetEmpStatus() != null ? emp.GetEmpStatus().getValue() : 0
      );

      // PositionID (nullable FK)
      if (emp.GetPosition() != null && emp.GetPosition().GetPositionID() > 0) {
        pstmt.setLong(7, emp.GetPosition().GetPositionID());
      } else {
        pstmt.setNull(7, Types.BIGINT);
      }

      // SupervisorID (nullable self-reference FK)
      if (
        emp.GetImmSupervisor() != null &&
        emp.GetImmSupervisor().GetEmployeeId() > 0
      ) {
        pstmt.setLong(8, emp.GetImmSupervisor().GetEmployeeId());
      } else {
        pstmt.setNull(8, Types.BIGINT);
      }

      // DepartmentID (nullable FK)
      if (
        emp.GetDepartment() != null && emp.GetDepartment().GetDepartmentId() > 0
      ) {
        pstmt.setInt(9, emp.GetDepartment().GetDepartmentId());
      } else {
        pstmt.setNull(9, Types.INTEGER);
      }

      // WorkScheduleID (nullable FK)
      if (
        emp.GetWorkSchedule() != null &&
        emp.GetWorkSchedule().GetScheduleId() > 0
      ) {
        pstmt.setInt(10, emp.GetWorkSchedule().GetScheduleId());
      } else {
        pstmt.setNull(10, Types.INTEGER);
      }

      pstmt.setDate(
        11,
        emp.GetDateHired() != null
          ? java.sql.Date.valueOf(emp.GetDateHired())
          : null
      );

      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  /**
   * Update — modifies an existing employee row.
   * DepartmentID and WorkScheduleID are now properly driven by the EmpDetail object.
   */
  public boolean Update(Connection conn, EmpDetail emp) throws SQLException {
    String sql =
      "UPDATE Employees SET " +
      "LastName          = ?, " +
      "FirstName         = ?, " +
      "Birthday          = ?, " +
      "Email             = ?, " +
      "PhoneNo           = ?, " +
      "EmploymentStatus  = ?, " +
      "PositionID        = ?, " +
      "SupervisorID      = ?, " +
      "DepartmentID      = ?, " +
      "WorkScheduleID    = ?, " +
      "DateHired         = ? " +
      "WHERE EmployeeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, emp.GetLastName());
      pstmt.setString(2, emp.GetFirstName());

      pstmt.setDate(
        3,
        emp.GetBirthday() != null
          ? java.sql.Date.valueOf(emp.GetBirthday())
          : null
      );

      pstmt.setString(4, emp.GetEmail());
      pstmt.setString(5, emp.GetPhoneNo());

      pstmt.setInt(
        6,
        emp.GetEmpStatus() != null ? emp.GetEmpStatus().getValue() : 0
      );

      if (emp.GetPosition() != null && emp.GetPosition().GetPositionID() > 0) {
        pstmt.setLong(7, emp.GetPosition().GetPositionID());
      } else {
        pstmt.setNull(7, Types.BIGINT);
      }

      if (
        emp.GetImmSupervisor() != null &&
        emp.GetImmSupervisor().GetEmployeeId() > 0
      ) {
        pstmt.setLong(8, emp.GetImmSupervisor().GetEmployeeId());
      } else {
        pstmt.setNull(8, Types.BIGINT);
      }

      if (
        emp.GetDepartment() != null && emp.GetDepartment().GetDepartmentId() > 0
      ) {
        pstmt.setInt(9, emp.GetDepartment().GetDepartmentId());
      } else {
        pstmt.setNull(9, Types.INTEGER);
      }

      if (
        emp.GetWorkSchedule() != null &&
        emp.GetWorkSchedule().GetScheduleId() > 0
      ) {
        pstmt.setInt(10, emp.GetWorkSchedule().GetScheduleId());
      } else {
        pstmt.setNull(10, Types.INTEGER);
      }

      pstmt.setDate(
        11,
        emp.GetDateHired() != null
          ? java.sql.Date.valueOf(emp.GetDateHired())
          : null
      );

      pstmt.setLong(12, emp.GetEmployeeId());

      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * Delete — soft delete (sets Status = 0).
   */
  public boolean Delete(Connection conn, long empNo) throws SQLException {
    String sql = "UPDATE Employees SET Status = 0 WHERE EmployeeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empNo);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * GetByID — fetches a single active employee from the complete view.
   */
  public EmpDetail GetByID(Connection conn, long empNo) throws SQLException {
    String sql =
      "SELECT * FROM vw_EmployeeCompleteDetails WHERE EmployeeID = ? AND Status = 1";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empNo);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? new EmpDetail(rs) : null;
      }
    }
  }

  /**
   * GetAll — all active employees from the complete view.
   */
  public List<EmpDetail> GetAll(Connection conn) throws SQLException {
    List<EmpDetail> employees = new ArrayList<>();
    String sql = "SELECT * FROM vw_EmployeeCompleteDetails WHERE Status = 1";
    try (
      PreparedStatement pstmt = conn.prepareStatement(sql);
      ResultSet rs = pstmt.executeQuery()
    ) {
      while (rs.next()) employees.add(new EmpDetail(rs));
    }
    return employees;
  }

  public List<EmpDetail> GetAll() throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetAll(conn);
    }
  }

  /**
   * Search — filters the complete view by ID, name, or position.
   */
  public List<EmpDetail> Search(String query) throws SQLException {
    List<EmpDetail> employees = new ArrayList<>();
    String sql =
      "SELECT * FROM vw_EmployeeCompleteDetails " +
      "WHERE Status = 1 AND (" +
      "  CAST(EmployeeID AS NVARCHAR) LIKE ? OR " +
      "  LastName    LIKE ? OR " +
      "  FirstName   LIKE ? OR " +
      "  PositionName LIKE ?)";

    try (
      Connection conn = DatabaseConnector.GetConnection();
      PreparedStatement pstmt = conn.prepareStatement(sql)
    ) {
      String wildcard = "%" + query + "%";
      for (int i = 1; i <= 4; i++) pstmt.setString(i, wildcard);

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          employees.add(new EmpDetail(rs));
        }
      }
    }
    return employees;
  }

  /**
   * GetEmployeeNameById — lightweight first-name lookup for the shell greeting.
   * Avoids the heavy vw_EmployeeCompleteDetails join.
   */
  public String GetEmployeeNameById(long employeeId) {
    String sql = "SELECT FirstName FROM Employees WHERE EmployeeID = ?";

    try (
      Connection conn = DatabaseConnector.GetConnection();
      PreparedStatement pstmt = conn.prepareStatement(sql)
    ) {
      pstmt.setLong(1, employeeId);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          String firstName = rs.getString("FirstName");
          return firstName != null ? firstName.trim() : null;
        }
      }
    } catch (SQLException e) {
      System.err.println("Error fetching employee name: " + e.getMessage());
    }
    return null;
  }

  /**
   * GetBasicInfo — returns a minimal EmployeeInfo (ID + names) for the supervisor
   * dropdown and similar lightweight uses.
   */
  public EmployeeInfo GetBasicInfo(Connection conn, long empNo)
    throws SQLException {
    String sql =
      "SELECT EmployeeID, FirstName, LastName FROM Employees " +
      "WHERE EmployeeID = ? AND Status = 1";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empNo);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          EmployeeInfo info = new EmployeeInfo();
          info.SetEmployeeId(rs.getLong("EmployeeID"));
          info.SetFirstName(rs.getString("FirstName"));
          info.SetLastName(rs.getString("LastName"));
          return info;
        }
      }
    }
    return null;
  }

  /**
   * GetAllBasicInfo — for the supervisor dropdown in the Employee form.
   * Returns all active employees as lightweight stubs.
   */
  public List<EmployeeInfo> GetAllBasicInfo() throws SQLException {
    List<EmployeeInfo> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeID, FirstName, LastName FROM Employees " +
      "WHERE Status = 1 ORDER BY LastName, FirstName";

    try (
      Connection conn = DatabaseConnector.GetConnection();
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        EmployeeInfo info = new EmployeeInfo();
        info.SetEmployeeId(rs.getLong("EmployeeID"));
        info.SetFirstName(rs.getString("FirstName"));
        info.SetLastName(rs.getString("LastName"));
        list.add(info);
      }
    }
    return list;
  }
}
