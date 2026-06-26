package com.example.urbaneye.service;

import com.example.urbaneye.model.Comment;
import com.example.urbaneye.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CommentService {
    
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public Comment addComment(String issueId, String userId, String content) {
        Comment comment = new Comment();
        comment.setIssueId(issueId);
        comment.setUserId(userId);
        comment.setText(content);
        comment.setCreatedAt(new Date());
        
        commentRepository.save(comment);
        return comment;
    }

    public List<Comment> getCommentsForIssue(String issueId) {
        return commentRepository.findByIssueId(issueId);
    }
}
