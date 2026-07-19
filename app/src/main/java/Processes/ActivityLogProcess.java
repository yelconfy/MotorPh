package Processes;

import DataAccess.SystemActivityDAO;
import Interface.IActivityLogProcess;
import Objects.models.SystemActivity;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Read-only process backing the Activity Log module (Phase 7c). Thin wrapper
 * over SystemActivityDAO; self-opens and degrades to an empty list on failure,
 * matching the other read-only screens.
 */
public class ActivityLogProcess implements IActivityLogProcess {

  private final SystemActivityDAO activityDAO;

  public ActivityLogProcess(SystemActivityDAO activityDAO) {
    this.activityDAO = activityDAO;
  }

  @Override
  public List<SystemActivity> GetRecentActivity(int limit) {
    try {
      return activityDAO.GetRecent(limit);
    } catch (SQLException e) {
      System.err.println("ActivityLogProcess.GetRecentActivity: " + e.getMessage());
      return Collections.emptyList();
    }
  }
}