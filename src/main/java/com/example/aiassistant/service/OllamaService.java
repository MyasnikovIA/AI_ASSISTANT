package com.example.aiassistant.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OllamaService {
    private final HttpClient httpClient;
    private String ollamaHost;
    private String modelName;

    public OllamaService(String ollamaHost, String modelName) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        this.ollamaHost = ollamaHost;
        this.modelName = modelName;
    }

    // Метод для чата с потоковой передачей
    public String sendChatRequest(List<JSONObject> messages, boolean stream) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", modelName);
            requestJson.put("messages", new JSONArray(messages));
            requestJson.put("stream", stream);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            if (stream) {
                return sendStreamingRequest(request, true);
            } else {
                return sendBlockingRequest(request);
            }

        } catch (Exception e) {
            System.err.println("Ошибка отправки запроса к OLLAMA: " + e.getMessage());
            return "Извините, произошла ошибка при обращении к модели: " + e.getMessage();
        }
    }

    // Метод для генерации (без истории) с потоковой передачей
    public String sendGenerateRequest(String prompt, boolean stream) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", modelName);
            requestJson.put("prompt", prompt);
            requestJson.put("stream", stream);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            if (stream) {
                return sendStreamingRequest(request, false);
            } else {
                return sendBlockingRequest(request);
            }

        } catch (Exception e) {
            System.err.println("Ошибка отправки запроса к OLLAMA: " + e.getMessage());
            return "Извините, произошла ошибка при обращении к модели: " + e.getMessage();
        }
    }

    // Новый метод для генерации с потоковой передачей по умолчанию
    public String sendGenerateRequestWithStream(String prompt) {
        return sendGenerateRequest(prompt, true);
    }

    private String sendBlockingRequest(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 200) {
            JSONObject responseJson = new JSONObject(response.body());

            if (responseJson.has("message")) {
                return responseJson.getJSONObject("message").getString("content");
            } else if (responseJson.has("response")) {
                return responseJson.getString("response");
            } else {
                return "Некорректный ответ от OLLAMA";
            }
        } else {
            throw new RuntimeException("HTTP ошибка: " + response.statusCode());
        }
    }

    private String sendStreamingRequest(HttpRequest request, boolean isChat) throws Exception {
        StringBuilder fullResponse = new StringBuilder();

        CompletableFuture<Void> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        response.body().forEach(line -> {
                            if (!line.trim().isEmpty()) {
                                try {
                                    JSONObject json = new JSONObject(line);
                                    String content = null;

                                    if (isChat && json.has("message")) {
                                        content = json.getJSONObject("message").getString("content");
                                    } else if (json.has("response")) {
                                        content = json.getString("response");
                                    }

                                    if (content != null && !content.isEmpty()) {
                                        System.out.print(content);
                                        fullResponse.append(content);
                                    }

                                } catch (Exception e) {
                                    // Пропускаем ошибки парсинга для потоковых данных
                                }
                            }
                        });
                    } else {
                        System.err.println("HTTP ошибка: " + response.statusCode());
                    }
                });

        try {
            // Ждем завершения с таймаутом
            future.get(120, TimeUnit.SECONDS); // 2 минуты таймаут
        } catch (TimeoutException e) {
            System.err.println("Таймаут при получении потокового ответа");
            future.cancel(true);
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Ошибка при получении потокового ответа: " + e.getMessage());
        }

        return fullResponse.toString();
    }

    // Получение списка доступных моделей
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/tags"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                JSONObject responseJson = new JSONObject(response.body());
                JSONArray modelsArray = responseJson.getJSONArray("models");

                for (int i = 0; i < modelsArray.length(); i++) {
                    JSONObject model = modelsArray.getJSONObject(i);
                    models.add(model.getString("name"));
                }
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения списка моделей: " + e.getMessage());
        }

        return models;
    }

    public void setModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModel() {
        return modelName;
    }
}