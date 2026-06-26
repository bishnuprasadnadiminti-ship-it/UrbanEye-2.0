package com.example.urbaneye.repository;

import com.example.urbaneye.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

interface JpaIssueRepository extends JpaRepository<Issue, String> {
    List<Issue> findByCommunityId(String communityId);

    @Query(value = "SELECT * FROM issues i WHERE " +
           "ST_Distance_Sphere(POINT(i.longitude, i.latitude), POINT(:longitude, :latitude)) <= :radius " +
           "AND i.parent_issue_id IS NULL AND i.status != 'RESOLVED' AND i.id != :excludeId", nativeQuery = true)
    List<Issue> findNearbyNonDuplicateIssues(@Param("latitude") double latitude, @Param("longitude") double longitude, @Param("radius") double radius, @Param("excludeId") String excludeId);

    /** Community-wide fallback when GPS is inaccurate (e.g. indoors). Returns up to 30 recent non-duplicate issues in the same community/category. */
    @Query(value = "SELECT * FROM issues i WHERE i.community_id = :communityId " +
           "AND i.parent_issue_id IS NULL AND i.status != 'RESOLVED' AND i.id != :excludeId " +
           "ORDER BY i.created_at DESC LIMIT 30", nativeQuery = true)
    List<Issue> findRecentByCommunity(@Param("communityId") String communityId, @Param("excludeId") String excludeId);

    List<Issue> findByParentIssueId(String parentIssueId);
}

@Repository
public class IssueRepository {
    private final JpaIssueRepository jpaIssueRepository;

    public IssueRepository(JpaIssueRepository jpaIssueRepository) {
        this.jpaIssueRepository = jpaIssueRepository;
    }

    public void save(Issue issue) {
        if (issue.getId() == null || issue.getId().isEmpty()) {
            issue.setId(UUID.randomUUID().toString());
        }
        jpaIssueRepository.save(issue);
    }

    /** Used by migration: preserves the existing Firestore document ID. */
    public void saveWithId(Issue issue) {
        jpaIssueRepository.save(issue);
    }

    public Issue findById(String id) {
        return jpaIssueRepository.findById(id).orElse(null);
    }

    public List<Issue> findByCommunityId(String communityId) {
        return jpaIssueRepository.findByCommunityId(communityId);
    }

    public List<Issue> findNearbyNonDuplicateIssues(double latitude, double longitude, double radius, String excludeId) {
        return jpaIssueRepository.findNearbyNonDuplicateIssues(latitude, longitude, radius, excludeId);
    }

    public List<Issue> findRecentByCommunity(String communityId, String excludeId) {
        return jpaIssueRepository.findRecentByCommunity(communityId, excludeId);
    }

    public List<Issue> findByParentIssueId(String parentIssueId) {
        return jpaIssueRepository.findByParentIssueId(parentIssueId);
    }

    public void delete(String id) {
        jpaIssueRepository.deleteById(id);
    }
}
