package Core.Form;

import DataAccess.AccessDAO;
import DataAccess.EmployeeDAO;
import DataAccess.SessionDAO;
import Helper.Injector;
import Objects.enums.Constants.ModuleIcons;
import Objects.models.AppModule;
import Objects.models.IAM.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

/**
 * The single persistent application window the user lands in after login.
 *
 * Replaces the old per-role JFrame routing in LoginForm.handleRedirection().
 * Layout:
 *   - NORTH  : dark top bar (greeting + role on the left, date + logout on the right)
 *   - WEST   : sidebar with a search box and a role-scoped navigation tree
 *   - CENTER : work area (CardLayout) where module views are swapped in place
 *   - SOUTH  : footer
 *
 * The shell is intentionally dumb about how module views are built: it receives
 * a Map<ModuleCode, Supplier<JComponent>> (assembled in Injector) and mounts a
 * view lazily the first time its module is selected. The set of modules shown
 * is the intersection of:
 *   (a) what the logged-in role is granted   -> AccessDAO.GetModulesForRole(roleId)
 *   (b) what actually has a registered view   -> keys of the supplied map
 *
 * Identity comes from Session (set by LoginForm). The right status panel from
 * the design is deferred to Phase 3.
 */
public class ShellFrame extends JFrame {

  // -------------------------------------------------------------------------
  // Brand tokens
  // -------------------------------------------------------------------------
  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color SIDEBAR_BG = new Color(0xF5F6F8);
  private static final Color FOOTER_BG = new Color(0xECEDEF);
  private static final Color TEXT_MUTED = new Color(0xB0BAC5);
  private static final String FONT_FAMILY = "Segoe UI";

  private static final String HOME_CARD = "__HOME__";

  // -------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------
  private final AccessDAO accessDAO;
  private final EmployeeDAO empDAO;
  private final Map<String, Supplier<JComponent>> moduleViews;

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel workArea = new JPanel(cardLayout);
  private final JPanel navListPanel = new JPanel();
  private String selectedModuleCode = null;
  private String navFilter = "";
  private final Set<String> mounted = new HashSet<>();
  private final List<AppModule> navModules = new ArrayList<>();
  private final SessionDAO sessionDAO = new SessionDAO();
  private SessionMonitor sessionMonitor;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------
  public ShellFrame(
    AccessDAO accessDAO,
    EmployeeDAO empDAO,
    Map<String, Supplier<JComponent>> moduleViews
  ) {
    long ti = System.nanoTime();
    for (var m : accessDAO.GetModulesForRole(Session.GetRoleId())) {
      ModuleIcons.For(m.GetModuleCode(), java.awt.Color.BLACK, 16);
    }
    System.out.printf(
      "[SHELL] icon decode (%d modules): %.0f ms%n",
      accessDAO.GetModulesForRole(Session.GetRoleId()).size(), // fine to double-call for a one-off measurement
      (System.nanoTime() - ti) / 1e6
    );

    this.accessDAO = accessDAO;
    this.empDAO = empDAO;
    this.moduleViews = moduleViews;

    setTitle("MotorPH ERP");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setSize(1100, 700);
    setMinimumSize(new Dimension(900, 560));
    setLocationRelativeTo(null);
    setExtendedState(JFrame.MAXIMIZED_BOTH);

    long t0 = System.nanoTime();
    resolveNavModules();
    long t1 = System.nanoTime();
    setContentPane(buildContent());
    long t2 = System.nanoTime();
    System.out.printf(
      "[SHELL] resolveNavModules(DB): %.0f ms | buildContent: %.0f ms%n",
      (t1 - t0) / 1e6,
      (t2 - t1) / 1e6
    );
    // Single-session heartbeat: detects takeover by another workstation.
    this.sessionMonitor = new SessionMonitor(
      sessionDAO,
      this::handleSessionEvicted
    );
    this.sessionMonitor.Start();
  }

  // -------------------------------------------------------------------------
  // Module resolution — role-granted AND view-registered
  // -------------------------------------------------------------------------
  private void resolveNavModules() {
    navModules.clear();
    for (AppModule m : accessDAO.GetModulesForRole(Session.GetRoleId())) {
      if (moduleViews.containsKey(m.GetModuleCode())) {
        navModules.add(m);
      } else {
        System.err.println(
          "ShellFrame: role is granted module '" +
            m.GetModuleCode() +
            "' but no view is registered for it."
        );
      }
    }
  }

  // -------------------------------------------------------------------------
  // Layout
  // -------------------------------------------------------------------------
  private JPanel buildContent() {
    JPanel root = new JPanel(new BorderLayout());
    root.add(buildTopBar(), BorderLayout.NORTH);
    root.add(buildSidebar(), BorderLayout.WEST);
    root.add(buildWorkArea(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);
    return root;
  }

  // ---- Top bar -----------------------------------------------------------
  private JComponent buildTopBar() {
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(BRAND_DARK);
    bar.setBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, BRAND_RED));

