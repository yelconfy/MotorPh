package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Leave_Type (01 - Reference Tables).
 *
 * Columns: LeaveTypeID, LeaveTypeName, IsPaid, DefaultDaysPerYear,
 *          CarryOverAllowed, MaxCarryOverDays, Status
 */
public class LeaveTypeInfo extends BaseObject {

  private int LeaveTypeId;
  private String LeaveTypeName;
  private boolean IsPaid;
  private double DefaultDaysPerYear;
  private boolean CarryOverAllowed;
  private double MaxCarryOverDays; // nullable in DB — 0.0 when null

  public LeaveTypeInfo() {}

  public LeaveTypeInfo(ResultSet rs) throws SQLException {
    this.LeaveTypeId = rs.getInt("LeaveTypeID");
    this.LeaveTypeName = rs.getString("LeaveTypeName");
    this.IsPaid = rs.getBoolean("IsPaid");
    this.DefaultDaysPerYear = rs.getDouble("DefaultDaysPerYear");
    this.CarryOverAllowed = rs.getBoolean("CarryOverAllowed");
    this.MaxCarryOverDays = rs.getDouble("MaxCarryOverDays"); // 0.0 if NULL
    this.SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetLeaveTypeId();
  }

  @Override
  public String toString() {
    return LeaveTypeName != null ? LeaveTypeName : "";
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

  public boolean IsPaid() {
    return IsPaid;
  }

  public void SetPaid(boolean v) {
    this.IsPaid = v;
  }

  public double GetDefaultDaysPerYear() {
    return DefaultDaysPerYear;
  }

  public void SetDefaultDaysPerYear(double v) {
    this.DefaultDaysPerYear = v;
  }

  public boolean IsCarryOverAllowed() {
    return CarryOverAllowed;
  }

  public void SetCarryOverAllowed(boolean v) {
    this.CarryOverAllowed = v;
  }

  public double GetMaxCarryOverDays() {
    return MaxCarryOverDays;
  }

  public void SetMaxCarryOverDays(double v) {
    this.MaxCarryOverDays = v;
  }
}
