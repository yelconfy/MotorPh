package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

public class EmployeeAddressInfo extends BaseObject {

  private long AddressID;
  private long EmployeeID;
  private String HouseBlkLotNo;
  private String Street;
  private String Barangay;
  private String CityMunicipality;
  private String Province;
  private String ZipCode;

  // Clean default constructor
  public EmployeeAddressInfo() {}

  public EmployeeAddressInfo(ResultSet rs) throws SQLException {
    // Sync these with your SQL Column Names exactly
    this.AddressID = rs.getLong("AddressID");
    this.EmployeeID = rs.getLong("EmployeeID");

    this.HouseBlkLotNo = rs.getString("HouseBlockLot");
    this.Street = rs.getString("Street");
    this.Barangay = rs.getString("Barangay");
    this.CityMunicipality = rs.getString("CityMunicipality");
    this.Province = rs.getString("Province");
    this.ZipCode = rs.getString("ZipCode");
  }

  @Override
  public Object GetIdentity() {
    return GetAddressID();
  }

  // <editor-fold defaultstate="collapsed" desc="Getters">
  public String GetFullAddress() {
    StringBuilder sb = new StringBuilder();
    if (HouseBlkLotNo != null) {
      sb.append(HouseBlkLotNo).append(" ");
    }
    if (Street != null) {
      sb.append(Street).append(", ");
    }
    if (Barangay != null) {
      sb.append(Barangay).append(", ");
    }
    if (CityMunicipality != null) {
      sb.append(CityMunicipality).append(", ");
    }
    if (Province != null) {
      sb.append(Province).append(" ");
    }
    if (ZipCode != null) {
      sb.append(ZipCode);
    }

    return sb.toString().trim();
  }

  public long GetAddressID() {
    return AddressID;
  }

  public long GetEmployeeID() {
    return EmployeeID;
  }

  public String GetHouseBlkLotNo() {
    return HouseBlkLotNo;
  }

  public String GetStreet() {
    return Street;
  }

  public String GetBarangay() {
    return Barangay;
  }

  public String GetCityMunicipality() {
    return CityMunicipality;
  }

  public String GetProvince() {
    return Province;
  }

  public String GetZipCode() {
    return ZipCode;
  }

  // </editor-fold>
  // <editor-fold defaultstate="collapsed" desc="Setters">
  public void SetAddressID(long addressID) {
    this.AddressID = addressID;
  }

  public void SetEmployeeID(long employeeID) {
    this.EmployeeID = employeeID;
  }

  public void SetHouseBlkLotNo(String houseBlkLotNo) {
    this.HouseBlkLotNo = houseBlkLotNo;
  }

  public void SetStreet(String street) {
    this.Street = street;
  }

  public void SetBarangay(String barangay) {
    this.Barangay = barangay;
  }

  public void SetCityMunicipality(String cityMunicipality) {
    this.CityMunicipality = cityMunicipality;
  }

  public void SetProvince(String province) {
    this.Province = province;
  }

  public void SetZipCode(String zipCode) {
    this.ZipCode = zipCode;
  }
  // </editor-fold>
}
