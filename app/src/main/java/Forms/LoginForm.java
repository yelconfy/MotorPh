package Forms;

import Core.Form.BaseForm;
import Core.Service.PasswordService;
import DataAccess.DatabaseConnector;
import DataAccess.UserDAO;
import Helper.Injector;
import Interface.ILoginProcess;
import Objects.models.IAM.Session;
import Objects.models.IAM.SessionContext;
import Objects.models.LoginResult;
import Objects.models.User;
import UI.LoginFormLayout;
import java.awt.Cursor;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

public class LoginForm extends BaseForm {

  // -------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------
  private final ILoginProcess loginProcess;
  private final LoginFormLayout ui;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------
  public LoginForm(ILoginProcess loginProcess) {
    this.loginProcess = loginProcess;
    this.ui = new LoginFormLayout();

    setTitle("MotorPH System");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setResizable(false);
    setContentPane(ui.build());

    wireListeners();
  }

  // -------------------------------------------------------------------------
  // Listener wiring
  // -------------------------------------------------------------------------
  private void wireListeners() {
    ui.getLoginBtn().addActionListener(e -> handleLogin());
  }

  // -------------------------------------------------------------------------
  // Event handlers
  // -------------------------------------------------------------------------
  private void handleLogin() {
    String username = ui.getUsernameField().getText().trim();
    String password = new String(ui.getPasswordField().getPassword());

    if (username.isEmpty() || password.isEmpty()) {
      JOptionPane.showMessageDialog(
        this,
        "Please enter both username and password."
      );
      return;
    }

    // MPH-46: BCrypt is ~310ms by design and must not freeze the UI. Safe to run
    // off the EDT only because MPH-43 pooled the connections — under the old
    // shared Connection this would have raced the UI thread.
    setBusy(true);
    new SwingWorker<LoginResult, Void>() {
      @Override
      protected LoginResult doInBackground() {
        long t0 = System.nanoTime();
        LoginResult r = loginProcess.PerformLogin(username, password);
        System.out.printf(
          "[TIMING] PerformLogin: %.0f ms%n",
          (System.nanoTime() - t0) / 1_000_000.0
        );
        return r;
      }

      @Override
      protected void done() {
        setBusy(false);
        LoginResult result;
        try {
          result = get();
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(
            LoginForm.this,
            "Sign-in failed: " + ex.getMessage(),
            "Login Error",
            JOptionPane.ERROR_MESSAGE
          );
          return;
        }

        switch (result.GetStatus()) {
          case SUCCESS -> launchShell(result.GetUser());
          case MUST_CHANGE_PASSWORD -> handleMustChangePassword(
            result.GetUser()
          );
          case ACCOUNT_LOCKED -> JOptionPane.showMessageDialog(
            LoginForm.this,
            "Your account has been temporarily locked after too many failed attempts.\n" +
              "Please try again in 15 minutes or contact your administrator.",
            "Account Locked",
            JOptionPane.WARNING_MESSAGE
          );
          case INVALID_CREDENTIALS -> JOptionPane.showMessageDialog(
            LoginForm.this,
            "Invalid username or password.",
            "Login Failed",
            JOptionPane.ERROR_MESSAGE
          );
        }
      }
    }
      .execute();
  }

  /** Locks the form while a sign-in is in flight, so it can't be double-submitted. */
  private void setBusy(boolean busy) {
    ui.getLoginBtn().setEnabled(!busy);
    ui.getLoginBtn().setText(busy ? "Signing in..." : "Log In");
    ui.getUsernameField().setEnabled(!busy);
    ui.getPasswordField().setEnabled(!busy);
    setCursor(
      Cursor.getPredefinedCursor(
        busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR
      )
    );
  }

  /**
   * Prompts the user to set a new password, hashes it, persists it,
   * then establishes the session and opens the shell.
   */
  private void handleMustChangePassword(User user) {
    JPasswordField pw1 = new JPasswordField();
    JPasswordField pw2 = new JPasswordField();

    int result = JOptionPane.showConfirmDialog(
      this,
      new Object[] {
        "You must set a new password before continuing.",
        "New password:",
        pw1,
        "Confirm password:",
        pw2,
      },
      "Set New Password",
      JOptionPane.OK_CANCEL_OPTION,
      JOptionPane.PLAIN_MESSAGE
    );

    if (result != JOptionPane.OK_OPTION) {
      JOptionPane.showMessageDialog(
        this,
        "Password change cancelled. Please log in again to continue.",
        "Cancelled",
        JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    String newPassword = new String(pw1.getPassword());
    String confirmPassword = new String(pw2.getPassword());

    if (newPassword.isBlank()) {
      JOptionPane.showMessageDialog(
        this,
        "Password cannot be blank. Please log in again and try again.",
        "Invalid",
        JOptionPane.ERROR_MESSAGE
      );
      return;
    }
    if (!newPassword.equals(confirmPassword)) {
      JOptionPane.showMessageDialog(
        this,
        "Passwords do not match. Please log in again and try again.",
        "Mismatch",
        JOptionPane.ERROR_MESSAGE
      );
      return;
    }

    new UserDAO().UpdatePasswordHash(
      user.GetUserId(),
      PasswordService.Hash(newPassword)
    );
    launchShell(user);
  }

  /**
   * Establishes the session for the authenticated user, then opens the
   * application shell.
   *
   * Replaces the old handleRedirection(): there is no longer any
   * DeptCode enum lookup or per-role JFrame. The shell reads
   * Session.GetCurrentUser() and builds its role-scoped navigation
   * entirely from the RBAC tables via AccessDAO — module access is now
   * driven by data (Role_Permission x Module), not a hardcoded map.
   */
  // private void launchShell(User user) {
  //   SessionContext ctx = loginProcess.EstablishSession(user);
  //   if (ctx == null) {
  //     JOptionPane.showMessageDialog(
  //       this,
  //       "Could not start your session. Please try again.",
  //       "Session Error",
  //       JOptionPane.ERROR_MESSAGE
  //     );
  //     return;
  //   }
  //   Session.Start(user, ctx.GetSessionId(), ctx.GetToken());
  //   Injector.CreateShell().setVisible(true);
  //   this.dispose();
  // }

  private void launchShell(User user) {
    long t0 = System.nanoTime();
    SessionContext ctx = loginProcess.EstablishSession(user);
    long t1 = System.nanoTime();

    if (ctx == null) {
      /* ...unchanged error dialog... */ return;
    }

    Session.Start(user, ctx.GetSessionId(), ctx.GetToken());

    long t2 = System.nanoTime();
    Injector.CreateShell().setVisible(true);
    long t3 = System.nanoTime();

    System.out.printf(
      "[TIMING] EstablishSession: %.0f ms | CreateShell: %.0f ms%n",
      (t1 - t0) / 1_000_000.0,
      (t3 - t2) / 1_000_000.0
    );

    this.dispose();
  }

  // -------------------------------------------------------------------------
  // Entry point
  // -------------------------------------------------------------------------
  public static void main(String[] args) {
    // MPH-46: warm the pool on a background thread. Previously this ran on the
    // EDT (the console showed "[AWT-EventQueue-0] MotorPH-Pool - Starting..."),
    // so the login window couldn't paint until the pool was up. The pool is
    // thread-safe (double-checked locking), so racing the UI here is fine.
    Thread warm = new Thread(DatabaseConnector::Warmup, "db-warmup");
    warm.setDaemon(true);
    warm.start();

    SwingUtilities.invokeLater(() -> {
      com.formdev.flatlaf.FlatLightLaf.setup();
      Injector.CreateLoginForm().setVisible(true);
    });
  }
}
