package Processes;

import Core.Service.PayrollCalculator;
import DataAccess.AllowanceDAO;
import DataAccess.AttendanceDAO;
import DataAccess.DatabaseConnector;
import DataAccess.DeductionDAO;
import DataAccess.EmployeeDAO;
import DataAccess.PayrollDAO;
import DataAccess.StatutoryRateDAO;
import Interface.IPayrollProcess;
import Interface.IStatutoryRates;
import Objects.enums.Status.PayrollDeductionSource;
import Objects.enums.Status.PayrollPeriodStatus;
import Objects.models.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payroll period-run engine: orchestration + persistence only.
 *
 * All deterministic money math (hours, deductions, earnings, snapshot mapping)
 * now lives in PayrollCalculator; the DB-bound statutory lookups sit behind the
 * IStatutoryRates port (StatutoryRateProvider). This class is responsible for:
 *
 *   - period lifecycle: RunPeriod generates Draft payslip SNAPSHOTS (header +
 *     line items) for all active employees; FinalizePeriod locks them; PayPeriod
 *     marks them Paid;
 *   - data sourcing: the active-employee list, attendance, and allowances;
 *   - transactions: a single write transaction per lifecycle action;
 *   - line-item persistence: Payroll_Allowance + the four STATUTORY
 *     Payroll_Deduction rows.
 *
 * Attendance penalties (lates / absences) are NOT itemized (no seeded
 * Deduction_Type), so the statutory lines intentionally sum to less than
 * TotalDeductions when penalties apply. TotalAdjustments stays 0.
 */
public class PayrollProcess implements IPayrollProcess {

  // Seeded Deduction_Type.DeductionName values (09 - RBAC seed).
  private static final String DT_SSS = "SSS";
  private static final String DT_PHILHEALTH = "PhilHealth";
  private static final String DT_PAGIBIG = "Pag-IBIG";
  private static final String DT_WITHHOLDING = "Withholding Tax";

  private final AttendanceDAO attendanceDAO;
  private final StatutoryRateDAO statRateDAO;
  private final PayrollDAO payrollDAO;
  private final EmployeeDAO employeeDAO;
  private final AllowanceDAO allowanceDAO;
  private final DeductionDAO deductionDAO;
  private final PayrollCalculator calculator;

  public PayrollProcess(
    AttendanceDAO _attendanceDAO,
    StatutoryRateDAO _statRateDAO,
    PayrollDAO _payrollDAO,
    EmployeeDAO _employeeDAO,
    AllowanceDAO _allowanceDAO,
    DeductionDAO _deductionDAO,
    PayrollCalculator _calculator
  ) {
    this.attendanceDAO = _attendanceDAO;
    this.statRateDAO = _statRateDAO;
    this.payrollDAO = _payrollDAO;
    this.employeeDAO = _employeeDAO;
    this.allowanceDAO = _allowanceDAO;
    this.deductionDAO = _deductionDAO;
    this.calculator = _calculator;
  }

  // =========================================================================
  // Period run / lifecycle
  // =========================================================================

