package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Maps to EmployeeSalary (versioned) / vw_CurrentSalary.
 *
 * SCHEMA REALITY CHECK (02 - Core Employee Tables):
 *   EmployeeSalary: SalaryID, EmployeeID, BasicSalary, HourlyRate, EffectiveDate
 *
 * RiceSubsidy / PhoneAllowance / ClothingAllowance are NOT columns here and are
 * NOT mirrored onto this object. They live in Employee_Allowance and are loaded
 * via AllowanceDAO into EmpDetail.Allowances. Read them through
 * EmpDetail.GetTotalAllowances() / GetAllowanceAmount(name) — never from here.
 *
 * GrossSemiMonthlyRate is a derived value, not stored.
 */
public class EmployeeSalaryInfo extends BaseObject {

  private long SalaryId;
  private double BasicSalary;
  private double HourlyRate;
  private LocalDate EffectiveDate;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public EmployeeSalaryInfo() {}

  /**
   * Smart Constructor — maps from vw_CurrentSalary or direct EmployeeSalary query.
   * Column names match both the base table and the view alias.
   */
  public EmployeeSalaryInfo(ResultSet rs) throws SQLException {
    this.SalaryId = rs.getLong("SalaryID");
    this.BasicSalary = rs.getDouble("BasicSalary");
    this.HourlyRate = rs.getDouble("HourlyRate");

    java.sql.Date ed = rs.getDate("EffectiveDate");
    this.EffectiveDate = (ed != null) ? ed.toLocalDate() : null;
  }

  @Override
  public Object GetIdentity() {
    return GetSalaryId();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Back-calculates HourlyRate from BasicSalary when the caller sets salary
   * manually (e.g. from the EmpMgmt form) and no DB row exists yet.
   * Uses the standard 21.75 working days × 8 hours formula.
   */
  public void CalculateHourlyRate() {
    if (this.BasicSalary > 0) {
      this.HourlyRate = this.BasicSalary / 21.75 / 8.0;
    }
  }

  /** Gross semi-monthly is always derived — never stored. */
  public double GetGrossSemiMonthlyRate() {
    return this.BasicSalary / 2.0;
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public long GetSalaryId() {
    return SalaryId;
  }

  public void SetSalaryId(long v) {
    this.SalaryId = v;
  }

  public double GetBasicSalary() {
    return BasicSalary;
  }

  public void SetBasicSalary(double v) {
    this.BasicSalary = v;
  }

  public double GetHourlyRate() {
    return HourlyRate;
  }

  public void SetHourlyRate(double v) {
    this.HourlyRate = v;
  }

  public LocalDate GetEffectiveDate() {
    return EffectiveDate;
  }

  public void SetEffectiveDate(LocalDate v) {
    this.EffectiveDate = v;
  }
}