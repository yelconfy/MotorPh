package Forms;

import Core.Enum.MaintenanceActionStatus;
import Core.Form.BaseForm;
import Core.Service.FormControlService;
import Helper.Paginator;
import Helper.TableUtil;
import Helper.TableUtil.TableModelCreator;
import Objects.models.BaseObject;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;

public abstract class BaseMaintenanceForm<
  T extends BaseObject
> extends BaseForm {

  protected final FormControlService validator;
  protected Paginator<T> paginator;
  protected JLabel pageTrackerLabel;

  protected abstract void RefreshUI();

  protected abstract void ControlComponentState(int stateKey);

  protected abstract T ScrapeForm();

  protected abstract boolean PerformSave(T data);

  protected abstract boolean IsNewRecord();

  protected abstract javax.swing.JTable GetMainTable();

  protected abstract TableModelCreator<
    T,
    ? extends javax.swing.table.TableModel
  > GetTableModelProvider();

  protected abstract void OnRowSelected();

  protected abstract List<T> FetchDefaultData();

  public BaseMaintenanceForm(FormControlService validator) {
    this.validator = validator;
  }

  @Override
  public void addNotify() {
    super.addNotify();

    // Ensure the selection listener is active BEFORE we put data in the table
    this.BindTableListener();

    if (this.IsReadyForData()) {
      // Pass null, null to load defaults and select first row
      this.PopulateTable(null, null);
    }
  }

  public void PopulateTable(List<T> searchResults, Object targetIdentity) {
    List<T> data = (searchResults != null) ? searchResults : FetchDefaultData();
    this.LoadTableData(data);

    javax.swing.SwingUtilities.invokeLater(() -> {
      // Pass the identity object to the helper
      int targetRow = getRowById(targetIdentity);

      JTable table = GetMainTable();
      if (table != null && table.getRowCount() > targetRow) {
        table.setRowSelectionInterval(targetRow, targetRow);
        table.scrollRectToVisible(table.getCellRect(targetRow, 0, true));
      }
      this.OnRowSelected();
    });
  }

  protected void BindTableListener() {
    JTable table = GetMainTable();
    if (table != null) {
      table
        .getSelectionModel()
        .addListSelectionListener(e -> {
          // Only trigger when the mouse is released/selection is final
          if (!e.getValueIsAdjusting()) {
            this.OnRowSelected();
          }
        });
    }
  }

  protected void UpdatePageTracker() {
    if (this.paginator != null) {
      int current = this.paginator.getCurrentPageNumber();
      int total = this.paginator.getTotalPages();

      // Handle edge case for empty results
      if (total == 0) {
        pageTrackerLabel.setText("No records found");
      } else {
        pageTrackerLabel.setText(
          String.format("Page %d of %d", current, total)
        );
      }
    }
  }

  protected void RefreshTableOnly() {
    // This updates the UI without resetting the paginator object
    TableUtil.populate(
      this.GetMainTable(),
      this.paginator.getCurrentPage(),
      this.GetTableModelProvider()
    );
    this.SelectFirstRow();
  }

  protected void ShowResult(boolean success, String action) {
    if (success) {
      JOptionPane.showMessageDialog(
        this,
        "Record " + action + " successfully!"
      );
      ControlComponentState(0); // Standard DEFAULT state
      RefreshUI();
    } else {
      JOptionPane.showMessageDialog(
        this,
        "Failed to " + action + " record.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  protected void ExecuteSave() {
    // This highlights the fields red and stops the process if bad data exists
    if (!validator.validate(this)) {
      javax.swing.JOptionPane.showMessageDialog(
        this,
        "Please correct the highlighted errors before saving.",
        "Validation Error",
        javax.swing.JOptionPane.ERROR_MESSAGE
      );
      return; // Kill the save process here
    }

    // Get the clean data from the UI (defined by child)
    // Since validator.validate() ran applyFormatting(), your ScrapeForm()
    // should use getCleanValue() to handle the "PHP" and commas.
    T data = ScrapeForm();

    // Determine the action label
    MaintenanceActionStatus actionLabel = IsNewRecord()
      ? MaintenanceActionStatus.ADDED
      : MaintenanceActionStatus.UPDATED;

    // 4. EXECUTE: Perform the actual DB call (defined by child)
    System.out.println(
      "Validation passed! Executing " + actionLabel + " in database..."
    );
    boolean isSuccess = PerformSave(data);

    if (isSuccess) {
      // Use the identity of the data we just scraped/saved
      Object currentId = data.GetIdentity();

      // This triggers the refresh while maintaining selection
      this.PopulateTable(null, currentId);

      ShowResult(isSuccess, actionLabel.getLabel());
      ControlComponentState(0);
    }
  }

  protected void LoadTableData(List<T> data) {
    if (data == null || data.isEmpty()) {
      this.paginator = new Paginator<>(java.util.Collections.emptyList(), 10);
      // Clear the table and the form fields
      TableUtil.populate(
        this.GetMainTable(),
        java.util.Collections.emptyList(),
        this.GetTableModelProvider()
      );
      // ClearUI();
      return;
    }

    this.paginator = new Paginator<>(data, 10);
    TableUtil.populate(
      this.GetMainTable(),
      this.paginator.getCurrentPage(),
      this.GetTableModelProvider()
    );

    // Only select and trigger math if there is actually data
    this.SelectFirstRow();
  }

  protected void SelectFirstRow() {
    this.UpdatePageTracker();
    JTable table = GetMainTable();

    if (table != null && table.getRowCount() > 0) {
      javax.swing.SwingUtilities.invokeLater(() -> {
        table.clearSelection();
        // This line usually triggers the listener, but we'll be safe
        table.setRowSelectionInterval(0, 0);

        this.OnRowSelected();

        table.scrollRectToVisible(table.getCellRect(0, 0, true));
      });
    }
  }

  private boolean IsReadyForData() {
    try {
      // This is a simple check: if FetchDefaultData causes an NPE
      // because of a null process, we aren't ready.
      return FetchDefaultData() != null;
    } catch (NullPointerException e) {
      return false;
    }
  }

  private int getRowById(Object identity) {
    if (identity == null) return 0;

    List<T> currentPage = this.paginator.getCurrentPage();
    for (int i = 0; i < currentPage.size(); i++) {
      // Use .equals() because GetIdentity() returns an Object
      if (identity.equals(currentPage.get(i).GetIdentity())) {
        return i;
      }
    }
    return 0; // Default to first row if not found on current page
  }
}
