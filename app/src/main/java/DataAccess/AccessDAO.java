package DataAccess;

import Objects.models.AppModule;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-access layer for the RBAC grant matrix
 * (03 - Security & Audit Tables: Role_Permission, joined with Module and
 * Permission from 01 - Reference Tables).
 *
 *   Role_Permission: RolePermissionID, RoleID, PermissionID, ModuleID, Status
 *   Module         : ModuleID, ModuleCode, ModuleName, Status
 *   Permission     : PermissionID, PermissionCode, Status
 *
 * Drives the role-scoped left navigation in ShellFrame and per-action gating
 * (e.g. whether a role may ADD/EDIT/DELETE within a module).
 *
 * Opens its own Connection (like UserDAO) since it is used outside the
 * employee-management transaction — ShellFrame builds it with `new AccessDAO()`.
 *
 * Depends on the RBAC seed (09 - Seed Access Control RBAC.sql) having populated
 * Module / Permission / Role_Permission; without it these queries return zero
 * rows and the shell would show no modules.
 */
public class AccessDAO {

    /**
     * GetModulesForRole — the distinct set of modules a role can access
     * (any active grant). Used to build the navigation tree.
     */
    public List<AppModule> GetModulesForRole(int roleId) {
        List<AppModule> modules = new ArrayList<>();
        String sql =
            "SELECT DISTINCT m.ModuleID, m.ModuleCode, m.ModuleName " +
            "FROM Role_Permission rp " +
            "JOIN Module m ON m.ModuleID = rp.ModuleID " +
            "WHERE rp.RoleID = ? AND rp.Status = 1 AND m.Status = 1 " +
            "ORDER BY m.ModuleName";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modules.add(new AppModule(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("AccessDAO.GetModulesForRole: " + e.getMessage());
        }
        return modules;
    }

    /**
     * HasPermission — true if the role holds the given permission on the given
     * module. Use for action-level gating, e.g.
     *   HasPermission(roleId, "EMPMGMT", "DELETE")
     */
    public boolean HasPermission(int roleId, String moduleCode, String permissionCode) {
        String sql =
            "SELECT 1 " +
            "FROM Role_Permission rp " +
            "JOIN Module m     ON m.ModuleID     = rp.ModuleID " +
            "JOIN Permission p ON p.PermissionID = rp.PermissionID " +
            "WHERE rp.RoleID = ? " +
            "  AND m.ModuleCode = ? " +
            "  AND p.PermissionCode = ? " +
            "  AND rp.Status = 1 AND m.Status = 1 AND p.Status = 1";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roleId);
            ps.setString(2, moduleCode);
            ps.setString(3, permissionCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("AccessDAO.HasPermission: " + e.getMessage());
            return false;
        }
    }

    /**
     * GetPermissionCodes — every permission code a role holds on a module.
     * Handy when ShellFrame wants to enable/disable a whole toolbar at once.
     */
    public List<String> GetPermissionCodes(int roleId, String moduleCode) {
        List<String> codes = new ArrayList<>();
        String sql =
            "SELECT p.PermissionCode " +
            "FROM Role_Permission rp " +
            "JOIN Module m     ON m.ModuleID     = rp.ModuleID " +
            "JOIN Permission p ON p.PermissionID = rp.PermissionID " +
            "WHERE rp.RoleID = ? AND m.ModuleCode = ? " +
            "  AND rp.Status = 1 AND m.Status = 1 AND p.Status = 1 " +
            "ORDER BY p.PermissionCode";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roleId);
            ps.setString(2, moduleCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codes.add(rs.getString("PermissionCode"));
                }
            }
        } catch (SQLException e) {
            System.err.println("AccessDAO.GetPermissionCodes: " + e.getMessage());
        }
        return codes;
    }
}