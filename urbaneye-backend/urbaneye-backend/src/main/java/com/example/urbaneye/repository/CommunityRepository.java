package com.example.urbaneye.repository;

import com.example.urbaneye.model.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

interface JpaCommunityRepository extends JpaRepository<Community, String> {}

@Repository
public class CommunityRepository {
    private final JpaCommunityRepository jpaCommunityRepository;

    public CommunityRepository(JpaCommunityRepository jpaCommunityRepository) {
        this.jpaCommunityRepository = jpaCommunityRepository;
    }

    public Community findById(String id) {
        return jpaCommunityRepository.findById(id).orElse(null);
    }

    public List<Community> findAll() {
        return jpaCommunityRepository.findAll();
    }

    public Community save(Community community) {
        if (community.getId() == null || community.getId().isBlank()) {
            community.setId(UUID.randomUUID().toString());
        }
        return jpaCommunityRepository.save(community);
    }

    /** Used by migration: preserves the existing Firestore document ID. */
    public Community saveWithId(Community community) {
        return jpaCommunityRepository.save(community);
    }

    public void deleteById(String id) {
        jpaCommunityRepository.deleteById(id);
    }
}