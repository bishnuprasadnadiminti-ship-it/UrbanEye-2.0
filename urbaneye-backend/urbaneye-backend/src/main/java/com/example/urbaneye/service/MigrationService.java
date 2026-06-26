package com.example.urbaneye.service;

import com.example.urbaneye.model.*;
import com.example.urbaneye.repository.*;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MigrationService {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;

    public MigrationService(UserRepository userRepository,
                            CommunityRepository communityRepository,
                            IssueRepository issueRepository,
                            CommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.issueRepository = issueRepository;
        this.commentRepository = commentRepository;
    }

    public Map<String, Object> migrateData() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        int usersMigrated = migrateUsers(db);
        int communitiesMigrated = migrateCommunities(db);
        int issuesMigrated = migrateIssues(db);
        int commentsMigrated = migrateComments(db);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", usersMigrated);
        result.put("communities", communitiesMigrated);
        result.put("issues", issuesMigrated);
        result.put("comments", commentsMigrated);
        result.put("status", "Migration completed successfully");
        return result;
    }

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private int migrateUsers(Firestore db) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection("users").get().get().getDocuments();
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            try {
                // Skip if already exists in MySQL
                if (userRepository.findById(doc.getId()) != null) {
                    continue;
                }

                User user = new User();
                user.setId(doc.getId());
                user.setEmail(doc.getString("email"));
                user.setName(doc.getString("name"));

                String role = doc.getString("role");
                user.setRole(role != null ? role : "USER");

                List<String> communityIds = (List<String>) doc.get("communityIds");
                user.setCommunityIds(communityIds != null ? communityIds : new ArrayList<>());

                List<String> bannedIds = (List<String>) doc.get("bannedFromCommunityIds");
                user.setBannedFromCommunityIds(bannedIds != null ? bannedIds : new ArrayList<>());

                userRepository.save(user);
                count++;
            } catch (Exception e) {
                System.err.println("⚠️ Failed to migrate user " + doc.getId() + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Migrated " + count + " users");
        return count;
    }

    // -------------------------------------------------------------------------
    // Communities
    // -------------------------------------------------------------------------
    private int migrateCommunities(Firestore db) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection("communities").get().get().getDocuments();
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            try {
                // Skip if already exists in MySQL
                if (communityRepository.findById(doc.getId()) != null) {
                    continue;
                }

                Community community = new Community();
                community.setId(doc.getId());
                community.setName(doc.getString("name"));
                community.setDescription(doc.getString("description"));
                community.setCategory(doc.getString("category"));
                community.setLocation(doc.getString("location"));
                
                String adminId = doc.getString("adminId");
                if ("4HogK34wHcFhWOYeAUdt".equals(doc.getId())) {
                    adminId = "tqcNlT16IZPWhCjf1Q2l2AP3qNi2"; // Nagpur is assigned to Mangesh
                } else if ("pcMtIOhyjNZ2Xf4oM1IC3Isqez03".equals(adminId)) {
                    adminId = "pcMtlOhyjNZ2Xf4oM1lC3Isqez03";
                }
                community.setAdminId(adminId);

                Long memberCount = doc.getLong("memberCount");
                community.setMemberCount(memberCount != null ? memberCount.intValue() : 0);

                communityRepository.saveWithId(community);
                count++;
            } catch (Exception e) {
                System.err.println("⚠️ Failed to migrate community " + doc.getId() + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Migrated " + count + " communities");
        return count;
    }

    // -------------------------------------------------------------------------
    // Issues
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private int migrateIssues(Firestore db) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection("issues").get().get().getDocuments();
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            try {
                // Skip if already exists in MySQL
                if (issueRepository.findById(doc.getId()) != null) {
                    continue;
                }

                Issue issue = new Issue();
                issue.setId(doc.getId());
                issue.setTitle(doc.getString("title"));
                issue.setDescription(doc.getString("description"));
                issue.setCategory(doc.getString("category"));
                issue.setImageUrl(doc.getString("imageUrl"));
                issue.setCommunityId(doc.getString("communityId"));
                issue.setReportedBy(doc.getString("reportedBy"));
                issue.setAddress(doc.getString("address"));

                Double lat = doc.getDouble("latitude");
                issue.setLatitude(lat != null ? lat : 0.0);

                Double lng = doc.getDouble("longitude");
                issue.setLongitude(lng != null ? lng : 0.0);

                Long upvoteCount = doc.getLong("upvoteCount");
                issue.setUpvoteCount(upvoteCount != null ? upvoteCount.intValue() : 0);

                // Parse status safely — fall back to NEW if value is unrecognised
                String statusStr = doc.getString("status");
                issue.setStatus(parseIssueStatus(statusStr));

                // Timestamps
                com.google.cloud.Timestamp createdAt = doc.getTimestamp("createdAt");
                if (createdAt != null) {
                    issue.setCreatedAt(createdAt.toDate());
                }
                com.google.cloud.Timestamp updatedAt = doc.getTimestamp("updatedAt");
                if (updatedAt != null) {
                    issue.setUpdatedAt(updatedAt.toDate());
                }

                List<String> upvotedBy = (List<String>) doc.get("upvotedBy");
                issue.setUpvotedBy(upvotedBy != null ? upvotedBy : new ArrayList<>());

                Boolean isUpdate = doc.getBoolean("isUpdate");
                issue.setUpdate(isUpdate != null && isUpdate);

                issueRepository.saveWithId(issue);
                count++;
            } catch (Exception e) {
                System.err.println("⚠️ Failed to migrate issue " + doc.getId() + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Migrated " + count + " issues");
        return count;
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------
    private int migrateComments(Firestore db) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection("comments").get().get().getDocuments();
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            try {
                // Skip if already exists in MySQL
                if (commentRepository.findById(doc.getId()) != null) {
                    continue;
                }

                Comment comment = new Comment();
                comment.setId(doc.getId());
                comment.setIssueId(doc.getString("issueId"));
                comment.setUserId(doc.getString("userId"));
                comment.setUserName(doc.getString("userName"));
                comment.setText(doc.getString("text"));

                com.google.cloud.Timestamp createdAt = doc.getTimestamp("createdAt");
                if (createdAt != null) {
                    comment.setCreatedAt(createdAt.toDate());
                }

                commentRepository.saveWithId(comment);
                count++;
            } catch (Exception e) {
                System.err.println("⚠️ Failed to migrate comment " + doc.getId() + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Migrated " + count + " comments");
        return count;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private IssueStatus parseIssueStatus(String value) {
        if (value == null) return IssueStatus.NEW;
        try {
            return IssueStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Unknown IssueStatus '" + value + "', defaulting to NEW");
            return IssueStatus.NEW;
        }
    }
}
