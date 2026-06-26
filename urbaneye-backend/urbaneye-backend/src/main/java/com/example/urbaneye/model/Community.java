package com.example.urbaneye.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "communities")
public class Community {
    @Id
    private String id;
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;
    private String location;
    private int memberCount = 0;
    private String adminId; // The unique user ID of the assigned admin for this community
}
