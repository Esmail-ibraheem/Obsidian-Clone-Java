package com.obsidianclone.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.obsidianclone.index.BacklinkEntry;
import com.obsidianclone.index.IndexService;

@WebMvcTest(BacklinkController.class)
class BacklinkControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    IndexService index;

    @Test
    void backlinksReturnsEntries() throws Exception {
        when(index.backlinks("Target.md"))
                .thenReturn(List.of(new BacklinkEntry("Source.md", 2, "refers to [[Target]]")));
        mvc.perform(get("/api/backlinks").param("path", "Target.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourcePath").value("Source.md"))
                .andExpect(jsonPath("$[0].line").value(2))
                .andExpect(jsonPath("$[0].snippet").value("refers to [[Target]]"));
    }
}
