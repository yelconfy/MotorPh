package DataAccess;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Pooled JDBC access (MPH-43), now with TWO pools:
 *
 *   - the MAIN pool  -> the operational database (default MotorPH_ERP)
 *   - the TRACE pool -> MPH_TRACE, the separate diagnostic-log database (script 18)
 *
 * WHY POOLING (MPH-43)
 * --------------------
 * The previous implementation cached ONE static Connection and handed the same
 * object to every caller, but every call site uses try-with-resources — which
 * CLOSED it. Each DAO call therefore closed the singleton, and the next rebuilt
 * it: a full TCP+TLS+auth handshake per call (~3s per login). Pooling makes
 * close() return the connection instead of destroying it; each thread gets its
 * own; Hikari resets autoCommit on return.
 *
 * WHY A SECOND POOL FOR TRACE
 * ---------------------------
 * MPH_TRACE is a physically separate database (same instance). Trace writes must
 * never borrow from or contend with the main pool, and must be able to fail
 * independently — if MPH_TRACE is down, the app keeps running on the main pool.
 * The trace pool is intentionally tiny (one background writer thread feeds it).
 *
 * CREDENTIALS (MPH-44)
 * --------------------
 * Resolution order, first hit wins: ./db.properties (gitignored) -> MOTORPH_DB_*
 * env vars -> compiled-in defaults. The defaults are committed secrets and
 * MPH-44 stays OPEN until they are removed. Both pools share the same server /
 * credentials and differ only in database name (db.name vs db.trace.name).
 */
public final class DatabaseConnector {

  // ---- Fallback configuration (MPH-44: to be deleted; see class javadoc) ----
  private static final String DEF_SERVER = "localhost";
  private static final String DEF_PORT = "1433";
  private static final String DEF_DB = "MotorPH_ERP";
  private static final String DEF_TRACE_DB = "MPH_TRACE";
  private static final String DEF_USER = "yel";
  private static final String DEF_PASS = "Password1";

  private static final String CONFIG_FILE = "db.properties";

  // ---- Pool sizing ---------------------------------------------------------
  // A desktop client, not a web server: one human, a handful of screens.
  private static final int MAX_POOL_SIZE = 10;
  private static final int MIN_IDLE = 2;
  // Trace is fed by a single background writer thread — it needs almost nothing.
  private static final int TRACE_MAX_POOL_SIZE = 2;
  private static final int TRACE_MIN_IDLE = 1;
  private static final long CONNECTION_TIMEOUT_MS = 10_000;   // fail fast if DB is down
  private static final long IDLE_TIMEOUT_MS = 600_000;        // 10 min
  private static final long MAX_LIFETIME_MS = 1_800_000;      // 30 min

  private static volatile HikariDataSource dataSource;       // main
  private static volatile HikariDataSource traceDataSource;  // MPH_TRACE

  private static volatile Properties cachedConfig;           // loaded once

  private DatabaseConnector() {}

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /** Borrows a pooled Connection to the MAIN database. Unchanged signature. */
  public static Connection GetConnection() throws SQLException {
    return MainPool().getConnection();
  }

  /** Borrows a pooled Connection to the TRACE database (MPH_TRACE). */
  public static Connection GetTraceConnection() throws SQLException {
    return TracePool().getConnection();
  }

  /**
   * Opens the MAIN pool ahead of time. Call once from startup so the first
   * handshake happens during launch rather than mid-login. Safe to call twice;
   * safe to skip (the pool opens lazily on first use).
   */
  @SuppressWarnings("try") // the borrow-and-immediately-return IS the warmup
  public static void Warmup() {
    try (Connection ignored = GetConnection()) {
      System.out.println("MSSQL connection pool ready.");
    } catch (SQLException e) {
      System.err.println("DatabaseConnector.Warmup: " + e.getMessage());
    }
  }

  /**
   * Opens the TRACE pool ahead of time. Separate from Warmup() and best-effort:
   * a failure here is logged and swallowed, because a missing MPH_TRACE must
   * never block startup — trace logging simply degrades to console-only.
   */
  @SuppressWarnings("try")
  public static void WarmupTrace() {
    try (Connection ignored = GetTraceConnection()) {
      System.out.println("MPH_TRACE connection pool ready.");
    } catch (SQLException e) {
      System.err.println(
        "DatabaseConnector.WarmupTrace: " + e.getMessage() +
        " — trace logging will be console-only until MPH_TRACE is reachable.");
    }
  }

  /**
   * Shuts BOTH pools down and closes every physical connection. Call on
   * application EXIT only — never on logout.
   */
  public static void CloseConnection() {
    Close(dataSource, "MSSQL connection pool");
    Close(traceDataSource, "MPH_TRACE connection pool");
    dataSource = null;
    traceDataSource = null;
  }

  private static void Close(HikariDataSource ds, String label) {
    if (ds != null && !ds.isClosed()) {
      ds.close();
      System.out.println(label + " closed.");
    }
  }

  // -------------------------------------------------------------------------
  // Pool construction (double-checked locking; each built exactly once)
  // -------------------------------------------------------------------------

