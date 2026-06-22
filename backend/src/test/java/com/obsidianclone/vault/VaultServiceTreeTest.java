package com.obsidianclone.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.config.VaultProperties;

class VaultServiceTreeTest {

    @TempDir
    Path vaultRoot;

    VaultService service;

    @BeforeEach
    void setUp() {
        VaultProperties props = new VaultProperties();
        props.setRoot(vaultRoot);
        service = new VaultService(new VaultPathResolver(props));
    }

    @Test
    void treeListsFoldersFirstThenFilesAlphabetically() throws IOException {
        Files.writeString(vaultRoot.resolve("zebra.md"), "z");
        Files.writeString(vaultRoot.resolve("apple.md"), "a");
        Files.createDirectories(vaultRoot.resolve("Sub"));
        Files.writeString(vaultRoot.resolve("Sub/child.md"), "c");

        List<FileNode> tree = service.tree();

        assertThat(tree).extracting(FileNode::name).containsExactly("Sub", "apple.md", "zebra.md");
        assertThat(tree.get(0).type()).isEqualTo(FileNode.Type.FOLDER);
        assertThat(tree.get(0).children()).extracting(FileNode::name).containsExactly("child.md");
        assertThat(tree.get(0).children().get(0).path()).isEqualTo("Sub/child.md");
    }

    @Test
    void treeExcludesDotFiles() throws IOException {
        Files.writeString(vaultRoot.resolve("visible.md"), "v");
        Files.writeString(vaultRoot.resolve(".hidden"), "h");
        Files.createDirectories(vaultRoot.resolve(".git"));

        List<FileNode> tree = service.tree();

        assertThat(tree).extracting(FileNode::name).containsExactly("visible.md");
    }

    @Test
    void treeUsesPosixRelativePaths() throws IOException {
        Files.createDirectories(vaultRoot.resolve("A/B"));
        Files.writeString(vaultRoot.resolve("A/B/deep.md"), "d");

        List<FileNode> tree = service.tree();

        FileNode a = tree.get(0);
        FileNode b = a.children().get(0);
        assertThat(b.path()).isEqualTo("A/B");
        assertThat(b.children().get(0).path()).isEqualTo("A/B/deep.md");
    }
}
