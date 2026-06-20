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

    /** Period roll-up over an already-computed (and possibly filtered) set. */
    AttendanceCalculator.Summary Summarize(List<DailyAttendanceRecord> records);
}