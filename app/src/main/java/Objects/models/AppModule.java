package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Module (01 - Reference Tables).
 *   Module: ModuleID, ModuleCode, ModuleName, Status
 *
 * Represents one navigable area of the app (Payroll, Employee Management,
 * Timekeeping). AccessDAO returns the list of AppModules a given role may
 * access; ShellFrame builds its role-scoped navigation tree from that list.
 *
 * ModuleCode is the stable app key used for routing/permission checks
 * (PAYROLL / EMPMGMT / TIMEKEEPING); ModuleName is the display label.
 *
 * Leaf reference entity, same shape and convention as PositionInfo.
 */
public class AppModule extends BaseObject {

  private int ModuleId;
  private String ModuleCode;
  private String ModuleName;

  public AppModule() {}

  public AppModule(int moduleId, String moduleCode, String moduleName) {
    this.ModuleId = moduleId;
    this.ModuleCode = moduleCode;
    this.ModuleName = moduleName;
    SetActive(true);
  }

  /**
   * Smart Constructor — maps from a Module row (or a Role_Permission × Module join).
   */
  public AppModule(ResultSet rs) throws SQLException {
    this.ModuleId = rs.getInt("ModuleID");
    this.ModuleCode = rs.getString("ModuleCode");
    this.ModuleName = rs.getString("ModuleName");
    SetActive(true);
  }

  @Override
  public Object GetIdentity() {
    return GetModuleId();
  }

  public int GetModuleId() {
    return ModuleId;
  }

  public void SetModuleId(int v) {
    this.ModuleId = v;
  }

  public String GetModuleCode() {
    return ModuleCode;
  }

  public void SetModuleCode(String v) {
    this.ModuleCode = v;
  }

  public String GetModuleName() {
    return ModuleName;
  }

  public void SetModuleName(String v) {
    this.ModuleName = v;
  }

  @Override
  public String toString() {
    // Display label for the navigation tree.
    return ModuleName;
  }
}
