package com.example.aiassistant.model;

import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class KnowledgeDocument {
    private String id;
    private String content;
    private String source;
    private LocalDateTime createdAt;
    private JSONObject metadata;
    private double[] embedding;

    public KnowledgeDocument(String content, String source) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.metadata = new JSONObject();
        this.metadata.put("source", source);
        this.metadata.put("created", createdAt.toString());
    }

    public KnowledgeDocument(String content, String source, JSONObject metadata) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.source = source;
        this.createdAt = LocalDateTime.now();
        this.metadata = metadata;
        if (!this.metadata.has("source")) {
            this.metadata.put("source", source);
        }
        if (!this.metadata.has("created")) {
            this.metadata.put("created", createdAt.toString());
        }
    }

    // Геттеры и сеттеры
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public JSONObject getMetadata() {
        return metadata;
    }

    public void setMetadata(JSONObject metadata) {
        this.metadata = metadata;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("content", content);
        json.put("source", source);
        json.put("createdAt", createdAt.toString());
        json.put("metadata", metadata);
        return json;
    }

    public static KnowledgeDocument fromJSON(JSONObject json) {
        try {
            String content = json.optString("content", "");
            String source = json.optString("source", "unknown");
            JSONObject metadata = json.optJSONObject("metadata");

            if (metadata == null) {
                metadata = new JSONObject();
            }

            KnowledgeDocument doc = new KnowledgeDocument(content, source, metadata);

            // Если есть ID в JSON, используем его
            if (json.has("id")) {
                doc.setId(json.getString("id"));
            }

            // Если есть createdAt в JSON, используем его
            if (json.has("createdAt")) {
                doc.setCreatedAt(LocalDateTime.parse(json.getString("createdAt")));
            }

            return doc;
        } catch (Exception e) {
            System.err.println("Ошибка создания KnowledgeDocument из JSON: " + e.getMessage());
            return new KnowledgeDocument("", "error");
        }
    }

    @Override
    public String toString() {
        return "KnowledgeDocument{" +
                "id='" + id + '\'' +
                ", content='" + (content.length() > 50 ? content.substring(0, 47) + "..." : content) + '\'' +
                ", source='" + source + '\'' +
                ", createdAt=" + createdAt +
                ", metadata=" + metadata +
                ", embedding=" + (embedding != null ? "[" + embedding.length + " dimensions]" : "null") +
                '}';
    }
}