package Helper;

import Core.Enum.SmartFieldType;
import Forms.FieldDescriptor;
import Forms.MaintenanceDescriptor;
import Forms.ReferenceMaintenancePanel;
import Objects.enums.ModuleCode;
import Objects.enums.Status.DeductionCategory;
import Objects.models.AccountRole;
import Objects.models.AllowanceTypeInfo;
import Objects.models.DeductionTypeInfo;
import Objects.models.DepartmentInfo;
import Objects.models.LeaveTypeInfo;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * Admin-owned reference-data maintenance screens — one module per table.
 *
 * Post-BKL-26 every screen here is descriptor-driven: a thin
 * IMaintenanceProcess<T> from Injector + a MaintenanceDescriptor<T> handed to
 * the generic ReferenceMaintenancePanel<T>. No bespoke Panel class per table.
 *
 * Permission codes for ADD/EDIT/DELETE gating arrive as the `perms` argument
 * (BKL-25) — Injector.CreateModuleViews() resolves them from Session's cached
 * matrix using the map key below, so no registration here names its module
 * code a second time. Keys come from ModuleCode (BKL-24), never raw strings.
 *
 * The remaining modules listed at the bottom are declared in ModuleCode and
 * seeded in script 09 but have no registration yet — ModuleReconciler prints
 * them as WARNs at startup, which doubles as the live BKL-01 TODO list. Each
 * slice added here removes exactly one WARN.
 *
 * BKL-26 field-kind extension: fields are registered with a stable key as
 * well as a label (MaintenanceDescriptor.Builder.field(key, label, type,
 * getter, setter), or FieldDescriptor.text/combo/checkbox(...) fed into the
 * single-arg field(FieldDescriptor<T>) overload for non-text kinds). POSITIONS
 * and DEPARTMENTS are migrated onto it; DEDUCTIONTYPE below is the first
 * screen that actually needs a non-TEXT kind.
 */
final class ReferenceModules {

  private ReferenceModules() {}

  // DEDUCTIONTYPE row-level protection: these four rows are matched by name in
  // PayrollProcess (DT_SSS / DT_PHILHEALTH / DT_PAGIBIG / DT_WITHHOLDING) and
  // wired to Contribution_Rate / the WHT bracket lookup — renaming or removing
  // any of them here would silently break statutory computation. Matched
  // case-insensitively against the seeded names (09_Seed_Access_Control_RBAC.sql).
  private static final List<String> PROTECTED_DEDUCTION_NAMES = List.of(
    "SSS",
    "PhilHealth",
    "Pag-IBIG",
    "Withholding Tax"
  );

  private static boolean isProtectedDeduction(DeductionTypeInfo d) {
    String name = d.GetDeductionName();
    return (
      name != null &&
      PROTECTED_DEDUCTION_NAMES.stream().anyMatch(name::equalsIgnoreCase)
    );
  }

  // STATUTORY excluded from the Category dropdown on purpose: the four
  // statutory rows are fixed seeded fixtures (see above) with no computation
  // wired to a freshly-Added row, so an Admin creating a new type should only
  // ever be choosing between LOAN and VOLUNTARY.
  private static final DeductionCategory[] ASSIGNABLE_CATEGORIES = {
    DeductionCategory.LOAN,
    DeductionCategory.VOLUNTARY,
  };

  // ALLOWANCETYPE row-level protection: these three rows are matched by exact
  // name — EmpDetail.GetAllowanceAmount("Rice Subsidy") etc. in the payroll
  // display path, AND hardcoded in the vw_MonthlyPayrollSummary SQL view (the
  // view coupling means a rename breaks reporting even beyond the Java layer).
  // Renaming or removing any of them here would silently break both. Same
  // fragility class as MPH-47 (statutory deductions); logged as its sibling.
  private static final List<String> PROTECTED_ALLOWANCE_NAMES = List.of(
    "Rice Subsidy",
    "Phone Allowance",
    "Clothing Allowance"
  );

