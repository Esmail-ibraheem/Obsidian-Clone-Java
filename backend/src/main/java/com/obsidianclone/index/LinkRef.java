package com.obsidianclone.index;

/**
 * A single wikilink or embed found in a note, e.g. {@code [[Target|Alias]]},
 * {@code [[Target#Heading]]}, {@code [[Target^block]]}, or {@code ![[Embed]]}.
 *
 * @param rawTarget    the link target without alias/anchor (may be empty for a
 *                     same-file anchor link like {@code [[#Heading]]})
 * @param displayAlias the alias after {@code |}, or null
 * @param anchorType   NONE, HEADING ({@code #}), or BLOCK ({@code ^})
 * @param anchor       the heading text or block id, or null
 * @param embed        true for {@code ![[...]]}
 * @param line         1-based line number where the link occurs
 * @param lineText     the trimmed source line (used for backlink snippets)
 */
public record LinkRef(
        String rawTarget,
        String displayAlias,
        AnchorType anchorType,
        String anchor,
        boolean embed,
        int line,
        String lineText) {

    public enum AnchorType {
        NONE,
        HEADING,
        BLOCK
    }
}
