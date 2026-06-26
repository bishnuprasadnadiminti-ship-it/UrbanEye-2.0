package com.example.urbaneye.service;

import com.example.urbaneye.model.Issue;
import com.example.urbaneye.model.IssueStatus;
import com.example.urbaneye.model.Comment;
import com.example.urbaneye.repository.IssueRepository;
import com.example.urbaneye.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final GeocodingService geocodingService;
    private final CommentRepository commentRepository;

    public IssueService(IssueRepository issueRepository, GeocodingService geocodingService, CommentRepository commentRepository) {
        this.issueRepository = issueRepository;
        this.geocodingService = geocodingService;
        this.commentRepository = commentRepository;
    }

    public Issue reportIssue(String userId, String title, String description, String category, String imageUrl,
                             double latitude, double longitude, String communityId) {
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setCategory(category);
        issue.setImageUrl(imageUrl); // Link to Firebase Storage obj
        issue.setLatitude(latitude);
        issue.setLongitude(longitude);
        issue.setCommunityId(communityId);
        issue.setReportedBy(userId);
        issue.setStatus(IssueStatus.NEW);
        issue.setCreatedAt(new Date());
        issue.setUpdatedAt(new Date());

        // Process Geo coordinates into human-readable address on backend
        String address = geocodingService.resolveAddress(latitude, longitude);
        issue.setAddress(address);

        issueRepository.save(issue);
        return issue;
    }

    public Issue reportIssueWithImage(String userId, String title, String description, String category, String imageUrl,
                                      double latitude, double longitude, String communityId, String severity, String tags) {
        Issue issue = new Issue();
        // Fallback checks or ID creation
        issue.setId(java.util.UUID.randomUUID().toString());
        issue.setTitle(title != null && !title.isEmpty() ? title : "Auto-generated Title");
        issue.setDescription(description);
        issue.setCategory(category != null && !category.isEmpty() ? category : "General");
        issue.setImageUrl(imageUrl);
        issue.setLatitude(latitude);
        issue.setLongitude(longitude);
        issue.setCommunityId(communityId);
        issue.setReportedBy(userId);
        issue.setStatus(IssueStatus.NEW);
        issue.setCreatedAt(new Date());
        issue.setUpdatedAt(new Date());
        issue.setSeverity(severity);
        issue.setTags(tags);

        // Process Geo coordinates into human-readable address on backend
        String address = geocodingService.resolveAddress(latitude, longitude);
        issue.setAddress(address);

        issueRepository.save(issue);
        return issue;
    }


    public List<Issue> getIssuesForCommunity(String communityId) {
        return issueRepository.findByCommunityId(communityId);
    }

    public Issue getIssueById(String id) {
        return issueRepository.findById(id);
    }

    public void updateStatus(String issueId, IssueStatus newStatus) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            issue.setStatus(newStatus);
            issue.setUpdatedAt(new Date());
            issueRepository.save(issue);

            // Sync status to child duplicates
            List<Issue> duplicates = issueRepository.findByParentIssueId(issueId);
            if (duplicates != null) {
                for (Issue dup : duplicates) {
                    dup.setStatus(newStatus);
                    dup.setUpdatedAt(new Date());
                    issueRepository.save(dup);
                }
            }
        }
    }

    public Issue linkAsDuplicate(String newIssueId, String parentIssueId, double confidence) {
        Issue issue = issueRepository.findById(newIssueId);
        Issue parent = issueRepository.findById(parentIssueId);
        if (issue != null && parent != null) {
            issue.setParentIssueId(parentIssueId);
            issue.setIsDuplicate(true);
            issue.setDuplicateConfidence(confidence);
            issue.setDuplicateReviewStatus("PENDING");
            issueRepository.save(issue);

            // Add the reporter of the duplicate to the parent's upvotedBy list if not present, and increment parent's upvote count
            String userId = issue.getReportedBy();
            if (userId != null) {
                if (parent.getUpvotedBy() == null) {
                    parent.setUpvotedBy(new java.util.ArrayList<>());
                }
                if (!parent.getUpvotedBy().contains(userId)) {
                    parent.getUpvotedBy().add(userId);
                    parent.setUpvoteCount(parent.getUpvoteCount() + 1);
                    issueRepository.save(parent);
                }
            }
        }
        return issue;
    }

    public void confirmDuplicate(String issueId) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            issue.setIsDuplicate(true);
            issue.setDuplicateReviewStatus("CONFIRMED");
            issue.setUpdatedAt(new Date());
            issueRepository.save(issue);
        }
    }

    public void rejectDuplicate(String issueId) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            // Keep link but flag as not duplicate
            issue.setIsDuplicate(false);
            issue.setDuplicateReviewStatus("REJECTED");
            issue.setParentIssueId(null);
            issue.setUpdatedAt(new Date());
            issueRepository.save(issue);
        }
    }

    public void convertToSeparate(String issueId) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            issue.setParentIssueId(null);
            issue.setIsDuplicate(false);
            issue.setDuplicateReviewStatus("NONE");
            issue.setUpdatedAt(new Date());
            issueRepository.save(issue);
        }
    }

    public void deleteIssue(String issueId) {
        issueRepository.delete(issueId);
    }

    public void markAsUpdate(String issueId) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            issue.setUpdate(true);
            issue.setUpdatedAt(new Date());
            issueRepository.save(issue);
        }
    }

    public void toggleUpvote(String issueId, String userId) {
        Issue issue = issueRepository.findById(issueId);
        if (issue != null) {
            if (issue.getUpvotedBy() == null) {
                issue.setUpvotedBy(new java.util.ArrayList<>());
            }
            if (issue.getUpvotedBy().contains(userId)) {
                issue.getUpvotedBy().remove(userId);
                issue.setUpvoteCount(Math.max(0, issue.getUpvoteCount() - 1));
            } else {
                issue.getUpvotedBy().add(userId);
                issue.setUpvoteCount(issue.getUpvoteCount() + 1);
            }
            issueRepository.save(issue);
        }
    }

    public void addComment(String issueId, String userId, String userName, String text) {
        Comment comment = new Comment();
        comment.setIssueId(issueId);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setText(text);
        comment.setCreatedAt(new Date());
        commentRepository.save(comment);
    }

    public List<Comment> getComments(String issueId) {
        return commentRepository.findByIssueId(issueId);
    }

    public List<Issue> getNearbyNonDuplicateIssues(double latitude, double longitude, double radius, String excludeId) {
        return issueRepository.findNearbyNonDuplicateIssues(latitude, longitude, radius, excludeId);
    }

    public List<Issue> getRecentByCommunity(String communityId, String excludeId) {
        return issueRepository.findRecentByCommunity(communityId, excludeId);
    }
}
