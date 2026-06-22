package com.obsidianclone.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

/**
 * Filesystem operations over the vault. All paths are vault-relative and are
 * validated through {@link VaultPathResolver} before any IO. The vault folder
 * is the single source of truth; there is no database.
 */
@Service
public class VaultService {

    private final VaultPathResolver resolver;

    public VaultService(VaultPathResolver resolver) {
        this.resolver = resolver;
    }

    /** Top-level entries of the vault as a sorted tree (folders first, then files). */
    public List<FileNode> tree() {
        Path root = resolver.root();
        try {
            if (!Files.isDirectory(root)) {
                Files.createDirectories(root);
            }
            return listChildren(root);
        } catch (IOException e) {
            throw new VaultException("Failed to read vault tree", e);
        }
    }

    private List<FileNode> listChildren(Path dir) throws IOException {
        List<FileNode> nodes = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> sorted = entries
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(treeOrder())
                    .toList();
            for (Path p : sorted) {
                String name = p.getFileName().toString();
                String rel = resolver.toRelative(p);
                if (Files.isDirectory(p)) {
                    nodes.add(FileNode.folder(name, rel, listChildren(p)));
                } else {
                    nodes.add(FileNode.file(name, rel));
                }
            }
        }
        return nodes;
    }

    /** Folders before files; within each group, case-insensitive by name. */
    private Comparator<Path> treeOrder() {
        return Comparator
                .comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
                .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER);
    }

    /** Read a file's content + metadata. */
    public NoteContent read(String path) {
        Path file = resolver.resolve(path);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new VaultNotFoundException("No such file: " + path);
        }
        try {
            String content = Files.readString(file);
            return new NoteContent(resolver.toRelative(file), content, mtime(file), Files.size(file));
        } catch (IOException e) {
            throw new VaultException("Failed to read file: " + path, e);
        }
    }

    /**
     * Write content to a file, creating it (and parent folders) if needed.
     *
     * @param baseMtime the mtime the caller last saw; if non-null and the file
     *                  exists with a different mtime, a {@link VaultConflictException}
     *                  is thrown carrying the current content.
     */
    public NoteContent write(String path, String content, Long baseMtime) {
        Path file = resolver.resolve(path);
        if (Files.isDirectory(file)) {
            throw new VaultException("Path is a directory: " + path);
        }
        try {
            if (baseMtime != null && Files.exists(file) && mtime(file) != baseMtime) {
                throw new VaultConflictException("File changed on disk: " + path, read(path));
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content == null ? "" : content);
            return read(path);
        } catch (IOException e) {
            throw new VaultException("Failed to write file: " + path, e);
        }
    }

    /** Create a new file; conflicts (409) if it already exists. */
    public NoteContent createFile(String path, String content) {
        Path file = resolver.resolve(path);
        if (Files.exists(file)) {
            throw new VaultConflictException("File already exists: " + path, read(path));
        }
        return write(path, content == null ? "" : content, null);
    }

    /** Create a folder (and any missing parents). Idempotent for existing folders. */
    public void createFolder(String path) {
        Path dir = resolver.resolve(path);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new VaultException("A file already exists at: " + path);
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new VaultException("Failed to create folder: " + path, e);
        }
    }

    /** Delete a file or folder (folders recursively). */
    public void delete(String path) {
        Path target = resolver.resolve(path);
        if (!Files.exists(target)) {
            throw new VaultNotFoundException("No such path: " + path);
        }
        try {
            if (Files.isDirectory(target)) {
                deleteRecursively(target);
            } else {
                Files.delete(target);
            }
        } catch (IOException e) {
            throw new VaultException("Failed to delete: " + path, e);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.delete(p);
            }
        }
    }

    /** Move/rename a file or folder. Target's parent folders are created as needed. */
    public void rename(String from, String to) {
        Path src = resolver.resolve(from);
        Path dst = resolver.resolve(to);
        if (!Files.exists(src)) {
            throw new VaultNotFoundException("No such path: " + from);
        }
        if (Files.exists(dst)) {
            throw new VaultConflictException("Target already exists: " + to,
                    Files.isDirectory(dst) ? null : read(to));
        }
        try {
            Path parent = dst.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(src, dst);
        } catch (IOException e) {
            throw new VaultException("Failed to rename: " + from + " -> " + to, e);
        }
    }

    private long mtime(Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis();
    }

    /** True if the path exists and is a regular markdown file. */
    public boolean isMarkdown(String path) {
        Path file = resolver.resolve(path);
        return Files.isRegularFile(file) && Objects.toString(file.getFileName(), "").endsWith(".md");
    }
}
