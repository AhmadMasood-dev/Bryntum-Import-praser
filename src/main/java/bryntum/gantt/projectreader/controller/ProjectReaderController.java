package bryntum.gantt.projectreader.controller;

import bryntum.gantt.projectreader.service.ProjectReaderService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ProjectReaderController {
    private final ProjectReaderService projectReaderService;

    public ProjectReaderController(ProjectReaderService projectReaderService) {
        this.projectReaderService = projectReaderService;
    }

    @PostMapping(
        value = "/parse",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> parse(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "indent", required = false) Integer indent,
        @RequestParam(value = "dateFormat", required = false) String dateFormat,
        @RequestParam(value = "dateTimeFormat", required = false) String dateTimeFormat,
        @RequestParam(value = "timeFormat", required = false) String timeFormat
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("error", "file is required"));
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName == null ? "upload" : Paths.get(originalName).getFileName().toString();
        String suffix = "";
        int lastDot = safeName.lastIndexOf('.');
        if (lastDot >= 0) {
            suffix = safeName.substring(lastDot);
        }
        if (suffix.isEmpty()) {
            suffix = ".tmp";
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("projectreader-", suffix);
            file.transferTo(tempFile.toFile());

            String json = projectReaderService.buildJson(tempFile, indent, dateFormat, dateTimeFormat, timeFormat);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("error", "Failed to parse file: " + e.getMessage()));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }
}
