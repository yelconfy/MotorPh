package Objects.models;

import Objects.enums.Status.HolidayType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * DB-mapped Holiday row (01 - Reference Tables / seeded by 14 - Seed Holidays):
 *
 *   Holiday: HolidayID, HolidayDate, HolidayName, HolidayType (0/1),
 *            IsRecurring, Status, LastUpdatedBy, LastUpdatedDate
 *
 * Read by HolidayDAO and folded into a HolidayCalendar for the attendance
 * calculator. The field is named {@code Type} to avoid clashing with the
 * imported HolidayType enum; the getter is GetHolidayType().
 */
public class HolidayInfo extends BaseObject {

  private long HolidayId;
  private LocalDate HolidayDate;
  private String HolidayName;
  private HolidayType Type;
  private boolean Recurring;

  public HolidayInfo() {}

  /** Smart constructor (Database). */
  public HolidayInfo(ResultSet rs) throws SQLException {
    this.HolidayId = rs.getLong("HolidayID");

    java.sql.Date d = rs.getDate("HolidayDate");
    this.HolidayDate = (d != null) ? d.toLocalDate() : null;

    this.HolidayName = rs.getString("HolidayName");
    this.Type = HolidayType.FromCode(rs.getInt("HolidayType"));
    this.Recurring = rs.getBoolean("IsRecurring");
    SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return HolidayId;
  }

  public long GetHolidayId() {
    return HolidayId;
  }

  public void SetHolidayId(long v) {
    this.HolidayId = v;
  }

  public LocalDate GetHolidayDate() {
    return HolidayDate;
  }

  public void SetHolidayDate(LocalDate v) {
    this.HolidayDate = v;
  }

  public String GetHolidayName() {
    return HolidayName;
  }

  public void SetHolidayName(String v) {
    this.HolidayName = v;
  }

  public HolidayType GetHolidayType() {
    return Type;
  }

  public void SetHolidayType(HolidayType v) {
    this.Type = v;
  }

  public boolean IsRecurring() {
    return Recurring;
  }

  public void SetRecurring(boolean v) {
    this.Recurring = v;
  }

  @Override
  public String toString() {
    return HolidayName + " (" + HolidayDate + ")";
  }
}