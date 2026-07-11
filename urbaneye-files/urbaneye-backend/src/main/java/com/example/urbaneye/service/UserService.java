package com.example.urbaneye.service;

import com.example.urbaneye.model.User;
import com.example.urbaneye.model.Community;
import com.example.urbaneye.repository.UserRepository;
import com.example.urbaneye.repository.CommunityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

    public UserService(UserRepository userRepository, CommunityRepository communityRepository) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
    }

    public void registerOrUpdateUser(String uid, String email, String name) {
        User user = userRepository.findById(uid);
        if (user == null) {
            user = new User();
            user.setId(uid);
            user.setEmail(email);
            user.setName(name);
            user.setCommunityIds(new ArrayList<>());
            user.setBannedFromCommunityIds(new ArrayList<>());
            userRepository.save(user);
        } else {
            user.setEmail(email);
            if (name != null && !name.isBlank()) {
                user.setName(name);
            }
            userRepository.save(user);
        }
    }

    public User getUserById(String uid) {
        return userRepository.findById(uid);
    }

    public void joinCommunity(String uid, String communityId) {
        User user = userRepository.findById(uid);
        if (user != null) {
            if (user.getCommunityIds() == null) {
                user.setCommunityIds(new ArrayList<>());
            }
            if (!user.getCommunityIds().contains(communityId)) {
                user.getCommunityIds().add(communityId);
                userRepository.save(user);

                Community community = communityRepository.findById(communityId);
                if (community != null) {
                    community.setMemberCount(community.getMemberCount() + 1);
                    communityRepository.save(community);
                }
            }
        }
    }

    public void leaveCommunity(String uid, String communityId) {
        User user = userRepository.findById(uid);
        if (user != null && user.getCommunityIds() != null && user.getCommunityIds().contains(communityId)) {
            user.getCommunityIds().remove(communityId);
            userRepository.save(user);

            Community community = communityRepository.findById(communityId);
            if (community != null) {
                community.setMemberCount(Math.max(0, community.getMemberCount() - 1));
                communityRepository.save(community);
            }
        }
    }
    
    public void banUserFromCommunity(String targetUid, String communityId) {
        User user = userRepository.findById(targetUid);
        if (user != null) {
            if (user.getBannedFromCommunityIds() == null) {
                user.setBannedFromCommunityIds(new ArrayList<>());
            }
            if (!user.getBannedFromCommunityIds().contains(communityId)) {
                user.getBannedFromCommunityIds().add(communityId);
                userRepository.save(user);
            }
        }
    }
}
