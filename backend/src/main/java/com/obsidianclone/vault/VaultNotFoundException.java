package com.obsidianclone.vault;

/** Thrown when a requested vault path does not exist (mapped to HTTP 404). */
public class VaultNotFoundException extends VaultException {

    public VaultNotFoundException(String message) {
        super(message);
    }
}
