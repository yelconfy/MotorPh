package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Read-only projection of vw_StatutoryRemittance (script 17 - Reporting layer).
 * One row per employee per calendar month, carrying all three agencies'
 * employee share / employer share / total. The three form renderers
 * (SSS R-3, PhilHealth RF-1, Pag-IBIG M1-1) each read the columns for their
 * own agency off this same row.
 *
 * Employer shares are re-derived in the view (Payroll_Deduction stores the
 * employee side only): SSS from the contribution table bracket; PhilHealth and
 * Pag-IBIG equal to the employee share (50/50 and 2%/2% respectively for 2024).
 */
public class StatutoryRemittanceRow {

  private final long employeeNo;
  private final String employeeFullName;
  private final String sssNo;
  private final String philHealthNo;
  private final String pagIbigNo;

  private final int payYear;
  private final int payMonth;

  private final double sssEmployeeShare;
  private final double sssEmployerShare;
  private final double sssTotal;

  private final double phicEmployeeShare;
  private final double phicEmployerShare;
  private final double phicTotal;

  private final double hdmfEmployeeShare;
  private final double hdmfEmployerShare;
  private final double hdmfTotal;

  public StatutoryRemittanceRow(ResultSet rs) throws SQLException {
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.sssNo = rs.getString("SssNo");
    this.philHealthNo = rs.getString("PhilHealthNo");
    this.pagIbigNo = rs.getString("PagIbigNo");

    this.payYear = rs.getInt("PayYear");
    this.payMonth = rs.getInt("PayMonth");

    this.sssEmployeeShare = rs.getDouble("SssEmployeeShare");
    this.sssEmployerShare = rs.getDouble("SssEmployerShare");
    this.sssTotal = rs.getDouble("SssTotal");

    this.phicEmployeeShare = rs.getDouble("PhicEmployeeShare");
    this.phicEmployerShare = rs.getDouble("PhicEmployerShare");
    this.phicTotal = rs.getDouble("PhicTotal");

    this.hdmfEmployeeShare = rs.getDouble("HdmfEmployeeShare");
    this.hdmfEmployerShare = rs.getDouble("HdmfEmployerShare");
    this.hdmfTotal = rs.getDouble("HdmfTotal");
  }

  public long GetEmployeeNo() {
    return employeeNo;
  }

  public String GetEmployeeFullName() {
    return employeeFullName;
  }

  public String GetSssNo() {
    return sssNo;
  }

  public String GetPhilHealthNo() {
    return philHealthNo;
  }

  public String GetPagIbigNo() {
    return pagIbigNo;
  }

  public int GetPayYear() {
    return payYear;
  }

  public int GetPayMonth() {
    return payMonth;
  }

  public double GetSssEmployeeShare() {
    return sssEmployeeShare;
  }

  public double GetSssEmployerShare() {
    return sssEmployerShare;
  }

  public double GetSssTotal() {
    return sssTotal;
  }

  public double GetPhicEmployeeShare() {
    return phicEmployeeShare;
  }

  public double GetPhicEmployerShare() {
    return phicEmployerShare;
  }

  public double GetPhicTotal() {
    return phicTotal;
  }

  public double GetHdmfEmployeeShare() {
    return hdmfEmployeeShare;
  }

  public double GetHdmfEmployerShare() {
    return hdmfEmployerShare;
  }

  public double GetHdmfTotal() {
    return hdmfTotal;
  }
}