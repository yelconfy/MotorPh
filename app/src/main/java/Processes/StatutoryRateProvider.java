package Processes;

import DataAccess.StatutoryRateDAO;
import Interface.IStatutoryRates;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * DB-backed IStatutoryRates: a thin adapter over StatutoryRateDAO, bound to a
 * single Connection and as-of date for the duration of one payroll run.
 *
 * This is the ONLY statutory piece that touches the database. PayrollCalculator
 * depends on the IStatutoryRates port, not on this class or the DAO, so the
 * money math stays unit-testable with a fake.
 *
 * Constructed per-run by PayrollProcess over the run's shared read Connection,
 * so every employee's rate lookups in a period reuse one connection (replacing
 * the old per-employee connection-open that lived inside ComputeEmployeeDeductions).
 */
public final class StatutoryRateProvider implements IStatutoryRates {

  private final StatutoryRateDAO dao;
  private final Connection conn;
  private final LocalDate asOf;

  public StatutoryRateProvider(StatutoryRateDAO dao, Connection conn, LocalDate asOf) {
    this.dao = dao;
    this.conn = conn;
    this.asOf = asOf;
  }

  @Override
  public double SssEmployeeShare(double monthlyBasic) throws SQLException {
    return dao.GetSssEmployeeShare(conn, monthlyBasic, asOf);
  }

  @Override
  public double PhilHealthEmployeeShare(double monthlyBasic) throws SQLException {
    return dao.GetPhilHealthEmployeeShare(conn, monthlyBasic, asOf);
  }

  @Override
  public double PagIbigEmployeeShare(double monthlyBasic) throws SQLException {
    return dao.GetPagIbigEmployeeShare(conn, monthlyBasic, asOf);
  }

  @Override
  public double WithholdingTax(double semiMonthlyTaxableIncome) throws SQLException {
    return dao.GetWithholdingTax(
      conn, semiMonthlyTaxableIncome, StatutoryRateDAO.PAYFREQ_SEMI_MONTHLY, asOf
    );
  }
}