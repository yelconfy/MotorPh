package Core.Service;

import Objects.enums.Status.AttendanceStatus;
import Objects.enums.Status.HolidayType;
import Objects.models.Attendance;
import Objects.models.AttendanceContext;
import Objects.models.DailyAttendanceRecord;
import Objects.models.DailyAttendanceRecord.DayType;
import Objects.models.HolidayCalendar;
import Objects.models.WorkScheduleInfo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless per-day attendance math. Single source of truth shared with
 * payroll (PayrollCalculator delegates ComputeDay). No JDBC — the process layer
 * injects schedules/holidays/approved-OT/approved-leave/night-window via
 * {@link AttendanceContext}.
 *
 * Rules: schedule-driven shift/grace/break [P1]; break deducted [P1]; grace
 * counts only beyond-grace minutes [P1]; rest day per Works* flags [P1]; holiday
 * type from the calendar [P3]; OT raw + approved-capped [P4]; approved leave
 * paid/unpaid on missing-punch days [P5]; night-differential minutes + undertime
 * [P6b].
 */
public final class AttendanceCalculator {

    /** Computes the derived metrics for a single attendance row. */
    public DailyAttendanceRecord ComputeDay(Attendance a, AttendanceContext ctx) {
        AttendanceContext c = (ctx != null) ? ctx : AttendanceContext.Empty();
        WorkScheduleInfo sched = c.ScheduleFor(a.GetEmployeeId());
        DayType dayType = ResolveDayType(a.GetAttendanceDate(), sched, c.Holidays());

        LocalTime in  = a.GetTimeIn();
        LocalTime out = a.GetTimeOut();

        // No punches: approved leave (paid/unpaid) overrides the absent default.
        if (in == null && out == null) {
            Boolean paidLeave = c.LeaveStatusFor(a.GetEmployeeId(), a.GetAttendanceDate());
            AttendanceStatus st = (paidLeave == null)
                ? AttendanceStatus.ABSENT
                : (paidLeave ? AttendanceStatus.ON_LEAVE : AttendanceStatus.ON_LEAVE_UNPAID);
            return new DailyAttendanceRecord(a, st, dayType, 0, 0, 0, 0, 0, 0, 0);
        }
        if (in == null || out == null) {
            return new DailyAttendanceRecord(a, AttendanceStatus.INCOMPLETE, dayType, 0, 0, 0, 0, 0, 0, 0);
        }

        LocalTime shiftStart = sched.GetTimeStart();
        LocalTime shiftEnd   = sched.GetTimeEnd();
        int grace            = sched.GetGracePeriodMinutes();
        int breakMin         = sched.GetBreakMinutes();
        long netShiftMin     = ScheduledNetMinutes(sched); // scheduled span minus unpaid break

        // Gross clocked time (guard an overnight out < in).
        long grossWorkedMin = Duration.between(in, out).toMinutes();
        if (grossWorkedMin < 0) grossWorkedMin += 24 * 60;

        // Deduct the unpaid break once the person has worked more than the break.
        long netWorkedMin = (grossWorkedMin > breakMin)
            ? grossWorkedMin - breakMin
            : grossWorkedMin;

        // Lateness vs scheduled start, with grace; count only minutes beyond grace.
        long rawLateMin = (shiftStart != null && in.isAfter(shiftStart))
            ? Duration.between(shiftStart, in).toMinutes()
            : 0;
        long lateMin = (rawLateMin > grace) ? rawLateMin - grace : 0;

        // Regular capped at the net scheduled shift; remainder is RAW overtime.
        long regMin    = Math.min(netWorkedMin, netShiftMin);
        long rawOtMin  = Math.max(0, netWorkedMin - netShiftMin);

        // Approved (paid) OT = raw capped by approved minutes for this emp/date.
        long approvedOtMin = Math.min(
            rawOtMin,
            c.ApprovedOvertimeMinutes(a.GetEmployeeId(), a.GetAttendanceDate())
        );

        // Night-differential minutes: overlap of [in,out] with the night window.
        long nightDiffMin = NightMinutes(in, out, c.NightWindowStart(), c.NightWindowEnd());

        // Undertime: early departure, but ONLY when the day was underworked net
        // (so early-birds aren't penalised) and never overlapping the lates dock
        // (front = lateness, back = undertime).
        long undertimeMin = 0;
        if (netWorkedMin < netShiftMin && shiftEnd != null && out.isBefore(shiftEnd)) {
            undertimeMin = Duration.between(out, shiftEnd).toMinutes();
        }

        AttendanceStatus status = (lateMin > 0)
            ? AttendanceStatus.LATE
            : AttendanceStatus.PRESENT;

        return new DailyAttendanceRecord(
            a, status, dayType, lateMin, grossWorkedMin, regMin, rawOtMin,
            approvedOtMin, nightDiffMin, undertimeMin
        );
    }

