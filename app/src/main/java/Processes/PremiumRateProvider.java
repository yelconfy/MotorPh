package Processes;

import DataAccess.PremiumRateDAO;
import Interface.IPremiumRates;
import Objects.enums.Constants.OvertimeRateMultiplier;
import Objects.enums.Constants.PremiumRateMultiplier;
import Objects.models.PremiumRateInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

/**
 * DB-backed IPremiumRates: loads the effective premium rates once per payroll
 * run from Premium_Rate (via PremiumRateDAO) and serves them by type.
 *
 * FALLBACK: if a type is missing from the table (or the table is empty because
 * 16 hasn't been applied), the matching Constants enum value is returned, so
 * payroll never breaks on a missing rate. Night-diff window defaults to
 * 22:00-06:00 (PH Labor Code) when not configured.
 *
 * Constructed per-run by PayrollProcess over the shared read Connection.
 */
public final class PremiumRateProvider implements IPremiumRates {

  private static final LocalTime DEFAULT_NIGHT_START = LocalTime.of(22, 0);
  private static final LocalTime DEFAULT_NIGHT_END   = LocalTime.of(6, 0);
  private static final double    DEFAULT_NIGHT_MULT  = 1.10;

  private final Map<String, PremiumRateInfo> rates;

  public PremiumRateProvider(PremiumRateDAO dao, Connection conn, LocalDate asOf)
    throws SQLException {
    this.rates = dao.GetEffectiveRates(conn, asOf);
  }

  private double mult(String type, double fallback) {
    PremiumRateInfo r = rates.get(type);
    return (r != null) ? r.GetMultiplier() : fallback;
  }

  @Override
  public double RegularOvertime() {
    return mult("REGULAR_OT", OvertimeRateMultiplier.REGULAR_OT.getMultiplier());
  }

  @Override
  public double RestDay() {
    return mult("REST_DAY", PremiumRateMultiplier.REST_DAY.getMultiplier());
  }

  @Override
  public double RegularHoliday() {
    return mult("REGULAR_HOLIDAY", PremiumRateMultiplier.REGULAR_HOLIDAY.getMultiplier());
  }

  @Override
  public double SpecialHoliday() {
    return mult("SPECIAL_HOLIDAY", PremiumRateMultiplier.SPECIAL_HOLIDAY.getMultiplier());
  }

  @Override
  public double NightDifferential() {
    return mult("NIGHT_DIFF", DEFAULT_NIGHT_MULT);
  }

  @Override
  public LocalTime NightWindowStart() {
    PremiumRateInfo r = rates.get("NIGHT_DIFF");
    return (r != null && r.GetWindowStart() != null) ? r.GetWindowStart() : DEFAULT_NIGHT_START;
  }

  @Override
  public LocalTime NightWindowEnd() {
    PremiumRateInfo r = rates.get("NIGHT_DIFF");
    return (r != null && r.GetWindowEnd() != null) ? r.GetWindowEnd() : DEFAULT_NIGHT_END;
  }
}