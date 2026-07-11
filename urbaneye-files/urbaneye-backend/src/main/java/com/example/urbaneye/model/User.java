package com.example.urbaneye.model;

import lombok.Data;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id; // Firebase UID
    private String email;
    private String name;
    private String role = "USER"; // defaults to USER
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_communities", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "community_id")
    private List<String> communityIds = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_banned_communities", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "community_id")
    private List<String> bannedFromCommunityIds = new ArrayList<>();
}
