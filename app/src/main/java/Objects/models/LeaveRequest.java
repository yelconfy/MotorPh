package Objects.models;

import Objects.enums.Status.RequestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to Leave_Request (04 - Leave & Compensation Tables).
 *
 * Columns: LeaveRequestID, EmployeeID, LeaveTypeID, StartDate, EndDate,
 *          NumberOfDays, Reason, Status, ActionedBy, DateFiled, DateActioned
 *
 * LeaveTypeName is populated when the DAO JOINs Leave_Type (list/detail queries).
 * ActionedByName is populated when the DAO JOINs Users (optional).
 */
public class LeaveRequest extends BaseObject {

  private long LeaveRequestId;
  private long EmployeeId;
  private int LeaveTypeId;
  private String LeaveTypeName; // from JOIN — null if not joined
  private LocalDate StartDate;
  private LocalDate EndDate;
  private double NumberOfDays;
  private String Reason;
  private RequestStatus Status;
  private Long ActionedBy; // nullable FK → Users.UserID
  private LocalDateTime DateFiled;
  private LocalDateTime DateActioned; // nullable

  public static final String[] DISPLAY_FIELDS = {
    "LeaveRequestId",
    "EmployeeId",
    "LeaveTypeName",
    "StartDate",
    "EndDate",
    "NumberOfDays",
    "Status",
  };

  public LeaveRequest() {}

  /**
   * Smart Constructor — works for both plain Leave_Request queries
   * and queries that JOIN Leave_Type (LeaveTypeName present).
   */
  public LeaveRequest(ResultSet rs) throws SQLException {
    this.LeaveRequestId = rs.getLong("LeaveRequestID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.LeaveTypeId = rs.getInt("LeaveTypeID");
    this.NumberOfDays = rs.getDouble("NumberOfDays");
    this.Reason = rs.getString("Reason");
    this.Status = RequestStatus.fromInt(rs.getInt("Status"));

    java.sql.Date sd = rs.getDate("StartDate");
    this.StartDate = (sd != null) ? sd.toLocalDate() : null;

    java.sql.Date ed = rs.getDate("EndDate");
    this.EndDate = (ed != null) ? ed.toLocalDate() : null;

    long actionedBy = rs.getLong("ActionedBy");
    this.ActionedBy = rs.wasNull() ? null : actionedBy;

    java.sql.Timestamp filed = rs.getTimestamp("DateFiled");
    this.DateFiled = (filed != null) ? filed.toLocalDateTime() : null;

    java.sql.Timestamp actioned = rs.getTimestamp("DateActioned");
    this.DateActioned = (actioned != null) ? actioned.toLocalDateTime() : null;

    // Optional JOIN columns — safe to skip if not in result set
    try {
      this.LeaveTypeName = rs.getString("LeaveTypeName");
    } catch (SQLException ignored) {}
  }

  @Override
  public Object GetIdentity() {
    return GetLeaveRequestId();
  }

  public long GetLeaveRequestId() {
    return LeaveRequestId;
  }

  public void SetLeaveRequestId(long v) {
    this.LeaveRequestId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public int GetLeaveTypeId() {
    return LeaveTypeId;
  }

  public void SetLeaveTypeId(int v) {
    this.LeaveTypeId = v;
  }

  public String GetLeaveTypeName() {
    return LeaveTypeName;
  }

  public void SetLeaveTypeName(String v) {
    this.LeaveTypeName = v;
  }

  public LocalDate GetStartDate() {
    return StartDate;
  }

  public void SetStartDate(LocalDate v) {
    this.StartDate = v;
  }

  public LocalDate GetEndDate() {
    return EndDate;
  }

  public void SetEndDate(LocalDate v) {
    this.EndDate = v;
  }

  public double GetNumberOfDays() {
    return NumberOfDays;
  }

  public void SetNumberOfDays(double v) {
    this.NumberOfDays = v;
  }

  public String GetReason() {
    return Reason;
  }

  public void SetReason(String v) {
    this.Reason = v;
  }

  public RequestStatus GetStatus() {
    return Status;
  }

  public void SetStatus(RequestStatus v) {
    this.Status = v;
  }

  public Long GetActionedBy() {
    return ActionedBy;
  }

  public void SetActionedBy(Long v) {
    this.ActionedBy = v;
  }

  public LocalDateTime GetDateFiled() {
    return DateFiled;
  }

  public void SetDateFiled(LocalDateTime v) {
    this.DateFiled = v;
  }

  public LocalDateTime GetDateActioned() {
    return DateActioned;
  }

  public void SetDateActioned(LocalDateTime v) {
    this.DateActioned = v;
  }
}
