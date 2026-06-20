package Core.Service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Thin BCrypt wrapper used by the login and password-change flows.
 *
 * Handles the special "PLACEHOLDER_REHASH_IN_APP" value that the ETL
 * script seeds when it cannot produce a real hash at import time.
 * On first login the placeholder is detected and the user is forced
 * to set a real password before they can proceed.
 */
public class PasswordService {

    private static final String PLACEHOLDER = "PLACEHOLDER_REHASH_IN_APP";
    private static final int    WORK_FACTOR = 12;

    private PasswordService() {}

    /** Returns true when the stored hash is the ETL placeholder. */
    public static boolean IsPlaceholder(String hash) {
        return PLACEHOLDER.equals(hash);
    }

    /**
     * Verifies a plaintext password against a BCrypt hash.
     * Always returns false for a placeholder — the caller must redirect
     * to the must-change-password flow instead.
     */
    public static boolean Verify(String plaintext, String hash) {
        if (IsPlaceholder(hash)) return false;
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (Exception e) {
            return false;
        }
    }

    /** Produces a BCrypt hash suitable for storage in Users.PasswordHash. */
    public static String Hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(WORK_FACTOR));
    }
}
