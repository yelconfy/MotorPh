package Core.Service;

import DataAccess.TraceLogDAO;
import Objects.enums.LogLevel;
import Objects.models.IAM.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * The single façade the whole app logs diagnostics through.
 *
 * Replaces scattered System.out / System.err calls with one place that both:
 *   1. mirrors the line to the console (so existing debugging is unchanged), and
 *   2. persists it to MPH_TRACE.Trace_Log (script 18) for later inspection.
 *
 * DESIGN RULES (each of these is a lesson paid for earlier in this project):
 *
 *  - ASYNC, OFF THE EDT. Trace writes run on a single background thread, never
 *    the caller's. We spent MPH-43/46 getting DB work off the event thread; a
 *    logging call must not put it back. A burst of logs serialises on one
 *    worker instead of blocking anyone.
 *
 *  - NEVER THROWS. A logging failure must never change application behaviour.
 *    Every persistence error is caught and downgraded to a console notice. If
 *    MPH_TRACE is unreachable, the app logs to console only and carries on —
 *    trace is a diagnostic aid, not a dependency.
 *
 *  - CONSOLE ALWAYS WORKS. The console mirror happens on the calling thread,
 *    synchronously, BEFORE the async DB hand-off. So even if the trace pool is
 *    down or the queue is saturated, you still see the line immediately, exactly
 *    like the old System.out.
 *
 *  - IDENTITY IS BEST-EFFORT. Username / sessionId are snapshotted from Session
 *    at call time (on the caller's thread, before the async hop) so the row
 *    reflects who was logged in when the event happened, not whoever is current
 *    when the worker drains the queue.
 *
 * This class is intentionally static/global: logging is cross-cutting and a
 * process-wide singleton matches how Session is already modelled.
 */
public final class LoggingService {

  /** Below this level, nothing is persisted (console mirror still happens). */
  private static volatile LogLevel minPersistLevel = LogLevel.INFO;

  /** Flip to false to hard-disable DB persistence (console-only mode). */
  private static volatile boolean persistenceEnabled = true;

  private static final TraceLogDAO TRACE_DAO = new TraceLogDAO();

  /**
   * Single-thread executor: trace order is preserved and we never spawn a
   * thread per log. Daemon, so it never keeps the JVM alive on shutdown.
   */
  private static final ExecutorService WRITER =
    Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "trace-writer");
        t.setDaemon(true);
        return t;
      }
    });

  private LoggingService() {}

  // -------------------------------------------------------------------------
  // Configuration
  // -------------------------------------------------------------------------

  /** Sets the minimum level that gets PERSISTED. Console mirroring is unaffected. */
  public static void SetMinPersistLevel(LogLevel level) {
    if (level != null) {
      minPersistLevel = level;
    }
  }

  /** Turns DB persistence on/off at runtime. Console mirroring is unaffected. */
  public static void SetPersistenceEnabled(boolean enabled) {
    persistenceEnabled = enabled;
  }

  // -------------------------------------------------------------------------
  // Convenience level methods
  // -------------------------------------------------------------------------

  public static void Trace(String source, String message) { Log(LogLevel.TRACE, source, message); }
  public static void Debug(String source, String message) { Log(LogLevel.DEBUG, source, message); }
  public static void Info(String source, String message)  { Log(LogLevel.INFO,  source, message); }
  public static void Warn(String source, String message)  { Log(LogLevel.WARN,  source, message); }
  public static void Error(String source, String message) { Log(LogLevel.ERROR, source, message); }

  /** ERROR with a throwable — appends the exception's message to the line. */
  public static void Error(String source, String message, Throwable t) {
    String full = (t == null)
      ? message
      : message + " | " + t.getClass().getSimpleName() + ": " + t.getMessage();
    Log(LogLevel.ERROR, source, full);
  }

  // -------------------------------------------------------------------------
  // Core
  // -------------------------------------------------------------------------

  /**
   * Records one diagnostic event: mirror to console now (caller's thread), then
   * hand the DB write to the background writer if it clears the persist level.
   */
  public static void Log(LogLevel level, String source, String message) {
    if (level == null) {
      level = LogLevel.INFO;
    }

    // 1. Console mirror — synchronous, always, so nothing is ever swallowed
    //    silently just because the DB is down.
    ConsoleMirror(level, source, message);

    // 2. Persist asynchronously, if enabled and severe enough.
    if (!persistenceEnabled || !level.AtLeast(minPersistLevel)) {
      return;
    }

    // Snapshot everything the row needs NOW, on the caller's thread, so the
    // async write can't be corrupted by a later thread name or a logout.
    final LogLevel lvl = level;
    final String src = source;
    final String msg = message;
    final String threadName = Thread.currentThread().getName();
    final String username = SafeUsername();
    final Long sessionId = SafeSessionId();

    try {
      WRITER.submit(() -> {
        try {
          TRACE_DAO.Insert(lvl, src, msg, threadName, username, sessionId);
        } catch (Exception e) {
          // Non-fatal: the trace DB is a diagnostic aid, not a dependency.
          System.err.println(
            "[LoggingService] trace persist failed (" + e.getMessage() +
            ") - console-only for this event.");
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      // Executor shutting down (app exit). Console mirror already happened.
    }
  }

  /**
   * Flushes and stops the writer. Call on application exit AFTER the last log,
   * so queued rows get a chance to persist. Best-effort with a short timeout —
   * we never hang shutdown for trace data.
   */
  public static void Shutdown() {
    WRITER.shutdown();
    try {
      if (!WRITER.awaitTermination(2, TimeUnit.SECONDS)) {
        WRITER.shutdownNow();
      }
    } catch (InterruptedException e) {
      WRITER.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static void ConsoleMirror(LogLevel level, String source, String message) {
    String line = "[" + level + "] " + (source == null ? "" : source + ": ") + message;
    if (level == LogLevel.ERROR || level == LogLevel.WARN) {
      System.err.println(line);
    } else {
      System.out.println(line);
    }
  }

  /** Session reads are static and null-safe, but guard anyway — logging must never throw. */
  private static String SafeUsername() {
    try {
      return Session.GetUsername();
    } catch (Exception e) {
      return null;
    }
  }

  private static Long SafeSessionId() {
    try {
      long id = Session.GetSessionId();
      return id > 0 ? id : null;
    } catch (Exception e) {
      return null;
    }
  }
}