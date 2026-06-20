package DataAccess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {

  // MSSQL Configuration
  private static final String SERVER = "25.28.112.221";
  private static final String PORT = "1433";
  private static final String DB_NAME = "MotorPH_ERP";

  // Set to empty/null for Windows Auth; Fill for SQL/SSMS Auth
  private static final String USER = "raf";
  private static final String PASS = "Password1";

  private static Connection connection = null;

  /**
   * Gets the current connection or initializes a new one.
   * Supports both Windows and SQL Server Authentication.
   */
  public static Connection GetConnection() throws SQLException {
    if (connection == null || connection.isClosed()) {
      try {
        // Register MSSQL Driver
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        String url;
        if (USER == null || USER.trim().isEmpty()) {
          // Windows Authentication (Integrated Security)
          url = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;integratedSecurity=true;encrypt=true;trustServerCertificate=true;",
            SERVER,
            PORT,
            DB_NAME
          );
          System.out.println("Connecting via Windows Authentication...");
        } else {
          // SQL Server Authentication (SSMS User)
          url = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true;",
            SERVER,
            PORT,
            DB_NAME,
            USER,
            PASS
          );
          System.out.println("Connecting via SQL Server Authentication...");
        }

        connection = DriverManager.getConnection(url);
        System.out.println("MSSQL Database Connection Established.");
      } catch (ClassNotFoundException e) {
        System.err.println("MSSQL JDBC Driver not found!");
        e.printStackTrace();
      }
    }
    return connection;
  }

  public static void CloseConnection() {
    if (connection != null) {
      try {
        if (!connection.isClosed()) {
          connection.close();
          System.out.println("Database Connection Closed.");
        }
      } catch (SQLException e) {
        System.err.println("Error closing connection:");
        e.printStackTrace();
      } finally {
        connection = null; // Ensure it can be re-initialized
      }
    }
  }
}