    // Left: greeting + role
    String firstName = empDAO.GetEmployeeNameById(Session.GetEmployeeId());
    if (firstName == null || firstName.isBlank()) {
      firstName =
        Session.GetUsername() != null ? Session.GetUsername() : "User";
    }

    JLabel greeting = new JLabel("Welcome, " + firstName);
    greeting.setFont(new Font(FONT_FAMILY, Font.BOLD, 18));
    greeting.setForeground(Color.WHITE);

    String roleName =
      Session.GetRoleName() != null ? Session.GetRoleName() : "\u2014";
    JLabel role = new JLabel(roleName);
    role.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
    role.setForeground(TEXT_MUTED);

    JPanel identity = new JPanel();
    identity.setOpaque(false);
    identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
    greeting.setAlignmentX(Component.LEFT_ALIGNMENT);
    role.setAlignmentX(Component.LEFT_ALIGNMENT);
    identity.add(greeting);
    identity.add(role);
    identity.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

    // Right: date + logout
    JLabel date = new JLabel(
      LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
    );
    date.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
    date.setForeground(TEXT_MUTED);

    JButton logout = new JButton("Log Out");
    logout.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
    logout.setForeground(Color.WHITE);
    logout.setBackground(BRAND_RED);
    logout.setFocusPainted(false);
    logout.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
    logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    logout.addActionListener(e -> performLogout());

    JPanel rightInner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
    rightInner.setOpaque(false);
    rightInner.add(date);
    rightInner.add(logout);

