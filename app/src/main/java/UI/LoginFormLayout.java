package UI;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.Border;

public class LoginFormLayout {

  // -------------------------------------------------------------------------
  // Brand colors
  // -------------------------------------------------------------------------
  private static final Color BRAND_DARK   = new Color(0x0D1B2A);
  private static final Color BRAND_RED    = new Color(0xE53935);
  private static final Color FORM_BG      = Color.WHITE;
  private static final Color BORDER_COLOR = new Color(0xBDBDBD);
  private static final Color TEXT_DARK    = new Color(0x212121);
  private static final Color TEXT_MUTED   = new Color(0x9E9E9E);

  // -------------------------------------------------------------------------
  // Fonts
  // -------------------------------------------------------------------------
  private static final Font TAGLINE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
  private static final Font WELCOME_FONT = new Font("Segoe UI", Font.BOLD,  22);
  private static final Font LABEL_FONT   = new Font("Segoe UI", Font.PLAIN, 12);
  private static final Font INPUT_FONT   = new Font("Segoe UI", Font.PLAIN, 13);
  private static final Font BUTTON_FONT  = new Font("Segoe UI", Font.BOLD,  13);

  // -------------------------------------------------------------------------
  // Components
  // -------------------------------------------------------------------------
  private JTextField     usernameField;
  private JPasswordField passwordField;
  private JButton        loginBtn;

  // -------------------------------------------------------------------------
  // Build
  // -------------------------------------------------------------------------
  public JPanel build() {
    JPanel root = new JPanel(new BorderLayout());
    root.add(buildBrandPanel(), BorderLayout.WEST);
    root.add(buildFormPanel(),  BorderLayout.CENTER);
    return root;
  }

  // -------------------------------------------------------------------------
  // Left panel — SVG logo centered on dark background
  // -------------------------------------------------------------------------
  private JPanel buildBrandPanel() {
    SVGDiagram diagram = loadSvgDiagram();

    JPanel panel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Red top accent bar
        g.setColor(BRAND_RED);
        g.fillRect(0, 0, getWidth(), 4);

        if (diagram == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        float svgW = diagram.getWidth();
        float svgH = diagram.getHeight();
        if (svgW <= 0) { svgW = 680; svgH = 200; }

        int pad = 20;
        float scale    = (float)(getWidth() - 2 * pad) / svgW;
        float scaledH  = svgH * scale;

        g2.translate(pad, (getHeight() - scaledH) / 2.0);
        g2.scale(scale, scale);

        try { diagram.render(g2); } catch (SVGException ignored) {}
        g2.dispose();
      }
    };
    panel.setBackground(BRAND_DARK);
    panel.setPreferredSize(new Dimension(240, 420));
    return panel;
  }

  private SVGDiagram loadSvgDiagram() {
    try {
      // Try classpath first (JAR distribution), fall back to source tree for dev
      InputStream is = getClass().getResourceAsStream(
        "/Assets/motorph_logo_concept_3_minimal_wordmark.svg"
      );
      if (is == null) {
        Path p = Paths.get("app", "src", "main", "java", "Assets",
          "motorph_logo_concept_3_minimal_wordmark.svg");
        File f = p.toFile();
        if (!f.exists()) return null;
        is = new FileInputStream(f);
      }
      SVGUniverse universe = new SVGUniverse();
      URI uri = universe.loadSVG(is, "motorph");
      is.close();
      SVGDiagram diagram = universe.getDiagram(uri);
      diagram.setIgnoringClipHeuristic(true);
      return diagram;
    } catch (Exception e) {
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // Right panel — form section
  // -------------------------------------------------------------------------
  private JPanel buildFormPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(FORM_BG);
    panel.setPreferredSize(new Dimension(360, 420));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx   = 1.0;

    gbc.fill    = GridBagConstraints.VERTICAL;
    gbc.weighty = 1.0;
    gbc.insets  = new Insets(0, 0, 0, 0);
    panel.add(Box.createVerticalGlue(), gbc);

    gbc.fill    = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0;

    JLabel welcome = new JLabel("Welcome back");
    welcome.setFont(WELCOME_FONT);
    welcome.setForeground(TEXT_DARK);
    gbc.insets = new Insets(0, 48, 4, 48);
    panel.add(welcome, gbc);

    JLabel subtitle = new JLabel("Sign in to your account");
    subtitle.setFont(TAGLINE_FONT);
    subtitle.setForeground(TEXT_MUTED);
    gbc.insets = new Insets(0, 48, 30, 48);
    panel.add(subtitle, gbc);

    JLabel userLabel = new JLabel("Username");
    userLabel.setFont(LABEL_FONT);
    userLabel.setForeground(TEXT_DARK);
    gbc.insets = new Insets(0, 48, 5, 48);
    panel.add(userLabel, gbc);

    usernameField = styledTextField();
    gbc.insets = new Insets(0, 48, 18, 48);
    panel.add(usernameField, gbc);

    JLabel passLabel = new JLabel("Password");
    passLabel.setFont(LABEL_FONT);
    passLabel.setForeground(TEXT_DARK);
    gbc.insets = new Insets(0, 48, 5, 48);
    panel.add(passLabel, gbc);

    passwordField = styledPasswordField();
    gbc.insets = new Insets(0, 48, 28, 48);
    panel.add(passwordField, gbc);

    loginBtn = styledButton("Log In");
    gbc.insets = new Insets(0, 48, 0, 48);
    panel.add(loginBtn, gbc);

    gbc.fill    = GridBagConstraints.VERTICAL;
    gbc.weighty = 1.4;
    gbc.insets  = new Insets(0, 0, 0, 0);
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  // -------------------------------------------------------------------------
  // Styled components
  // -------------------------------------------------------------------------
  private JTextField styledTextField() {
    JTextField f = new JTextField();
    f.setFont(INPUT_FONT);
    f.setPreferredSize(new Dimension(0, 36));
    f.setBorder(inputBorder());
    return f;
  }

  private JPasswordField styledPasswordField() {
    JPasswordField f = new JPasswordField();
    f.setFont(INPUT_FONT);
    f.setPreferredSize(new Dimension(0, 36));
    f.setBorder(inputBorder());
    return f;
  }

  private Border inputBorder() {
    return BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(BORDER_COLOR, 1),
      BorderFactory.createEmptyBorder(4, 10, 4, 10)
    );
  }

  private JButton styledButton(String text) {
    JButton btn = new JButton(text) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = getModel().isPressed()
          ? new Color(0xB71C1C)
          : getModel().isRollover() ? new Color(0xEF5350) : BRAND_RED;
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(getText())) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), x, y);
        g2.dispose();
      }
    };
    btn.setFont(BUTTON_FONT);
    btn.setPreferredSize(new Dimension(0, 40));
    btn.setOpaque(false);
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return btn;
  }

  // -------------------------------------------------------------------------
  // Getters
  // -------------------------------------------------------------------------
  public JTextField    getUsernameField()  { return usernameField; }
  public JPasswordField getPasswordField() { return passwordField; }
  public JButton       getLoginBtn()       { return loginBtn; }
}