  private static String workDaysSummary(WorkScheduleInfo ws) {
    StringBuilder sb = new StringBuilder();
    if (ws.GetWorksMon()) sb.append("Mon ");
    if (ws.GetWorksTue()) sb.append("Tue ");
    if (ws.GetWorksWed()) sb.append("Wed ");
    if (ws.GetWorksThu()) sb.append("Thu ");
    if (ws.GetWorksFri()) sb.append("Fri ");
    if (ws.GetWorksSat()) sb.append("Sat ");
    if (ws.GetWorksSun()) sb.append("Sun ");
    return sb.toString().trim();
  }

  private static boolean isProtectedAllowance(AllowanceTypeInfo a) {
    String name = a.GetAllowanceName();
    return (
      name != null &&
      PROTECTED_ALLOWANCE_NAMES.stream().anyMatch(name::equalsIgnoreCase)
    );
  }

  static void register(Map<String, Function<List<String>, JComponent>> views) {
    // POSITIONS — BKL-26 retrofit proof (single Name field).
    views.put(ModuleCode.POSITIONS.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreatePositionMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<PositionInfo>builder()
          .title("Position Maintenance")
          .subtitle(
            "Add, rename, or remove job titles used across the workforce."
          )
          .itemLabel("position")
          .inUseMessage(
            "This position can't be deleted because one or more employees are " +
              "still assigned to it. Reassign them first."
          )
          .saveFailedMessage(
            "Could not save the position. The name may already exist."
          )
          .idFn(PositionInfo::GetPositionID)
          .labelFn(PositionInfo::GetPositionName)
          .factory(PositionInfo::new)
          .column("Position ID", PositionInfo::GetPositionID)
          .column("Position Name", PositionInfo::GetPositionName)
          .field(
            "name",
            "Position Name",
            SmartFieldType.MANDATORY_GENERIC,
            PositionInfo::GetPositionName,
            PositionInfo::SetPositionName
          )
          .build()
      )
    );

