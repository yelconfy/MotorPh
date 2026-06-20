package Core.Service;

import Objects.models.EmpDetail;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure, stateless workforce computations over a list of employees.
 *
 * Everything here is derived from fields already present on the EmpDetail rows
 * returned by EmpMgmtProcess.GetEmpDetails() (the complete-details view), so it
 * needs no extra per-employee queries.
 *
 * "Profile complete" is judged on the fields available in that list view
 * (position, department, contact, base pay, hire date). Statutory-ID
 * completeness is intentionally NOT checked here because statutory data is
 * hydrated only on the detail load (GetCompleteEmployee); surface that in the
 * profile view, not the directory roll-up.
 */
public final class WorkforceAnalytics {

    public Snapshot Analyze(List<EmpDetail> employees) {
        Snapshot s = new Snapshot();
        if (employees == null) return s;

        int tenureSum = 0;
        int tenureCount = 0;

        for (EmpDetail e : employees) {
            if (e == null) continue;
            s.totalActive++;

            s.byDepartment.merge(DepartmentLabel(e), 1, Integer::sum);
            s.byStatus.merge(StatusLabel(e), 1, Integer::sum);
            s.byPosition.merge(PositionLabel(e), 1, Integer::sum);

            if (!IsProfileComplete(e)) s.incompleteProfiles++;

            if (e.GetDateHired() != null) {
                tenureSum += TenureYears(e);
                tenureCount++;
            }
        }

        s.departmentCount = s.byDepartment.size();
        s.avgTenureYears  = (tenureCount > 0) ? (double) tenureSum / tenureCount : 0.0;
        return s;
    }

    public boolean IsProfileComplete(EmpDetail e) {
        if (e == null) return false;
        return e.GetPosition()   != null && e.GetPosition().GetPositionID()    > 0
            && e.GetDepartment() != null && e.GetDepartment().GetDepartmentId() > 0
            && NotBlank(e.GetEmail())
            && NotBlank(e.GetPhoneNo())
            && e.GetCompensation() != null && e.GetCompensation().GetBasicSalary() > 0
            && e.GetDateHired() != null;
    }

    public int TenureYears(EmpDetail e) {
        if (e == null || e.GetDateHired() == null) return 0;
        return Period.between(e.GetDateHired(), LocalDate.now()).getYears();
    }

    // -- label helpers (shared with the directory table) --------------------

    public static String StatusLabel(EmpDetail e) {
        return (e.GetEmpStatus() != null) ? Title(e.GetEmpStatus().toString()) : "Unknown";
    }

    public static String DepartmentLabel(EmpDetail e) {
        if (e.GetDepartment() != null && e.GetDepartment().GetDepartmentId() > 0
                && NotBlank(e.GetDepartment().GetDepartmentName())) {
            return e.GetDepartment().GetDepartmentName();
        }
        return "Unassigned";
    }

    public static String PositionLabel(EmpDetail e) {
        if (e.GetPosition() != null && e.GetPosition().GetPositionID() > 0
                && NotBlank(e.GetPosition().GetPositionName())) {
            return e.GetPosition().GetPositionName();
        }
        return "Unassigned";
    }

    private static boolean NotBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String Title(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Headcount roll-up over a set of employees. */
    public static final class Snapshot {
        private int totalActive;
        private int departmentCount;
        private int incompleteProfiles;
        private double avgTenureYears;
        private final Map<String, Integer> byDepartment = new TreeMap<>();
        private final Map<String, Integer> byStatus     = new TreeMap<>();
        private final Map<String, Integer> byPosition   = new TreeMap<>();

        public int GetTotalActive()        { return totalActive; }
        public int GetDepartmentCount()    { return departmentCount; }
        public int GetIncompleteProfiles() { return incompleteProfiles; }
        public double GetAvgTenureYears()  { return avgTenureYears; }
        public Map<String, Integer> GetByDepartment() { return byDepartment; }
        public Map<String, Integer> GetByStatus()     { return byStatus; }
        public Map<String, Integer> GetByPosition()   { return byPosition; }
    }
}