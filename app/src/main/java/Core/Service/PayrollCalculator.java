package Core.Service;

import Interface.IPremiumRates;
import Interface.IStatutoryRates;
import Objects.enums.Constants.*;
import Objects.enums.Status.AttendanceStatus;
import Objects.models.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure payroll computation — no JDBC, no DAOs, no transactions.
 *
 * Everything deterministic that PayrollProcess used to do inline now lives here
 * so it can be unit-tested with plain objects:
 *   - hours: delegates per-day math to Core.Service.AttendanceCalculator (the
 *     single source of truth shared with the Timekeeping screen) and aggregates
 *     the results into a WorkedHoursSummary;
 *   - deductions: statutory cadence + semi-monthly withholding, with the only
 *     DB-bound step pushed behind the IStatutoryRates port;
 *   - earnings: semi-monthly basic + halved allowances + OT + rest-day / holiday
 *     premiums (additional earnings at full rate);
 *   - snapshot mapping: EmpPaySlip -> persisted Payslip header.
 *
 * Behaviour is preserved exactly from the previous PayrollProcess implementation.
 * In particular, a day with a missing punch (either ABSENT or INCOMPLETE) is
 * counted as one absent day with zero pay credit, matching the old IsAbsent rule
 * (TimeIn == null || TimeOut == null); regular/overtime minutes are floored to
 * whole hours per day before bucketing.
 */
public final class PayrollCalculator {

  private static final double STANDARD_HOURS_PER_DAY = 8.0;

  // AttendanceCalculator is pure, stateless per-day math (same instance reused).
  private final AttendanceCalculator attendanceCalculator =
    new AttendanceCalculator();

  // =========================================================================
  // Hours
  // =========================================================================

  /**
   * Aggregates a single employee's attendance rows into a WorkedHoursSummary.
   * The per-day formula is owned by AttendanceCalculator.ComputeDay so Payroll
   * and Timekeeping never disagree on hours.
   */
  public WorkedHoursSummary CalculateHoursWorked(
    List<Attendance> logs,
    AttendanceContext ctx
  ) {
    WorkedHoursSummary summary = new WorkedHoursSummary();
    if (logs == null) {
      return summary;
    }

    for (Attendance record : logs) {
      DailyAttendanceRecord day = attendanceCalculator.ComputeDay(record, ctx);

      if (day.GetStatus() == AttendanceStatus.ON_LEAVE) {
        summary.AddPaidLeaveDays(1); // paid via flat basic; not docked, no hours
        continue;
      }

      if (
        day.GetStatus() == AttendanceStatus.ABSENT ||
        day.GetStatus() == AttendanceStatus.INCOMPLETE ||
        day.GetStatus() == AttendanceStatus.ON_LEAVE_UNPAID
      ) {
        summary.AddAbsentDays(1); // unpaid leave docks like an absence (cash-equivalent)
        continue;
      }

      if (day.GetLateMinutes() > 0) {
        summary.AddLateMinutes((int) day.GetLateMinutes());
      }

      // Floor to whole hours per day (regMin / 60), then bucket by day type.
      int regHours = (int) (day.GetRegularMinutes() / 60);
      switch (day.GetDayType()) {
        case HOLIDAY -> summary.AddHolidayHours(regHours);
        case HOLIDAY_SPECIAL -> summary.AddSpecialHolidayHours(regHours);
        case WEEKEND -> summary.AddWeekendHours(regHours);
        default -> summary.AddRegularHours(regHours);
      }

      summary.AddOvertimeHours((int) (day.GetApprovedOvertimeMinutes() / 60));
      summary.AddNightDiffHours((int) (day.GetNightDiffMinutes() / 60));
      if (day.GetUndertimeMinutes() > 0) {
        summary.AddUndertimeMinutes((int) day.GetUndertimeMinutes());
      }
    }
    return summary;
  }

  // =========================================================================
  // Deductions
  // =========================================================================

