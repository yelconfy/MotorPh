package Objects.models;

public class BaseEmployeeInfo extends BaseObject {

  private long employeeId;

  public BaseEmployeeInfo() {}

  public BaseEmployeeInfo(long employeeId) {
    this.employeeId = employeeId;
    this.SetActive(true);
  }

  @Override
  public Object GetIdentity() {
    return GetEmployeeId();
  }

  public long GetEmployeeId() {
    return employeeId;
  }

  public void SetEmployeeId(long employeeId) {
    this.employeeId = employeeId;
  }
}
