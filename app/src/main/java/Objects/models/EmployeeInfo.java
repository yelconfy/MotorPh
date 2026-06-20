package Objects.models;

import Objects.enums.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class EmployeeInfo extends BaseEmployeeInfo {

  private String LastName;
  private String FirstName;
  private LocalDate Birthday;
  private EmployeeAddressInfo Address;

  // <editor-fold defaultstate="collapsed" desc="Constructors">
  public EmployeeInfo() {}

  // Original Constructor (Modified to use long for empNo)
  public EmployeeInfo(
    long empNo,
    String lastName,
    String firstName,
    LocalDate birthday,
    EmployeeAddressInfo address
  ) {
    super(empNo);
    this.LastName = lastName;
    this.FirstName = firstName;
    this.Birthday = birthday;
    this.Address = address;
  }

  /**
   * The "Smart Constructor" Handles the 'Extra Handling' logic directly from
   * a Database ResultSet. This follows the pattern from your C# snippet.
   */
  public EmployeeInfo(ResultSet rs) throws SQLException {
    // 1. Pass the long ID to the base class
    super(rs.getLong("EmployeeID"));
    // 2. Direct mapping
    this.LastName = rs.getString("LastName");
    this.FirstName = rs.getString("FirstName");

    // 3. Handling Date Conversion (SQL Date -> Java LocalDate)
    java.sql.Date dbDate = rs.getDate("Birthday");
    this.Birthday = (dbDate != null) ? dbDate.toLocalDate() : null;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Getters">
  public String GetFullName() {
    // Simple concatenation: Handles cases where a middle name isn't present
    return String.format("%s %s", FirstName, LastName);
  }

  public String GetLastName() {
    return LastName;
  }

  public String GetFirstName() {
    return FirstName;
  }

  public LocalDate GetBirthday() {
    return Birthday;
  }

  public EmployeeAddressInfo GetAddress() {
    return Address;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Setters">
  public void SetLastName(String lastName) {
    this.LastName = lastName;
  }

  public void SetFirstName(String firstName) {
    this.FirstName = firstName;
  }

  public void SetBirthday(LocalDate birthday) {
    this.Birthday = birthday;
  }

  public void SetAddress(EmployeeAddressInfo address) {
    this.Address = address;
  }
  // </editor-fold>
}
