package Interface;

import Objects.models.SystemActivity;
import java.util.List;

/**
 * Contract for the Activity Log screen (Phase 7c) — read-only access to the
 * merged audit + access timeline (vw_SystemActivity).
 */
public interface IActivityLogProcess {

  /** Most recent activity rows, newest first, capped at {@code limit}. */
  List<SystemActivity> GetRecentActivity(int limit);
}