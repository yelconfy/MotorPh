package Interface;

import Objects.models.StatutoryRemittanceRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the Statutory Remittance report screen — read-only access to
 * vw_StatutoryRemittance (script 17) plus a reprint-aware print-audit write.
 * One row per employee per month; the three form renderers (SSS R-3,
 * PhilHealth RF-1, Pag-IBIG M1-1) each consume the same rows.
 */
public interface IStatutoryRemittanceProcess {

  /** Remittance rows for the chosen year+month, employee order. */
  List<StatutoryRemittanceRow> GetForMonth(int year, int month);

  /** Distinct (year, month) periods with data, newest first — as int[]{year,month}. */
  List<int[]> GetAvailablePeriods();

  /**
   * Records a remittance PDF export to the audit trail; returns true if a prior
   * export of the same agency+period already exists (i.e. a reprint).
   *
   * @param agency short agency key used in the audit RecordID, e.g. "SSS-R3".
   */
  boolean RecordPrint(String agency, int year, int month, String username, String reason)
    throws SQLException;
}