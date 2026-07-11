package com.example.urbaneye.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class FirestoreTestController {

    @GetMapping("/firestore-test")
    public String testFirestore() {
        try {
            Firestore db = FirestoreClient.getFirestore();

            Map<String, Object> data = new HashMap<>();
            data.put("message", "Firestore is working");

            db.collection("debug")
                    .document("testDoc")
                    .set(data)
                    .get();

            return "✅ Firestore is connected and data written";

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Firestore NOT connected: " + e.getMessage();
        }
    }

    @PostMapping("/add-user")
    public String addUser(@RequestBody Map<String, Object> data) {
        try {
            Firestore db = FirestoreClient.getFirestore();

            db.collection("users")
                    .add(data)
                    .get();

            return "✅ User added successfully";

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error adding user: " + e.getMessage();
        }
    }
}