  private static HikariDataSource MainPool() throws SQLException {
    HikariDataSource ds = dataSource;
    if (ds != null && !ds.isClosed()) {
      return ds;
    }
    synchronized (DatabaseConnector.class) {
      if (dataSource == null || dataSource.isClosed()) {
        dataSource = BuildMain();
      }
      return dataSource;
    }
  }

  private static HikariDataSource TracePool() throws SQLException {
    HikariDataSource ds = traceDataSource;
    if (ds != null && !ds.isClosed()) {
      return ds;
    }
    synchronized (DatabaseConnector.class) {
      if (traceDataSource == null || traceDataSource.isClosed()) {
        traceDataSource = BuildTrace();
      }
      return traceDataSource;
    }
  }

  private static HikariDataSource BuildMain() throws SQLException {
    Properties cfg = Config();
    String name = cfg.getProperty("db.name", DEF_DB);
    return BuildPool(cfg, name, "MotorPH-Pool", MAX_POOL_SIZE, MIN_IDLE);
  }

  private static HikariDataSource BuildTrace() throws SQLException {
    Properties cfg = Config();
    String name = cfg.getProperty("db.trace.name", DEF_TRACE_DB);
    return BuildPool(cfg, name, "MPH_TRACE-Pool", TRACE_MAX_POOL_SIZE, TRACE_MIN_IDLE);
  }

  /**
   * Shared pool builder — same server / credentials, parameterised by database
   * name, pool name and sizing. Keeping this single method means the two pools
   * can never drift in their connection settings.
   */
  private static HikariDataSource BuildPool(
    Properties cfg,
    String dbName,
    String poolName,
    int maxPool,
    int minIdle
  ) throws SQLException {
    String server = cfg.getProperty("db.server", DEF_SERVER);
    String port = cfg.getProperty("db.port", DEF_PORT);
    String user = cfg.getProperty("db.user", DEF_USER);
    String pass = cfg.getProperty("db.password", DEF_PASS);

    boolean windowsAuth = (user == null || user.trim().isEmpty());

    String url = windowsAuth
      ? String.format(
          "jdbc:sqlserver://%s:%s;databaseName=%s;integratedSecurity=true;" +
          "encrypt=true;trustServerCertificate=true;",
          server, port, dbName)
      : String.format(
          "jdbc:sqlserver://%s:%s;databaseName=%s;" +
          "encrypt=true;trustServerCertificate=true;",
          server, port, dbName);

    HikariConfig hc = new HikariConfig();
    hc.setPoolName(poolName);
    hc.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    hc.setJdbcUrl(url);
    if (!windowsAuth) {
      hc.setUsername(user);
      hc.setPassword(pass);
    }
    hc.setMaximumPoolSize(maxPool);
    hc.setMinimumIdle(minIdle);
    hc.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    hc.setIdleTimeout(IDLE_TIMEOUT_MS);
    hc.setMaxLifetime(MAX_LIFETIME_MS);
    hc.setAutoCommit(true);

    try {
      HikariDataSource ds = new HikariDataSource(hc);
      System.out.println(
        poolName + " initialised (" +
        (windowsAuth ? "Windows Auth" : "SQL Server Auth") +
        ", max " + maxPool + ") -> " + server + ":" + port + "/" + dbName);
      return ds;
    } catch (RuntimeException e) {
      throw new SQLException(
        "Failed to initialise pool '" + poolName + "': " + e.getMessage(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Config resolution: db.properties -> env vars -> compiled-in defaults.
  // Loaded once and cached so both pools see identical settings.
  // -------------------------------------------------------------------------

  private static Properties Config() {
    Properties c = cachedConfig;
    if (c != null) {
      return c;
    }
    synchronized (DatabaseConnector.class) {
      if (cachedConfig == null) {
        cachedConfig = LoadConfig();
      }
      return cachedConfig;
    }
  }

  private static Properties LoadConfig() {
    Properties p = new Properties();

    Path file = Paths.get(CONFIG_FILE);
    if (Files.exists(file)) {
      try (InputStream in = new FileInputStream(file.toFile())) {
        p.load(in);
        System.out.println("DB config loaded from " + file.toAbsolutePath());
        return p;
      } catch (IOException e) {
        System.err.println(
          "DatabaseConnector: could not read " + CONFIG_FILE +
          " (" + e.getMessage() + ") -- falling back to env / defaults.");
      }
    }

    PutIfSet(p, "db.server", System.getenv("MOTORPH_DB_SERVER"));
    PutIfSet(p, "db.port", System.getenv("MOTORPH_DB_PORT"));
    PutIfSet(p, "db.name", System.getenv("MOTORPH_DB_NAME"));
    PutIfSet(p, "db.trace.name", System.getenv("MOTORPH_DB_TRACE_NAME"));
    PutIfSet(p, "db.user", System.getenv("MOTORPH_DB_USER"));
    PutIfSet(p, "db.password", System.getenv("MOTORPH_DB_PASSWORD"));
    return p;
  }

  private static void PutIfSet(Properties p, String key, String value) {
    if (value != null && !value.trim().isEmpty()) {
      p.setProperty(key, value);
    }
  }
}