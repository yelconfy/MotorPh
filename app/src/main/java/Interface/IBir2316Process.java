package Interface;

import Objects.models.Bir2316Row;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the BIR Form 2316 certificate screen — read-only access to
 * vw_Bir2316 (script 17) plus a reprint-aware print-audit write. One row per
 * employee per year; a certificate PDF is exported one employee at a time.
 */
public interface IBir2316Process {

  /** 2316 rows for the chosen year, employee order. */
  List<Bir2316Row> GetForYear(int year);

  /** Distinct years with data, newest first — for the picker. */
  List<Integer> GetAvailableYears();

  /**
   * Records a 2316 certificate export to the audit trail; returns true if a
   * prior export for the same employee+year already exists (a reprint).
   */
  boolean RecordPrint(long employeeNo, int year, String username, String reason)
    throws SQLException;
}