    JPanel rightSide = new JPanel(new GridBagLayout()); // single child auto-centers vertically
    rightSide.setOpaque(false);
    rightSide.add(rightInner);
    rightSide.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));

    bar.add(identity, BorderLayout.WEST);
    bar.add(rightSide, BorderLayout.EAST);
    return bar;
  }

  // ---- Sidebar (search + tree) -------------------------------------------
  private JComponent buildSidebar() {
    JPanel side = new JPanel(new BorderLayout());
    side.setBackground(SIDEBAR_BG);
    side.setPreferredSize(new Dimension(260, 0));
    side.setBorder(
      BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xD8DBDF))
    );

    JPanel header = new JPanel(new BorderLayout(0, 6));
    header.setOpaque(false);
    header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("NAVIGATION");
    title.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
    title.setForeground(new Color(0x6B7682));

    JTextField search = new JTextField();
    search.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
    search.setToolTipText("Search modules");
    search
      .getDocument()
      .addDocumentListener(
        (SimpleDocListener) () -> {
          navFilter = search.getText();
          rebuildNavList();
        }
      );

    header.add(title, BorderLayout.NORTH);
    header.add(search, BorderLayout.SOUTH);

    navListPanel.setLayout(new BoxLayout(navListPanel, BoxLayout.Y_AXIS));
    navListPanel.setBackground(SIDEBAR_BG);
    navListPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
    rebuildNavList();

    JScrollPane scroll = new JScrollPane(navListPanel);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setBackground(SIDEBAR_BG);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    side.add(header, BorderLayout.NORTH);
    side.add(scroll, BorderLayout.CENTER);
    return side;
  }

  // ---- Work area ----------------------------------------------------------
  private JComponent buildWorkArea() {
    workArea.setBackground(Color.WHITE);
    workArea.add(buildHomeCard(), HOME_CARD);
    cardLayout.show(workArea, HOME_CARD);
    return workArea;
  }

  private JComponent buildHomeCard() {
    JPanel home = new JPanel(new GridBagLayout());
    home.setBackground(Color.WHITE);

    JPanel box = new JPanel();
    box.setOpaque(false);
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

    JLabel headline = new JLabel("MotorPH ERP");
    headline.setFont(new Font(FONT_FAMILY, Font.BOLD, 28));
    headline.setForeground(BRAND_DARK);
    headline.setAlignmentX(Component.CENTER_ALIGNMENT);

    String hint = navModules.isEmpty()
      ? "No modules are available for your role. Contact your administrator."
      : "Select a module from the left to begin.";
    JLabel sub = new JLabel(hint);
    sub.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
    sub.setForeground(new Color(0x6B7682));
    sub.setAlignmentX(Component.CENTER_ALIGNMENT);

    box.add(headline);
    box.add(Box.createVerticalStrut(8));
    box.add(sub);
    home.add(box);
    return home;
  }

  // ---- Footer -------------------------------------------------------------
  private JComponent buildFooter() {
    JPanel footer = new JPanel(new BorderLayout());
    footer.setBackground(FOOTER_BG);
    footer.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
        BorderFactory.createEmptyBorder(6, 16, 6, 16)
      )
    );

    String roleName =
      Session.GetRoleName() != null ? Session.GetRoleName() : "\u2014";
    JLabel left = new JLabel("MotorPH ERP  \u2022  " + roleName + " session");
    left.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
    left.setForeground(new Color(0x6B7682));

    JLabel right = new JLabel("v1.0");
    right.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
    right.setForeground(new Color(0x6B7682));

    footer.add(left, BorderLayout.WEST);
    footer.add(right, BorderLayout.EAST);
    return footer;
  }

  // -------------------------------------------------------------------------
  // Behaviour
  // -------------------------------------------------------------------------

  /** Rebuilds the nav tree, optionally filtered by a case-insensitive substring. */
  private void rebuildNavList() {
    String needle = navFilter == null ? "" : navFilter.trim().toLowerCase();
    navListPanel.removeAll();

    for (AppModule m : navModules) {
      if (
        needle.isEmpty() || m.GetModuleName().toLowerCase().contains(needle)
      ) {
        navListPanel.add(buildNavRow(m));
      }
    }

    navListPanel.revalidate();
    navListPanel.repaint();
  }

  private JComponent buildNavRow(AppModule module) {
    boolean selected = module.GetModuleCode().equals(selectedModuleCode);
    Color textColor = selected ? BRAND_DARK : new Color(0x5F6368);
    Color rowBg = selected ? Color.WHITE : SIDEBAR_BG;

    JLabel label = new JLabel(module.GetModuleName());
    label.setIcon(ModuleIcons.For(module.GetModuleCode(), textColor, 16));
    label.setIconTextGap(10);
    label.setFont(new Font(FONT_FAMILY, selected ? Font.BOLD : Font.PLAIN, 13));
    label.setForeground(textColor);

    JPanel row = new JPanel(new BorderLayout());
    row.setBackground(rowBg);
    row.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(
          0,
          3,
          0,
          0,
          selected ? BRAND_RED : rowBg
        ),
        BorderFactory.createEmptyBorder(8, 11, 8, 12)
      )
    );
    row.add(label, BorderLayout.CENTER);
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    row.setMaximumSize(
      new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height)
    );

    row.addMouseListener(
      new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          selectedModuleCode = module.GetModuleCode();
          showModule(module);
          rebuildNavList();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!selected) row.setBackground(FOOTER_BG);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (!selected) row.setBackground(rowBg);
        }
      }
    );

    return row;
  }

  /** Mounts the module's view on first use, then brings it to the front. */
  private void showModule(AppModule module) {
    String code = module.GetModuleCode();

    if (!mounted.contains(code)) {
      JComponent view;
      try {
        Supplier<JComponent> supplier = moduleViews.get(code);
        view = (supplier != null)
          ? supplier.get()
          : errorCard(
              "No view is registered for " + module.GetModuleName() + "."
            );
      } catch (Exception ex) {
        view = errorCard(
          "Could not open " + module.GetModuleName() + ":\n" + ex.getMessage()
        );
      }
      workArea.add(view, code);
      mounted.add(code);
    }
    cardLayout.show(workArea, code);
  }

  private JComponent errorCard(String message) {
    JTextArea area = new JTextArea(message);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
    area.setForeground(BRAND_RED);
    area.setBackground(Color.WHITE);
    area.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
    return area;
  }

  private void performLogout() {
    sessionMonitor.Stop();
    long sid = Session.GetSessionId();
    if (sid > 0) {
      try {
        sessionDAO.RevokeSession(sid, "User logout");
      } catch (SQLException ex) {
        System.err.println("ShellFrame.performLogout: " + ex.getMessage());
      }
    }
    Session.End();
    dispose();
    Injector.CreateLoginForm().setVisible(true);
  }

  /** Invoked by the heartbeat (on the EDT) when this session was taken over. */
  private void handleSessionEvicted() {
    Session.End();
    dispose();
    Injector.CreateLoginForm().setVisible(true);
    JOptionPane.showMessageDialog(
      null,
      "You have been signed out because this account was used to sign in on another device.",
      "Signed In Elsewhere",
      JOptionPane.WARNING_MESSAGE
    );
  }

  // -------------------------------------------------------------------------
  // Tiny functional adapter so the search box needs only one lambda.
  // -------------------------------------------------------------------------
  @FunctionalInterface
  private interface SimpleDocListener
    extends javax.swing.event.DocumentListener
  {
    void onChange();

    @Override
    default void insertUpdate(javax.swing.event.DocumentEvent e) {
      onChange();
    }

    @Override
    default void removeUpdate(javax.swing.event.DocumentEvent e) {
      onChange();
    }

    @Override
    default void changedUpdate(javax.swing.event.DocumentEvent e) {
      onChange();
    }
  }
}
