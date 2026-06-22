package com.obsidianclone.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.obsidianclone.api.dto.RenameResponse;
import com.obsidianclone.vault.FileNode;
import com.obsidianclone.vault.NoteContent;
import com.obsidianclone.vault.VaultConflictException;
import com.obsidianclone.vault.VaultException;
import com.obsidianclone.vault.VaultNotFoundException;
import com.obsidianclone.vault.VaultService;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    VaultService vault;

    @MockBean
    RenameService renameService;

    @Test
    void treeReturnsNodes() throws Exception {
        when(vault.tree()).thenReturn(List.of(FileNode.file("a.md", "a.md")));
        mvc.perform(get("/api/vault/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("a.md"))
                .andExpect(jsonPath("$[0].type").value("FILE"));
    }

    @Test
    void readReturnsContentAndMtime() throws Exception {
        when(vault.read("a.md")).thenReturn(new NoteContent("a.md", "# A", 123L, 3L));
        mvc.perform(get("/api/files").param("path", "a.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# A"))
                .andExpect(jsonPath("$.mtime").value(123));
    }

    @Test
    void readMissingReturns404() throws Exception {
        when(vault.read("x.md")).thenThrow(new VaultNotFoundException("nope"));
        mvc.perform(get("/api/files").param("path", "x.md"))
                .andExpect(status().isNotFound());
    }

    @Test
    void traversalReturns400() throws Exception {
        when(vault.read("../escape")).thenThrow(new VaultException("escape"));
        mvc.perform(get("/api/files").param("path", "../escape"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writeReturnsNewContent() throws Exception {
        when(vault.write(eq("a.md"), any(), isNull()))
                .thenReturn(new NoteContent("a.md", "new", 200L, 3L));
        mvc.perform(put("/api/files").param("path", "a.md")
                        .contentType(MediaType.TEXT_PLAIN).content("new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mtime").value(200));
    }

    @Test
    void writeConflictReturns409WithCurrentContent() throws Exception {
        when(vault.write(eq("a.md"), any(), eq(100L)))
                .thenThrow(new VaultConflictException("changed",
                        new NoteContent("a.md", "server-version", 150L, 14L)));
        mvc.perform(put("/api/files").param("path", "a.md").param("baseMtime", "100")
                        .contentType(MediaType.TEXT_PLAIN).content("mine"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentContent").value("server-version"))
                .andExpect(jsonPath("$.currentMtime").value(150));
    }

    @Test
    void createFileReturns201() throws Exception {
        when(vault.createFile("n.md", "c")).thenReturn(new NoteContent("n.md", "c", 1L, 1L));
        mvc.perform(post("/api/files").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"n.md\",\"type\":\"file\",\"content\":\"c\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("n.md"));
    }

    @Test
    void createFolderReturns201() throws Exception {
        mvc.perform(post("/api/files").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"Projects\",\"type\":\"folder\"}"))
                .andExpect(status().isCreated());
        verify(vault).createFolder("Projects");
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/files").param("path", "a.md"))
                .andExpect(status().isNoContent());
        verify(vault).delete("a.md");
    }

    @Test
    void writeWithEmptyBodyAndNoContentTypeSucceeds() throws Exception {
        when(vault.write(eq("a.md"), eq(""), isNull()))
                .thenReturn(new NoteContent("a.md", "", 10L, 0L));
        // No body, no Content-Type header (the "clear a note" shape).
        mvc.perform(put("/api/files").param("path", "a.md"))
                .andExpect(status().isOk());
    }

    @Test
    void nullJsonBodyReturns400() throws Exception {
        mvc.perform(post("/api/files").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renameReturnsUpdatedNotes() throws Exception {
        when(renameService.rename("o.md", "n.md", true))
                .thenReturn(new RenameResponse("o.md", "n.md", List.of("s.md")));
        mvc.perform(post("/api/files/rename").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"o.md\",\"to\":\"n.md\",\"updateLinks\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedNotes[0]").value("s.md"));
    }
}
