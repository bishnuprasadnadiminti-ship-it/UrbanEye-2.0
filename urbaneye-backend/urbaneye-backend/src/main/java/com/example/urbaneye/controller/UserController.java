package com.example.urbaneye.controller;

import com.example.urbaneye.dto.RegisterUserRequest;
import com.example.urbaneye.model.User;
import com.example.urbaneye.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@AuthenticationPrincipal User authenticatedUser,
                                          @RequestBody(required = false) RegisterUserRequest request) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized - Valid Firebase token required"));
        }

        String name = request != null ? request.getName() : null;
        userService.registerOrUpdateUser(authenticatedUser.getId(), authenticatedUser.getEmail(), name);

        User savedUser = userService.getUserById(authenticatedUser.getId());
        return ResponseEntity.ok(savedUser);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized - Valid Firebase token required"));
        }

        User savedUser = userService.getUserById(authenticatedUser.getId());
        if (savedUser == null) {
            userService.registerOrUpdateUser(authenticatedUser.getId(), authenticatedUser.getEmail(), authenticatedUser.getName());
            savedUser = userService.getUserById(authenticatedUser.getId());
        }

        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/join/{communityId}")
    public ResponseEntity<?> joinCommunity(@AuthenticationPrincipal User authenticatedUser,
                                           @PathVariable String communityId) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized - Valid Firebase token required"));
        }

        userService.joinCommunity(authenticatedUser.getId(), communityId);
        return ResponseEntity.ok(Map.of("message", "Joined community successfully"));
    }

    @PostMapping("/leave/{communityId}")
    public ResponseEntity<?> leaveCommunity(@AuthenticationPrincipal User authenticatedUser,
                                            @PathVariable String communityId) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized - Valid Firebase token required"));
        }

        userService.leaveCommunity(authenticatedUser.getId(), communityId);
        return ResponseEntity.ok(Map.of("message", "Left community successfully"));
    }
}
