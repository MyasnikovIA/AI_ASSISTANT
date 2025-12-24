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
import java.util.function.Consumer;

public class OllamaService {
    private final HttpClient httpClient;
    private String ollamaHost;
    private String modelName;
    private Consumer<String> streamingCallback;
    private boolean useChatMode = true; // По умолчанию используем режим чата
    private boolean useCache = true; // Использовать кэш по умолчанию

    public OllamaService(String ollamaHost, String modelName) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        this.ollamaHost = ollamaHost;
        this.modelName = modelName;
    }

    // Установка callback для потокового вывода
    public void setStreamingCallback(Consumer<String> callback) {
        this.streamingCallback = callback;
    }

    // Метод для чата с потоковой передачей и поддержкой кэша
    public String sendChatRequest(List<JSONObject> messages, boolean stream) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", modelName);
            requestJson.put("messages", new JSONArray(messages));
            requestJson.put("stream", stream);
            requestJson.put("cache", useCache); // Добавляем параметр кэша

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

    // Метод для генерации (без истории) с потоковой передачей и поддержкой кэша
    public String sendGenerateRequest(String prompt, boolean stream) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", modelName);
            requestJson.put("prompt", prompt);
            requestJson.put("stream", stream);
            requestJson.put("cache", useCache); // Добавляем параметр кэша

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

    // Универсальный метод для отправки запроса с выбором режима
    public String sendRequest(String prompt, List<JSONObject> messages, boolean stream) {
        if (useChatMode && messages != null && !messages.isEmpty()) {
            return sendChatRequest(messages, stream);
        } else {
            return sendGenerateRequest(prompt, stream);
        }
    }

    // Метод для очистки кэша модели
    public boolean clearModelCache() {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/cache/clear"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                System.out.println("Кэш модели " + modelName + " успешно очищен");
                return true;
            } else {
                System.err.println("Ошибка очистки кэша: " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Ошибка очистки кэша модели: " + e.getMessage());
            return false;
        }
    }

    // Метод для получения информации о кэше
    public JSONObject getCacheInfo() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/cache/info"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            } else {
                System.err.println("Ошибка получения информации о кэше: " + response.statusCode());
                return null;
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения информации о кэше: " + e.getMessage());
            return null;
        }
    }

    // Новый метод для генерации с потоковой передачей по умолчанию
    public String sendGenerateRequestWithStream(String prompt) {
        return sendGenerateRequest(prompt, true);
    }

    // Новый метод для чата с потоковой передачей по умолчанию
    public String sendChatRequestWithStream(List<JSONObject> messages) {
        return sendChatRequest(messages, true);
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
                                    } else if (json.has("message") && json.getJSONObject("message").has("content")) {
                                        content = json.getJSONObject("message").getString("content");
                                    }

                                    if (content != null && !content.isEmpty()) {
                                        // Вызываем callback, если он установлен
                                        if (streamingCallback != null) {
                                            streamingCallback.accept(content);
                                        } else {
                                            System.out.print(content);
                                        }
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

    // Новый метод для загрузки модели
    public boolean pullModel(String modelName) {
        try {
            System.out.println("Загрузка модели: " + modelName);

            JSONObject requestJson = new JSONObject();
            requestJson.put("name", modelName);
            requestJson.put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/pull"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                System.out.println("Модель " + modelName + " успешно загружена");
                return true;
            } else {
                System.err.println("Ошибка загрузки модели: " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Ошибка загрузки модели: " + e.getMessage());
            return false;
        }
    }

    // Новый метод для удаления модели
    public boolean deleteModel(String modelName) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("name", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/delete"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                System.out.println("Модель " + modelName + " успешно удалена");
                return true;
            } else {
                System.err.println("Ошибка удаления модели: " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Ошибка удаления модели: " + e.getMessage());
            return false;
        }
    }

    // Новый метод для получения информации о модели
    public JSONObject getModelInfo(String modelName) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("name", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/show"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            } else {
                System.err.println("Ошибка получения информации о модели: " + response.statusCode());
                return null;
            }

        } catch (Exception e) {
            System.err.println("Ошибка получения информации о модели: " + e.getMessage());
            return null;
        }
    }

    // Новый метод для копирования модели
    public boolean copyModel(String sourceModel, String targetModel) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("source", sourceModel);
            requestJson.put("destination", targetModel);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/copy"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                System.out.println("Модель успешно скопирована: " + sourceModel + " -> " + targetModel);
                return true;
            } else {
                System.err.println("Ошибка копирования модели: " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Ошибка копирования модели: " + e.getMessage());
            return false;
        }
    }

    // Новые методы для управления режимами работы
    public void setUseChatMode(boolean useChatMode) {
        this.useChatMode = useChatMode;
        System.out.println("Режим работы изменен на: " + (useChatMode ? "ЧАТ" : "ГЕНЕРАЦИЯ"));
    }

    public boolean isUseChatMode() {
        return useChatMode;
    }

    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
        System.out.println("Использование кэша: " + (useCache ? "ВКЛ" : "ВЫКЛ"));
    }

    public boolean isUseCache() {
        return useCache;
    }

    public void setModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModel() {
        return modelName;
    }
}