  /**
   * Generates Draft payslip snapshots (header + allowance/deduction line items)
   * for every active employee in the period. Computes outside any transaction
   * (reads), then persists in one write transaction. Idempotent: an employee
   * that already has a slip for the period is skipped, so a re-run only adds
   * newly-eligible employees.
   *
   * All rate lookups for the run share the single read Connection via the
   * StatutoryRateProvider built below.
   *
   * @return the PayrollPeriodID used (existing open period reused, else created)
   */
  @Override
  public long RunPeriod(
    PayrollPeriod period,
    LocalDate asOf,
    long generatedByUserId
  ) throws SQLException {
    boolean secondCutoff = IsSecondCutoff(period);

    // --- Phase 1: compute (reads only) -------------------------------------
    List<ComputedPayroll> results = new ArrayList<>();
    List<EmpDetail> employees = employeeDAO.GetAll();

    try (Connection readConn = DatabaseConnector.GetConnection()) {
      IStatutoryRates rates = new StatutoryRateProvider(
        statRateDAO,
        readConn,
        asOf
      );

      for (EmpDetail emp : employees) {
        // Relies on vw_EmployeeCompleteDetails.Status -> EmpDetail.IsActive().
        if (!emp.IsActive()) {
          continue;
        }

        // Allowances are the only thing the view doesn't carry.
        emp.SetAllowances(
          allowanceDAO.GetByEmployeeID(readConn, emp.GetEmployeeId())
        );

        List<Attendance> logs = attendanceDAO.GetByDateRange(
          readConn,
          emp.GetEmployeeId(),
          period.GetStartDate(),
          period.GetEndDate()
        );
        WorkedHoursSummary hours = calculator.CalculateHoursWorked(logs);

        boolean hasData =
          hours.GetRegularHours() > 0 ||
          hours.GetHolidayHours() > 0 ||
          hours.GetWeekendHours() > 0 ||
          hours.GetTotalAbsentDays() > 0;
        if (!hasData) {
          continue; // no attendance in the period -> no slip
        }

        EmpDeductions deductions = calculator.ComputeEmployeeDeductions(
          emp.GetCompensation(),
          hours,
          secondCutoff,
          rates
        );
        EmpPaySlip slip = calculator.GenerateEmpPaySlip(
          emp,
          emp.GetCompensation(),
          period.GetStartDate(),
          period.GetEndDate(),
          hours,
          deductions
        );

        Payslip header = calculator.ToPayslipSnapshot(
          emp,
          slip,
          deductions,
          hours
        );
        results.add(
          new ComputedPayroll(header, emp.GetAllowances(), deductions)
        );
      }
    }

    // --- Phase 2: persist (one write transaction) --------------------------
    return InTransaction(conn -> {
      long periodId = ResolveOrCreatePeriod(conn, period);
      Map<String, Integer> deductionTypeIds = LoadDeductionTypeIds(conn);

      for (ComputedPayroll cp : results) {
        cp.header.SetPayrollPeriodId(periodId);
        if (
          payrollDAO.GetByEmployeeAndPeriod(
            conn,
            cp.header.GetEmployeeId(),
            periodId
          ) !=
          null
        ) {
          continue; // already has a slip for this period (idempotent re-run)
        }

        long payslipId = payrollDAO.SavePayslip(
          conn,
          cp.header,
          generatedByUserId
        );

        if (cp.header.GetTotalAllowances() > 0) {
          SaveAllowanceLines(conn, payslipId, cp.allowances);
        }
        SaveStatutoryDeductionLines(
          conn,
          payslipId,
          cp.deductions,
          deductionTypeIds
        );
      }
      return periodId;
    });
  }

  /** Finalizes every Draft payslip in the period and closes the period. */
  @Override
  public boolean FinalizePeriod(long periodId) throws SQLException {
    return InTransaction(conn -> {
      for (Payslip slip : payrollDAO.GetByPeriod(conn, periodId)) {
        payrollDAO.FinalizePayslip(conn, slip.GetPayslipId()); // Draft -> Finalized only
      }
      payrollDAO.UpdatePeriodStatus(conn, periodId, PayrollPeriodStatus.CLOSED);
      return true;
    });
  }

  /** Marks finalized payslips Paid and the period Paid; records the pay date. */
  @Override
  public int PayPeriod(long periodId, LocalDate payDate) throws SQLException {
    return InTransaction(conn -> {
      int paid = payrollDAO.MarkPaidByPeriod(conn, periodId); // Finalized -> Paid
      payrollDAO.UpdatePeriodPayDate(conn, periodId, payDate);
      payrollDAO.UpdatePeriodStatus(conn, periodId, PayrollPeriodStatus.PAID);
      return paid;
    });
  }

