package Objects.models;

import Objects.enums.Status.RequestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Maps to Overtime_Request (04 - Leave & Compensation Tables).
 *
 * Columns: OvertimeRequestID, EmployeeID, OvertimeDate, OvertimeStart,
 *          OvertimeEnd, Reason, Status, ActionedBy, DateFiled, DateActioned
 */
public class OvertimeRequest extends BaseObject {

  private long OvertimeRequestId;
  private long EmployeeId;
  private LocalDate OvertimeDate;
  private LocalTime OvertimeStart;
  private LocalTime OvertimeEnd;
  private String Reason;
  private RequestStatus Status;
  private Long ActionedBy;
  private LocalDateTime DateFiled;
  private LocalDateTime DateActioned;

  public static final String[] DISPLAY_FIELDS = {
    "OvertimeRequestId",
    "EmployeeId",
    "OvertimeDate",
    "OvertimeStart",
    "OvertimeEnd",
    "Status",
  };

  public OvertimeRequest() {}

  public OvertimeRequest(ResultSet rs) throws SQLException {
    this.OvertimeRequestId = rs.getLong("OvertimeRequestID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.Reason = rs.getString("Reason");
    this.Status = RequestStatus.fromInt(rs.getInt("Status"));

    java.sql.Date od = rs.getDate("OvertimeDate");
    this.OvertimeDate = (od != null) ? od.toLocalDate() : null;

    java.sql.Time os = rs.getTime("OvertimeStart");
    this.OvertimeStart = (os != null) ? os.toLocalTime() : null;

    java.sql.Time oe = rs.getTime("OvertimeEnd");
    this.OvertimeEnd = (oe != null) ? oe.toLocalTime() : null;

    long actionedBy = rs.getLong("ActionedBy");
    this.ActionedBy = rs.wasNull() ? null : actionedBy;

    java.sql.Timestamp filed = rs.getTimestamp("DateFiled");
    this.DateFiled = (filed != null) ? filed.toLocalDateTime() : null;

    java.sql.Timestamp actioned = rs.getTimestamp("DateActioned");
    this.DateActioned = (actioned != null) ? actioned.toLocalDateTime() : null;
  }

  @Override
  public Object GetIdentity() {
    return GetOvertimeRequestId();
  }

  /** Duration of the overtime request in minutes. */
  public long GetOvertimeMinutes() {
    if (OvertimeStart == null || OvertimeEnd == null) return 0;
    long minutes = java.time.Duration.between(
      OvertimeStart,
      OvertimeEnd
    ).toMinutes();
    return minutes < 0 ? minutes + 24 * 60 : minutes; // crosses midnight guard
  }

  /** Duration in hours (decimal). */
  public double GetOvertimeHours() {
    return GetOvertimeMinutes() / 60.0;
  }

  public long GetOvertimeRequestId() {
    return OvertimeRequestId;
  }

  public void SetOvertimeRequestId(long v) {
    this.OvertimeRequestId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public LocalDate GetOvertimeDate() {
    return OvertimeDate;
  }

  public void SetOvertimeDate(LocalDate v) {
    this.OvertimeDate = v;
  }

  public LocalTime GetOvertimeStart() {
    return OvertimeStart;
  }

  public void SetOvertimeStart(LocalTime v) {
    this.OvertimeStart = v;
  }

  public LocalTime GetOvertimeEnd() {
    return OvertimeEnd;
  }

  public void SetOvertimeEnd(LocalTime v) {
    this.OvertimeEnd = v;
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
