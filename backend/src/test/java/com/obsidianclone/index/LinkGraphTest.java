package com.obsidianclone.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LinkGraphTest {

    private final MarkdownParser parser = new MarkdownParser();

    private LinkGraph graphWith(String... pathThenContentPairs) {
        LinkGraph graph = new LinkGraph();
        for (int i = 0; i < pathThenContentPairs.length; i += 2) {
            String path = pathThenContentPairs[i];
            String content = pathThenContentPairs[i + 1];
            graph.put(parser.parse(path, content));
        }
        return graph;
    }

    @Test
    void resolvesByBasenameRegardlessOfFolder() {
        LinkGraph graph = graphWith(
                "Folder/Target.md", "# Target",
                "Source.md", "link to [[Target]]");
        assertThat(graph.resolve("Target", "Source.md")).contains("Folder/Target.md");
    }

    @Test
    void ambiguousBasenameResolvesToShortestPath() {
        // No root-level Note.md, so resolution must go through basename matching.
        LinkGraph graph = graphWith(
                "a/b/c/Note.md", "deep",
                "x/Note.md", "mid");
        assertThat(graph.resolve("Note", "other.md")).contains("x/Note.md");
    }

    @Test
    void unresolvedTargetReturnsEmpty() {
        LinkGraph graph = graphWith("Source.md", "link to [[Ghost]]");
        assertThat(graph.resolve("Ghost", "Source.md")).isEmpty();
    }

    @Test
    void pathLinkNamingAMissingFolderStaysUnresolved() {
        LinkGraph graph = graphWith("Folder/Target.md", "x");
        // A target that names a folder must not fall back to a basename match in a
        // different folder.
        assertThat(graph.resolve("bogus/Target", "a.md")).isEmpty();
        // A bare name still resolves by basename.
        assertThat(graph.resolve("Target", "a.md")).contains("Folder/Target.md");
    }

    @Test
    void resolvesExactRelativePathWithOrWithoutExtension() {
        LinkGraph graph = graphWith("Folder/Note.md", "x");
        assertThat(graph.resolve("Folder/Note", "a.md")).contains("Folder/Note.md");
        assertThat(graph.resolve("Folder/Note.md", "a.md")).contains("Folder/Note.md");
    }

    @Test
    void aliasAndAnchorDoNotChangeResolutionTarget() {
        LinkGraph graph = graphWith(
                "Target.md", "# Target\n## Section",
                "Source.md", "[[Target#Section|see section]]");
        // backlink should still attribute to Target.md
        List<BacklinkEntry> backlinks = graph.backlinks("Target.md");
        assertThat(backlinks).extracting(BacklinkEntry::sourcePath).containsExactly("Source.md");
    }

    @Test
    void backlinksIncludeLineAndSnippetAndEmbeds() {
        LinkGraph graph = graphWith(
                "Target.md", "# Target",
                "A.md", "first line\nrefers to [[Target]] here",
                "B.md", "embeds ![[Target]]");

        List<BacklinkEntry> backlinks = graph.backlinks("Target.md");
        assertThat(backlinks).extracting(BacklinkEntry::sourcePath).containsExactly("A.md", "B.md");
        BacklinkEntry a = backlinks.get(0);
        assertThat(a.line()).isEqualTo(2);
        assertThat(a.snippet()).contains("[[Target]]");
    }

    @Test
    void backlinksExcludeSelfReferences() {
        LinkGraph graph = graphWith("Self.md", "I link to [[Self]]");
        assertThat(graph.backlinks("Self.md")).isEmpty();
    }

    @Test
    void newlyAddedTargetResolvesPreviouslyDanglingLink() {
        LinkGraph graph = graphWith("Source.md", "see [[Later]]");
        assertThat(graph.backlinks("Later.md")).isEmpty();

        graph.put(parser.parse("Later.md", "# Later"));
        assertThat(graph.backlinks("Later.md")).extracting(BacklinkEntry::sourcePath)
                .containsExactly("Source.md");
    }

    @Test
    void removingTargetMakesLinkDangleAgain() {
        LinkGraph graph = graphWith(
                "Later.md", "# Later",
                "Source.md", "see [[Later]]");
        assertThat(graph.backlinks("Later.md")).hasSize(1);

        graph.remove("Later.md");
        assertThat(graph.resolve("Later", "Source.md")).isEmpty();
    }

    @Test
    void tagIndexAggregatesAcrossNotes() {
        LinkGraph graph = graphWith(
                "A.md", "#shared #onlya",
                "B.md", "#shared");
        assertThat(graph.tagIndex().get("shared")).containsExactlyInAnyOrder("A.md", "B.md");
        assertThat(graph.tagIndex().get("onlya")).containsExactly("A.md");
    }

    @Test
    void headingsExposedPerNote() {
        LinkGraph graph = graphWith("Doc.md", "# One\n## Two");
        assertThat(graph.headings("Doc.md")).containsExactly("One", "Two");
        assertThat(graph.headings("missing.md")).isEmpty();
    }
}
