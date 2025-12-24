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
    public String getId() { return id; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public JSONObject getMetadata() { return metadata; }
    public double[] getEmbedding() { return embedding; }
    public void setEmbedding(double[] embedding) { this.embedding = embedding; }

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
                // Внутреннее поле, поэтому нужно рефлексия или сеттер
                // Для простоты создаем новый объект
                doc = new KnowledgeDocument(content, source, metadata);
                try {
                    // Используем reflection для установки ID
                    java.lang.reflect.Field idField = KnowledgeDocument.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(doc, json.getString("id"));
                } catch (Exception e) {
                    System.err.println("Не удалось установить ID из JSON: " + e.getMessage());
                }
            }

            return doc;
        } catch (Exception e) {
            System.err.println("Ошибка создания KnowledgeDocument из JSON: " + e.getMessage());
            return new KnowledgeDocument("", "error");
        }
    }
}