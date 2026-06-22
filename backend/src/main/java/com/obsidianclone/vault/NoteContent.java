package com.obsidianclone.vault;

/**
 * The content and metadata of a vault file.
 *
 * @param path    vault-relative POSIX path
 * @param content UTF-8 text content
 * @param mtime   last-modified time in epoch milliseconds
 * @param size    size in bytes
 */
public record NoteContent(String path, String content, long mtime, long size) {
}
