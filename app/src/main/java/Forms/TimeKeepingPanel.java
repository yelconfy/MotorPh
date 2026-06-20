package Forms;

import Core.Service.AttendanceCalculator;
import Helper.Paginator;
import Interface.ITimeKeepingProcess;
import Objects.enums.Status.AttendanceStatus;
import Objects.models.DailyAttendanceRecord;
import Objects.models.table.DailyAttendanceTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Enhanced Timekeeping view (replaces the old JFrame TimeKeepingForm).
 *
 * Reads computed DailyAttendanceRecords from the process, shows status / late /
 * worked / overtime / day-type columns with colour-coded rows, lets the user
 * filter by status, rolls up a period summary, and exports the current view to
 * CSV. The hours math is the same one PayrollProcess uses (via
 * AttendanceCalculator), so this screen and Payroll never disagree.
 */
public class TimeKeepingPanel extends JPanel {

    // Brand tokens (match ShellFrame)
    private static final Color BRAND_DARK = new Color(0x0D1B2A);
    private static final Color BRAND_RED  = new Color(0xE53935);
    private static final Color ROW_LATE   = new Color(0xFFF3CD);
    private static final Color ROW_BAD    = new Color(0xF8D7DA);
    private static final Color ROW_OT     = new Color(0xE3F2FD);
    private static final String FONT = "Segoe UI";
    private static final int PAGE_SIZE = 35;

    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ITimeKeepingProcess process;

