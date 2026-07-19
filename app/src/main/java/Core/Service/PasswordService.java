package Core.Service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Thin BCrypt wrapper used by the login and password-change flows.
 *
 * Handles the special "PLACEHOLDER_REHASH_IN_APP" value that the ETL script
 * seeds when it cannot produce a real hash at import time. On first login the
 * placeholder is detected and the user is forced to set a real password before
 * they can proceed.
 *
 * WORK FACTOR (MPH-46)
 * --------------------
 * Lowered 12 -> 10. BCrypt cost is a power of two, so each step DOUBLES the
 * work: cost 12 measured ~1250 ms per verify on this hardware (jBCrypt is a
 * pure-Java, unoptimised implementation), which was ~75% of the entire login.
 * Cost 10 is 4x cheaper -> ~310 ms, and 10 is the OWASP-recommended MINIMUM for
 * bcrypt, so this remains a defensible choice rather than an arbitrary one.
 *
 * Do NOT drop below 10. The expense is the security control: it is what makes a
 * stolen hash table costly to brute-force. This is a tuning decision, not an
 * optimisation of waste.
 *
 * COST MIGRATION
 * --------------
 * BCrypt.checkpw() reads the cost from the STORED HASH, not from WORK_FACTOR —
 * so changing the constant alone does nothing for existing users. They would
 * keep verifying at cost 12 forever. NeedsRehash() detects that, and
 * LoginProcess transparently re-hashes on the next successful login. Standard
 * cost-migration pattern; the user notices nothing.
 */
public class PasswordService {

    private static final String PLACEHOLDER = "PLACEHOLDER_REHASH_IN_APP";
    private static final int    WORK_FACTOR = 10;

    private PasswordService() {}

    /** Returns true when the stored hash is the ETL placeholder. */
    public static boolean IsPlaceholder(String hash) {
        return PLACEHOLDER.equals(hash);
    }

    /**
     * Verifies a plaintext password against a BCrypt hash.
     * Always returns false for a placeholder — the caller must redirect to the
     * must-change-password flow instead.
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

    /**
     * True when a VERIFIED hash was produced at a cost factor other than the
     * current WORK_FACTOR, and should therefore be re-hashed.
     *
     * Only ever call this AFTER Verify() has returned true — re-hashing needs
     * the plaintext, and the plaintext is only known to be correct at that point.
     *
     * Note this catches a cost that is too HIGH as well as too LOW. Too low is a
     * security gap; too high is the 1250 ms this ticket exists to remove. Either
     * way the stored hash disagrees with policy, so bring it in line.
     *
     * A malformed/unparseable hash returns false: leave it alone and let Verify
     * be the authority on it, rather than silently rewriting something we do not
     * understand.
     */
    public static boolean NeedsRehash(String hash) {
        int cost = CostOf(hash);
        return cost > 0 && cost != WORK_FACTOR;
    }

    /**
     * Extracts the cost factor from a BCrypt hash, or -1 if it cannot be read.
     *
     * Format: $2a$10$<22-char salt><31-char digest>
     *          ^^^ ^^
     *          |   +-- cost, 2 digits, positions 4-5
     *          +------ algorithm version
     */
    static int CostOf(String hash) {
        if (hash == null || hash.length() < 7 || hash.charAt(0) != '$') {
            return -1;
        }
        // Guard the separators so we only parse something shaped like a bcrypt hash.
        if (hash.charAt(3) != '$' || hash.charAt(6) != '$') {
            return -1;
        }
        try {
            return Integer.parseInt(hash.substring(4, 6));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}