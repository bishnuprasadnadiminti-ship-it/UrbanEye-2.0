package com.example.urbaneye.controller;

import com.example.urbaneye.dto.IssueRequest;
import com.example.urbaneye.dto.CommentRequest;
import com.example.urbaneye.model.Issue;
import com.example.urbaneye.model.User;
import com.example.urbaneye.model.Comment;
import com.example.urbaneye.service.IssueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issues")
public class IssueController {
    
    private final IssueService issueService;
    private final com.example.urbaneye.service.LocalImageService localImageService;
    private final com.example.urbaneye.service.GeminiVisionService geminiVisionService;
    private final com.example.urbaneye.service.DuplicateDetectionService duplicateDetectionService;

    public IssueController(IssueService issueService, 
                           com.example.urbaneye.service.LocalImageService localImageService, 
                           com.example.urbaneye.service.GeminiVisionService geminiVisionService,
                           com.example.urbaneye.service.DuplicateDetectionService duplicateDetectionService) {
        this.issueService = issueService;
        this.localImageService = localImageService;
        this.geminiVisionService = geminiVisionService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    @PostMapping("/with-image")
    @PreAuthorize("@communitySecurity.canInteract(principal, #communityId)")
    public ResponseEntity<?> reportIssueWithImage(@AuthenticationPrincipal User user,
                                                  @RequestParam("imageFile") org.springframework.web.multipart.MultipartFile imageFile,
                                                  @RequestParam(value = "title", required = false) String title,
                                                  @RequestParam(value = "category", required = false) String category,
                                                  @RequestParam("latitude") double latitude,
                                                  @RequestParam("longitude") double longitude,
                                                  @RequestParam("communityId") String communityId,
                                                  @RequestParam(value = "userDescription", required = false) String userDescription,
                                                  @RequestParam(value = "skipAi", required = false, defaultValue = "false") boolean skipAi,
                                                  @RequestParam(value = "bypassDuplicateCheck", required = false, defaultValue = "false") boolean bypassDuplicateCheck,
                                                  @RequestParam(value = "forceDuplicateOf", required = false) String forceDuplicateOf) {
        try {
            // 1. Validate image size (< 2MB)
            if (imageFile.getSize() > 2 * 1024 * 1024) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "Image file exceeds maximum size of 2MB"
                ));
            }

            // 2. Save locally
            String localImagePath = localImageService.store(imageFile);

            String finalDescription;
            String severity;
            String tags;

            if (skipAi) {
                System.out.println("[IssueController] Skipping AI analysis, using manual fallback inputs.");
                finalDescription = userDescription != null ? userDescription.trim() : "";
                severity = "MEDIUM";
                tags = "manual";
            } else {
                // 3. Call Gemini
                String mimeType = imageFile.getContentType();
                if (mimeType == null) {
                    mimeType = "image/jpeg";
                }
                com.example.urbaneye.service.GeminiVisionService.AiResult aiResult = 
                        geminiVisionService.analyzeImage(imageFile.getBytes(), mimeType);

                // 4. Combine descriptions
                finalDescription = aiResult.getDescription();
                if (userDescription != null && !userDescription.trim().isEmpty()) {
                    finalDescription = finalDescription + "\n\nUser Notes:\n" + userDescription.trim();
                }

                severity = aiResult.getSeverity();
                tags = String.join(",", aiResult.getTags());

                if (title == null || title.trim().isEmpty()) {
                    title = aiResult.getTitle() != null ? aiResult.getTitle() : "Auto-generated Title";
                }
                if (category == null || category.trim().isEmpty()) {
                    category = aiResult.getCategory() != null ? aiResult.getCategory() : "General";
                }
            }

            // 5. Save Issue
            Issue issue = issueService.reportIssueWithImage(
                    user.getId(),
                    title,
                    finalDescription,
                    category,
                    localImagePath,
                    latitude,
                    longitude,
                    communityId,
                    severity,
                    tags
            );

            // 5a. Duplicate check flow
            if (forceDuplicateOf != null && !forceDuplicateOf.isEmpty()) {
                issueService.linkAsDuplicate(issue.getId(), forceDuplicateOf, 100.0);
                return ResponseEntity.ok(Map.of(
                    "issue", issue,
                    "duplicateFound", true,
                    "alreadyLinked", true,
                    "fallback", false
                ));
            }

