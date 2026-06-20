package Objects.models;

import java.util.List;

/**
 * A fully-itemized payslip for printing: the frozen Payslip header (basic,
 * allowance/deduction totals, gross, net) plus its persisted line items.
 *
 * Nothing here is recomputed — the renderer reads the header for totals and the
 * lists for the breakdown, so a reprint always matches what was finalized.
 */
public class PayslipDetail {

  private final Payslip header;
  private final List<PayslipAllowanceLine> allowances;
  private final List<PayslipDeductionLine> deductions;

  public PayslipDetail(
    Payslip header,
    List<PayslipAllowanceLine> allowances,
    List<PayslipDeductionLine> deductions
  ) {
    this.header = header;
    this.allowances = allowances;
    this.deductions = deductions;
  }

  public Payslip GetHeader() { return header; }
  public List<PayslipAllowanceLine> GetAllowances() { return allowances; }
  public List<PayslipDeductionLine> GetDeductions() { return deductions; }
}