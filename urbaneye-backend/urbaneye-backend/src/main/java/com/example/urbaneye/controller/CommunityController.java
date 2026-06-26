package com.example.urbaneye.controller;

import com.example.urbaneye.model.Community;
import com.example.urbaneye.service.CommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {
    
    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }
    @PostMapping
    public ResponseEntity<Community> createCommunity(@RequestBody Community community) {
        Community savedCommunity = communityService.createCommunity(community);
        return ResponseEntity.ok(savedCommunity);
    }
    @GetMapping
    public ResponseEntity<List<Community>> getAllCommunities() {
        return ResponseEntity.ok(communityService.getAllCommunities());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Community> getCommunityById(@PathVariable String id) {
        Community comm = communityService.getCommunityById(id);
        if (comm == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(comm);
    }
}
