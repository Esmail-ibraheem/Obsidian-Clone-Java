package com.obsidianclone.api.dto;

import java.util.List;

/**
 * Result of a rename.
 *
 * @param from         the old path
 * @param to           the new path
 * @param updatedNotes paths of notes whose wikilinks were rewritten
 */
public record RenameResponse(String from, String to, List<String> updatedNotes) {
}
