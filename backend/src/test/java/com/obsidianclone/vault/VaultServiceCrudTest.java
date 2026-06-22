package com.obsidianclone.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.config.VaultProperties;

class VaultServiceCrudTest {

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
    void writeThenReadRoundTrips() {
        NoteContent written = service.write("note.md", "# Hello", null);
        assertThat(written.path()).isEqualTo("note.md");
        assertThat(written.mtime()).isPositive();

        NoteContent read = service.read("note.md");
        assertThat(read.content()).isEqualTo("# Hello");
    }

    @Test
    void writeCreatesParentFolders() {
        service.write("a/b/c.md", "deep", null);
        assertThat(Files.exists(vaultRoot.resolve("a/b/c.md"))).isTrue();
    }

    @Test
    void readMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.read("missing.md"))
                .isInstanceOf(VaultNotFoundException.class);
    }

    @Test
    void writeWithStaleBaseMtimeConflicts() throws IOException {
        service.write("note.md", "v1", null);
        long realMtime = Files.getLastModifiedTime(vaultRoot.resolve("note.md")).toMillis();
        long staleMtime = realMtime - 10_000;

        assertThatThrownBy(() -> service.write("note.md", "v2", staleMtime))
                .isInstanceOf(VaultConflictException.class)
                .satisfies(e -> {
                    VaultConflictException c = (VaultConflictException) e;
                    assertThat(c.getCurrent().content()).isEqualTo("v1");
                });
    }

    @Test
    void writeWithMatchingBaseMtimeSucceeds() {
        NoteContent v1 = service.write("note.md", "v1", null);
        NoteContent v2 = service.write("note.md", "v2", v1.mtime());
        assertThat(v2.content()).isEqualTo("v2");
    }

    @Test
    void createFileRejectsExisting() {
        service.write("note.md", "existing", null);
        assertThatThrownBy(() -> service.createFile("note.md", "new"))
                .isInstanceOf(VaultConflictException.class);
    }

    @Test
    void createFolderIsIdempotent() {
        service.createFolder("Projects");
        service.createFolder("Projects");
        assertThat(Files.isDirectory(vaultRoot.resolve("Projects"))).isTrue();
    }

    @Test
    void deleteFileRemovesIt() {
        service.write("note.md", "x", null);
        service.delete("note.md");
        assertThat(Files.exists(vaultRoot.resolve("note.md"))).isFalse();
    }

    @Test
    void deleteFolderRemovesRecursively() {
        service.write("dir/a.md", "a", null);
        service.write("dir/sub/b.md", "b", null);
        service.delete("dir");
        assertThat(Files.exists(vaultRoot.resolve("dir"))).isFalse();
    }

    @Test
    void deleteMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.delete("nope.md"))
                .isInstanceOf(VaultNotFoundException.class);
    }

    @Test
    void renameMovesFilePreservingContent() {
        service.write("old.md", "content", null);
        service.rename("old.md", "renamed/new.md");
        assertThat(Files.exists(vaultRoot.resolve("old.md"))).isFalse();
        assertThat(service.read("renamed/new.md").content()).isEqualTo("content");
    }

    @Test
    void renameRejectsTraversal() {
        service.write("old.md", "content", null);
        assertThatThrownBy(() -> service.rename("old.md", "../escaped.md"))
                .isInstanceOf(VaultException.class);
    }
}
