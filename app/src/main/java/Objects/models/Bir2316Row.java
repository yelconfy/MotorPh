package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Read-only projection of vw_Bir2316 (script 17 - Reporting layer). One row per
 * employee per year — the data for a BIR Form 2316 certificate.
 *
 * All figures are from frozen payslip snapshots except TaxDue / OverUnderWithheld,
 * which the view computes from the 2024 TRAIN annual brackets applied to
 * TaxableCompensation. TaxWithheld is the actual sum deducted.
 */
public class Bir2316Row {

  private final long employeeNo;
  private final String employeeFullName;
  private final String tin;
  private final String position;
  private final String registeredAddress;
  private final int payYear;

  private final double grossCompensation;
  private final double taxableAllowances;
  private final double nonTaxableAllowances;

  private final double sssContribution;
  private final double philHealthContribution;
  private final double pagIbigContribution;
  private final double mandatoryContributions;

  private final double thirteenthMonthPay;
  private final double thirteenthMonthNonTaxable;
  private final double thirteenthMonthTaxable;

  private final double taxableCompensation;
  private final double taxWithheld;
  private final double taxDue;
  private final double overUnderWithheld;

  public Bir2316Row(ResultSet rs) throws SQLException {
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.tin = rs.getString("TIN");
    this.position = rs.getString("Position");
    this.registeredAddress = rs.getString("RegisteredAddress");
    this.payYear = rs.getInt("PayYear");

    this.grossCompensation = rs.getDouble("GrossCompensation");
    this.taxableAllowances = rs.getDouble("TaxableAllowances");
    this.nonTaxableAllowances = rs.getDouble("NonTaxableAllowances");

    this.sssContribution = rs.getDouble("SssContribution");
    this.philHealthContribution = rs.getDouble("PhilHealthContribution");
    this.pagIbigContribution = rs.getDouble("PagIbigContribution");
    this.mandatoryContributions = rs.getDouble("MandatoryContributions");

    this.thirteenthMonthPay = rs.getDouble("ThirteenthMonthPay");
    this.thirteenthMonthNonTaxable = rs.getDouble("ThirteenthMonthNonTaxable");
    this.thirteenthMonthTaxable = rs.getDouble("ThirteenthMonthTaxable");

    this.taxableCompensation = rs.getDouble("TaxableCompensation");
    this.taxWithheld = rs.getDouble("TaxWithheld");
    this.taxDue = rs.getDouble("TaxDue");
    this.overUnderWithheld = rs.getDouble("OverUnderWithheld");
  }

  public long GetEmployeeNo() { return employeeNo; }
  public String GetEmployeeFullName() { return employeeFullName; }
  public String GetTin() { return tin; }
  public String GetPosition() { return position; }
  public String GetRegisteredAddress() { return registeredAddress; }
  public int GetPayYear() { return payYear; }

  public double GetGrossCompensation() { return grossCompensation; }
  public double GetTaxableAllowances() { return taxableAllowances; }
  public double GetNonTaxableAllowances() { return nonTaxableAllowances; }

  public double GetSssContribution() { return sssContribution; }
  public double GetPhilHealthContribution() { return philHealthContribution; }
  public double GetPagIbigContribution() { return pagIbigContribution; }
  public double GetMandatoryContributions() { return mandatoryContributions; }

  public double GetThirteenthMonthPay() { return thirteenthMonthPay; }
  public double GetThirteenthMonthNonTaxable() { return thirteenthMonthNonTaxable; }
  public double GetThirteenthMonthTaxable() { return thirteenthMonthTaxable; }

  public double GetTaxableCompensation() { return taxableCompensation; }
  public double GetTaxWithheld() { return taxWithheld; }
  public double GetTaxDue() { return taxDue; }
  public double GetOverUnderWithheld() { return overUnderWithheld; }
}