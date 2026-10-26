package com.hirehub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Manages physical storage, retrieval, and deletion of candidate resume files.
 */
@Service
public class FileStorageService {

    private final Path storageDirectory;

    public FileStorageService(@Value("${hirehub.upload.dir:uploads/resumes}") String uploadDir) {
        this.storageDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize resume storage directory: " + uploadDir, e);
        }
    }

    /**
     * Stores an uploaded PDF resume and returns the sanitized unique filename.
     */
    public String storeResume(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        String rawFilename = file.getOriginalFilename();
        String cleanOriginalName = StringUtils.cleanPath(rawFilename != null ? rawFilename : "resume.pdf");

        // Validate PDF extension
        if (!cleanOriginalName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF documents (.pdf) are allowed");
        }

        // Validate MIME type if present
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new IllegalArgumentException("Invalid content type. Expected application/pdf");
        }

        // Unique filename prevents overwrites and collisions
        String safeBasename = cleanOriginalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String uniqueFilename = UUID.randomUUID() + "_" + safeBasename;

        Path targetLocation = this.storageDirectory.resolve(uniqueFilename).normalize();

        // Path traversal guard
        if (!targetLocation.startsWith(this.storageDirectory)) {
            throw new IllegalArgumentException("Invalid file path sequence");
        }

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resume file: " + cleanOriginalName, e);
        }
    }

    /**
     * Loads a stored resume as a Spring Resource for streaming/previewing.
     */
    public Resource loadResumeAsResource(String fileName) {
        try {
            Path filePath = this.storageDirectory.resolve(fileName).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                throw new IllegalArgumentException("Invalid file path");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("Resume file not found: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Resume file not found: " + fileName, e);
        }
    }

    /**
     * Deletes a stored resume file from disk.
     */
    public void deleteResume(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path filePath = this.storageDirectory.resolve(fileName).normalize();
            if (filePath.startsWith(this.storageDirectory)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
            // Best effort deletion
        }
    }
}
