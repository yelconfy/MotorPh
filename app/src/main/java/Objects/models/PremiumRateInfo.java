package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DB-mapped Premium_Rate row (16 - Premium Rate Table).
 *
 *   Premium_Rate: PremiumRateID, PremiumType, Multiplier, WindowStart, WindowEnd,
 *                 EffectiveDate, Status
 *
 * PremiumType is a code string (REGULAR_OT, REST_DAY, REGULAR_HOLIDAY,
 * SPECIAL_HOLIDAY, NIGHT_DIFF, ...). WindowStart/End are populated for NIGHT_DIFF
 * only. Read by PremiumRateDAO and exposed through the IPremiumRates port.
 */
public class PremiumRateInfo extends BaseObject {

  private int PremiumRateId;
  private String PremiumType;
  private double Multiplier;
  private LocalTime WindowStart; // nullable
  private LocalTime WindowEnd;   // nullable
  private LocalDate EffectiveDate;

  public PremiumRateInfo() {}

  public PremiumRateInfo(ResultSet rs) throws SQLException {
    this.PremiumRateId = rs.getInt("PremiumRateID");
    this.PremiumType = rs.getString("PremiumType");
    this.Multiplier = rs.getDouble("Multiplier");

    java.sql.Time ws = rs.getTime("WindowStart");
    this.WindowStart = (ws != null) ? ws.toLocalTime() : null;

    java.sql.Time we = rs.getTime("WindowEnd");
    this.WindowEnd = (we != null) ? we.toLocalTime() : null;

    java.sql.Date ed = rs.getDate("EffectiveDate");
    this.EffectiveDate = (ed != null) ? ed.toLocalDate() : null;

    SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return PremiumRateId;
  }

  public int GetPremiumRateId() { return PremiumRateId; }
  public String GetPremiumType() { return PremiumType; }
  public double GetMultiplier() { return Multiplier; }
  public LocalTime GetWindowStart() { return WindowStart; }
  public LocalTime GetWindowEnd() { return WindowEnd; }
  public LocalDate GetEffectiveDate() { return EffectiveDate; }
}