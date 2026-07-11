package com.example.urbaneye.service;

import com.example.urbaneye.model.Community;
import com.example.urbaneye.repository.CommunityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommunityService {
    private final CommunityRepository communityRepository;

    public Community createCommunity(Community community) {
        return communityRepository.save(community);
    }
    public CommunityService(CommunityRepository communityRepository) {
        this.communityRepository = communityRepository;
    }

    public List<Community> getAllCommunities() {
        return communityRepository.findAll();
    }

    public Community getCommunityById(String id) {
        return communityRepository.findById(id);
    }
}
