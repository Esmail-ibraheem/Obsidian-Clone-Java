package com.obsidianclone.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.config.VaultProperties;

class VaultPathResolverTest {

    @TempDir
    Path vaultRoot;

    VaultPathResolver resolver;

    @BeforeEach
    void setUp() {
        VaultProperties props = new VaultProperties();
        props.setRoot(vaultRoot);
        resolver = new VaultPathResolver(props);
    }

    @Test
    void resolvesRelativePathInsideVault() {
        Path resolved = resolver.resolve("Notes/a.md");
        assertThat(resolved).isEqualTo(vaultRoot.resolve("Notes/a.md").normalize());
        assertThat(resolved.startsWith(vaultRoot)).isTrue();
    }

    @Test
    void leadingSlashIsTreatedAsVaultRelative() {
        assertThat(resolver.resolve("/Notes/a.md"))
                .isEqualTo(vaultRoot.resolve("Notes/a.md").normalize());
    }

    @Test
    void emptyAndRootResolveToVaultRoot() {
        assertThat(resolver.resolve("")).isEqualTo(vaultRoot);
        assertThat(resolver.resolve("/")).isEqualTo(vaultRoot);
        assertThat(resolver.resolve(null)).isEqualTo(vaultRoot);
    }

    @Test
    void rejectsParentTraversal() {
        assertThatThrownBy(() -> resolver.resolve("../escape.md"))
                .isInstanceOf(VaultException.class);
    }

    @Test
    void rejectsDeepTraversalThatEscapes() {
        assertThatThrownBy(() -> resolver.resolve("a/../../b"))
                .isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> resolver.resolve("/etc/../../../etc/passwd"))
                .isInstanceOf(VaultException.class);
    }

    @Test
    void containedDotDotIsAllowed() {
        // a/b/../c stays inside the vault -> allowed, normalized to a/c
        assertThat(resolver.resolve("a/b/../c"))
                .isEqualTo(vaultRoot.resolve("a/c").normalize());
    }

    @Test
    void toRelativeRoundTrips() {
        Path abs = resolver.resolve("Folder/Note.md");
        assertThat(resolver.toRelative(abs)).isEqualTo("Folder/Note.md");
    }

    @Test
    void rejectsSymlinkComponents() throws IOException {
        Path outside = Files.createTempDirectory("outside-vault");
        Path link = vaultRoot.resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symlinks not supported in this environment");
        }
        // A path through the symlink, and the symlink itself, are both rejected.
        assertThatThrownBy(() -> resolver.resolve("escape/secret.md")).isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> resolver.resolve("escape")).isInstanceOf(VaultException.class);
    }

    @Test
    void rejectsDanglingSymlinkLeaf() throws IOException {
        Path link = vaultRoot.resolve("ghost.md");
        try {
            Files.createSymbolicLink(link, Path.of("/nonexistent/target.md"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symlinks not supported in this environment");
        }
        assertThatThrownBy(() -> resolver.resolve("ghost.md")).isInstanceOf(VaultException.class);
    }
}
