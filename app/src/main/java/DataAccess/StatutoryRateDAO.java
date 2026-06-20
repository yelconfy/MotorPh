package DataAccess;

import java.sql.*;
import java.time.LocalDate;

/**
 * StatutoryRateDAO — reads the versioned statutory RATE tables seeded by
 * "12 - Seed Statutory Tables.sql" and returns the employee-side MONTHLY
 * figures consumed by PayrollProcess.
 *
 * NOT to be confused with StatutoryDAO, which manages per-employee statutory
 * ID NUMBERS (StatutoryDetails: SssNo / PhilHealthNo / TinNo / PagIbigNo) and
 * is used by EmpMgmtProcess. This class is purely a rate/bracket lookup.
 *
 * Tables (01 - Reference Tables):
 *   SSS_Contribution_Table : RangeFrom, RangeTo, EmployeeShare (absolute PHP), EffectiveDate
 *   Contribution_Rate      : DeductionTypeID(FK), RangeFrom, RangeTo, EmployeeRate,
 *                            EmployerRate, IncomeFloor, IncomeCeiling, EffectiveDate
 *                            -> used for both PhilHealth and Pag-IBIG
 *   WithholdingTax_Table   : PayFrequency, RangeFrom, RangeTo, BaseTax, RateOnExcess, EffectiveDate
 *
 * RATE COLUMNS ARE PERCENTAGES. EmployeeRate / EmployerRate / RateOnExcess are
 * DECIMAL(5,2) and store values like 2.50 / 15.00; this DAO divides by 100.
 * SSS stores an absolute peso EmployeeShare and is returned as-is.
 *
 * Every figure returned is MONTHLY. How it is spread across semi-monthly
 * cutoffs is a PayrollProcess decision, not a DAO concern.
 *
 * Follows the shared-Connection convention of the other DAOs so callers can
 * participate in an existing transaction.
 */
public class StatutoryRateDAO {

  // WithholdingTax_Table.PayFrequency codes
  public static final int PAYFREQ_DAILY        = 0;
  public static final int PAYFREQ_WEEKLY       = 1;
  public static final int PAYFREQ_SEMI_MONTHLY = 2;
  public static final int PAYFREQ_MONTHLY      = 3;

  /**
   * SSS employee share — absolute monthly PHP for the bracket containing
   * monthlyBasic, using the latest table effective on/before asOf.
   */
  public double GetSssEmployeeShare(Connection conn, double monthlyBasic, LocalDate asOf)
      throws SQLException {
    String sql =
      "SELECT TOP 1 EmployeeShare FROM SSS_Contribution_Table " +
      "WHERE Status = 1 AND EffectiveDate <= ? " +
      "  AND ? >= RangeFrom AND (RangeTo IS NULL OR ? <= RangeTo) " +
      "ORDER BY EffectiveDate DESC";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setDate(1, Date.valueOf(asOf));
      ps.setDouble(2, monthlyBasic);
      ps.setDouble(3, monthlyBasic);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getDouble("EmployeeShare") : 0.0;
      }
    }
  }

  /** PhilHealth employee share (monthly): (EE% / 100) x clamp(basic, floor, ceiling). */
  public double GetPhilHealthEmployeeShare(Connection conn, double monthlyBasic, LocalDate asOf)
      throws SQLException {
    return RateBasedEmployeeShare(conn, "PhilHealth", monthlyBasic, asOf);
  }

  /** Pag-IBIG employee share (monthly): (EE% / 100) x min(basic, ceiling). */
  public double GetPagIbigEmployeeShare(Connection conn, double monthlyBasic, LocalDate asOf)
      throws SQLException {
    return RateBasedEmployeeShare(conn, "Pag-IBIG", monthlyBasic, asOf);
  }

  /** Shared Contribution_Rate lookup for PhilHealth / Pag-IBIG. */
  private double RateBasedEmployeeShare(Connection conn, String deductionName,
                                        double monthlyBasic, LocalDate asOf)
      throws SQLException {
    String sql =
      "SELECT TOP 1 cr.EmployeeRate, cr.IncomeFloor, cr.IncomeCeiling " +
      "FROM Contribution_Rate cr " +
      "JOIN Deduction_Type dt ON dt.DeductionTypeID = cr.DeductionTypeID " +
      "WHERE dt.DeductionName = ? AND cr.Status = 1 AND cr.EffectiveDate <= ? " +
      "  AND ? >= cr.RangeFrom AND (cr.RangeTo IS NULL OR ? <= cr.RangeTo) " +
      "ORDER BY cr.EffectiveDate DESC";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, deductionName);
      ps.setDate(2, Date.valueOf(asOf));
      ps.setDouble(3, monthlyBasic);
      ps.setDouble(4, monthlyBasic);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return 0.0;

        double ratePct = rs.getDouble("EmployeeRate");        // stored as percent
        double base    = monthlyBasic;

        double floor = rs.getDouble("IncomeFloor");
        if (!rs.wasNull()) base = Math.max(base, floor);

        double ceil = rs.getDouble("IncomeCeiling");
        if (!rs.wasNull()) base = Math.min(base, ceil);

        return Round2(base * (ratePct / 100.0));
      }
    }
  }

  /**
   * Withholding tax for taxableIncome at the given pay frequency, using the
   * highest bracket whose RangeFrom does not exceed the income.
   * tax = BaseTax + (RateOnExcess / 100) x (taxableIncome - RangeFrom).
   */
  public double GetWithholdingTax(Connection conn, double taxableIncome,
                                  int payFrequency, LocalDate asOf) throws SQLException {
    if (taxableIncome <= 0) return 0.0;
    String sql =
      "SELECT TOP 1 BaseTax, RateOnExcess, RangeFrom FROM WithholdingTax_Table " +
      "WHERE PayFrequency = ? AND Status = 1 AND EffectiveDate <= ? " +
      "  AND ? >= RangeFrom " +
      "ORDER BY RangeFrom DESC, EffectiveDate DESC";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, payFrequency);
      ps.setDate(2, Date.valueOf(asOf));
      ps.setDouble(3, taxableIncome);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return 0.0;
        double baseTax = rs.getDouble("BaseTax");
        double ratePct = rs.getDouble("RateOnExcess");        // percent
        double from    = rs.getDouble("RangeFrom");
        return Round2(baseTax + (ratePct / 100.0) * (taxableIncome - from));
      }
    }
  }

  private static double Round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}