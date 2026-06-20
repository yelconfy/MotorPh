package Core.Enum;

public enum MaintenanceActionStatus {
  ADDED("added"),
  UPDATED("updated"),
  DELETED("deleted");

  private final String label;

  MaintenanceActionStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
