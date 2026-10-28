package com.pydlove.workcove.system.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping
public class ReleaseController {

    private final ObjectMapper objectMapper;
    private final Path downloadsDirectory;

    public ReleaseController(
            ObjectMapper objectMapper,
            @Value("${workcove.releases.downloads-dir}") String downloadsDirectory) {
        this.objectMapper = objectMapper;
        this.downloadsDirectory =
                Path.of(downloadsDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/api/release/latest")
    public Map<String, Object> latest() throws IOException {
        Path manifest = manifestPath();
        if (!Files.isRegularFile(manifest)) {
            return Map.of("version", "", "releaseCode", "");
        }
        JsonNode value = objectMapper.readTree(manifest.toFile());
        return objectMapper.convertValue(value, Map.class);
    }

    @GetMapping("/downloads/{filename}")
    public ResponseEntity<Resource> download(
            @PathVariable String filename) throws IOException {
        if (!filename.matches("[A-Za-z0-9._-]+")) {
            return ResponseEntity.notFound().build();
        }
        Path path = downloadsDirectory.resolve(filename).normalize();
        if (!path.startsWith(downloadsDirectory) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(new FileSystemResource(path));
    }

    private Path manifestPath() {
        return downloadsDirectory.resolve("latest.json").normalize();
    }
}