  /**
   * Computes statutory + penalty deductions for one employee/cutoff.
   *
   * Cash cadence: full monthly statutory on the 2nd cutoff only (0 on the 1st).
   * Withholding tax is on the semi-monthly taxable income on both cutoffs.
   * The rate lookups are the only DB-bound step, hidden behind IStatutoryRates.
   */
  public EmpDeductions ComputeEmployeeDeductions(
    EmployeeSalaryInfo salary,
    WorkedHoursSummary hours,
    boolean isSecondCutoff,
    IStatutoryRates rates
  ) throws SQLException {
    EmpDeductions deductions = new EmpDeductions();

    boolean hasAttendance =
      hours.GetRegularHours() > 0 ||
      hours.GetHolidayHours() > 0 ||
      hours.GetSpecialHolidayHours() > 0 ||
      hours.GetWeekendHours() > 0 ||
      hours.GetTotalAbsentDays() > 0 ||
      hours.GetPaidLeaveDays() > 0;

    if (!hasAttendance) {
      deductions.SetTotalDeductions(0.0);
      return deductions;
    }

    double monthlyBasic = salary.GetBasicSalary();

    double monthlySss = rates.SssEmployeeShare(monthlyBasic);
    double monthlyPhil = rates.PhilHealthEmployeeShare(monthlyBasic);
    double monthlyPagibig = rates.PagIbigEmployeeShare(monthlyBasic);
    double monthlyStatutory = monthlySss + monthlyPhil + monthlyPagibig;

    // Cash cadence: full monthly statutory on the 2nd cutoff only.
    if (isSecondCutoff) {
      deductions.SetSssContribution(monthlySss);
      deductions.SetPhilHealthContribution(monthlyPhil);
      deductions.SetPagIbigContribution(monthlyPagibig);
    } else {
      deductions.SetSssContribution(0.0);
      deductions.SetPhilHealthContribution(0.0);
      deductions.SetPagIbigContribution(0.0);
    }

    // WHT base = semi-monthly basic minus half the monthly statutory, evenly on
    // both cutoffs (independent of the cash cadence above). Taxable allowances
    // are not yet folded in (deferred).
    double semiMonthlyBasic = monthlyBasic / 2.0;
    double taxableIncome = semiMonthlyBasic - (monthlyStatutory / 2.0);
    deductions.SetWithholdingTax(rates.WithholdingTax(taxableIncome));

    ApplyAttendancePenalties(deductions, hours, salary.GetHourlyRate());
    CalculateTotalDeductions(deductions);
    return deductions;
  }

  // =========================================================================
  // Payslip (in-memory computation object)
  // =========================================================================

  public EmpPaySlip GenerateEmpPaySlip(
    EmpDetail emp,
    EmployeeSalaryInfo salary,
    LocalDate start,
    LocalDate end,
    WorkedHoursSummary hours,
    EmpDeductions deductions,
    IPremiumRates premiums
  ) {
    boolean hasWorked =
      hours.GetRegularHours() > 0 ||
      hours.GetHolidayHours() > 0 ||
      hours.GetSpecialHolidayHours() > 0 ||
      hours.GetWeekendHours() > 0 ||
      hours.GetPaidLeaveDays() > 0;

    double hourlyRate = salary.GetHourlyRate();

    double semiMonthlyBasic = hasWorked ? (salary.GetBasicSalary() / 2.0) : 0.0;

    // Monthly allowances split evenly across the two cutoffs.
    double allowances = hasWorked ? (emp.GetTotalAllowances() / 2.0) : 0.0;

    // OT and rest-day / holiday premiums: additional earnings at full rate.
    double overtimePay =
      hours.GetOvertimeHours() * hourlyRate * premiums.RegularOvertime();
    double weekendPremiumPay =
      hours.GetWeekendHours() * hourlyRate * premiums.RestDay();
    double holidayPremiumPay =
      hours.GetHolidayHours() * hourlyRate * premiums.RegularHoliday();
    double specialHolidayPremiumPay =
      hours.GetSpecialHolidayHours() * hourlyRate * premiums.SpecialHoliday();
    double nightDiffPay =
      hours.GetNightDiffHours() *
      hourlyRate *
      (premiums.NightDifferential() - 1.0);

    double grossPay = Round2(
      semiMonthlyBasic +
        allowances +
        overtimePay +
        weekendPremiumPay +
        holidayPremiumPay +
        specialHolidayPremiumPay +
        nightDiffPay
    );
    double netPay = Round2(grossPay - deductions.GetTotalDeductions());

    EmpPaySlip slip = new EmpPaySlip(
      emp,
      grossPay,
      netPay,
      deductions,
      start,
      end
    );
    // Breakdown carried on the slip so the persisted Payslip snapshot maps 1:1
    // (no recomputation of basic / allowances downstream).
    slip.SetBasicPay(semiMonthlyBasic);
    slip.SetTotalAllowances(allowances);
    return slip;
  }

