package com.example.urbaneye.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${local.image.dir:uploads/complaints}")
    private String uploadDir;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply rate limits to all API endpoints
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * Serve uploaded complaint images as static resources.
     * Files saved under uploads/complaints/xxx.jpg are accessible at
     * http://localhost:8080/uploads/complaints/xxx.jpg
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the upload root (parent of uploadDir) as an absolute file: URI
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().getParent();
        String fileUri = uploadRoot.toUri().toString(); // e.g. file:///C:/…/uploads/
        // Expose /uploads/** → <cwd>/uploads/ on disk
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(fileUri);
    }
}
