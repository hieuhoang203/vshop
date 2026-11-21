package vn.vshop.modules.identity.gateway;

/**
 * Creates sign-in identities in the identity provider (Keycloak). The provider stays the only place
 * that stores credentials; this application never sees or keeps a password after the call. Calls
 * the network, so never inside a database transaction.
 */
public interface IdentityProvisioner {
    /** False until the provisioning client is configured; registration is then unavailable. */
    boolean configured();

    /** Creates an enabled buyer identity. */
    Outcome createBuyer(NewIdentity identity);

    /**
     * Sends the person a link to confirm their e-mail address. False when the provider could not
     * send it (for example no mail server is configured).
     */
    boolean sendVerificationEmail(String userId);

    /**
     * Sends a password-reset link when an account with this address exists. Returns true both when
     * it was sent and when there is no such account, so callers cannot probe which addresses are
     * registered; false only when the provider failed.
     */
    boolean sendPasswordReset(String email);

    record NewIdentity(String email, String givenName, String familyName, String password) {}

    enum Outcome {
        CREATED,
        EMAIL_TAKEN,
        PASSWORD_REJECTED,
        UNAVAILABLE
    }
}
