package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

public class EmpDeductions {

  private double withholdingTax;
  private double sssContribution;
  private double philHealthContribution;
  private double pagIbigContribution;
  private double latesDeduction;
  private double absencesDeduction;
  private double totalDeductions;
  private double undertimeDeduction;

  // <editor-fold defaultstate="collapsed" desc="Constructors">
  // Clean default constructor (for CSV or manual calc)
  public EmpDeductions() {}

  // Smart Constructor (for Database)
  public EmpDeductions(ResultSet rs) throws SQLException {
    this.withholdingTax = rs.getDouble("WithholdingTax");
    this.sssContribution = rs.getDouble("SSS");
    this.philHealthContribution = rs.getDouble("PhilHealth");
    this.pagIbigContribution = rs.getDouble("PagIbig");
    this.latesDeduction = rs.getDouble("Lates");
    this.absencesDeduction = rs.getDouble("Absences");
    this.totalDeductions = rs.getDouble("TotalDeductions");
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Getters">
  public double GetTotalDeductions() {
    return totalDeductions;
  }

  public double GetWithholdingTax() {
    return withholdingTax;
  }

  public double GetSssContribution() {
    return sssContribution;
  }

  public double GetPhilHealthContribution() {
    return philHealthContribution;
  }

  public double GetPagIbigContribution() {
    return pagIbigContribution;
  }

  public double GetLatesDeduction() {
    return latesDeduction;
  }

  public double GetAbsencesDeduction() {
    return absencesDeduction;
  }

  public double GetUndertimeDeduction() {
    return undertimeDeduction;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Setters">
  public void SetWithholdingTax(double withholdingTax) {
    this.withholdingTax = withholdingTax;
  }

  public void SetSssContribution(double sssContribution) {
    this.sssContribution = sssContribution;
  }

  public void SetPhilHealthContribution(double philHealthContribution) {
    this.philHealthContribution = philHealthContribution;
  }

  public void SetPagIbigContribution(double pagIbigContribution) {
    this.pagIbigContribution = pagIbigContribution;
  }

  public void SetLatesDeduction(double latesDeduction) {
    this.latesDeduction = latesDeduction;
  }

  public void SetAbsencesDeduction(double absencesDeduction) {
    this.absencesDeduction = absencesDeduction;
  }

  public void SetTotalDeductions(double totalDeductions) {
    this.totalDeductions = totalDeductions;
  }

  public void SetUndertimeDeduction(double v) {
    this.undertimeDeduction = v;
  }
  // </editor-fold>
}
