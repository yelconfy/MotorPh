package Objects.models;

import Objects.enums.Status.LoanStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to Employee_Loan (04 - Leave & Compensation Tables).
 *
 * Columns: LoanID, EmployeeID, DeductionTypeID, PrincipalAmount, InterestRate,
 *          TotalPayable, InstallmentAmount, NumberOfTerms, StartDate, Status,
 *          LastUpdatedBy, LastUpdatedDate
 *
 * Outstanding balance is NOT stored here — use vw_LoanBalance (or
 * LoanDAO.GetOutstandingBalance) which subtracts paid payroll deductions.
 *
 * DeductionTypeName is optionally populated when the DAO JOINs Deduction_Type.
 */
public class EmployeeLoan extends BaseObject {

  private long LoanId;
  private long EmployeeId;
  private int DeductionTypeId;
  private String DeductionTypeName; // from JOIN — null if not joined
  private double PrincipalAmount;
  private double InterestRate; // nullable in DB — 0.0 when null
  private double TotalPayable;
  private double InstallmentAmount;
  private int NumberOfTerms;
  private LocalDate StartDate;
  private LoanStatus Status;
  private String LastUpdatedBy;
  private LocalDateTime LastUpdatedDate;

  public EmployeeLoan() {}

  public EmployeeLoan(ResultSet rs) throws SQLException {
    this.LoanId = rs.getLong("LoanID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.DeductionTypeId = rs.getInt("DeductionTypeID");
    this.PrincipalAmount = rs.getDouble("PrincipalAmount");
    this.InterestRate = rs.getDouble("InterestRate");
    this.TotalPayable = rs.getDouble("TotalPayable");
    this.InstallmentAmount = rs.getDouble("InstallmentAmount");
    this.NumberOfTerms = rs.getInt("NumberOfTerms");
    this.Status = LoanStatus.fromInt(rs.getInt("Status"));
    this.LastUpdatedBy = rs.getString("LastUpdatedBy");

    java.sql.Date sd = rs.getDate("StartDate");
    this.StartDate = (sd != null) ? sd.toLocalDate() : null;

    java.sql.Timestamp lud = rs.getTimestamp("LastUpdatedDate");
    this.LastUpdatedDate = (lud != null) ? lud.toLocalDateTime() : null;

    // Optional JOIN column
    try {
      this.DeductionTypeName = rs.getString("DeductionName");
    } catch (SQLException ignored) {}
  }

  @Override
  public Object GetIdentity() {
    return GetLoanId();
  }

  /** Computed remaining terms = total terms minus months elapsed since start. */
  public int GetRemainingTerms() {
    if (StartDate == null) return NumberOfTerms;
    long monthsElapsed = java.time.Period.between(
      StartDate,
      LocalDate.now()
    ).toTotalMonths();
    return (int) Math.max(0, NumberOfTerms - monthsElapsed);
  }

  public long GetLoanId() {
    return LoanId;
  }

  public void SetLoanId(long v) {
    this.LoanId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public int GetDeductionTypeId() {
    return DeductionTypeId;
  }

  public void SetDeductionTypeId(int v) {
    this.DeductionTypeId = v;
  }

  public String GetDeductionTypeName() {
    return DeductionTypeName;
  }

  public void SetDeductionTypeName(String v) {
    this.DeductionTypeName = v;
  }

  public double GetPrincipalAmount() {
    return PrincipalAmount;
  }

  public void SetPrincipalAmount(double v) {
    this.PrincipalAmount = v;
  }

  public double GetInterestRate() {
    return InterestRate;
  }

  public void SetInterestRate(double v) {
    this.InterestRate = v;
  }

  public double GetTotalPayable() {
    return TotalPayable;
  }

  public void SetTotalPayable(double v) {
    this.TotalPayable = v;
  }

  public double GetInstallmentAmount() {
    return InstallmentAmount;
  }

  public void SetInstallmentAmount(double v) {
    this.InstallmentAmount = v;
  }

  public int GetNumberOfTerms() {
    return NumberOfTerms;
  }

  public void SetNumberOfTerms(int v) {
    this.NumberOfTerms = v;
  }

  public LocalDate GetStartDate() {
    return StartDate;
  }

  public void SetStartDate(LocalDate v) {
    this.StartDate = v;
  }

  public LoanStatus GetLoanStatus() {
    return Status;
  }

  public void SetLoanStatus(LoanStatus v) {
    this.Status = v;
  }

  public String GetLastUpdatedBy() {
    return LastUpdatedBy;
  }

  public void SetLastUpdatedBy(String v) {
    this.LastUpdatedBy = v;
  }

  public LocalDateTime GetLastUpdatedDate() {
    return LastUpdatedDate;
  }

  public void SetLastUpdatedDate(LocalDateTime v) {
    this.LastUpdatedDate = v;
  }
}
