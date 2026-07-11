package com.example.urbaneye.model;

import lombok.Data;
import jakarta.persistence.*;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Entity
@Table(name = "issues")
public class Issue {
    @Id
    private String id;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String category;

    @Column(columnDefinition = "MEDIUMTEXT") // Base64-encoded image from camera
    private String imageUrl;
    
    private double latitude;
    private double longitude;
    
    @Column(columnDefinition = "TEXT")
    private String address; // Populated by reverse geocoding via Nominatim
    
    private String communityId;
    private String reportedBy; // Reference to Firebase UID
    
    @Enumerated(EnumType.STRING)
    private IssueStatus status = IssueStatus.NEW;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
    
    private int upvoteCount = 0;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "issue_upvotes", joinColumns = @JoinColumn(name = "issue_id"))
    @Column(name = "user_id")
    private List<String> upvotedBy = new ArrayList<>();
    
    @JsonProperty("isUpdate")
    private boolean isUpdate = false;

    private String severity;
    private String tags;

    private String parentIssueId;
    private Boolean isDuplicate = false;
    private Double duplicateConfidence;
    private String duplicateReviewStatus = "NONE"; // NONE, PENDING, CONFIRMED, REJECTED
}

