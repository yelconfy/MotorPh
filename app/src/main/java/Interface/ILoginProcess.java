package Interface;

import Objects.models.LoginResult;
import Objects.models.User;
import Objects.models.IAM.SessionContext;

public interface ILoginProcess {

    LoginResult PerformLogin(String username, String password);

    /**
     * Establishes a single live session for the authenticated user, revoking
     * any existing live session for that account (takeover policy). Call once,
     * at the point the user actually enters the shell (covers both the normal
     * success path and the must-change-password path).
     *
     * @return the new session context (SessionID + raw token), or null if the
     *         session could not be created.
     */
    SessionContext EstablishSession(User user);
}