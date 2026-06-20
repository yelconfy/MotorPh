package Objects.models;

import Objects.enums.Status.PayrollPeriodStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Maps to Payroll_Period (05 - Payroll Tables).
 *
 * Columns: PayrollPeriodID, PeriodName, StartDate, EndDate, PayDate, Status
 * Status: 0=Open, 1=Processing, 2=Closed, 3=Paid
 *
 * Finalize-lock (Status >= 1 → read-only) is enforced in APP LOGIC, not DB.
 */
public class PayrollPeriod extends BaseObject {

  private long PayrollPeriodId;
  private String PeriodName;
  private LocalDate StartDate;
  private LocalDate EndDate;
  private LocalDate PayDate; // nullable
  private PayrollPeriodStatus Status;

  public PayrollPeriod() {}

  public PayrollPeriod(ResultSet rs) throws SQLException {
    this.PayrollPeriodId = rs.getLong("PayrollPeriodID");
    this.PeriodName = rs.getString("PeriodName");
    this.Status = PayrollPeriodStatus.fromInt(rs.getInt("Status"));

    java.sql.Date sd = rs.getDate("StartDate");
    this.StartDate = (sd != null) ? sd.toLocalDate() : null;

    java.sql.Date ed = rs.getDate("EndDate");
    this.EndDate = (ed != null) ? ed.toLocalDate() : null;

    java.sql.Date pd = rs.getDate("PayDate");
    this.PayDate = (pd != null) ? pd.toLocalDate() : null;
  }

  @Override
  public Object GetIdentity() {
    return GetPayrollPeriodId();
  }

  @Override
  public String toString() {
    return PeriodName != null
      ? PeriodName
      : (StartDate != null ? StartDate + " – " + EndDate : "");
  }

  /** True when this period is locked (Status >= Processing). */
  public boolean IsLocked() {
    return (
      Status != null &&
      Status.getValue() >= PayrollPeriodStatus.PROCESSING.getValue()
    );
  }

  public long GetPayrollPeriodId() {
    return PayrollPeriodId;
  }

  public void SetPayrollPeriodId(long v) {
    this.PayrollPeriodId = v;
  }

  public String GetPeriodName() {
    return PeriodName;
  }

  public void SetPeriodName(String v) {
    this.PeriodName = v;
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

  public LocalDate GetPayDate() {
    return PayDate;
  }

  public void SetPayDate(LocalDate v) {
    this.PayDate = v;
  }

  public PayrollPeriodStatus GetPayrollStatus() {
    return Status;
  }

  public void SetPayrollStatus(PayrollPeriodStatus v) {
    this.Status = v;
  }
}