    /** Maps a list of raw rows to computed records using the run context. */
    public List<DailyAttendanceRecord> ComputeAll(List<Attendance> rows, AttendanceContext ctx) {
        List<DailyAttendanceRecord> out = new ArrayList<>();
        if (rows != null) {
            AttendanceContext c = (ctx != null) ? ctx : AttendanceContext.Empty();
            for (Attendance a : rows) {
                out.add(ComputeDay(a, c));
            }
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
                default         -> { /* ON_LEAVE / ON_LEAVE_UNPAID: not counted here */ }
            }
            s.totalLateMinutes += r.GetLateMinutes();
            s.workedHours      += (int) (r.GetRegularMinutes()  / 60);
            s.overtimeHours    += (int) (r.GetOvertimeMinutes() / 60);
        }
        return s;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /** Scheduled PAID minutes per day: (TimeEnd - TimeStart) minus the unpaid break. */
    private static long ScheduledNetMinutes(WorkScheduleInfo s) {
        LocalTime start = s.GetTimeStart();
        LocalTime end   = s.GetTimeEnd();
        if (start == null || end == null) return 8 * 60; // safe default: 8h
        long span = Duration.between(start, end).toMinutes();
        if (span < 0) span += 24 * 60; // crosses midnight
        return Math.max(0, span - s.GetBreakMinutes());
    }

    /**
     * Minutes of [in,out] that fall inside the night window. Handles a window
     * that wraps midnight (e.g. 22:00-06:00). Same-day punches only (out > in);
     * overnight rows are not night-counted here (a later refinement).
     */
    private static long NightMinutes(LocalTime in, LocalTime out, LocalTime nStart, LocalTime nEnd) {
        if (in == null || out == null || nStart == null || nEnd == null) return 0;
        if (!out.isAfter(in)) return 0; // overnight not handled here
        if (nStart.isBefore(nEnd)) {
            return OverlapMin(in, out, nStart, nEnd);
        }
        // Wrapping window: [nStart, 24:00) + [00:00, nEnd)
        return OverlapMin(in, out, nStart, LocalTime.MAX)
             + OverlapMin(in, out, LocalTime.MIDNIGHT, nEnd);
    }

    /** Overlap minutes of [a,b] with [c,d] (all same-day, non-wrapping). */
    private static long OverlapMin(LocalTime a, LocalTime b, LocalTime c, LocalTime d) {
        LocalTime start = a.isAfter(c) ? a : c;
        LocalTime end   = b.isBefore(d) ? b : d;
        if (!end.isAfter(start)) return 0;
        return Duration.between(start, end).toMinutes();
    }

    private DayType ResolveDayType(LocalDate date, WorkScheduleInfo schedule, HolidayCalendar holidays) {
        if (date == null) return DayType.REGULAR;
        HolidayType ht = (holidays != null) ? holidays.TypeOf(date) : null;
        if (ht != null) {
            return (ht == HolidayType.SPECIAL_NON_WORKING)
                ? DayType.HOLIDAY_SPECIAL
                : DayType.HOLIDAY;
        }
        return IsScheduledWorkday(date, schedule) ? DayType.REGULAR : DayType.WEEKEND;
    }

    private static boolean IsScheduledWorkday(LocalDate date, WorkScheduleInfo s) {
        if (s == null) {
            DayOfWeek d = date.getDayOfWeek();
            return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
        }
        return s.WorksOn(date);
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
        public int  GetDaysWorked()       { return onTimeDays + lateDays; }
    }
}