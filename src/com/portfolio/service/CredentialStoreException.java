package com.portfolio.service;

/** A credential-store operation failed. The message never contains the secret. */
public class CredentialStoreException extends Exception {
    private static final long serialVersionUID = 1L;

    private final CredentialStore.Failure failure;

    public CredentialStoreException(CredentialStore.Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public CredentialStore.Failure failure() {
        return failure;
    }
}
