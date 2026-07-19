package Interface;

import java.time.LocalTime;

/**
 * Port for labour-premium multipliers + the night-differential window, decoupling
 * PayrollCalculator from JDBC and from the hardcoded Constants enums. Implemented
 * by Processes.PremiumRateProvider over the versioned Premium_Rate table.
 *
 * Mirrors IStatutoryRates: constructed per-run by PayrollProcess, bound to the
 * run's read Connection + as-of date. Tests can supply a fake.
 *
 * Multipliers are full rates (e.g. 1.25 = 125%). The night window is the span
 * (default 22:00-06:00) used by AttendanceCalculator to count night-diff minutes
 * in Phase 6b.
 */
public interface IPremiumRates {

  double RegularOvertime();   // REGULAR_OT
  double RestDay();           // REST_DAY (worked rest day / weekend)
  double RegularHoliday();    // REGULAR_HOLIDAY
  double SpecialHoliday();    // SPECIAL_HOLIDAY (special non-working)
  double NightDifferential(); // NIGHT_DIFF

  LocalTime NightWindowStart();
  LocalTime NightWindowEnd();
}