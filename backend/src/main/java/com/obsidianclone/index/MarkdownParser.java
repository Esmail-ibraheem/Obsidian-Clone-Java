package com.obsidianclone.index;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Extracts wikilinks, embeds, tags, and headings from markdown text using
 * Obsidian's syntax. Hand-rolled (line-aware) so we can handle Obsidian-specific
 * constructs that standard CommonMark parsers don't: {@code [[wikilinks]]},
 * {@code ![[embeds]]}, alias/anchor forms, and {@code #tags}.
 *
 * <p>Code is respected: fenced code blocks are skipped entirely, and inline
 * code spans are masked so links/tags inside them are ignored. Tags inside a
 * wikilink's {@code #heading} anchor are not mistaken for tags.
 */
@Component
public class MarkdownParser {

    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*)$");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern WIKILINK = Pattern.compile("(!?)\\[\\[([^\\[\\]\\n]+)\\]\\]");
    private static final Pattern TAG = Pattern.compile("(?<![\\w/&#])#([A-Za-z0-9_][A-Za-z0-9_/-]*)");

    public ParsedNote parse(String path, String text) {
        List<LinkRef> links = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();
        List<String> headings = new ArrayList<>();

        String[] lines = text == null ? new String[0] : text.split("\n", -1);
        boolean inFence = false;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int lineNo = i + 1;

            if (FENCE.matcher(raw).find()) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher hm = HEADING.matcher(raw);
            if (hm.matches()) {
                String heading = hm.group(2).replaceAll("\\s+#+\\s*$", "").strip();
                if (!heading.isEmpty()) {
                    headings.add(heading);
                }
                // fall through: a heading line can still contain links/tags
            }

            String scan = maskInlineCode(raw);

            // Wikilinks / embeds; mask their spans so anchors aren't read as tags.
            StringBuilder forTags = new StringBuilder(scan);
            Matcher wm = WIKILINK.matcher(scan);
            while (wm.find()) {
                links.add(parseInner(wm.group(1), wm.group(2), lineNo, raw.strip()));
                for (int k = wm.start(); k < wm.end(); k++) {
                    forTags.setCharAt(k, ' ');
                }
            }

            Matcher tm = TAG.matcher(forTags.toString());
            while (tm.find()) {
                String tag = tm.group(1);
                // Obsidian tags can't be purely numeric.
                if (tag.matches(".*[A-Za-z_/-].*")) {
                    tags.add(tag);
                }
            }
        }

        return new ParsedNote(path, links, tags, headings);
    }

    private LinkRef parseInner(String bang, String inner, int lineNo, String lineText) {
        boolean embed = "!".equals(bang);

        String left = inner;
        String alias = null;
        int pipe = inner.indexOf('|');
        if (pipe >= 0) {
            left = inner.substring(0, pipe);
            alias = inner.substring(pipe + 1).strip();
            if (alias.isEmpty()) {
                alias = null;
            }
        }
        left = left.strip();

        LinkRef.AnchorType anchorType = LinkRef.AnchorType.NONE;
        String anchor = null;
        String target = left;

        int hash = left.indexOf('#');
        int caret = left.indexOf('^');
        if (hash >= 0) {
            target = left.substring(0, hash).strip();
            anchor = left.substring(hash + 1).strip();
            anchorType = LinkRef.AnchorType.HEADING;
        } else if (caret >= 0) {
            target = left.substring(0, caret).strip();
            anchor = left.substring(caret + 1).strip();
            anchorType = LinkRef.AnchorType.BLOCK;
        }

        return new LinkRef(target, alias, anchorType, anchor, embed, lineNo, lineText);
    }

    private String maskInlineCode(String raw) {
        Matcher m = INLINE_CODE.matcher(raw);
        if (!m.find()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw);
        m.reset();
        while (m.find()) {
            for (int k = m.start(); k < m.end(); k++) {
                sb.setCharAt(k, ' ');
            }
        }
        return sb.toString();
    }
}
