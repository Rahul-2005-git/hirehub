package com.hirehub.controller;

import com.hirehub.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for retrieving and viewing resume PDF files.
 */
@RestController
@RequestMapping("/api/resumes")
@Tag(name = "Resumes", description = "Endpoints for downloading and viewing candidate resumes")
public class ResumeController {

    private final FileStorageService fileStorageService;

    public ResumeController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{fileName}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download or stream candidate PDF resume")
    public ResponseEntity<Resource> getResume(@PathVariable String fileName) {
        Resource resource = fileStorageService.loadResumeAsResource(fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
