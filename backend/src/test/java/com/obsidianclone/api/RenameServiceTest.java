package com.obsidianclone.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.obsidianclone.api.dto.RenameResponse;
import com.obsidianclone.config.VaultProperties;
import com.obsidianclone.index.IndexService;
import com.obsidianclone.index.MarkdownParser;
import com.obsidianclone.vault.VaultPathResolver;
import com.obsidianclone.vault.VaultService;

class RenameServiceTest {

    @TempDir
    Path vaultRoot;

    VaultService vault;
    IndexService index;
    RenameService renameService;

    @BeforeEach
    void setUp() {
        VaultProperties props = new VaultProperties();
        props.setRoot(vaultRoot);
        vault = new VaultService(new VaultPathResolver(props));
        index = new IndexService(vault, new MarkdownParser());
        renameService = new RenameService(vault, index);
    }

    @Test
    void renameRewritesBacklinksPreservingAliasAnchorAndEmbed() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", String.join("\n",
                "plain [[Target]]",
                "alias [[Target|see it]]",
                "anchor [[Target#Section]]",
                "embed ![[Target]]"), null);
        index.build();

        RenameResponse resp = renameService.rename("Target.md", "Renamed.md", true);

        assertThat(resp.updatedNotes()).containsExactly("Source.md");
        String rewritten = vault.read("Source.md").content();
        assertThat(rewritten)
                .contains("[[Renamed]]")
                .contains("[[Renamed|see it]]")
                .contains("[[Renamed#Section]]")
                .contains("![[Renamed]]")
                .doesNotContain("[[Target")
                .doesNotContain("![[Target");
        assertThat(vault.read("Renamed.md").content()).isEqualTo("# Target");
    }

    @Test
    void renameWithoutUpdateLinksLeavesBacklinksAlone() {
        vault.write("Target.md", "# Target", null);
        vault.write("Source.md", "links [[Target]]", null);
        index.build();

        RenameResponse resp = renameService.rename("Target.md", "Renamed.md", false);

        assertThat(resp.updatedNotes()).isEmpty();
        assertThat(vault.read("Source.md").content()).contains("[[Target]]");
    }

    @Test
    void rewriteLeavesUnrelatedLinksUntouched() {
        String content = "[[Target]] and [[Other]] and [[TargetSuffix]]";
        String rewritten = renameService.rewrite(content, "Target", "Renamed");
        assertThat(rewritten).isEqualTo("[[Renamed]] and [[Other]] and [[TargetSuffix]]");
    }

    @Test
    void rewritePreservesFolderPrefix() {
        String content = "see [[notes/Target|alias]]";
        String rewritten = renameService.rewrite(content, "Target", "Renamed");
        assertThat(rewritten).isEqualTo("see [[notes/Renamed|alias]]");
    }

    @Test
    void rewriteSkipsLinksInsideFencedAndInlineCode() {
        String content = String.join("\n",
                "Real link [[Target]] here.",
                "```",
                "code [[Target]] example",
                "```",
                "inline `[[Target]]` too.");
        String rewritten = renameService.rewrite(content, "Target", "Renamed");

        assertThat(rewritten).contains("Real link [[Renamed]] here.");
        assertThat(rewritten).contains("code [[Target]] example"); // fenced: untouched
        assertThat(rewritten).contains("inline `[[Target]]` too."); // inline code: untouched
    }
}
