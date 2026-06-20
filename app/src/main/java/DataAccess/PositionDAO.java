package DataAccess;

import Objects.models.PositionInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PositionDAO {

  // GetAll - Returns all positions for dropdowns/lists in the UI
  public List<PositionInfo> GetAll(Connection conn) throws SQLException {
    List<PositionInfo> positions = new ArrayList<>();
    String sql = "SELECT * FROM Positions ORDER BY PositionName ASC";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        positions.add(new PositionInfo(rs));
      }
    }
    return positions;
  }

  // GetByID - Used when loading an employee to see what their position title is
  public PositionInfo GetByID(Connection conn, long positionID)
    throws SQLException {
    String sql = "SELECT * FROM Positions WHERE PositionID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, positionID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return new PositionInfo(rs);
        }
      }
    }
    return null;
  }

  // Insert - Add a new position title to the system
  public boolean Insert(Connection conn, String positionName)
    throws SQLException {
    String sql = "INSERT INTO Positions (PositionName) VALUES (?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, positionName);
      return pstmt.executeUpdate() > 0;
    }
  }

  // Update - For your maintenance screen to rename a position
  public boolean Update(Connection conn, PositionInfo position)
    throws SQLException {
    String sql = "UPDATE Positions SET PositionName = ? WHERE PositionID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, position.GetPositionName());
      pstmt.setLong(2, position.GetPositionID());
      return pstmt.executeUpdate() > 0;
    }
  }

  // Delete - Removes a position from the lookup table Note: This will fail if any employee is currently linked to this PositionID
  public boolean Delete(Connection conn, long positionID) throws SQLException {
    String sql = "DELETE FROM Positions WHERE PositionID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, positionID);
      return pstmt.executeUpdate() > 0;
    }
  }
}
