package Processes;

import DataAccess.DatabaseConnector;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class BaseMaintenanceProcess {

  @FunctionalInterface
  protected interface TransactionLogic {
    boolean Execute(Connection conn) throws SQLException;
  }

  protected boolean ExecuteAtomic(TransactionLogic logic) {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (logic.Execute(conn)) {
          conn.commit();
          return true;
        }
        conn.rollback();
        return false;
      } catch (SQLException e) {
        conn.rollback();
        System.err.println("Transaction Failed: " + e.getMessage());
        return false;
      }
    } catch (SQLException e) {
      System.err.println("Database Connection Error: " + e.getMessage());
      return false;
    }
  }
}
