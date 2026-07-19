package Objects.enums;

/**
 * Status enums for every TINYINT status column in the schema.
 *
 * Convention: fromInt(int) on each enum returns the matching constant,
 * defaulting to the 0-value (initial/pending state) when unrecognized.
 *
 * Schema references:
 *   Leave_Request.Status          (04)  0=Pending,1=Approved,2=Rejected,3=Cancelled
 *   Overtime_Request.Status       (04)  0=Pending,1=Approved,2=Rejected,3=Cancelled
 *   Payroll_Period.Status         (05)  0=Open,1=Processing,2=Closed,3=Paid
 *   Payslip.Status                (05)  0=Draft,1=Finalized,2=Paid
 *   Employee_Loan.Status          (04)  0=Active,1=FullyPaid,2=Cancelled
 *   Deduction_Type.Category       (01)  0=Statutory,1=Loan,2=Voluntary
 *   Payroll_Deduction.SourceType  (05)  0=Manual,1=Statutory,2=Loan,3=Voluntary
 *   Holiday.HolidayType           (01)  0=Regular,1=SpecialNonWorking
 */
public class Status {

  // Leave_Request.Status / Overtime_Request.Status
  public enum RequestStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2),
    CANCELLED(3);

    private final int value;

    RequestStatus(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static RequestStatus fromInt(int i) {
      for (RequestStatus s : values()) if (s.value == i) return s;
      return PENDING;
    }
  }

  // Payroll_Period.Status
  public enum PayrollPeriodStatus {
    OPEN(0),
    PROCESSING(1),
    CLOSED(2),
    PAID(3);

    private final int value;

    PayrollPeriodStatus(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static PayrollPeriodStatus fromInt(int i) {
      for (PayrollPeriodStatus s : values()) if (s.value == i) return s;
      return OPEN;
    }
  }

  // Payslip.Status
  public enum PayslipStatus {
    DRAFT(0),
    FINALIZED(1),
    PAID(2);

    private final int value;

    PayslipStatus(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static PayslipStatus fromInt(int i) {
      for (PayslipStatus s : values()) if (s.value == i) return s;
      return DRAFT;
    }
  }

  // Employee_Loan.Status
  public enum LoanStatus {
    ACTIVE(0),
    FULLY_PAID(1),
    CANCELLED(2);

    private final int value;

    LoanStatus(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return switch (this) {
        case ACTIVE -> "Active";
        case FULLY_PAID -> "Fully Paid";
        case CANCELLED -> "Cancelled";
      };
    }

    public static LoanStatus fromInt(int i) {
      for (LoanStatus s : values()) if (s.value == i) return s;
      return ACTIVE;
    }
  }

  // Deduction_Type.Category
  public enum DeductionCategory {
    STATUTORY(0),
    LOAN(1),
    VOLUNTARY(2);

    private final int value;

    DeductionCategory(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static DeductionCategory fromInt(int i) {
      for (DeductionCategory s : values()) if (s.value == i) return s;
      return STATUTORY;
    }
  }

  // Payroll_Deduction.SourceType
  public enum PayrollDeductionSource {
    MANUAL(0),
    STATUTORY(1),
    LOAN(2),
    VOLUNTARY(3);

    private final int value;

    PayrollDeductionSource(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static PayrollDeductionSource fromInt(int i) {
      for (PayrollDeductionSource s : values()) if (s.value == i) return s;
      return MANUAL;
    }
  }

  /**
   * Classification of a Holiday row (mirrors Holiday.HolidayType TINYINT).
   *
   *   REGULAR             (0) - regular holiday, 200% if worked
   *   SPECIAL_NON_WORKING (1) - special non-working day, 130% if worked
   *
   * The premium MULTIPLIERS themselves live in Constants.PremiumRateMultiplier
   * (and will move to the versioned Premium_Rate table in Phase 6); this enum only
   * carries the classification + its DB code so there is a single source of truth
   * for the rates.
   */
  public enum HolidayType {
    REGULAR(0),
    SPECIAL_NON_WORKING(1);

    private final int code;

    HolidayType(int code) {
      this.code = code;
    }

    public int GetCode() {
      return code;
    }

    /** Maps the Holiday.HolidayType TINYINT to the enum (anything != 1 => REGULAR). */
    public static HolidayType FromCode(int code) {
      return (code == 1) ? SPECIAL_NON_WORKING : REGULAR;
    }
  }

  public enum EmploymentStatus {
    PROBATIONARY(0),
    REGULAR(1),
    TERMINATED(2);

    private final int value;

    // Constructor for the Enum
    EmploymentStatus(int value) {
      this.value = value;
    }

    // Returns the integer to be saved in SQL
    public int getValue() {
      return value;
    }

    // This converts the INT from the Database back into an Enum object.
    public static EmploymentStatus fromInt(int i) {
      for (EmploymentStatus status : EmploymentStatus.values()) {
        if (status.getValue() == i) {
          return status;
        }
      }
      // Default to PROBATIONARY if the number doesn't match
      return PROBATIONARY;
    }
  }

  public enum AttendanceStatus {
    PRESENT("Present"),
    LATE("Late"),
    INCOMPLETE("Incomplete"),
    ABSENT("Absent"),
    ON_LEAVE("On Leave"),
    ON_LEAVE_UNPAID("Unpaid Leave");

    private final String label;

    AttendanceStatus(String label) {
      this.label = label;
    }

    public String GetLabel() {
      return label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  // Audit_Log.ActionType
  public enum AuditAction {
    INSERT(0),
    UPDATE(1),
    DELETE(2),
    PRINT(3);

    private final int value;

    AuditAction(int v) {
      this.value = v;
    }

    public int getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name().charAt(0) + name().substring(1).toLowerCase();
    }

    public static AuditAction fromInt(int i) {
      for (AuditAction a : values()) if (a.value == i) return a;
      return INSERT;
    }
  }
}
