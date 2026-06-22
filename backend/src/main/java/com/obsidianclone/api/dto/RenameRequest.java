package com.obsidianclone.api.dto;

/**
 * Request to rename/move a vault entry.
 *
 * @param from        current vault-relative path
 * @param to          new vault-relative path
 * @param updateLinks whether to rewrite wikilinks in notes that link to {@code from}
 */
public record RenameRequest(String from, String to, boolean updateLinks) {
}
