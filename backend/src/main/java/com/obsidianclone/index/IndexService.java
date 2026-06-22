package com.obsidianclone.index;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.obsidianclone.vault.FileNode;
import com.obsidianclone.vault.VaultException;
import com.obsidianclone.vault.VaultService;

import jakarta.annotation.PostConstruct;

/**
 * Maintains the link/tag index for the whole vault. Builds the full index on
 * startup by walking the vault, then keeps it current via incremental updates
 * driven by filesystem-change events (wired in the watch module). Reads and
 * writes are guarded by a read/write lock since the watcher updates the graph
 * concurrently with API reads.
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private final VaultService vault;
    private final MarkdownParser parser;
    private final LinkGraph graph = new LinkGraph();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public IndexService(VaultService vault, MarkdownParser parser) {
        this.vault = vault;
        this.parser = parser;
    }

    @PostConstruct
    public void build() {
        lock.writeLock().lock();
        try {
            graph.clear();
            int count = 0;
            for (String path : markdownPaths()) {
                indexQuietly(path);
                count++;
            }
            log.info("Built link index for {} markdown notes", count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Re-parse and index a single note (create or modify). */
    public void onFileChanged(String path) {
        if (!isMarkdown(path)) {
            return;
        }
        lock.writeLock().lock();
        try {
            indexQuietly(path);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void onFileDeleted(String path) {
        lock.writeLock().lock();
        try {
            // path may be a deleted directory (native watcher reports one event for
            // the dir, not per contained note), so remove everything beneath it too.
            graph.removeRecursively(path);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void onFileRenamed(String from, String to) {
        lock.writeLock().lock();
        try {
            graph.remove(from);
            if (isMarkdown(to)) {
                indexQuietly(to);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<BacklinkEntry> backlinks(String path) {
        lock.readLock().lock();
        try {
            return graph.backlinks(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<String> resolve(String target, String fromPath) {
        lock.readLock().lock();
        try {
            return graph.resolve(target, fromPath);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, List<String>> tags() {
        lock.readLock().lock();
        try {
            return graph.tagIndex();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> headings(String path) {
        lock.readLock().lock();
        try {
            return graph.headings(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void indexQuietly(String path) {
        try {
            String content = vault.read(path).content();
            graph.put(parser.parse(path, content));
        } catch (VaultException e) {
            // File vanished between listing and reading, or unreadable: drop it.
            graph.remove(path);
        }
    }

    private boolean isMarkdown(String path) {
        return path != null && path.endsWith(".md");
    }

    private List<String> markdownPaths() {
        List<String> paths = new java.util.ArrayList<>();
        collect(vault.tree(), paths);
        return paths;
    }

    private void collect(List<FileNode> nodes, List<String> out) {
        for (FileNode node : nodes) {
            if (node.type() == FileNode.Type.FOLDER) {
                if (node.children() != null) {
                    collect(node.children(), out);
                }
            } else if (node.path().endsWith(".md")) {
                out.add(node.path());
            }
        }
    }
}
