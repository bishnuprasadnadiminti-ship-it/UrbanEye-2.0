package com.example.urbaneye.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalImageService {

    @Value("${local.image.dir:uploads/complaints}")
    private String uploadDirSetting;

    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        // Target path is relative to current working directory (project root)
        Path uploadPath = Paths.get(uploadDirSetting);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String storedFilename = UUID.randomUUID().toString() + extension;
        Path targetLocation = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetLocation);

        // Return as a URL path the browser can fetch: /uploads/complaints/uuid.jpg
        // Spring will serve /uploads/** from the uploads/ directory on disk (see WebMvcConfig).
        return "/" + uploadDirSetting.replace("\\", "/") + "/" + storedFilename;
    }
}
