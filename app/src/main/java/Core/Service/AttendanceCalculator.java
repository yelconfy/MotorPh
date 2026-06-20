package Core.Service;

import Objects.enums.Status.AttendanceStatus;
import Objects.enums.Constants.Holidays;
import Objects.enums.Constants.ShiftTime;
import Objects.models.Attendance;
import Objects.models.DailyAttendanceRecord;
import Objects.models.DailyAttendanceRecord.DayType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pure, stateless per-day attendance math.
 *
 * This is intentionally a carbon copy of the logic inside
 * PayrollProcess.ProcessDailyAttendance / IsAbsent so the Timekeeping screen
 * and Payroll never disagree on hours. Two payroll behaviours are mirrored on
 * purpose (do not "fix" them here in isolation, or the two screens drift):
 *   1. The lunch break is NOT subtracted from worked time.
 *   2. Daily hours are floored to whole hours (regMin/60), so the period
 *      summary re-floors per day rather than dividing a grand total.
 *
 * RECOMMENDED FOLLOW-UP: have PayrollProcess.ProcessDailyAttendance delegate to
 * ComputeDay(...) so this is the only place the formula lives.
 */
public final class AttendanceCalculator {

    /** Computes the derived metrics for a single attendance row. */
    public DailyAttendanceRecord ComputeDay(Attendance a) {
        DayType dayType = ResolveDayType(a.GetAttendanceDate());

        LocalTime in  = a.GetTimeIn();
        LocalTime out = a.GetTimeOut();

        // Missing punch(es): no pay credit, exactly as PayrollProcess.IsAbsent.
        if (in == null && out == null) {
            return new DailyAttendanceRecord(a, AttendanceStatus.ABSENT, dayType, 0, 0, 0, 0);
        }
        if (in == null || out == null) {
            return new DailyAttendanceRecord(a, AttendanceStatus.INCOMPLETE, dayType, 0, 0, 0, 0);
        }

        LocalTime shiftStart = ShiftTime.START.GetTime();
        long shiftMin = ShiftTime.SHIFT_LENGTH.GetDuration().toMinutes();

        long workedMin = Duration.between(in, out).toMinutes();
        long lateMin   = in.isAfter(shiftStart)
            ? Duration.between(shiftStart, in).toMinutes()
            : 0;

        long adjustedMin = Math.max(0, workedMin - lateMin);
        long regMin = Math.min(adjustedMin, shiftMin);
        long otMin  = Math.max(0, adjustedMin - shiftMin);

        AttendanceStatus status = (lateMin > 0)
            ? AttendanceStatus.LATE
            : AttendanceStatus.PRESENT;

        return new DailyAttendanceRecord(a, status, dayType, lateMin, workedMin, regMin, otMin);
    }

    /** Maps a list of raw rows to computed records, preserving order. */
    public java.util.List<DailyAttendanceRecord> ComputeAll(List<Attendance> rows) {
        java.util.List<DailyAttendanceRecord> out = new java.util.ArrayList<>();
        if (rows != null) {
            for (Attendance a : rows) out.add(ComputeDay(a));
        }
        return out;
    }

    /** Rolls a set of computed records into a period summary. */
    public Summary Summarize(List<DailyAttendanceRecord> records) {
        Summary s = new Summary();
        if (records == null) return s;

        for (DailyAttendanceRecord r : records) {
            s.totalRecords++;
            switch (r.GetStatus()) {
                case PRESENT    -> s.onTimeDays++;
                case LATE       -> s.lateDays++;
                case INCOMPLETE -> s.incompleteDays++;
                case ABSENT     -> s.absentDays++;
            }
            s.totalLateMinutes += r.GetLateMinutes();
            s.workedHours      += (int) (r.GetRegularMinutes()  / 60);  // per-day floor, payroll-consistent
            s.overtimeHours    += (int) (r.GetOvertimeMinutes() / 60);
        }
        return s;
    }

    private DayType ResolveDayType(LocalDate date) {
        if (date == null) return DayType.REGULAR;
        if (Holidays.IsHoliday(date)) return DayType.HOLIDAY;
        DayOfWeek d = date.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return DayType.WEEKEND;
        return DayType.REGULAR;
    }

    /** Period roll-up over a set of computed records. */
    public static final class Summary {
        private int  totalRecords;
        private int  onTimeDays;
        private int  lateDays;
        private int  incompleteDays;
        private int  absentDays;
        private long totalLateMinutes;
        private int  workedHours;
        private int  overtimeHours;

        public int  GetTotalRecords()     { return totalRecords; }
        public int  GetOnTimeDays()       { return onTimeDays; }
        public int  GetLateDays()         { return lateDays; }
        public int  GetIncompleteDays()   { return incompleteDays; }
        public int  GetAbsentDays()       { return absentDays; }
        public long GetTotalLateMinutes() { return totalLateMinutes; }
        public int  GetWorkedHours()      { return workedHours; }
        public int  GetOvertimeHours()    { return overtimeHours; }

        /** Days actually worked (on time + late). */
        public int GetDaysWorked() { return onTimeDays + lateDays; }
    }
}