package DataAccess;

import Objects.enums.Status.PayrollPeriodStatus;
import Objects.enums.Status.PayslipStatus;
import Objects.models.PayrollPeriod;
import Objects.models.Payslip;
import Objects.models.PayslipAllowanceLine;
import Objects.models.PayslipDeductionLine;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Payroll_Period, Payslip, Payroll_Allowance, and Payroll_Deduction.
 *
 * Schema (05 - Payroll Tables):
 *   Payroll_Period: PayrollPeriodID, PeriodName, StartDate, EndDate, PayDate, Status
 *   Payslip: PayslipID, EmployeeID, PayrollPeriodID, BasicPay, TotalAllowances,
 *            GrossPay, TotalDeductions, TotalAdjustments, NetPay,
 *            DaysWorked, HoursWorked, Status, GeneratedBy, GeneratedDate
 *   Payroll_Allowance: PayrollAllowanceID, PayslipID, AllowanceTypeID, Amount, Remarks
 *   Payroll_Deduction: PayrollDeductionID, PayslipID, DeductionTypeID,
 *                      SourceType, SourceID, Amount, Remarks
 *
 * FINALIZE-LOCK: Status >= 1 (Processing/Finalized/Paid) → read-only in APP LOGIC.
 * This DAO does NOT enforce that check — callers must check Payslip.IsLocked()
 * or PayrollPeriod.IsLocked() before calling mutating methods.
 */
public class PayrollDAO {

  // =========================================================================
  // Payroll_Period
  // =========================================================================

