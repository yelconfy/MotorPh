package Interface;

import java.sql.SQLException;

/**
 * Port for the employee-side MONTHLY statutory figures payroll needs, decoupling
 * PayrollCalculator from JDBC. Implemented by Processes.StatutoryRateProvider,
 * which wraps StatutoryRateDAO bound to a single Connection + as-of date.
 *
 * WithholdingTax takes the already-computed SEMI-MONTHLY taxable income; the
 * pay-frequency bracket (semi-monthly) is an implementation detail the provider
 * owns, so the calculator never sees a PayFrequency code.
 *
 * Tests can supply a fake implementation to exercise PayrollCalculator without
 * a live database.
 */
public interface IStatutoryRates {

  double SssEmployeeShare(double monthlyBasic) throws SQLException;

  double PhilHealthEmployeeShare(double monthlyBasic) throws SQLException;

  double PagIbigEmployeeShare(double monthlyBasic) throws SQLException;

  double WithholdingTax(double semiMonthlyTaxableIncome) throws SQLException;
}