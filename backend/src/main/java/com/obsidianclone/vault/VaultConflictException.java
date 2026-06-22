package com.obsidianclone.vault;

/**
 * Thrown when a write/create would clobber a concurrent change: the caller's
 * expected {@code baseMtime} no longer matches what's on disk, or a create
 * targets an existing path. Carries the current on-disk content so the API can
 * return it (HTTP 409) and let the client reconcile.
 */
public class VaultConflictException extends VaultException {

    private final transient NoteContent current;

    public VaultConflictException(String message, NoteContent current) {
        super(message);
        this.current = current;
    }

    public NoteContent getCurrent() {
        return current;
    }
}
