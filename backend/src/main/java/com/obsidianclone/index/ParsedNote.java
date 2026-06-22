package com.obsidianclone.index;

import java.util.List;
import java.util.Set;

/**
 * The result of parsing a markdown note: its outgoing links/embeds, tags, and
 * headings. {@code path} is the vault-relative POSIX path of the note.
 */
public record ParsedNote(
        String path,
        List<LinkRef> links,
        Set<String> tags,
        List<String> headings) {
}