  /**
   * InsertPeriod — creates a new payroll period. Returns generated ID.
   */
  public long InsertPeriod(Connection conn, PayrollPeriod period)
    throws SQLException {
    String sql =
      "INSERT INTO Payroll_Period (PeriodName, StartDate, EndDate, PayDate) " +
      "OUTPUT INSERTED.PayrollPeriodID " +
      "VALUES (?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, period.GetPeriodName());
      pstmt.setDate(2, Date.valueOf(period.GetStartDate()));
      pstmt.setDate(3, Date.valueOf(period.GetEndDate()));
      pstmt.setDate(
        4,
        period.GetPayDate() != null ? Date.valueOf(period.GetPayDate()) : null
      );

      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  /**
   * UpdatePeriodPayDate — stamps the pay date when a period is paid out.
   */
  public boolean UpdatePeriodPayDate(
    Connection conn,
    long periodId,
    java.time.LocalDate payDate
  ) throws SQLException {
    String sql =
      "UPDATE Payroll_Period SET PayDate = ? WHERE PayrollPeriodID = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setDate(1, payDate != null ? Date.valueOf(payDate) : null);
      pstmt.setLong(2, periodId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * UpdatePeriodStatus — advances a period through its lifecycle.
   */
  public boolean UpdatePeriodStatus(
    Connection conn,
    long periodId,
    PayrollPeriodStatus newStatus
  ) throws SQLException {
    String sql =
      "UPDATE Payroll_Period SET Status = ? WHERE PayrollPeriodID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, newStatus.getValue());
      pstmt.setLong(2, periodId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * GetOpenPeriods — all Open periods for the period picker dropdown.
   */
  public List<PayrollPeriod> GetOpenPeriods(Connection conn)
    throws SQLException {
    List<PayrollPeriod> list = new ArrayList<>();
    String sql =
      "SELECT * FROM Payroll_Period WHERE Status = 0 ORDER BY StartDate DESC";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) list.add(new PayrollPeriod(rs));
    }
    return list;
  }

  /**
   * GetAllPeriods — full history for an admin/audit view.
   */
  public List<PayrollPeriod> GetAllPeriods(Connection conn)
    throws SQLException {
    List<PayrollPeriod> list = new ArrayList<>();
    String sql = "SELECT * FROM Payroll_Period ORDER BY StartDate DESC";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) list.add(new PayrollPeriod(rs));
    }
    return list;
  }

  // =========================================================================
  // Payslip
  // =========================================================================

  /**
   * SavePayslip — inserts a new Draft payslip snapshot.
   * Caller must supply the UserID of whoever triggered generation.
   * Returns the generated PayslipID, or -1 on failure.
   *
   * UNIQUE constraint (EmployeeID, PayrollPeriodID) will throw if a slip
   * already exists — callers should check first via GetByEmployeeAndPeriod().
   */
  public long SavePayslip(Connection conn, Payslip slip, long generatedByUserId)
    throws SQLException {
    String sql =
      "INSERT INTO Payslip " +
      "(EmployeeID, PayrollPeriodID, BasicPay, TotalAllowances, GrossPay, " +
      " TotalDeductions, TotalAdjustments, NetPay, DaysWorked, HoursWorked, " +
      " Status, GeneratedBy) " +
      "OUTPUT INSERTED.PayslipID " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, slip.GetEmployeeId());
      pstmt.setLong(2, slip.GetPayrollPeriodId());
      pstmt.setDouble(3, slip.GetBasicPay());
      pstmt.setDouble(4, slip.GetTotalAllowances());
      pstmt.setDouble(5, slip.GetGrossPay());
      pstmt.setDouble(6, slip.GetTotalDeductions());
      pstmt.setDouble(7, slip.GetTotalAdjustments());
      pstmt.setDouble(8, slip.GetNetPay());
      pstmt.setDouble(9, slip.GetDaysWorked());
      pstmt.setDouble(10, slip.GetHoursWorked());
      pstmt.setLong(11, generatedByUserId);

      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  /**
   * MarkPaidByPeriod — advances all Finalized payslips in a period to Paid.
   * Returns the count marked. Guards against paying Drafts (Status = 1 only).
   */
  public int MarkPaidByPeriod(Connection conn, long periodId)
    throws SQLException {
    String sql =
      "UPDATE Payslip SET Status = 2 " +
      "WHERE PayrollPeriodID = ? AND Status = 1";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, periodId);
      return pstmt.executeUpdate();
    }
  }

  /**
   * FinalizePayslip — advances a Draft payslip to Finalized.
   * Enforces lock: only Draft (0) payslips can be finalized.
   */
  public boolean FinalizePayslip(Connection conn, long payslipId)
    throws SQLException {
    String sql =
      "UPDATE Payslip SET Status = 1 " + "WHERE PayslipID = ? AND Status = 0"; // Only draft → finalized

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, payslipId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * GetByEmployeeAndPeriod — single payslip lookup (existence check + display).
   * JOINs Employees and Payroll_Period for display columns.
   */
  public Payslip GetByEmployeeAndPeriod(
    Connection conn,
    long employeeId,
    long periodId
  ) throws SQLException {
    String sql =
      "SELECT ps.*, e.FirstName, e.LastName, pp.PeriodName, pp.StartDate, pp.EndDate " +
      "FROM Payslip ps " +
      "JOIN Employees e      ON e.EmployeeID         = ps.EmployeeID " +
      "JOIN Payroll_Period pp ON pp.PayrollPeriodID   = ps.PayrollPeriodID " +
      "WHERE ps.EmployeeID = ? AND ps.PayrollPeriodID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setLong(2, periodId);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? new Payslip(rs) : null;
      }
    }
  }

  /**
   * GetByPeriod — all payslips for a given period (for payroll run screen).
   */
  public List<Payslip> GetByPeriod(Connection conn, long periodId)
    throws SQLException {
    List<Payslip> list = new ArrayList<>();
    String sql =
      "SELECT ps.*, e.FirstName, e.LastName, pp.PeriodName, pp.StartDate, pp.EndDate " +
      "FROM Payslip ps " +
      "JOIN Employees e       ON e.EmployeeID        = ps.EmployeeID " +
      "JOIN Payroll_Period pp ON pp.PayrollPeriodID  = ps.PayrollPeriodID " +
      "WHERE ps.PayrollPeriodID = ? " +
      "ORDER BY e.LastName, e.FirstName";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, periodId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) list.add(new Payslip(rs));
      }
    }
    return list;
  }

  /**
   * GetByEmployee — payslip history for a specific employee.
   */
  public List<Payslip> GetByEmployee(Connection conn, long employeeId)
    throws SQLException {
    List<Payslip> list = new ArrayList<>();
    String sql =
      "SELECT ps.*, e.FirstName, e.LastName, pp.PeriodName, pp.StartDate, pp.EndDate " +
      "FROM Payslip ps " +
      "JOIN Employees e       ON e.EmployeeID        = ps.EmployeeID " +
      "JOIN Payroll_Period pp ON pp.PayrollPeriodID  = ps.PayrollPeriodID " +
      "WHERE ps.EmployeeID = ? " +
      "ORDER BY pp.StartDate DESC";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) list.add(new Payslip(rs));
      }
    }
    return list;
  }

  /**
   * GetById — single payslip header by PayslipID, with display JOINs.
   * Used by the print flow to fetch the frozen header before its line items.
   */
  public Payslip GetById(Connection conn, long payslipId) throws SQLException {
  String sql =
    "SELECT ps.*, e.FirstName, e.LastName, " +
    "       pp.PeriodName, pp.StartDate, pp.EndDate, " +
    "       pos.PositionName, dep.DepartmentName, " +
    "       sal.BasicSalary AS MonthlyRate, sal.HourlyRate " +
    "FROM Payslip ps " +
    "JOIN Employees e        ON e.EmployeeID       = ps.EmployeeID " +
    "JOIN Payroll_Period pp  ON pp.PayrollPeriodID = ps.PayrollPeriodID " +
    "LEFT JOIN Positions pos   ON pos.PositionID   = e.PositionID " +
    "LEFT JOIN Departments dep ON dep.DepartmentID = e.DepartmentID " +
    "OUTER APPLY ( " +
    "  SELECT TOP 1 s.BasicSalary, s.HourlyRate " +
    "  FROM EmployeeSalary s " +
    "  WHERE s.EmployeeID = ps.EmployeeID " +
    "    AND s.EffectiveDate <= pp.EndDate " +     // period-accurate: rate as of the slip's period
    "  ORDER BY s.EffectiveDate DESC " +
    ") sal " +
    "WHERE ps.PayslipID = ?";

  try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setLong(1, payslipId);
    try (ResultSet rs = pstmt.executeQuery()) {
      return rs.next() ? new Payslip(rs) : null;
    }
  }
}

  // =========================================================================
  // Line-item reads (for itemized payslip printing)
  // =========================================================================

  /** Allowance lines for one payslip, joined to Allowance_Type for the name. */
  public List<PayslipAllowanceLine> GetAllowanceLines(Connection conn, long payslipId)
    throws SQLException {
    List<PayslipAllowanceLine> list = new ArrayList<>();
    String sql =
      "SELECT pa.PayrollAllowanceID, pa.PayslipID, pa.AllowanceTypeID, " +
      "       t.AllowanceName, pa.Amount, pa.Remarks " +
      "FROM Payroll_Allowance pa " +
      "JOIN Allowance_Type t ON t.AllowanceTypeID = pa.AllowanceTypeID " +
      "WHERE pa.PayslipID = ? " +
      "ORDER BY t.AllowanceName";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, payslipId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) list.add(new PayslipAllowanceLine(rs));
      }
    }
    return list;
  }

  /**
   * Deduction lines for one payslip, joined to Deduction_Type for the name.
   * Ordered by Category (statutory first) then name, matching payslip layout.
   */
  public List<PayslipDeductionLine> GetDeductionLines(Connection conn, long payslipId)
    throws SQLException {
    List<PayslipDeductionLine> list = new ArrayList<>();
    String sql =
      "SELECT pd.PayrollDeductionID, pd.PayslipID, pd.DeductionTypeID, " +
      "       t.DeductionName, pd.SourceType, pd.Amount, pd.Remarks " +
      "FROM Payroll_Deduction pd " +
      "JOIN Deduction_Type t ON t.DeductionTypeID = pd.DeductionTypeID " +
      "WHERE pd.PayslipID = ? " +
      "ORDER BY t.Category, t.DeductionName";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, payslipId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) list.add(new PayslipDeductionLine(rs));
      }
    }
    return list;
  }

  // =========================================================================
  // Payroll_Allowance line items
  // =========================================================================

  /**
   * SavePayrollAllowance — appends one allowance line to a payslip.
   * UNIQUE (PayslipID, AllowanceTypeID) will throw on duplicate.
   */
  public boolean SavePayrollAllowance(
    Connection conn,
    long payslipId,
    int allowanceTypeId,
    double amount,
    String remarks
  ) throws SQLException {
    String sql =
      "INSERT INTO Payroll_Allowance (PayslipID, AllowanceTypeID, Amount, Remarks) " +
      "VALUES (?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, payslipId);
      pstmt.setInt(2, allowanceTypeId);
      pstmt.setDouble(3, amount);
      pstmt.setString(4, remarks);
      return pstmt.executeUpdate() > 0;
    }
  }

  // =========================================================================
  // Payroll_Deduction line items
  // =========================================================================

  /**
   * SavePayrollDeduction — appends one deduction line to a payslip.
   * sourceId is null for statutory/manual; set to the loan PK for loan deductions.
   */
  public boolean SavePayrollDeduction(
    Connection conn,
    long payslipId,
    int deductionTypeId,
    int sourceType,
    Long sourceId,
    double amount,
    String remarks
  ) throws SQLException {
    String sql =
      "INSERT INTO Payroll_Deduction " +
      "(PayslipID, DeductionTypeID, SourceType, SourceID, Amount, Remarks) " +
      "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, payslipId);
      pstmt.setInt(2, deductionTypeId);
      pstmt.setInt(3, sourceType);
      if (sourceId != null) pstmt.setLong(4, sourceId);
      else pstmt.setNull(4, Types.BIGINT);
      pstmt.setDouble(5, amount);
      pstmt.setString(6, remarks);
      return pstmt.executeUpdate() > 0;
    }
  }
}
