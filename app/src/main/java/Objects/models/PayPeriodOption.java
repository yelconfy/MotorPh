package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Lightweight option for the Payroll Summary period picker. Distinct
 * (PayYear, PayMonth) pairs that actually have payslips, drawn from
 * vw_MonthlyPayrollSummary. toString() is the dropdown label ("June 2024").
 */
public class PayPeriodOption {

  private final int year;
  private final int month;
  private final String label;

  public PayPeriodOption(int year, int month, String monthName) {
    this.year = year;
    this.month = month;
    this.label = (monthName != null ? monthName : ("Month " + month)) + " " + year;
  }

  /** Smart constructor — maps a DISTINCT PayYear/PayMonth/PayMonthName row. */
  public PayPeriodOption(ResultSet rs) throws SQLException {
    this(rs.getInt("PayYear"), rs.getInt("PayMonth"), rs.getString("PayMonthName"));
  }

  public int GetYear() {
    return year;
  }

  public int GetMonth() {
    return month;
  }

  @Override
  public String toString() {
    return label;
  }
}