  public double ComputeOvertimePay(
    WorkedHoursSummary summary,
    double hourlyRate
  ) {
    if (summary == null || hourlyRate <= 0) {
      return 0.0;
    }
    double otHours = summary.GetOvertimeHours();
    return Round2(
      otHours * hourlyRate * OvertimeRateMultiplier.REGULAR_OT.getMultiplier()
    );
  }

  // =========================================================================
  // Snapshot mapping
  // =========================================================================

  /** Maps the in-memory computation result to a persisted Payslip snapshot. */
  public Payslip ToPayslipSnapshot(
    EmpDetail emp,
    EmpPaySlip computed,
    EmpDeductions deductions,
    WorkedHoursSummary hours
  ) {
    Payslip slip = new Payslip();
    slip.SetEmployeeId(emp.GetEmployeeId());
    slip.SetBasicPay(computed.GetBasicPay());
    slip.SetTotalAllowances(computed.GetTotalAllowances());
    slip.SetGrossPay(computed.GetGrossPay());
    slip.SetTotalDeductions(deductions.GetTotalDeductions());
    slip.SetTotalAdjustments(0.0); // adjustments not yet modeled
    slip.SetNetPay(computed.GetNetPay());
    slip.SetHoursWorked(hours.GetTotalWorkedHours());
    slip.SetDaysWorked(hours.GetTotalWorkedHours() / STANDARD_HOURS_PER_DAY);
    return slip;
  }

  // =========================================================================
  // Private - deductions helpers
  // =========================================================================

  private void ApplyAttendancePenalties(
    EmpDeductions deductions,
    WorkedHoursSummary hours,
    double hourlyRate
  ) {
    deductions.SetLatesDeduction(
      ComputeLatesDeduction(hours.GetTotalLateMinutes(), hourlyRate)
    );
    deductions.SetAbsencesDeduction(
      ComputeAbsencesDeduction(hours.GetTotalAbsentDays(), hourlyRate)
    );
    deductions.SetUndertimeDeduction(
      ComputeUndertimeDeduction(hours.GetTotalUndertimeMinutes(), hourlyRate)
    );
  }

  private double ComputeUndertimeDeduction(
    int undertimeMinutes,
    double hourlyRate
  ) {
    return Round2((hourlyRate / 60.0) * undertimeMinutes); // same basis as lates
  }

  private double ComputeLatesDeduction(int lateMinutes, double hourlyRate) {
    return Round2((hourlyRate / 60.0) * lateMinutes);
  }

  private double ComputeAbsencesDeduction(int absentDays, double hourlyRate) {
    return Round2(hourlyRate * STANDARD_HOURS_PER_DAY * absentDays);
  }

  private void CalculateTotalDeductions(EmpDeductions deductions) {
    double total =
      deductions.GetSssContribution() +
      deductions.GetPhilHealthContribution() +
      deductions.GetPagIbigContribution() +
      deductions.GetWithholdingTax() +
      deductions.GetLatesDeduction() +
      deductions.GetAbsencesDeduction() +
      deductions.GetUndertimeDeduction();
    deductions.SetTotalDeductions(Round2(total));
  }

  // =========================================================================
  // Private - utilities
  // =========================================================================

  private static double Round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
