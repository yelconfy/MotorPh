package Forms;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import Core.Form.BaseForm;
import Core.Service.PasswordService;
import DataAccess.UserDAO;
import Helper.Injector;
import Interface.ILoginProcess;
import Objects.models.LoginResult;
import Objects.models.User;
import Objects.models.IAM.Session;
import Objects.models.IAM.SessionContext;
import UI.LoginFormLayout;

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
            JOptionPane.showMessageDialog(this, "Please enter both username and password.");
            return;
        }

        LoginResult result = loginProcess.PerformLogin(username, password);

        switch (result.GetStatus()) {
            case SUCCESS -> launchShell(result.GetUser());
            case MUST_CHANGE_PASSWORD -> handleMustChangePassword(result.GetUser());
            case ACCOUNT_LOCKED -> JOptionPane.showMessageDialog(
                this,
                "Your account has been temporarily locked after too many failed attempts.\n" +
                "Please try again in 15 minutes or contact your administrator.",
                "Account Locked",
                JOptionPane.WARNING_MESSAGE
            );
            case INVALID_CREDENTIALS -> JOptionPane.showMessageDialog(
                this,
                "Invalid username or password.",
                "Login Failed",
                JOptionPane.ERROR_MESSAGE
            );
        }
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
                "New password:", pw1,
                "Confirm password:", pw2
            },
            "Set New Password",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            JOptionPane.showMessageDialog(this,
                "Password change cancelled. Please log in again to continue.",
                "Cancelled", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String newPassword     = new String(pw1.getPassword());
        String confirmPassword = new String(pw2.getPassword());

        if (newPassword.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Password cannot be blank. Please log in again and try again.",
                "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this,
                "Passwords do not match. Please log in again and try again.",
                "Mismatch", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new UserDAO().UpdatePasswordHash(user.GetUserId(), PasswordService.Hash(newPassword));
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
    private void launchShell(User user) {
        SessionContext ctx = loginProcess.EstablishSession(user);
        if (ctx == null) {
            JOptionPane.showMessageDialog(this,
                "Could not start your session. Please try again.",
                "Session Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Session.Start(user, ctx.GetSessionId(), ctx.GetToken());
        Injector.CreateShell().setVisible(true);
        this.dispose();
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            com.formdev.flatlaf.FlatLightLaf.setup();
            Injector.CreateLoginForm().setVisible(true);
        });
    }
}