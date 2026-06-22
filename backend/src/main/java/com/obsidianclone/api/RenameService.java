package com.obsidianclone.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.obsidianclone.api.dto.RenameResponse;
import com.obsidianclone.index.BacklinkEntry;
import com.obsidianclone.index.IndexService;
import com.obsidianclone.index.MarkdownText;
import com.obsidianclone.vault.VaultService;

/**
 * Renames/moves a vault entry and (optionally) rewrites wikilinks in notes that
 * pointed at it, the way Obsidian's "automatically update internal links" does.
 * Rewriting is basename-based: links whose target basename matches the old name
 * are updated to the new name, preserving any folder prefix, {@code .md} suffix,
 * alias, and anchor.
 */
@Service
public class RenameService {

    // group1: !?[[ , group2: target, group3: optional |alias / #anchor / ^block , group4: ]]
    private static final Pattern WIKILINK =
            Pattern.compile("(!?\\[\\[)([^\\]|#^]+)([|#^][^\\]]*)?(\\]\\])");

    private final VaultService vault;
    private final IndexService index;

    public RenameService(VaultService vault, IndexService index) {
        this.vault = vault;
        this.index = index;
    }

    public RenameResponse rename(String from, String to, boolean updateLinks) {
        String oldBase = baseName(from);
        String newBase = baseName(to);

        // Capture the notes linking to `from` BEFORE the move, while links still resolve.
        Set<String> sources = new LinkedHashSet<>();
        if (updateLinks) {
            for (BacklinkEntry entry : index.backlinks(from)) {
                sources.add(entry.sourcePath());
            }
        }

        vault.rename(from, to);
        index.onFileRenamed(from, to);

        List<String> updated = new ArrayList<>();
        if (updateLinks && !oldBase.equalsIgnoreCase(newBase)) {
            for (String src : sources) {
                String content = vault.read(src).content();
                String rewritten = rewrite(content, oldBase, newBase);
                if (!rewritten.equals(content)) {
                    vault.write(src, rewritten, null);
                    index.onFileChanged(src);
                    updated.add(src);
                }
            }
        }
        return new RenameResponse(from, to, updated);
    }

    String rewrite(String content, String oldBase, String newBase) {
        // Match links on a code-masked copy so wikilinks inside fenced/inline code
        // are never rewritten; splice replacements back into the original content.
        String masked = MarkdownText.maskCode(content);
        Matcher m = WIKILINK.matcher(masked);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(content, last, m.start());
            String target = m.group(2).strip();
            if (baseNameOf(target).equalsIgnoreCase(oldBase)) {
                out.append(m.group(1))
                        .append(retarget(target, newBase))
                        .append(nullToEmpty(m.group(3)))
                        .append(m.group(4));
            } else {
                out.append(content, m.start(), m.end());
            }
            last = m.end();
        }
        out.append(content.substring(last));
        return out.toString();
    }

    /** Replace the basename portion of a target, keeping folder prefix and .md suffix. */
    private String retarget(String target, String newBase) {
        boolean md = target.endsWith(".md");
        int slash = target.lastIndexOf('/');
        String prefix = slash >= 0 ? target.substring(0, slash + 1) : "";
        return prefix + newBase + (md ? ".md" : "");
    }

    /** Basename of a vault path, without folder or .md extension. */
    private String baseName(String path) {
        return baseNameOf(path);
    }

    private String baseNameOf(String target) {
        String name = target;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
