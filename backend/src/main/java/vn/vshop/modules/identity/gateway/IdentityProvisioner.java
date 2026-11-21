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

    record NewIdentity(String email, String givenName, String familyName, String password) {}

    enum Outcome {
        CREATED,
        EMAIL_TAKEN,
        PASSWORD_REJECTED,
        UNAVAILABLE
    }
}
