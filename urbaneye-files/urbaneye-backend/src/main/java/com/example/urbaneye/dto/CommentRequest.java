package com.example.urbaneye.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentRequest {
    @NotBlank(message = "Comment text is required")
    private String text;
    private String userName; // Client can supply or grab from token config

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}
