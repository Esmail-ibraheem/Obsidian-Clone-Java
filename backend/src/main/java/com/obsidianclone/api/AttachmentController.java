package com.obsidianclone.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import com.obsidianclone.vault.VaultNotFoundException;
import com.obsidianclone.vault.VaultPathResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Streams binary attachments (images, etc.) from the vault for {@code ![[...]]}
 * embeds. The path after {@code /api/attachments/} is the vault-relative file
 * path; it is resolved through {@link VaultPathResolver} so traversal is blocked.
 */
@RestController
public class AttachmentController {

    private static final String PREFIX = "/api/attachments/";

    private final VaultPathResolver resolver;

    public AttachmentController(VaultPathResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/api/attachments/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String encoded = uri.substring(uri.indexOf(PREFIX) + PREFIX.length());
        // Percent-decode with PATH semantics: '+' is a literal plus in a path,
        // not a space (URLDecoder's form-decoding would corrupt filenames).
        String relative = UriUtils.decode(encoded, StandardCharsets.UTF_8);

        Path file = resolver.resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new VaultNotFoundException("No such attachment: " + relative);
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                mediaType = MediaType.parseMediaType(probed);
            }
        } catch (IOException | InvalidMediaTypeException ignored) {
            // OS mime map missing or malformed -> fall back to octet-stream
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .body(new FileSystemResource(file));
    }
}