    // Filters
    private final JTextField searchField = new JTextField(16);
    private final JSpinner    fromSpinner = new JSpinner(new SpinnerDateModel());
    private final JSpinner    toSpinner   = new JSpinner(new SpinnerDateModel());
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[] {
        "All statuses", "On time", "Late", "Incomplete", "Absent", "With overtime"
    });

    // Grid
    private final DailyAttendanceTableModel tableModel = new DailyAttendanceTableModel(new ArrayList<>());
    private final JTable table = new JTable(tableModel);

    // Pagination
    private final JButton prevBtn = new JButton("Previous");
    private final JButton nextBtn = new JButton("Next");
    private final JLabel  pageLabel = new JLabel("Page 0 of 0");

    // Summary bar
    private final JLabel summaryLabel = new JLabel();

    // State
    private List<DailyAttendanceRecord> allRecords = new ArrayList<>();
    private Paginator<DailyAttendanceRecord> paginator;

    public TimeKeepingPanel(ITimeKeepingProcess process) {
        this.process = process;

        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildCenter(),   BorderLayout.CENTER);
        add(buildSummary(),  BorderLayout.SOUTH);

        initDefaults();
        runQuery();
    }

    // ---- Header (title + filters) ------------------------------------------
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        JLabel title = new JLabel("Timekeeping");
        title.setFont(new Font(FONT, Font.BOLD, 20));
        title.setForeground(BRAND_DARK);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.setBackground(Color.WHITE);

        searchField.setFont(new Font(FONT, Font.PLAIN, 13));
        searchField.setToolTipText("Employee # or name");

        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));

        JButton searchBtn = brandButton("Search");
        searchBtn.addActionListener(e -> runQuery());

        JButton exportBtn = new JButton("Export CSV");
        exportBtn.setFont(new Font(FONT, Font.PLAIN, 12));
        exportBtn.addActionListener(e -> exportCsv());

        statusFilter.setFont(new Font(FONT, Font.PLAIN, 13));
        statusFilter.addActionListener(e -> applyFilter());

        filters.add(new JLabel("Search:"));
        filters.add(searchField);
        filters.add(new JLabel("From:"));
        filters.add(fromSpinner);
        filters.add(new JLabel("To:"));
        filters.add(toSpinner);
        filters.add(searchBtn);
        filters.add(new JLabel("   Show:"));
        filters.add(statusFilter);
        filters.add(exportBtn);

        header.add(title,   BorderLayout.NORTH);
        header.add(filters, BorderLayout.SOUTH);
        return header;
    }

    // ---- Center (table + pagination) ---------------------------------------
    private JComponent buildCenter() {
        table.setFont(new Font(FONT, Font.PLAIN, 13));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new StatusRowRenderer());
        table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        pager.setBackground(Color.WHITE);
        prevBtn.setFont(new Font(FONT, Font.PLAIN, 12));
        nextBtn.setFont(new Font(FONT, Font.PLAIN, 12));
        pageLabel.setFont(new Font(FONT, Font.PLAIN, 12));
        prevBtn.addActionListener(e -> {
            if (paginator != null && paginator.hasPreviousPage()) {
                paginator.previousPage();
                syncPage();
            }
        });
        nextBtn.addActionListener(e -> {
            if (paginator != null && paginator.hasNextPage()) {
                paginator.nextPage();
                syncPage();
            }
        });
        pager.add(prevBtn);
        pager.add(pageLabel);
        pager.add(nextBtn);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Color.WHITE);
        center.add(scroll, BorderLayout.CENTER);
        center.add(pager,  BorderLayout.SOUTH);
        return center;
    }

    // ---- Summary bar -------------------------------------------------------
    private JComponent buildSummary() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        bar.setBackground(new Color(0xECEDEF));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
            BorderFactory.createEmptyBorder(4, 16, 4, 16)));
        summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
        summaryLabel.setForeground(BRAND_DARK);
        bar.add(summaryLabel);
        return bar;
    }

    private JButton brandButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(FONT, Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(BRAND_RED);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ---- Behaviour ---------------------------------------------------------

    private void initDefaults() {
        LocalDate today = LocalDate.now();
        fromSpinner.setValue(toDate(today.withDayOfMonth(1)));
        toSpinner.setValue(toDate(today));
    }

    /** Re-queries the data source using the current search text + date range. */
    private void runQuery() {
        String q = searchField.getText().trim();
        LocalDate from = toLocalDate((Date) fromSpinner.getValue());
        LocalDate to   = toLocalDate((Date) toSpinner.getValue());

        allRecords = process.GetTimeRecords(
            q.isEmpty() ? Optional.empty() : Optional.of(q),
            Optional.of(from),
            Optional.of(to)
        );
        applyFilter();
    }

    /** Re-applies the status filter to the already-fetched records. */
    private void applyFilter() {
        Predicate<DailyAttendanceRecord> pred = currentPredicate();
        List<DailyAttendanceRecord> filtered = new ArrayList<>();
        for (DailyAttendanceRecord r : allRecords) {
            if (pred.test(r)) filtered.add(r);
        }

        paginator = new Paginator<>(filtered, PAGE_SIZE);
        syncPage();
        updateSummary(filtered);
    }

    private Predicate<DailyAttendanceRecord> currentPredicate() {
        return switch (statusFilter.getSelectedIndex()) {
            case 1 -> r -> r.GetStatus() == AttendanceStatus.PRESENT;
            case 2 -> r -> r.GetStatus() == AttendanceStatus.LATE;
            case 3 -> r -> r.GetStatus() == AttendanceStatus.INCOMPLETE;
            case 4 -> r -> r.GetStatus() == AttendanceStatus.ABSENT;
            case 5 -> DailyAttendanceRecord::HasOvertime;
            default -> r -> true;
        };
    }

    private void syncPage() {
        if (paginator == null) {
            tableModel.setPageData(new ArrayList<>());
            pageLabel.setText("Page 0 of 0");
            return;
        }
        tableModel.setPageData(paginator.getCurrentPage());
        pageLabel.setText(String.format("Page %d of %d",
            paginator.getCurrentPageNumber(), paginator.getTotalPages()));
    }

    private void updateSummary(List<DailyAttendanceRecord> filtered) {
        AttendanceCalculator.Summary s = process.Summarize(filtered);
        summaryLabel.setText(String.format(
            "Records: %d     On time: %d     Late: %d (%d min)     Incomplete: %d     Absent: %d     Worked: %d hrs     OT: %d hrs",
            s.GetTotalRecords(), s.GetOnTimeDays(), s.GetLateDays(),
            s.GetTotalLateMinutes(), s.GetIncompleteDays(), s.GetAbsentDays(),
            s.GetWorkedHours(), s.GetOvertimeHours()));
    }

    // ---- CSV export --------------------------------------------------------

    private void exportCsv() {
        Predicate<DailyAttendanceRecord> pred = currentPredicate();
        List<DailyAttendanceRecord> rows = new ArrayList<>();
        for (DailyAttendanceRecord r : allRecords) {
            if (pred.test(r)) rows.add(r);
        }
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing to export for the current filter.",
                "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("timekeeping_export.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        StringBuilder sb = new StringBuilder(
            "Emp #,Last Name,First Name,Date,Time In,Time Out,Status,Late (min),Worked (min),OT (min),Day Type\n");
        for (DailyAttendanceRecord r : rows) {
            sb.append(r.GetEmployeeId()).append(',')
              .append(csv(r.GetLastName())).append(',')
              .append(csv(r.GetFirstName())).append(',')
              .append(r.GetDate() != null ? r.GetDate() : "").append(',')
              .append(r.GetTimeIn()  != null ? r.GetTimeIn().format(EXPORT_TIME)  : "").append(',')
              .append(r.GetTimeOut() != null ? r.GetTimeOut().format(EXPORT_TIME) : "").append(',')
              .append(r.GetStatus().GetLabel()).append(',')
              .append(r.GetLateMinutes()).append(',')
              .append(r.GetRegularMinutes()).append(',')
              .append(r.GetOvertimeMinutes()).append(',')
              .append(r.GetDayType()).append('\n');
        }

        try {
            Files.writeString(chooser.getSelectedFile().toPath(), sb.toString());
            JOptionPane.showMessageDialog(this, "Exported " + rows.size() + " rows.",
                "Export", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write file:\n" + ex.getMessage(),
                "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String v) {
        if (v == null) return "";
        return v.contains(",") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    // ---- Date helpers ------------------------------------------------------

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ---- Row colouring -----------------------------------------------------

    private final class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            if (isSelected) return c;

            DailyAttendanceTableModel model = (DailyAttendanceTableModel) t.getModel();
            AttendanceStatus status = model.GetStatusAt(row);
            boolean hasOt = model.GetRecordAt(row).HasOvertime();

            Color bg = Color.WHITE;
            if (status == AttendanceStatus.ABSENT || status == AttendanceStatus.INCOMPLETE) {
                bg = ROW_BAD;
            } else if (status == AttendanceStatus.LATE) {
                bg = ROW_LATE;
            } else if (hasOt) {
                bg = ROW_OT;
            }
            c.setBackground(bg);
            c.setForeground(BRAND_DARK);
            return c;
        }
    }
}