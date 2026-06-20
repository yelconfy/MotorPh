package DataAccess;

import Objects.models.EmployeeAddressInfo;
import java.sql.*;

public class EmployeeAddressesDao {

  // GetByEmployeeID - Retrieves address for object composition
  public EmployeeAddressInfo GetByEmployeeID(Connection conn, long empID)
    throws SQLException {
    String sql = "SELECT * FROM EmployeeAddresses WHERE EmployeeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          // Protocol: Utilizing your Smart Constructor for mapping
          return new EmployeeAddressInfo(rs);
        }
      }
    }
    return null;
  }

  // Insert - Creates a new address record linked to an Employee
  public boolean Insert(Connection conn, long empID, EmployeeAddressInfo addr)
    throws SQLException {
    String sql =
      "INSERT INTO EmployeeAddresses (EmployeeID, HouseBlockLot, Street, Barangay, CityMunicipality, Province, ZipCode) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empID);
      pstmt.setString(2, addr.GetHouseBlkLotNo());
      pstmt.setString(3, addr.GetStreet());
      pstmt.setString(4, addr.GetBarangay());
      pstmt.setString(5, addr.GetCityMunicipality());
      pstmt.setString(6, addr.GetProvince());
      pstmt.setString(7, addr.GetZipCode());

      return pstmt.executeUpdate() > 0;
    }
  }

  // Update - Updates address details based on the EmployeeID
  public boolean Update(Connection conn, long empID, EmployeeAddressInfo addr)
    throws SQLException {
    String sql =
      "UPDATE EmployeeAddresses SET HouseBlockLot = ?, Street = ?, Barangay = ?, " +
      "CityMunicipality = ?, Province = ?, ZipCode = ? WHERE EmployeeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, addr.GetHouseBlkLotNo());
      pstmt.setString(2, addr.GetStreet());
      pstmt.setString(3, addr.GetBarangay());
      pstmt.setString(4, addr.GetCityMunicipality());
      pstmt.setString(5, addr.GetProvince());
      pstmt.setString(6, addr.GetZipCode());
      pstmt.setLong(7, empID);

      return pstmt.executeUpdate() > 0;
    }
  }
}
