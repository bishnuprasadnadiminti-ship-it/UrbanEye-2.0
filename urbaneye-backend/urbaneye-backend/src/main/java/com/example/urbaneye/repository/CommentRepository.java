package com.example.urbaneye.repository;

import com.example.urbaneye.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

interface JpaCommentRepository extends JpaRepository<Comment, String> {
    List<Comment> findByIssueId(String issueId);
}

@Repository
public class CommentRepository {
    private final JpaCommentRepository jpaCommentRepository;

    public CommentRepository(JpaCommentRepository jpaCommentRepository) {
        this.jpaCommentRepository = jpaCommentRepository;
    }

    public void save(Comment comment) {
        if (comment.getId() == null || comment.getId().isEmpty()) {
            comment.setId(UUID.randomUUID().toString());
        }
        jpaCommentRepository.save(comment);
    }

    /** Used by migration: preserves the existing Firestore document ID. */
    public void saveWithId(Comment comment) {
        jpaCommentRepository.save(comment);
    }

    public Comment findById(String id) {
        return jpaCommentRepository.findById(id).orElse(null);
    }

    public List<Comment> findByIssueId(String issueId) {
        return jpaCommentRepository.findByIssueId(issueId);
    }
}
