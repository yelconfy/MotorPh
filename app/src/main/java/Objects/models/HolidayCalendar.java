package Objects.models;

import Objects.enums.Status.HolidayType;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, immutable date -> HolidayType lookup for a given range.
 *
 * The process layer builds one from HolidayDAO rows and injects it (via
 * AttendanceContext) into the pure AttendanceCalculator, so the calculator does
 * no JDBC and stays unit-testable. Holidays are matched by EXACT date; recurring
 * (year-agnostic) expansion is a later enhancement (the IsRecurring column is
 * carried but not yet used).
 */
public final class HolidayCalendar {

  private final Map<LocalDate, HolidayType> byDate;

  public HolidayCalendar(List<HolidayInfo> holidays) {
    Map<LocalDate, HolidayType> m = new HashMap<>();
    if (holidays != null) {
      for (HolidayInfo h : holidays) {
        if (h != null && h.GetHolidayDate() != null && h.IsActive()) {
          m.put(h.GetHolidayDate(), h.GetHolidayType());
        }
      }
    }
    this.byDate = m;
  }

  private HolidayCalendar(Map<LocalDate, HolidayType> byDate) {
    this.byDate = byDate;
  }

  /** Null-object calendar (no holidays) for null-safe defaults. */
  public static HolidayCalendar Empty() {
    return new HolidayCalendar(Collections.emptyMap());
  }

  /** The holiday type for a date, or null if the date is not a holiday. */
  public HolidayType TypeOf(LocalDate date) {
    return (date == null) ? null : byDate.get(date);
  }

  public boolean IsHoliday(LocalDate date) {
    return TypeOf(date) != null;
  }
}