package com.example.urbaneye.controller;

import com.example.urbaneye.model.IssueStatus;
import com.example.urbaneye.model.User;
import com.example.urbaneye.dto.IssueRequest;
import com.example.urbaneye.security.CommunitySecurity;
import com.example.urbaneye.service.IssueService;
import com.example.urbaneye.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IssueService issueService;
    private final UserService userService;
    private final CommunitySecurity communitySecurity;

    public AdminController(IssueService issueService, UserService userService, CommunitySecurity communitySecurity) {
        this.issueService = issueService;
        this.userService = userService;
        this.communitySecurity = communitySecurity;
    }

    @PutMapping("/issues/{issueId}/status")
    public ResponseEntity<?> updateIssueStatus(@AuthenticationPrincipal User user, 
                                               @PathVariable String issueId, 
                                               @RequestParam IssueStatus status) {
        var issue = issueService.getIssueById(issueId);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Strict runtime validation
        if (!communitySecurity.isAdminOf(user, issue.getCommunityId())) {
            var community = communitySecurity.getCommunityRepository().findById(issue.getCommunityId());
            return ResponseEntity.status(403).body(Map.of(
                "error", "Access Denied: Not an admin for this community",
                "userId", user != null ? user.getId() : "null",
                "userRole", user != null ? user.getRole() : "null",
                "communityId", issue.getCommunityId(),
                "communityAdminId", community != null ? community.getAdminId() : "null"
            ));
        }

        issueService.updateStatus(issueId, status);
        return ResponseEntity.ok(Map.of("message", "Status updated successfully"));
    }

    @PostMapping("/ban/{targetUserId}/community/{communityId}")
    public ResponseEntity<?> banUserFromCommunity(@AuthenticationPrincipal User user,
                                                  @PathVariable String targetUserId,
                                                  @PathVariable String communityId) {
        if (!communitySecurity.isAdminOf(user, communityId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied: Not an admin for this community"));
        }
        
        userService.banUserFromCommunity(targetUserId, communityId);
        return ResponseEntity.ok(Map.of("message", "User banned from community"));
    }

    @DeleteMapping("/issues/{issueId}")
    public ResponseEntity<?> deleteIssue(@AuthenticationPrincipal User user, @PathVariable String issueId) {
        var issue = issueService.getIssueById(issueId);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        if (!communitySecurity.isAdminOf(user, issue.getCommunityId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        issueService.deleteIssue(issueId);
        return ResponseEntity.ok(Map.of("message", "Issue deleted successfully"));
    }

    @PutMapping("/issues/{issueId}/mark-update")
    public ResponseEntity<?> markAsUpdate(@AuthenticationPrincipal User user, @PathVariable String issueId) {
        var issue = issueService.getIssueById(issueId);
        if (issue == null) return ResponseEntity.notFound().build();
        if (!communitySecurity.isAdminOf(user, issue.getCommunityId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        issueService.markAsUpdate(issueId);
        return ResponseEntity.ok(Map.of("message", "Issue marked as update"));
    }

    @PostMapping("/community/{communityId}/updates")
    public ResponseEntity<?> postAdminUpdate(@AuthenticationPrincipal User user,
                                             @PathVariable String communityId,
                                             @Valid @RequestBody IssueRequest request) {
        if (!communitySecurity.isAdminOf(user, communityId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        
        var issue = issueService.reportIssue(
            user.getId(), 
            request.getTitle() != null ? request.getTitle() : "Official Update", 
            request.getDescription(), 
            "UPDATE", 
            request.getImageUrl(), 
            request.getLatitude() != null ? request.getLatitude() : 0.0, 
            request.getLongitude() != null ? request.getLongitude() : 0.0, 
            communityId
        );
        issueService.markAsUpdate(issue.getId());
        
        return ResponseEntity.ok(Map.of("message", "Update posted successfully"));
    }

    @PutMapping("/issues/{issueId}/duplicate-action")
    public ResponseEntity<?> manageDuplicate(@AuthenticationPrincipal User user,
                                             @PathVariable String issueId,
                                             @RequestParam String action) {
        var issue = issueService.getIssueById(issueId);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        if (!communitySecurity.isAdminOf(user, issue.getCommunityId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }

        switch (action.toUpperCase()) {
            case "CONFIRM":
                issueService.confirmDuplicate(issueId);
                break;
            case "REJECT":
                issueService.rejectDuplicate(issueId);
                break;
            case "CONVERT":
                issueService.convertToSeparate(issueId);
                break;
            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid duplicate action"));
        }

        return ResponseEntity.ok(Map.of("message", "Duplicate action applied successfully"));
    }
}