  @Override
  public List<Payslip> GetPayslipsForPeriod(long periodId) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return payrollDAO.GetByPeriod(conn, periodId);
    }
  }

  @Override
  public List<PayrollPeriod> GetOpenPeriods() throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return payrollDAO.GetOpenPeriods(conn);
    }
  }

  // =========================================================================
  // Private - line item persistence
  // =========================================================================

  /** DeductionName -> DeductionTypeID, loaded once per run. */
  private Map<String, Integer> LoadDeductionTypeIds(Connection conn)
    throws SQLException {
    Map<String, Integer> map = new HashMap<>();
    for (DeductionTypeInfo t : deductionDAO.GetAllTypes(conn)) {
      map.put(t.GetDeductionName(), t.GetDeductionTypeId());
    }
    return map;
  }

  /** One Payroll_Allowance row per allowance, at the per-cutoff (halved) amount. */
  private void SaveAllowanceLines(
    Connection conn,
    long payslipId,
    List<AllowanceInfo> allowances
  ) throws SQLException {
    if (allowances == null) {
      return;
    }
    for (AllowanceInfo a : allowances) {
      double perCutoff = Round2(a.GetAmount() / 2.0);
      if (perCutoff <= 0) {
        continue;
      }
      payrollDAO.SavePayrollAllowance(
        conn,
        payslipId,
        a.GetAllowanceTypeId(),
        perCutoff,
        "Semi-monthly share"
      );
    }
  }

  /** The four statutory deduction lines (SourceType = STATUTORY, SourceID = null). */
  private void SaveStatutoryDeductionLines(
    Connection conn,
    long payslipId,
    EmpDeductions d,
    Map<String, Integer> typeIds
  ) throws SQLException {
    int src = PayrollDeductionSource.STATUTORY.getValue();
    WriteStatutoryLine(
      conn,
      payslipId,
      typeIds,
      DT_SSS,
      d.GetSssContribution(),
      src
    );
    WriteStatutoryLine(
      conn,
      payslipId,
      typeIds,
      DT_PHILHEALTH,
      d.GetPhilHealthContribution(),
      src
    );
    WriteStatutoryLine(
      conn,
      payslipId,
      typeIds,
      DT_PAGIBIG,
      d.GetPagIbigContribution(),
      src
    );
    WriteStatutoryLine(
      conn,
      payslipId,
      typeIds,
      DT_WITHHOLDING,
      d.GetWithholdingTax(),
      src
    );
  }

  private void WriteStatutoryLine(
    Connection conn,
    long payslipId,
    Map<String, Integer> typeIds,
    String deductionName,
    double amount,
    int sourceType
  ) throws SQLException {
    if (amount <= 0) {
      return; // e.g. statutory is 0 on the 1st cutoff
    }
    Integer typeId = typeIds.get(deductionName);
    if (typeId == null) {
      System.err.println(
        "PayrollProcess: Deduction_Type '" +
          deductionName +
          "' is not seeded; skipping its line item."
      );
      return;
    }
    payrollDAO.SavePayrollDeduction(
      conn,
      payslipId,
      typeId,
      sourceType,
      null,
      Round2(amount),
      null
    );
  }

  // =========================================================================
  // Private - period helpers
  // =========================================================================

  /** Semi-monthly: 1st cutoff ends on the 15th, 2nd cutoff ends 16th..EOM. */
  private boolean IsSecondCutoff(PayrollPeriod period) {
    return period.GetEndDate().getDayOfMonth() > 15;
  }

  /**
   * Reuses an Open period with the same date range (idempotent re-runs); if a
   * matching period exists but is already locked (Processing/Closed/Paid),
   * refuses; otherwise creates a new Open period.
   */
  private long ResolveOrCreatePeriod(Connection conn, PayrollPeriod period)
    throws SQLException {
    for (PayrollPeriod existing : payrollDAO.GetAllPeriods(conn)) {
      if (
        period.GetStartDate().equals(existing.GetStartDate()) &&
        period.GetEndDate().equals(existing.GetEndDate())
      ) {
        if (existing.IsLocked()) {
          throw new IllegalStateException(
            "Payroll period '" +
              existing.GetPeriodName() +
              "' is " +
              existing.GetPayrollStatus() +
              " and cannot be regenerated."
          );
        }
        return existing.GetPayrollPeriodId();
      }
    }
    return payrollDAO.InsertPeriod(conn, period);
  }

  // =========================================================================
  // Private - utilities
  // =========================================================================

  private static double Round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  /** Bundles one employee's computed header + line-item inputs across phases. */
  private static final class ComputedPayroll {

    final Payslip header;
    final List<AllowanceInfo> allowances; // monthly amounts; halved when persisted
    final EmpDeductions deductions;

    ComputedPayroll(
      Payslip header,
      List<AllowanceInfo> allowances,
      EmpDeductions deductions
    ) {
      this.header = header;
      this.allowances = allowances;
      this.deductions = deductions;
    }
  }

  /** Minimal transactional wrapper (PayrollProcess manages its own writes). */
  private interface TxWork<T> {
    T run(Connection conn) throws SQLException;
  }

  private <T> T InTransaction(TxWork<T> work) throws SQLException {
    Connection conn = null;
    try {
      conn = DatabaseConnector.GetConnection();
      conn.setAutoCommit(false);
      T result = work.run(conn);
      conn.commit();
      return result;
    } catch (SQLException | RuntimeException e) {
      if (conn != null) {
        try {
          conn.rollback();
        } catch (SQLException ignore) {}
      }
      throw e;
    } finally {
      if (conn != null) {
        try {
          conn.setAutoCommit(true);
        } catch (SQLException ignore) {}
        try {
          conn.close();
        } catch (SQLException ignore) {}
      }
    }
  }
}
