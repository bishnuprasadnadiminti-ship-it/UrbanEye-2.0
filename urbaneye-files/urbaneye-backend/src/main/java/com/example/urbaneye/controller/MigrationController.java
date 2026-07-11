package com.example.urbaneye.controller;

import com.example.urbaneye.service.MigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * One-time migration endpoint.
 *
 * After the backend is restarted with MySQL configured, call this ONCE:
 *   POST http://localhost:8080/api/public/migrate-from-firestore
 *
 * It reads every document from Firestore (users, communities, issues, comments)
 * and writes them into MySQL.  Already-migrated records are skipped so the
 * endpoint is safe to call multiple times.
 */
@RestController
@RequestMapping("/api/public")
public class MigrationController {

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/migrate-from-firestore")
    public ResponseEntity<Map<String, Object>> migrate() {
        try {
            Map<String, Object> result = migrationService.migrateData();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "Migration failed",
                            "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ));
        }
    }
}
