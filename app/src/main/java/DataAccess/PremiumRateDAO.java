package DataAccess;

import Objects.models.PremiumRateInfo;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO for Premium_Rate (16 - Premium Rate Table).
 *
 * Effective-dated lookup, same convention as the statutory rate tables: returns
 * the latest active row per PremiumType whose EffectiveDate <= asOf, keyed by
 * PremiumType. The IPremiumRates provider wraps this for one payroll run.
 */
public class PremiumRateDAO {

  /**
   * GetEffectiveRates — latest active rate per PremiumType as of the given date.
   * Returns an empty map if the table is empty (the provider then falls back to
   * the enum defaults), so payroll still runs if 16 hasn't been applied.
   */
  public Map<String, PremiumRateInfo> GetEffectiveRates(Connection conn, LocalDate asOf)
    throws SQLException {
    Map<String, PremiumRateInfo> map = new HashMap<>();
    String sql =
      "SELECT pr.* FROM Premium_Rate pr " +
      "WHERE pr.Status = 1 AND pr.EffectiveDate <= ? " +
      "AND pr.EffectiveDate = (" +
      "  SELECT MAX(p2.EffectiveDate) FROM Premium_Rate p2 " +
      "  WHERE p2.PremiumType = pr.PremiumType AND p2.Status = 1 AND p2.EffectiveDate <= ?" +
      ")";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      java.sql.Date d = Date.valueOf(asOf);
      pstmt.setDate(1, d);
      pstmt.setDate(2, d);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          PremiumRateInfo info = new PremiumRateInfo(rs);
          map.put(info.GetPremiumType(), info);
        }
      }
    }
    return map;
  }
}