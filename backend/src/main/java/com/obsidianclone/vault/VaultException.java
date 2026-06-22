package com.obsidianclone.vault;

/** Base exception for vault operations (invalid path, IO failure, etc.). */
public class VaultException extends RuntimeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
