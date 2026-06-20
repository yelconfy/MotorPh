package Core.Form;

import DataAccess.SessionDAO;
import Objects.models.IAM.Session;

import java.sql.SQLException;
import javax.swing.Timer;

/**
 * Periodic single-session heartbeat for the desktop shell.
 *
 * Every HEARTBEAT_MS it asks SessionDAO.TouchSession to refresh the backing
 * User_Session row and report whether it is still live. When another
 * workstation logs in with the same account, the login revokes this row
 * (takeover); the very next heartbeat sees it is no longer live and runs the
 * supplied onEvicted callback once.
 *
 * Runs on the Swing event-dispatch thread (javax.swing.Timer), deliberately:
 * DatabaseConnector hands out a single shared Connection, so all DB access must
 * stay on the EDT to avoid racing the rest of the UI on that connection. The
 * heartbeat query is a one-row, PK-targeted UPDATE, so the cost is negligible.
 * Because the timer fires on the EDT, onEvicted can touch Swing directly.
 *
 * A transient SQLException (e.g. a brief DB blip) is logged and ignored — it is
 * NOT treated as eviction, so a flaky connection won't kick the user out. Only
 * a clean "row is no longer live" result triggers logout.
 */
public final class SessionMonitor {

  private static final int HEARTBEAT_MS = 30_000; // 30s; must be < SessionDAO TTL

  private final SessionDAO sessionDAO;
  private final Runnable onEvicted;
  private final Timer timer;
  private boolean evicted = false;

  public SessionMonitor(SessionDAO sessionDAO, Runnable onEvicted) {
    this.sessionDAO = sessionDAO;
    this.onEvicted = onEvicted;
    this.timer = new Timer(HEARTBEAT_MS, e -> beat());
    this.timer.setRepeats(true);
  }

  /** Begins heartbeating. */
  public void Start() {
    timer.start();
  }

  /** Stops heartbeating (call on logout / teardown). */
  public void Stop() {
    timer.stop();
  }

  private void beat() {
    long sid = Session.GetSessionId();
    if (sid <= 0) {
      return; // no active session to monitor
    }

    boolean alive;
    try {
      alive = sessionDAO.TouchSession(sid);
    } catch (SQLException ex) {
      // Transient DB issue — do NOT evict on error.
      System.err.println("SessionMonitor.beat: " + ex.getMessage());
      return;
    }

    if (!alive && !evicted) {
      evicted = true;
      timer.stop();
      onEvicted.run(); // already on the EDT
    }
  }
}