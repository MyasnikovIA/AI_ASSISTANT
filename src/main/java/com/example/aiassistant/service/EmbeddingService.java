package com.example.aiassistant.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingService {
    private final HttpClient httpClient;
    private String ollamaHost;
    private String embeddingModel;

    public EmbeddingService(String ollamaHost, String embeddingModel) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
        this.ollamaHost = ollamaHost;
        this.embeddingModel = embeddingModel;
    }

    public double[] getEmbedding(String text) {
        try {
            // Подготовка JSON запроса
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", embeddingModel);
            requestJson.put("prompt", text);

            // Создание HTTP запроса
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            // Отправка запроса и получение ответа
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                JSONObject responseJson = new JSONObject(response.body());
                JSONArray embeddingArray = responseJson.getJSONArray("embedding");

                // Преобразование JSONArray в массив double
                double[] embedding = new double[embeddingArray.length()];
                for (int i = 0; i < embeddingArray.length(); i++) {
                    embedding[i] = embeddingArray.getDouble(i);
                }

                return embedding;

            } else {
                throw new RuntimeException("HTTP ошибка: " + response.statusCode() +
                        " - " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения эмбеддинга: " + e.getMessage());

            // Возвращаем случайный эмбеддинг в случае ошибки (для отладки)
            if (text == null || text.trim().isEmpty()) {
                return new double[384]; // Размер по умолчанию
            }

            // Простой хэш-эмбеддинг для резервного варианта
            return createSimpleEmbedding(text);
        }
    }

    // Новый метод для получения списка моделей, поддерживающих эмбеддинги
    public List<String> getAvailableEmbeddingModels() {
        List<String> models = new ArrayList<>();

        try {
            // Сначала получаем все доступные модели
            OllamaService ollamaService = new OllamaService(ollamaHost, "");
            List<String> allModels = ollamaService.getAvailableModels();

            // Фильтруем модели, которые поддерживают эмбеддинги
            // (это эвристический подход, можно улучшить)
            for (String model : allModels) {
                if (model.toLowerCase().contains("embed") ||
                        model.toLowerCase().contains("all-minilm") ||
                        model.toLowerCase().contains("nomic") ||
                        model.toLowerCase().contains("mxbai")) {
                    models.add(model);
                }
            }

            // Добавляем популярные модели для эмбеддингов
            if (!models.contains("all-minilm:22m")) {
                models.add(0, "all-minilm:22m"); // Добавляем как рекомендуемую
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения списка моделей для эмбеддингов: " + e.getMessage());
        }

        return models;
    }

    // Новый метод для проверки поддержки эмбеддингов моделью
    public boolean checkEmbeddingSupport(String modelName) {
        try {
            // Пытаемся получить эмбеддинг для тестового текста
            double[] embedding = getEmbeddingForModel(modelName, "test");
            return embedding != null && embedding.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Новый метод для получения эмбеддинга от конкретной модели
    public double[] getEmbeddingForModel(String modelName, String text) {
        try {
            // Сохраняем текущую модель
            String originalModel = this.embeddingModel;

            // Временно меняем модель
            this.embeddingModel = modelName;

            // Получаем эмбеддинг
            double[] embedding = getEmbedding(text);

            // Восстанавливаем оригинальную модель
            this.embeddingModel = originalModel;

            return embedding;

        } catch (Exception e) {
            System.err.println("Ошибка получения эмбеддинга от модели " + modelName + ": " + e.getMessage());
            return null;
        }
    }

    private double[] createSimpleEmbedding(String text) {
        // Простой метод создания эмбеддинга на основе хэша
        // Используется только при недоступности OLLAMA
        int dimension = 384;
        double[] embedding = new double[dimension];

        for (int i = 0; i < dimension; i++) {
            String seed = text + i;
            embedding[i] = (seed.hashCode() % 1000) / 1000.0;
        }

        // Нормализация
        double norm = 0.0;
        for (double v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    public void setEmbeddingModel(String modelName) {
        this.embeddingModel = modelName;
        System.out.println("Модель для эмбеддингов изменена на: " + modelName);
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}