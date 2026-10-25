package com.portfolio.service;

import java.util.Optional;

/**
 * Where a secret lives outside the application's own files. The production implementation is the Windows Credential Manager;
 * tests use an in-memory one. Implementations never log, print or include a secret in an exception message.
 */
public interface CredentialStore {
    /** Stores or replaces the secret saved under {@code target}. */
    void write(String target, String userName, String secret) throws CredentialStoreException;

    /** Returns the stored secret, or empty when nothing is stored under {@code target}. */
    Optional<String> read(String target) throws CredentialStoreException;

    /** Removes the entry; removing one that does not exist is not an error. */
    void delete(String target) throws CredentialStoreException;

    /** Why a credential-store operation failed. */
    enum Failure {
        /** The store cannot be reached at all (not Windows, PowerShell missing, timed out). */
        UNAVAILABLE,
        /** The store was reached but refused or failed the operation. */
        FAILED
    }
}
