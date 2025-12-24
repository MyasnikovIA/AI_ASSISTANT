package com.example.aiassistant.model;

import org.json.JSONObject;

public class ChatMessage {
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    private Role role;
    private String content;
    private JSONObject metadata;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
        this.metadata = new JSONObject();
        this.metadata.put("timestamp", System.currentTimeMillis());
    }

    // Геттеры и сеттеры
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public JSONObject getMetadata() { return metadata; }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("role", role.name().toLowerCase());
        json.put("content", content);
        json.put("metadata", metadata);
        return json;
    }
}