            if (!bypassDuplicateCheck) {
                // Stage 1: Geo search within 500m (excludes the just-saved issue)
                List<Issue> candidates = issueService.getNearbyNonDuplicateIssues(latitude, longitude, 500.0, issue.getId());

                // Stage 2: GPS often drifts badly indoors — fall back to last 30 community issues
                boolean usedGeoFallback = false;
                if (candidates == null || candidates.isEmpty()) {
                    candidates = issueService.getRecentByCommunity(communityId, issue.getId());
                    usedGeoFallback = true;
                    System.out.println("[DuplicateCheck] GPS returned 0 nearby → using community-wide fallback (" + (candidates != null ? candidates.size() : 0) + " candidates)");
                } else {
                    System.out.println("[DuplicateCheck] Geo found " + candidates.size() + " nearby issue(s) within 500m");
                }

                if (candidates != null && !candidates.isEmpty()) {
                    var dupResult = duplicateDetectionService.checkForDuplicate(finalDescription, latitude, longitude, candidates);
                    System.out.println("[DuplicateCheck] AI result: duplicate=" + dupResult.isDuplicate() + " confidence=" + dupResult.getConfidence() + " via=" + (usedGeoFallback ? "community-fallback" : "geo") + " reason=" + dupResult.getReason());

                    if (dupResult.isDuplicate() && dupResult.getConfidence() >= 85) {
                        // Link the new issue as a duplicate of the matched one
                        issueService.linkAsDuplicate(issue.getId(), dupResult.getMatchedIssueId(), dupResult.getConfidence());

                        Issue master = issueService.getIssueById(dupResult.getMatchedIssueId());
                        return ResponseEntity.ok(Map.of(
                            "duplicateFound", true,
                            "confidence", dupResult.getConfidence(),
                            "issueId", issue.getId(),
                            "masterIssue", master,
                            "fallback", false
                        ));
                    }
                }
            }

            // 6. Return success
            return ResponseEntity.ok(Map.of(
                "issue", issue,
                "duplicateFound", false,
                "fallback", false
            ));
        } catch (com.example.urbaneye.exception.FreeTierExhaustedException e) {
            throw e; // Handled by GlobalExceptionHandler (returns 409 and fallback: true)
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to process issue with AI analysis",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping
    @PreAuthorize("@communitySecurity.canInteract(principal, #request.communityId)")
    public ResponseEntity<Issue> reportIssue(@AuthenticationPrincipal User user, 
                                             @Valid @RequestBody IssueRequest request) {
        Issue issue = issueService.reportIssue(
                user.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getCategory(),
                request.getImageUrl(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCommunityId()
        );
        return ResponseEntity.ok(issue);
    }

    @GetMapping("/community/{communityId}")
    @PreAuthorize("@communitySecurity.canInteract(principal, #communityId)")
    public ResponseEntity<List<Issue>> getCommunityFeed(@AuthenticationPrincipal User user, 
                                                        @PathVariable String communityId) {
        List<Issue> issues = issueService.getIssuesForCommunity(communityId);
        return ResponseEntity.ok(issues);
    }

    @PostMapping("/{id}/upvote")
    public ResponseEntity<?> toggleUpvote(@AuthenticationPrincipal User user, @PathVariable String id) {
        issueService.toggleUpvote(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Upvote toggled"));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal User user, 
                                        @PathVariable String id, 
                                        @Valid @RequestBody CommentRequest request) {
        String userName = request.getUserName() != null ? request.getUserName() : "Citizen";
        issueService.addComment(id, user.getId(), userName, request.getText());
        return ResponseEntity.ok(Map.of("message", "Comment added"));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable String id) {
        return ResponseEntity.ok(issueService.getComments(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Issue> getIssueById(@PathVariable String id) {
        Issue issue = issueService.getIssueById(id);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(issue);
    }

    @PostMapping("/{id}/confirm-support")
    public ResponseEntity<?> confirmSupport(@AuthenticationPrincipal User user, @PathVariable String id, @RequestParam String masterId) {
        issueService.toggleUpvote(masterId, user.getId());
        return ResponseEntity.ok(Map.of("message", "Registered support on existing complaint successfully"));
    }

    @PostMapping("/{id}/convert-to-separate")
    public ResponseEntity<?> convertToSeparate(@PathVariable String id) {
        issueService.convertToSeparate(id);
        return ResponseEntity.ok(Map.of("message", "Converted complaint to separate successfully"));
    }
}