    // DEPARTMENTS — BKL-01 stage 3a, first genuinely descriptor-driven slice.
    // Two user-entered columns (Code + Name); both editable (the generic panel
    // toggles the whole form at once, so there is no code-immutable-on-edit path).
    // idFn must cast: DepartmentInfo.GetDepartmentId() is int, which does NOT
    // autobox to the Long that idFn(Function<T,Long>) requires — a bare
    // DepartmentInfo::GetDepartmentId method ref will not compile here.
    views.put(ModuleCode.DEPARTMENTS.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateDepartmentMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<DepartmentInfo>builder()
          .title("Department Maintenance")
          .subtitle(
            "Add, recode, rename, or remove the departments employees are assigned to."
          )
          .itemLabel("department")
          .inUseMessage(
            "This department can't be deleted because one or more employees are " +
              "still assigned to it. Reassign them first."
          )
          .saveFailedMessage(
            "Could not save the department. The code or name may already exist."
          )
          .idFn(d -> (long) d.GetDepartmentId())
          .labelFn(DepartmentInfo::GetDepartmentName)
          .factory(DepartmentInfo::new)
          .column("Department ID", DepartmentInfo::GetDepartmentId)
          .column("Department Code", DepartmentInfo::GetDepartmentCode)
          .column("Department Name", DepartmentInfo::GetDepartmentName)
          .field(
            "code",
            "Department Code",
            SmartFieldType.MANDATORY_GENERIC,
            DepartmentInfo::GetDepartmentCode,
            DepartmentInfo::SetDepartmentCode
          )
          .field(
            "name",
            "Department Name",
            SmartFieldType.MANDATORY_GENERIC,
            DepartmentInfo::GetDepartmentName,
            DepartmentInfo::SetDepartmentName
          )
          .build()
      )
    );

    // DEDUCTIONTYPE — BKL-01 stage 3b, first of the four remaining flat
    // tables. Category is COMBO-backed (BKL-26 field-kind extension) — the
    // only new field kind this slice needed. The four statutory rows are
    // locked out of Edit/Delete via protectedWhen; IN_USE on Delete comes from
    // DeductionDAO.IsInUse (soft-delete table, no FK exception to catch).
    views.put(ModuleCode.DEDUCTIONTYPE.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateDeductionTypeMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<DeductionTypeInfo>builder()
          .title("Deduction Type Maintenance")
          .subtitle("Add or remove loan and voluntary deduction types.")
          .itemLabel("deduction type")
          .inUseMessage(
            "This deduction type can't be removed because one or more employees " +
              "still have an active deduction or loan against it."
          )
          .saveFailedMessage(
            "Could not save the deduction type. The name may already exist."
          )
          .protectedMessage(
            "This deduction type backs statutory payroll calculations and can't " +
              "be edited or removed here."
          )
          .protectedWhen(ReferenceModules::isProtectedDeduction)
          .idFn(d -> (long) d.GetDeductionTypeId())
          .labelFn(DeductionTypeInfo::GetDeductionName)
          .factory(DeductionTypeInfo::new)
          .column("Deduction Type ID", DeductionTypeInfo::GetDeductionTypeId)
          .column("Name", DeductionTypeInfo::GetDeductionName)
          .column("Category", DeductionTypeInfo::GetCategory)
          .field(
            FieldDescriptor.text(
              "name",
              "Deduction Name",
              SmartFieldType.MANDATORY_GENERIC,
              DeductionTypeInfo::GetDeductionName,
              DeductionTypeInfo::SetDeductionName
            )
          )
          .field(
            FieldDescriptor.combo(
              "category",
              "Category",
              ASSIGNABLE_CATEGORIES,
              DeductionCategory::toString,
              DeductionTypeInfo::GetCategory,
              DeductionTypeInfo::SetCategory
            )
          )
          .build()
      )
    );

    // ALLOWANCETYPE — BKL-01 stage 3b. First screen to exercise the CHECKBOX
    // field kind (IsTaxable / IsRecurring, both BIT). No COMBO here. The three
    // seeded rows are name-matched in payroll + the summary view, so they're
    // locked via protectedWhen (MPH-47 sibling). IN_USE on Delete comes from
    // AllowanceDAO.IsInUse (soft-delete table, no FK exception to catch).
    views.put(ModuleCode.ALLOWANCETYPE.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateAllowanceTypeMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<AllowanceTypeInfo>builder()
          .title("Allowance Type Maintenance")
          .subtitle(
            "Add or remove allowance types and set whether each is taxable and recurring."
          )
          .itemLabel("allowance type")
          .inUseMessage(
            "This allowance type can't be removed because one or more employees " +
              "still have an active allowance of this type."
          )
          .saveFailedMessage(
            "Could not save the allowance type. The name may already exist."
          )
          .protectedMessage(
            "This allowance type is referenced by name in payroll and reporting " +
              "and can't be edited or removed here."
          )
          .protectedWhen(ReferenceModules::isProtectedAllowance)
          .idFn(a -> (long) a.GetAllowanceTypeId())
          .labelFn(AllowanceTypeInfo::GetAllowanceName)
          .factory(AllowanceTypeInfo::new)
          .column("Allowance Type ID", AllowanceTypeInfo::GetAllowanceTypeId)
          .column("Name", AllowanceTypeInfo::GetAllowanceName)
          .column("Taxable", AllowanceTypeInfo::IsTaxable)
          .column("Recurring", AllowanceTypeInfo::IsRecurring)
          .field(
            FieldDescriptor.text(
              "name",
              "Allowance Name",
              SmartFieldType.MANDATORY_GENERIC,
              AllowanceTypeInfo::GetAllowanceName,
              AllowanceTypeInfo::SetAllowanceName
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "taxable",
              "Taxable",
              AllowanceTypeInfo::IsTaxable,
              AllowanceTypeInfo::SetTaxable
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "recurring",
              "Recurring",
              AllowanceTypeInfo::IsRecurring,
              AllowanceTypeInfo::SetRecurring
            )
          )
          .build()
      )
    );

    // LEAVETYPE — BKL-01 stage 3b, third of the four flat tables and the
    // first to exercise the NUMERIC field kind (DefaultDaysPerYear /
    // MaxCarryOverDays) plus a real cross-field business rule (the carry-over
    // check, validated in LeaveTypeMaintenanceProcess — see its javadoc).
    // IsPaid / CarryOverAllowed reuse CHECKBOX, unchanged since ALLOWANCETYPE.
    views.put(ModuleCode.LEAVETYPE.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateLeaveTypeMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<LeaveTypeInfo>builder()
          .title("Leave Type Maintenance")
          .subtitle(
            "Add or remove leave types and set default entitlement and carry-over rules."
          )
          .itemLabel("leave type")
          .inUseMessage(
            "This leave type can't be removed because one or more leave requests " +
              "or entitlements still reference it."
          )
          .saveFailedMessage(
            "Could not save the leave type. The name may already exist."
          )
          .idFn(lt -> (long) lt.GetLeaveTypeId())
          .labelFn(LeaveTypeInfo::GetLeaveTypeName)
          .factory(LeaveTypeInfo::new)
          .column("Leave Type ID", LeaveTypeInfo::GetLeaveTypeId)
          .column("Name", LeaveTypeInfo::GetLeaveTypeName)
          .column("Paid", LeaveTypeInfo::IsPaid)
          .column("Default Days/Year", LeaveTypeInfo::GetDefaultDaysPerYear)
          .column("Carry-Over Allowed", LeaveTypeInfo::IsCarryOverAllowed)
          .column("Max Carry-Over", LeaveTypeInfo::GetMaxCarryOverDays)
          .field(
            "name",
            "Leave Type Name",
            SmartFieldType.MANDATORY_GENERIC,
            LeaveTypeInfo::GetLeaveTypeName,
            LeaveTypeInfo::SetLeaveTypeName
          )
          .field(
            FieldDescriptor.checkbox(
              "isPaid",
              "Paid Leave",
              LeaveTypeInfo::IsPaid,
              LeaveTypeInfo::SetPaid
            )
          )
          .field(
            FieldDescriptor.numeric(
              "defaultDays",
              "Default Days Per Year",
              LeaveTypeInfo::GetDefaultDaysPerYear,
              LeaveTypeInfo::SetDefaultDaysPerYear
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "carryOverAllowed",
              "Carry-Over Allowed",
              LeaveTypeInfo::IsCarryOverAllowed,
              LeaveTypeInfo::SetCarryOverAllowed
            )
          )
          .field(
            FieldDescriptor.numeric(
              "maxCarryOver",
              "Max Carry-Over Days",
              LeaveTypeInfo::GetMaxCarryOverDays,
              LeaveTypeInfo::SetMaxCarryOverDays
            )
          )
          .build()
      )
    );

    // WORKSCHEDULE — BKL-01 stage 3b, last of the four flat tables and the
    // biggest field-kind lift of the three: TIME (new) for TimeStart/TimeEnd,
    // TEXT+MANDATORY_INTEGER (existing, adapted via inline lambdas) for
    // BreakMinutes/GracePeriodMinutes, CHECKBOX (existing) x7 for the
    // day-of-week flags. No cross-field validation, unlike LEAVETYPE — an
    // overnight shift (TimeEnd < TimeStart) is valid, not an error.
    views.put(ModuleCode.WORKSCHEDULE.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateWorkScheduleMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<WorkScheduleInfo>builder()
          .title("Work Schedule Maintenance")
          .subtitle(
            "Add or remove shift schedules and the days each one covers."
          )
          .itemLabel("work schedule")
          .inUseMessage(
            "This work schedule can't be removed because one or more employees " +
              "are still assigned to it. Reassign them first."
          )
          .saveFailedMessage(
            "Could not save the work schedule. The name may already exist."
          )
          .idFn(ws -> (long) ws.GetScheduleId())
          .labelFn(WorkScheduleInfo::GetScheduleName)
          .factory(WorkScheduleInfo::new)
          .column("Schedule ID", WorkScheduleInfo::GetScheduleId)
          .column("Name", WorkScheduleInfo::GetScheduleName)
          .column("Start", WorkScheduleInfo::GetTimeStart)
          .column("End", WorkScheduleInfo::GetTimeEnd)
          .column("Break (min)", WorkScheduleInfo::GetBreakMinutes)
          .column("Work Days", ReferenceModules::workDaysSummary)
          .field(
            "name",
            "Schedule Name",
            SmartFieldType.MANDATORY_GENERIC,
            WorkScheduleInfo::GetScheduleName,
            WorkScheduleInfo::SetScheduleName
          )
          .field(
            FieldDescriptor.time(
              "timeStart",
              "Time Start",
              WorkScheduleInfo::GetTimeStart,
              WorkScheduleInfo::SetTimeStart
            )
          )
          .field(
            FieldDescriptor.time(
              "timeEnd",
              "Time End",
              WorkScheduleInfo::GetTimeEnd,
              WorkScheduleInfo::SetTimeEnd
            )
          )
          .field(
            "breakMinutes",
            "Break (minutes)",
            SmartFieldType.MANDATORY_INTEGER,
            ws -> String.valueOf(ws.GetBreakMinutes()),
            (ws, s) -> ws.SetBreakMinutes(Integer.parseInt(s))
          )
          .field(
            "gracePeriod",
            "Grace Period (minutes)",
            SmartFieldType.MANDATORY_INTEGER,
            ws -> String.valueOf(ws.GetGracePeriodMinutes()),
            (ws, s) -> ws.SetGracePeriodMinutes(Integer.parseInt(s))
          )
          .field(
            FieldDescriptor.checkbox(
              "worksMon",
              "Monday",
              WorkScheduleInfo::GetWorksMon,
              WorkScheduleInfo::SetWorksMon
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksTue",
              "Tuesday",
              WorkScheduleInfo::GetWorksTue,
              WorkScheduleInfo::SetWorksTue
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksWed",
              "Wednesday",
              WorkScheduleInfo::GetWorksWed,
              WorkScheduleInfo::SetWorksWed
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksThu",
              "Thursday",
              WorkScheduleInfo::GetWorksThu,
              WorkScheduleInfo::SetWorksThu
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksFri",
              "Friday",
              WorkScheduleInfo::GetWorksFri,
              WorkScheduleInfo::SetWorksFri
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksSat",
              "Saturday",
              WorkScheduleInfo::GetWorksSat,
              WorkScheduleInfo::SetWorksSat
            )
          )
          .field(
            FieldDescriptor.checkbox(
              "worksSun",
              "Sunday",
              WorkScheduleInfo::GetWorksSun,
              WorkScheduleInfo::SetWorksSun
            )
          )
          .build()
      )
    );

    // ROLEMGMT — BKL-01 stage 3e (resequenced ahead of 3c/3d — see TRACKER.md).
    // Flat 2-field table, same shape as DEPARTMENTS: no new field kind. Delete
    // is guarded by RoleDAO.IsInUse against Users.RoleID (a real NOT NULL FK).
    // RoleCode is schema-nullable (unlike RoleName) — left as plain GENERIC,
    // not MANDATORY_GENERIC, to match the DB constraint exactly rather than
    // impose a stricter rule than Account_Role itself requires.
    views.put(ModuleCode.ROLEMGMT.GetCode(), perms ->
      new ReferenceMaintenancePanel<>(
        Injector.CreateRoleMaintenanceProcess(),
        Injector.CreateValidator(),
        perms,
        MaintenanceDescriptor.<AccountRole>builder()
          .title("Role Maintenance")
          .subtitle("Add, rename, or remove the roles user accounts are assigned to.")
          .itemLabel("role")
          .inUseMessage(
            "This role can't be removed because one or more user accounts are " +
            "still assigned to it. Reassign them first."
          )
          .saveFailedMessage("Could not save the role. The name or code may already exist.")
          .idFn(r -> (long) r.GetRoleId())
          .labelFn(AccountRole::GetRoleName)
          .factory(AccountRole::new)
          .column("Role ID", AccountRole::GetRoleId)
          .column("Role Name", AccountRole::GetRoleName)
          .column("Role Code", AccountRole::GetRoleCode)
          .field(
            "name",
            "Role Name",
            SmartFieldType.MANDATORY_GENERIC,
            AccountRole::GetRoleName,
            AccountRole::SetRoleName
          )
          .field(
            "code",
            "Role Code",
            SmartFieldType.GENERIC,
            AccountRole::GetRoleCode,
            AccountRole::SetRoleCode
          )
          .build()
      )
    );

    // Awaiting a slice (seeded + declared, no view yet — see ModuleReconciler WARNs):
    //   HOLIDAY, PREMIUMRATE, SSSTABLE, CONTRIBRATE, WHTTABLE, ROLEMGMT,
    //   RBACGRANTS
  }
}
