package com.obsidianclone.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollingWatchStrategyTest {

    @TempDir
    Path root;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private PollingWatchStrategy strategy;

    @AfterEach
    void tearDown() {
        if (strategy != null) {
            strategy.stop();
        }
    }

    private void startWatching() {
        strategy = new PollingWatchStrategy(80);
        strategy.start(root, (type, path) -> events.add(type + ":" + root.relativize(path)));
    }

    @Test
    void detectsCreateModifyDelete() throws IOException {
        Path file = root.resolve("note.md");
        startWatching();

        Files.writeString(file, "v1");
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("CREATED:note.md"));

        Files.writeString(file, "v1 plus more");
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("MODIFIED:note.md"));

        Files.delete(file);
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("DELETED:note.md"));
    }

    @Test
    void detectsChangesInSubdirectories() throws IOException {
        Files.createDirectories(root.resolve("sub"));
        startWatching();

        Files.writeString(root.resolve("sub/deep.md"), "x");
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("CREATED:sub/deep.md"));
    }

    @Test
    void existingFilesAreBaselinedNotReportedAsCreated() throws IOException {
        Files.writeString(root.resolve("pre-existing.md"), "already here");
        startWatching();

        // Give a couple of poll cycles to pass.
        Files.writeString(root.resolve("trigger.md"), "t");
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("CREATED:trigger.md"));

        assertThat(events).doesNotContain("CREATED:pre-existing.md");
    }

    @Test
    void ignoresDotDirectories() throws IOException {
        Files.createDirectories(root.resolve(".git"));
        startWatching();

        Files.writeString(root.resolve(".git/config"), "data");
        Files.writeString(root.resolve("visible.md"), "v");
        await().atMost(Duration.ofSeconds(3)).until(() -> events.contains("CREATED:visible.md"));

        assertThat(events).noneMatch(e -> e.contains(".git"));
    }
}
