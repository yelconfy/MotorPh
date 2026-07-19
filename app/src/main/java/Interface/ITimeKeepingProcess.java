package Interface;

import Core.Service.AttendanceCalculator;
import Objects.models.Attendance;
import Objects.models.DailyAttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ITimeKeepingProcess {

    /** Raw attendance rows (unchanged — kept for any existing callers). */
    List<Attendance> GetEmpAttendance(
        Optional<String> query,
        Optional<LocalDate> fromDate,
        Optional<LocalDate> toDate
    );

    /** Computed, presentation-ready records for the Timekeeping grid. */
    List<DailyAttendanceRecord> GetTimeRecords(
        Optional<String> query,
        Optional<LocalDate> fromDate,
        Optional<LocalDate> toDate
    );

    /**
     * Computed records for ONE employee over a date range (Phase 7b DTR).
     *
     * Unlike GetTimeRecords (which fuzzy-matches a free-text search string),
     * this is an EXACT EmployeeID match via AttendanceDAO.GetByDateRange — the
     * same exact lookup Payroll uses — so a per-employee Daily Time Record can
     * never accidentally pull a different employee's rows. Same calculator and
     * run context as the grid, so DTR / Timekeeping / Payroll never disagree.
     */
    List<DailyAttendanceRecord> GetTimeRecordsForEmployee(
        long employeeId,
        LocalDate fromDate,
        LocalDate toDate
    );

    /** Period roll-up over an already-computed (and possibly filtered) set. */
    AttendanceCalculator.Summary Summarize(List<DailyAttendanceRecord> records);
}