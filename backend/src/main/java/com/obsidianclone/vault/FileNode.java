package com.obsidianclone.vault;

import java.util.List;

/**
 * A node in the vault file tree. Folders carry {@code children}; files have
 * {@code children == null}. {@code path} is vault-relative, POSIX-style.
 */
public record FileNode(String name, String path, Type type, List<FileNode> children) {

    public enum Type {
        FILE,
        FOLDER
    }

    public static FileNode file(String name, String path) {
        return new FileNode(name, path, Type.FILE, null);
    }

    public static FileNode folder(String name, String path, List<FileNode> children) {
        return new FileNode(name, path, Type.FOLDER, children);
    }
}
