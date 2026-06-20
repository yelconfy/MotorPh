package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Departments (01 - Reference Tables).
 * Columns: DepartmentID, DepartmentCode, DepartmentName
 */
public class DepartmentInfo extends BaseObject {

  private int DepartmentId;
  private String DepartmentCode;
  private String DepartmentName;

  public DepartmentInfo() {}

  public DepartmentInfo(ResultSet rs) throws SQLException {
    this.DepartmentId = rs.getInt("DepartmentID");
    this.DepartmentCode = rs.getString("DepartmentCode");
    this.DepartmentName = rs.getString("DepartmentName");
  }

  @Override
  public Object GetIdentity() {
    return GetDepartmentId();
  }

  @Override
  public String toString() {
    return DepartmentName != null ? DepartmentName : "";
  }

  public int GetDepartmentId() {
    return DepartmentId;
  }

  public void SetDepartmentId(int v) {
    this.DepartmentId = v;
  }

  public String GetDepartmentCode() {
    return DepartmentCode;
  }

  public void SetDepartmentCode(String v) {
    this.DepartmentCode = v;
  }

  public String GetDepartmentName() {
    return DepartmentName;
  }

  public void SetDepartmentName(String v) {
    this.DepartmentName = v;
  }
}
