package com.obsidianclone.api.dto;

/**
 * Request to create a vault entry.
 *
 * @param path    vault-relative path to create
 * @param type    {@code "file"} or {@code "folder"}
 * @param content initial file content (ignored for folders; may be null)
 */
public record CreateRequest(String path, String type, String content) {
}
