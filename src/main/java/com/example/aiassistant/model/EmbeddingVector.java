package com.example.aiassistant.model;

import java.util.Arrays;

public class EmbeddingVector {
    private String documentId;
    private double[] vector;
    private double norm;

    public EmbeddingVector(String documentId, double[] vector) {
        this.documentId = documentId;
        this.vector = vector;
        this.norm = calculateNorm(vector);
    }

    private double calculateNorm(double[] vector) {
        double sum = 0.0;
        for (double v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    // Геттеры
    public String getDocumentId() { return documentId; }
    public double[] getVector() { return vector; }
    public double getNorm() { return norm; }

    // Косинусная схожесть
    public double cosineSimilarity(EmbeddingVector other) {
        if (this.vector.length != other.vector.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        for (int i = 0; i < vector.length; i++) {
            dotProduct += this.vector[i] * other.vector[i];
        }

        return dotProduct / (this.norm * other.norm);
    }

    @Override
    public String toString() {
        return "EmbeddingVector{" +
                "documentId='" + documentId + '\'' +
                ", dimension=" + vector.length +
                ", norm=" + norm +
                '}';
    }
}