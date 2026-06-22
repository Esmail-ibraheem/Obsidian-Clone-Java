package com.obsidianclone.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.config.VaultProperties;
import com.obsidianclone.vault.VaultPathResolver;
import com.obsidianclone.vault.VaultService;
import com.obsidianclone.watch.ChangeType;
import com.obsidianclone.watch.FileChangeEvent;

class IndexEventListenerTest {

    @TempDir
    Path vaultRoot;

    VaultService vault;
    IndexService index;
    IndexEventListener listener;

    @BeforeEach
    void setUp() {
        VaultProperties props = new VaultProperties();
        props.setRoot(vaultRoot);
        vault = new VaultService(new VaultPathResolver(props));
        index = new IndexService(vault, new MarkdownParser());
        listener = new IndexEventListener(index);
    }

    @Test
    void modifiedEventReindexesNote() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", "no link", null);
        index.build();
        assertThat(index.backlinks("Target.md")).isEmpty();

        vault.write("Source.md", "now [[Target]]", null);
        listener.onFileChange(new FileChangeEvent(ChangeType.MODIFIED, "Source.md"));

        assertThat(index.backlinks("Target.md")).extracting(BacklinkEntry::sourcePath)
                .containsExactly("Source.md");
    }

    @Test
    void deletedEventRemovesNote() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", "[[Target]]", null);
        index.build();
        assertThat(index.backlinks("Target.md")).hasSize(1);

        listener.onFileChange(new FileChangeEvent(ChangeType.DELETED, "Source.md"));
        assertThat(index.backlinks("Target.md")).isEmpty();
    }
}
