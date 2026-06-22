package com.obsidianclone.index;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.obsidianclone.watch.FileChangeEvent;

/** Routes filesystem-change events into incremental index updates. */
@Component
public class IndexEventListener {

    private final IndexService index;

    public IndexEventListener(IndexService index) {
        this.index = index;
    }

    @EventListener
    public void onFileChange(FileChangeEvent event) {
        switch (event.type()) {
            case CREATED, MODIFIED -> index.onFileChanged(event.path());
            case DELETED -> index.onFileDeleted(event.path());
        }
    }
}
