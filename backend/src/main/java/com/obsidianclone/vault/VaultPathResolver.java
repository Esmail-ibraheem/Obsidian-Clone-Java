package com.obsidianclone.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.obsidianclone.config.VaultProperties;

/**
 * Translates client-supplied, vault-relative paths into absolute paths that are
 * guaranteed to live inside the vault root. This is the single chokepoint that
 * defends against path traversal — every filesystem operation must resolve here
 * first.
 *
 * <p>Semantics:
 * <ul>
 *   <li>Input is always treated as vault-relative; leading slashes are stripped
 *       (so {@code "/Notes/a.md"} and {@code "Notes/a.md"} are equivalent, and
 *       {@code ""} / {@code "/"} mean the vault root).</li>
 *   <li>After normalization the result must remain within the root, otherwise a
 *       {@link VaultException} is thrown (this rejects {@code ../} escapes).</li>
 *   <li>For paths that already exist, the resolved real path is additionally
 *       checked to remain within the root, defending against symlink escape.</li>
 * </ul>
 */
@Component
public class VaultPathResolver {

    private final Path root;

    public VaultPathResolver(VaultProperties properties) {
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    /** The absolute, normalized vault root. */
    public Path root() {
        return root;
    }

    /**
     * Resolve a vault-relative path to an absolute path inside the vault.
     *
     * @throws VaultException if the path escapes the vault root
     */
    public Path resolve(String relative) {
        String rel = relative == null ? "" : relative.strip().replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }

        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root)) {
            throw new VaultException("Path escapes the vault: " + relative);
        }

        // Defense in depth: if the target (or its nearest existing ancestor) is a
        // symlink that points outside the vault, reject it.
        Path existing = resolved;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing != null) {
            try {
                Path real = existing.toRealPath();
                if (!real.startsWith(root.toRealPath())) {
                    throw new VaultException("Path escapes the vault via symlink: " + relative);
                }
            } catch (IOException e) {
                throw new VaultException("Cannot resolve path: " + relative, e);
            }
        }

        return resolved;
    }

    /** Convert an absolute path inside the vault back to a vault-relative POSIX path. */
    public String toRelative(Path absolute) {
        Path normalized = absolute.toAbsolutePath().normalize();
        return root.relativize(normalized).toString().replace('\\', '/');
    }
}
