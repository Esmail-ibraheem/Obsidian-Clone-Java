package com.obsidianclone.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.obsidianclone.index.BacklinkEntry;
import com.obsidianclone.index.IndexService;

/** REST endpoints backed by the link index (backlinks, tags). */
@RestController
@RequestMapping("/api")
public class BacklinkController {

    private final IndexService index;

    public BacklinkController(IndexService index) {
        this.index = index;
    }

    @GetMapping("/backlinks")
    public List<BacklinkEntry> backlinks(@RequestParam String path) {
        return index.backlinks(path);
    }

    @GetMapping("/tags")
    public Map<String, List<String>> tags() {
        return index.tags();
    }
}
