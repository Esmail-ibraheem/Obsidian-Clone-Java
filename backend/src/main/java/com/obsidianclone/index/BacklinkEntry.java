package com.obsidianclone.index;

/**
 * One backlink: a reference to a note from {@code sourcePath} at {@code line},
 * with the source line text as a {@code snippet} for display.
 */
public record BacklinkEntry(String sourcePath, int line, String snippet) {
}
