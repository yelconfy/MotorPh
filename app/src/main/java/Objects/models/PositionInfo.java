package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PositionInfo extends BaseObject {

  private long positionID;
  private String positionName;

  // <editor-fold defaultstate="collapsed" desc="Constructors">
  public PositionInfo() {}

  /**
   * Smart Constructor for DAO usage
   */
  public PositionInfo(ResultSet rs) throws SQLException {
    this.positionID = rs.getLong("PositionID");
    this.positionName = rs.getString("PositionName");
  }

  // </editor-fold>

  @Override
  public Object GetIdentity() {
    return GetPositionID();
  }

  // <editor-fold defaultstate="collapsed" desc="Getters and Setters">
  public long GetPositionID() {
    return positionID;
  }

  public void SetPositionID(long positionID) {
    this.positionID = positionID;
  }

  public String GetPositionName() {
    return positionName;
  }

  public void SetPositionName(String positionName) {
    this.positionName = positionName;
  }

  // </editor-fold>

  @Override
  public String toString() {
    // This is helpful for JComboBoxes in your Maintenance Screen
    return this.positionName;
  }
}
