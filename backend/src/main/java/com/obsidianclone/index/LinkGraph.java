package com.obsidianclone.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory graph of notes and their links. Holds each note's {@link ParsedNote}
 * and resolves wikilink targets the way Obsidian does:
 * <ol>
 *   <li>exact vault-relative path (with or without {@code .md});</li>
 *   <li>otherwise match by basename — if several match, the shortest path wins
 *       (deterministic, alphabetical tie-break).</li>
 * </ol>
 * Matching is case-insensitive. Backlinks are computed on demand against the
 * current note set, so adding/removing notes immediately changes resolution
 * (e.g. a previously-unresolved link becomes resolved once its target exists).
 *
 * <p>Not thread-safe on its own; {@link IndexService} guards access.
 */
public class LinkGraph {

    private final Map<String, ParsedNote> notes = new LinkedHashMap<>();
    /** lower-cased basename -> set of note paths sharing it. */
    private final Map<String, TreeSet<String>> byBasename = new LinkedHashMap<>();

    public void put(ParsedNote note) {
        remove(note.path());
        notes.put(note.path(), note);
        byBasename.computeIfAbsent(baseKey(note.path()), k -> new TreeSet<>()).add(note.path());
    }

    public void remove(String path) {
        if (notes.remove(path) != null) {
            String key = baseKey(path);
            TreeSet<String> set = byBasename.get(key);
            if (set != null) {
                set.remove(path);
                if (set.isEmpty()) {
                    byBasename.remove(key);
                }
            }
        }
    }

    public void clear() {
        notes.clear();
        byBasename.clear();
    }

    public boolean contains(String path) {
        return notes.containsKey(path);
    }

    public Set<String> notePaths() {
        return notes.keySet();
    }

    /**
     * Resolve a link target as seen from {@code fromPath}.
     *
     * @return the resolved note path, or empty if unresolved
     */
    public Optional<String> resolve(String target, String fromPath) {
        if (target == null || target.isBlank()) {
            // same-file anchor link, e.g. [[#Heading]]
            return notes.containsKey(fromPath) ? Optional.of(fromPath) : Optional.empty();
        }
        String t = target.strip().replace('\\', '/');

        // 1. exact relative path (with / without .md)
        if (notes.containsKey(t)) {
            return Optional.of(t);
        }
        if (!t.endsWith(".md") && notes.containsKey(t + ".md")) {
            return Optional.of(t + ".md");
        }
        // case-insensitive exact path
        for (String p : notes.keySet()) {
            if (p.equalsIgnoreCase(t) || p.equalsIgnoreCase(t + ".md")) {
                return Optional.of(p);
            }
        }

        // 2. basename match (shortest path wins)
        TreeSet<String> matches = byBasename.get(nameKey(t));
        if (matches != null && !matches.isEmpty()) {
            return matches.stream().min(shortestPath());
        }
        return Optional.empty();
    }

    /** All backlinks to {@code targetPath} from other notes. */
    public List<BacklinkEntry> backlinks(String targetPath) {
        List<BacklinkEntry> result = new ArrayList<>();
        for (ParsedNote note : notes.values()) {
            if (note.path().equals(targetPath)) {
                continue; // skip self-references
            }
            for (LinkRef link : note.links()) {
                Optional<String> resolved = resolve(link.rawTarget(), note.path());
                if (resolved.isPresent() && resolved.get().equals(targetPath)) {
                    result.add(new BacklinkEntry(note.path(), link.line(), link.lineText()));
                }
            }
        }
        result.sort(Comparator.comparing(BacklinkEntry::sourcePath).thenComparingInt(BacklinkEntry::line));
        return result;
    }

    /** Tag -> note paths containing it. */
    public Map<String, List<String>> tagIndex() {
        Map<String, List<String>> index = new LinkedHashMap<>();
        for (ParsedNote note : notes.values()) {
            for (String tag : note.tags()) {
                index.computeIfAbsent(tag, k -> new ArrayList<>()).add(note.path());
            }
        }
        return index;
    }

    public List<String> headings(String path) {
        ParsedNote note = notes.get(path);
        return note == null ? List.of() : note.headings();
    }

    private Comparator<String> shortestPath() {
        return Comparator
                .comparingInt((String p) -> p.split("/").length)
                .thenComparing(Comparator.naturalOrder());
    }

    /** basename key for a note path: filename without dir, without .md, lower-cased. */
    private String baseKey(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return stripMd(name).toLowerCase();
    }

    /** name key for a link target (may already be a bare name or a path). */
    private String nameKey(String target) {
        return baseKey(target);
    }

    private String stripMd(String name) {
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }
}
