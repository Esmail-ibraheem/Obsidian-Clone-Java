package com.obsidianclone.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    void extractsPlainWikilink() {
        ParsedNote n = parser.parse("a.md", "See [[Other Note]] here.");
        assertThat(n.links()).hasSize(1);
        LinkRef link = n.links().get(0);
        assertThat(link.rawTarget()).isEqualTo("Other Note");
        assertThat(link.displayAlias()).isNull();
        assertThat(link.embed()).isFalse();
        assertThat(link.anchorType()).isEqualTo(LinkRef.AnchorType.NONE);
        assertThat(link.line()).isEqualTo(1);
    }

    @Test
    void extractsAliasAnchorAndEmbed() {
        ParsedNote n = parser.parse("a.md", String.join("\n",
                "[[Note|Alias]]",
                "[[Note#Section]]",
                "[[Note^block1]]",
                "![[Embedded Note]]",
                "![[picture.png]]"));

        assertThat(n.links()).hasSize(5);

        assertThat(n.links().get(0).displayAlias()).isEqualTo("Alias");

        assertThat(n.links().get(1).anchorType()).isEqualTo(LinkRef.AnchorType.HEADING);
        assertThat(n.links().get(1).anchor()).isEqualTo("Section");
        assertThat(n.links().get(1).rawTarget()).isEqualTo("Note");

        assertThat(n.links().get(2).anchorType()).isEqualTo(LinkRef.AnchorType.BLOCK);
        assertThat(n.links().get(2).anchor()).isEqualTo("block1");

        assertThat(n.links().get(3).embed()).isTrue();
        assertThat(n.links().get(3).rawTarget()).isEqualTo("Embedded Note");

        assertThat(n.links().get(4).embed()).isTrue();
        assertThat(n.links().get(4).rawTarget()).isEqualTo("picture.png");
    }

    @Test
    void extractsMultipleLinksOnOneLineWithLineNumber() {
        ParsedNote n = parser.parse("a.md", "intro\nlink to [[A]] and [[B]] together");
        assertThat(n.links()).extracting(LinkRef::rawTarget).containsExactly("A", "B");
        assertThat(n.links()).allSatisfy(l -> assertThat(l.line()).isEqualTo(2));
    }

    @Test
    void extractsTagsButNotNumericOnly() {
        ParsedNote n = parser.parse("a.md", "Topics: #java #nested/tag #project-x #123 end");
        assertThat(n.tags()).containsExactlyInAnyOrder("java", "nested/tag", "project-x");
    }

    @Test
    void ignoresTagsAndLinksInsideCode() {
        ParsedNote n = parser.parse("a.md", String.join("\n",
                "Inline `#nottag and [[NotLink]]` here.",
                "```",
                "#alsonot [[AlsoNotLink]]",
                "```",
                "real #tag and [[RealLink]]"));

        assertThat(n.tags()).containsExactly("tag");
        assertThat(n.links()).extracting(LinkRef::rawTarget).containsExactly("RealLink");
    }

    @Test
    void doesNotTreatHeadingMarkerAsTagButExtractsHeadings() {
        ParsedNote n = parser.parse("a.md", String.join("\n",
                "# Title",
                "## Sub Heading",
                "content #realtag"));

        assertThat(n.headings()).containsExactly("Title", "Sub Heading");
        assertThat(n.tags()).containsExactly("realtag");
    }

    @Test
    void doesNotReadAnchorAsTag() {
        ParsedNote n = parser.parse("a.md", "Jump to [[Doc#Overview]] now");
        assertThat(n.tags()).isEmpty();
        assertThat(n.links().get(0).anchor()).isEqualTo("Overview");
    }
}
