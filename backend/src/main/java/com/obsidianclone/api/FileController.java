package com.obsidianclone.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.obsidianclone.api.dto.CreateRequest;
import com.obsidianclone.api.dto.RenameRequest;
import com.obsidianclone.api.dto.RenameResponse;
import com.obsidianclone.vault.FileNode;
import com.obsidianclone.vault.NoteContent;
import com.obsidianclone.vault.VaultService;

/** REST endpoints for the vault file tree and note CRUD. */
@RestController
@RequestMapping("/api")
public class FileController {

    private final VaultService vault;
    private final RenameService renameService;

    public FileController(VaultService vault, RenameService renameService) {
        this.vault = vault;
        this.renameService = renameService;
    }

    @GetMapping("/vault/tree")
    public List<FileNode> tree() {
        return vault.tree();
    }

    @GetMapping("/files")
    public NoteContent read(@RequestParam String path) {
        return vault.read(path);
    }

    @PutMapping(value = "/files", consumes = MediaType.TEXT_PLAIN_VALUE)
    public NoteContent write(@RequestParam String path,
                             @RequestParam(required = false) Long baseMtime,
                             @RequestBody(required = false) String content) {
        return vault.write(path, content == null ? "" : content, baseMtime);
    }

    @PostMapping("/files")
    public ResponseEntity<NoteContent> create(@RequestBody CreateRequest request) {
        if ("folder".equalsIgnoreCase(request.type())) {
            vault.createFolder(request.path());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        NoteContent created = vault.createFile(request.path(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/files")
    public ResponseEntity<Void> delete(@RequestParam String path) {
        vault.delete(path);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/rename")
    public RenameResponse rename(@RequestBody RenameRequest request) {
        return renameService.rename(request.from(), request.to(), request.updateLinks());
    }
}
