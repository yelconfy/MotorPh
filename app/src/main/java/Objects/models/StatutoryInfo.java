package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to StatutoryDetails (02 - Core Employee Tables).
 *   StatutoryDetails: EmployeeID (PK), SssNo, PhilHealthNo, TinNo, PagIbigNo
 *
 * CHANGE: now extends BaseObject (like every other table-backed model object)
 * and carries EmployeeID so GetIdentity() can return the table's real primary
 * key. Previously this class extended nothing and had no identity, which was
 * the one remaining inconsistency in the model layer.
 *
 * EmployeeID stays 0 for manually-built instances (e.g. the add-employee form,
 * where the ID is not known until insert) — consistent with how EmpDetail
 * handles new records.
 */
public class StatutoryInfo extends BaseObject {

  private long employeeId;
  private String sssNo;
  private String philHealthNo;
  private String tinNo;
  private String pagIbigNo;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public StatutoryInfo() {}

  public StatutoryInfo(
    String sssNo,
    String philHealthNo,
    String tinNo,
    String pagIbigNo
  ) {
    this.sssNo = sssNo;
    this.philHealthNo = philHealthNo;
    this.tinNo = tinNo;
    this.pagIbigNo = pagIbigNo;
  }

  /**
   * Smart Constructor — maps from SELECT * FROM StatutoryDetails, which
   * includes EmployeeID.
   */
  public StatutoryInfo(ResultSet rs) throws SQLException {
    this.employeeId = rs.getLong("EmployeeID");
    this.sssNo = rs.getString("SssNo");
    this.philHealthNo = rs.getString("PhilHealthNo");
    this.tinNo = rs.getString("TinNo");
    this.pagIbigNo = rs.getString("PagIbigNo");
  }

  @Override
  public Object GetIdentity() {
    return GetEmployeeId();
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public long GetEmployeeId() {
    return employeeId;
  }

  public void SetEmployeeId(long employeeId) {
    this.employeeId = employeeId;
  }

  public String GetSssNo() {
    return sssNo;
  }

  public String GetPhilHealthNo() {
    return philHealthNo;
  }

  public String GetTinNo() {
    return tinNo;
  }

  public String GetPagIbigNo() {
    return pagIbigNo;
  }

  public void SetSssNo(String sssNo) {
    this.sssNo = sssNo;
  }

  public void SetPhilHealthNo(String philHealthNo) {
    this.philHealthNo = philHealthNo;
  }

  public void SetTinNo(String tinNo) {
    this.tinNo = tinNo;
  }

  public void SetPagIbigNo(String pagIbigNo) {
    this.pagIbigNo = pagIbigNo;
  }
}
