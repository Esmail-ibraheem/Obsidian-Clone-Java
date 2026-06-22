package com.obsidianclone.index;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared markdown text utilities for code-aware processing. Used by both the
 * parser (to skip links/tags in code) and the rename rewriter (to avoid mutating
 * links inside code examples), so the two always agree on what "code" is.
 */
public final class MarkdownText {

    private static final Pattern FENCE = Pattern.compile("^\\s*(`{3,}|~{3,})");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");

    private MarkdownText() {
    }

    /** Mask an inline-code span ({@code `like this`}) on a line with spaces, preserving positions. */
    public static String maskInlineCode(String line) {
        Matcher m = INLINE_CODE.matcher(line);
        if (!m.find()) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line);
        m.reset();
        while (m.find()) {
            for (int k = m.start(); k < m.end(); k++) {
                sb.setCharAt(k, ' ');
            }
        }
        return sb.toString();
    }

    /**
     * Return a copy of {@code content} with all fenced code blocks and inline
     * code spans blanked to spaces (length and newline positions preserved), so a
     * regex over the result will never match inside code.
     */
    public static String maskCode(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder out = new StringBuilder(content.length());
        FenceState fence = new FenceState();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (fence.isDelimiter(line) || fence.inFence()) {
                out.append(" ".repeat(line.length()));
            } else {
                out.append(maskInlineCode(line));
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Tracks fenced-code state across lines. A block opened by {@code ```} can
     * only be closed by a {@code ```} fence of at least the same length (and
     * likewise for {@code ~~~}); a different/shorter fence inside is content.
     */
    public static final class FenceState {
        private char marker = 0;
        private int length = 0;
        private boolean open = false;

        public boolean inFence() {
            return open;
        }

        /** True if {@code line} is a fence open/close delimiter; updates state as a side effect. */
        public boolean isDelimiter(String line) {
            Matcher m = FENCE.matcher(line);
            if (!m.find()) {
                return false;
            }
            String fence = m.group(1);
            char c = fence.charAt(0);
            int len = fence.length();
            if (!open) {
                open = true;
                marker = c;
                length = len;
                return true;
            }
            if (c == marker && len >= length) {
                open = false;
                marker = 0;
                length = 0;
                return true;
            }
            return false; // a non-matching fence while open is just code content
        }
    }
}
