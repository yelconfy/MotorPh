package Objects.models;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Read-only projection of vw_LoanLedgerReport (script 17 - Reporting layer).
 * One row per employee loan: terms plus current outstanding balance. Balance
 * math lives in vw_LoanBalance (script 06); this is display only.
 */
public class LoanLedgerRow {

  private final long loanId;
  private final long employeeNo;
  private final String employeeFullName;
  private final String department;
  private final String loanType;
  private final double principal;
  private final double totalPayable;
  private final double amountPaid;
  private final double outstandingBalance;
  private final double installment;
  private final int terms;
  private final LocalDate startDate;
  private final int statusCode;
  private final String statusLabel;

  public LoanLedgerRow(ResultSet rs) throws SQLException {
    this.loanId = rs.getLong("LoanID");
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.department = rs.getString("Department");
    this.loanType = rs.getString("LoanType");
    this.principal = rs.getDouble("Principal");
    this.totalPayable = rs.getDouble("TotalPayable");
    this.amountPaid = rs.getDouble("AmountPaid");
    this.outstandingBalance = rs.getDouble("OutstandingBalance");
    this.installment = rs.getDouble("Installment");
    this.terms = rs.getInt("Terms");
    Date sd = rs.getDate("StartDate");
    this.startDate = (sd != null) ? sd.toLocalDate() : null;
    this.statusCode = rs.getInt("StatusCode");
    this.statusLabel = rs.getString("StatusLabel");
  }

  public long GetLoanId() { return loanId; }
  public long GetEmployeeNo() { return employeeNo; }
  public String GetEmployeeFullName() { return employeeFullName; }
  public String GetDepartment() { return department; }
  public String GetLoanType() { return loanType; }
  public double GetPrincipal() { return principal; }
  public double GetTotalPayable() { return totalPayable; }
  public double GetAmountPaid() { return amountPaid; }
  public double GetOutstandingBalance() { return outstandingBalance; }
  public double GetInstallment() { return installment; }
  public int GetTerms() { return terms; }
  public LocalDate GetStartDate() { return startDate; }
  public int GetStatusCode() { return statusCode; }
  public String GetStatusLabel() { return statusLabel; }
}