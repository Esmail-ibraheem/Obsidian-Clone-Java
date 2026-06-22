package com.obsidianclone.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.config.VaultProperties;
import com.obsidianclone.vault.VaultPathResolver;
import com.obsidianclone.vault.VaultService;

class IndexServiceTest {

    @TempDir
    Path vaultRoot;

    VaultService vault;
    IndexService index;

    @BeforeEach
    void setUp() {
        VaultProperties props = new VaultProperties();
        props.setRoot(vaultRoot);
        vault = new VaultService(new VaultPathResolver(props));
        index = new IndexService(vault, new MarkdownParser());
    }

    @Test
    void buildIndexesWholeVaultIncludingSubfolders() {
        vault.write("Target.md", "# Target", null);
        vault.write("notes/Source.md", "links to [[Target]]", null);
        index.build();

        assertThat(index.backlinks("Target.md")).extracting(BacklinkEntry::sourcePath)
                .containsExactly("notes/Source.md");
    }

    @Test
    void onFileChangedReindexesSingleNote() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", "no link yet", null);
        index.build();
        assertThat(index.backlinks("Target.md")).isEmpty();

        vault.write("Source.md", "now links [[Target]]", null);
        index.onFileChanged("Source.md");

        assertThat(index.backlinks("Target.md")).extracting(BacklinkEntry::sourcePath)
                .containsExactly("Source.md");
    }

    @Test
    void onFileDeletedRemovesItFromIndex() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", "links [[Target]]", null);
        index.build();
        assertThat(index.backlinks("Target.md")).hasSize(1);

        index.onFileDeleted("Source.md");
        assertThat(index.backlinks("Target.md")).isEmpty();
    }

    @Test
    void onFileRenamedMovesIndexEntry() {
        vault.write("Target.md", "# Target", null);
        vault.write("Old.md", "links [[Target]]", null);
        index.build();

        vault.rename("Old.md", "New.md");
        index.onFileRenamed("Old.md", "New.md");

        assertThat(index.backlinks("Target.md")).extracting(BacklinkEntry::sourcePath)
                .containsExactly("New.md");
    }

    @Test
    void deletingADirectoryRemovesContainedNotes() {
        vault.write("Target.md", "# T", null);
        vault.write("folder/a.md", "[[Target]]", null);
        vault.write("folder/sub/b.md", "[[Target]]", null);
        index.build();
        assertThat(index.backlinks("Target.md")).hasSize(2);

        // The native watcher emits a single DELETED for the directory.
        index.onFileDeleted("folder");
        assertThat(index.backlinks("Target.md")).isEmpty();
    }

    @Test
    void resolveAndTagsAndHeadingsExposed() {
        vault.write("Folder/Doc.md", "# Heading\n#topic\nlink [[Doc]]", null);
        index.build();

        assertThat(index.resolve("Doc", "Folder/Doc.md")).contains("Folder/Doc.md");
        assertThat(index.tags().get("topic")).containsExactly("Folder/Doc.md");
        assertThat(index.headings("Folder/Doc.md")).containsExactly("Heading");
    }
}
