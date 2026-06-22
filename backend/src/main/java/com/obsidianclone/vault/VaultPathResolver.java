package com.obsidianclone.vault;

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

        // Reject symlinks anywhere along the path. The lexical check above stops
        // ".." traversal, but the actual IO syscalls (read/write/move/list) follow
        // symlinks, so an in-vault symlink could redirect outside the root. A notes
        // vault has no legitimate need for symlinks, so we forbid them outright.
        // isSymbolicLink inspects the link itself (true even for dangling links),
        // closing the dangling-leaf write-through case too.
        Path current = root;
        for (Path part : root.relativize(resolved)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new VaultException("Symlinks are not allowed in the vault: " + relative);
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
