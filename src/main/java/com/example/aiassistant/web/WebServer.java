package com.example.aiassistant.web;

import com.example.aiassistant.service.*;
import com.example.aiassistant.model.ChatMessage;
import com.example.aiassistant.util.SpeakToText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private final int port;
    private final AssistantService assistantService;
    private final VectorDBService vectorDB;
    private final ExecutorService executorService;
    private final Map<String, List<EventSourceClient>> eventSources;
    private boolean running;

    public WebServer(int port, AssistantService assistantService, VectorDBService vectorDB) {
        this.port = port;
        this.assistantService = assistantService;
        this.vectorDB = vectorDB;
        this.executorService = Executors.newFixedThreadPool(10);
        this.eventSources = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() {
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));

            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Веб-сервер запущен на порту " + port);
            System.out.println("Откройте в браузере: http://localhost:" + port);
            System.out.println("Консольный интерфейс также доступен");
            System.out.println("-".repeat(50));

            running = true;

            while (running) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        handleAccept(serverChannel, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }

                    iter.remove();
                }
            }

        } catch (IOException e) {
            System.err.println("Ошибка запуска веб-сервера: " + e.getMessage());
        }
    }

    private void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                return;
            }

            buffer.flip();
            String request = StandardCharsets.UTF_8.decode(buffer).toString();

            // Обработка запроса в отдельном потоке
            executorService.submit(() -> {
                try {
                    handleRequest(clientChannel, request);
                } catch (IOException e) {
                    System.err.println("Ошибка обработки запроса: " + e.getMessage());
                }
            });

        } catch (IOException e) {
            try {
                clientChannel.close();
            } catch (IOException ex) {
                // Игнорируем ошибку закрытия
            }
        }
    }

    private void handleRequest(SocketChannel clientChannel, String request) throws IOException {
        String[] lines = request.split("\r\n");
        if (lines.length == 0) {
            sendError(clientChannel, 400, "Bad Request");
            return;
        }

        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) {
            sendError(clientChannel, 400, "Bad Request");
            return;
        }

        String method = requestLine[0];
        String path = requestLine[1];

        // Парсинг заголовков
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) break;

            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }

        // Парсинг тела запроса
        String body = "";
        boolean bodyStart = false;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                bodyStart = true;
                continue;
            }
            if (bodyStart) {
                body += lines[i] + "\n";
            }
        }
        body = body.trim();

        // Обработка маршрутов
        if (method.equals("GET")) {
            handleGetRequest(clientChannel, path, headers);
        } else if (method.equals("POST")) {
            handlePostRequest(clientChannel, path, headers, body);
        } else if (method.equals("OPTIONS")) {
            handleOptionsRequest(clientChannel);
        } else {
            sendError(clientChannel, 405, "Method Not Allowed");
        }
    }

    private void handleGetRequest(SocketChannel clientChannel, String path, Map<String, String> headers) throws IOException {
        // Обслуживание статических файлов
        if (path.equals("/") || path.equals("/index.html")) {
            serveFile(clientChannel, "index.html", "text/html");
        } else if (path.equals("/easyui/themes/default/easyui.css")) {
            serveFile(clientChannel, "easyui/themes/default/easyui.css", "text/css");
        } else if (path.equals("/easyui/themes/icon.css")) {
            serveFile(clientChannel, "easyui/themes/icon.css", "text/css");
        } else if (path.equals("/easyui/themes/default/images/")) {
            // Обработка изображений
            serveFile(clientChannel, "easyui/themes/default/images/loading.gif", "image/gif");
        } else if (path.equals("/easyui/jquery.min.js")) {
            serveFile(clientChannel, "easyui/jquery.min.js", "application/javascript");
        } else if (path.equals("/easyui/jquery.easyui.min.js")) {
            serveFile(clientChannel, "easyui/jquery.easyui.min.js", "application/javascript");
        } else if (path.equals("/js/app.js")) {
            serveFile(clientChannel, "js/app.js", "application/javascript");
        } else if (path.equals("/css/style.css")) {
            serveFile(clientChannel, "css/style.css", "text/css");
        } else if (path.startsWith("/api/")) {
            handleApiGetRequest(clientChannel, path, headers);
        } else {
            // Попробовать обслужить как статический файл
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            serveFile(clientChannel, "" + path, getContentType(path));
        }
    }

    private void handleApiGetRequest(SocketChannel clientChannel, String path, Map<String, String> headers) throws IOException {
        if (path.equals("/api/status")) {
            JSONObject status = new JSONObject();
            status.put("status", "running");
            status.put("version", "1.0");
            status.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(clientChannel, status);

        } else if (path.equals("/api/models")) {
            List<String> models = assistantService.getAvailableModels();
            JSONObject response = new JSONObject();
            response.put("models", new JSONArray(models));
            response.put("current_model", assistantService.getCurrentModel());
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/embedding_models")) {
            List<String> models = assistantService.getAvailableEmbeddingModels();
            JSONObject response = new JSONObject();
            response.put("models", new JSONArray(models));
            response.put("current_model", assistantService.getEmbeddingModel());
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/statistics")) {
            JSONObject stats = assistantService.getStatistics();
            sendJsonResponse(clientChannel, stats);

        } else if (path.equals("/api/chat_history")) {
            List<ChatMessage> history = assistantService.getChatHistory();
            JSONArray historyArray = new JSONArray();
            for (ChatMessage msg : history) {
                historyArray.put(msg.toJSON());
            }
            JSONObject response = new JSONObject();
            response.put("history", historyArray);
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/prompts")) {
            JSONObject prompts = assistantService.exportPrompts();
            sendJsonResponse(clientChannel, prompts);

        } else if (path.equals("/api/chat_stream")) {
            // Подключение к EventSource
            String sessionId = UUID.randomUUID().toString();
            EventSourceClient client = new EventSourceClient(clientChannel, sessionId);
            String channel = headers.getOrDefault("Last-Event-Id", "general");

            eventSources.computeIfAbsent(channel, k -> new ArrayList<>()).add(client);

            // Отправляем начальное сообщение
            JSONObject initEvent = new JSONObject();
            initEvent.put("type", "connected");
            initEvent.put("session_id", sessionId);
            initEvent.put("timestamp", System.currentTimeMillis());
            sendEventSourceMessage(clientChannel, initEvent);

            // Не закрываем соединение - оно будет использоваться для отправки событий

        } else {
            sendError(clientChannel, 404, "Not Found");
        }
    }

    private void handlePostRequest(SocketChannel clientChannel, String path, Map<String, String> headers, String body) throws IOException {
        if (path.startsWith("/api/")) {
            handleApiPostRequest(clientChannel, path, headers, body);
        } else {
            sendError(clientChannel, 404, "Not Found");
        }
    }

    private void handleApiPostRequest(SocketChannel clientChannel, String path, Map<String, String> headers, String body) throws IOException {
        JSONObject request;
        try {
            request = new JSONObject(body);
        } catch (Exception e) {
            sendError(clientChannel, 400, "Invalid JSON");
            return;
        }

        if (path.equals("/api/ask")) {
            String question = request.optString("question", "");
            String sessionId = request.optString("session_id", UUID.randomUUID().toString());

            if (question.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Question cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            // Отправляем подтверждение
            JSONObject response = new JSONObject();
            response.put("status", "processing");
            response.put("session_id", sessionId);
            response.put("question", question);
            sendJsonResponse(clientChannel, response);

            // Обрабатываем вопрос в фоне
            executorService.submit(() -> {
                try {
                    String answer = assistantService.askQuestion(question);

                    // Отправляем результат через EventSource
                    JSONObject answerEvent = new JSONObject();
                    answerEvent.put("type", "answer_complete");
                    answerEvent.put("session_id", sessionId);
                    answerEvent.put("answer", answer);
                    answerEvent.put("timestamp", System.currentTimeMillis());

                    broadcastEvent("general", answerEvent);

                } catch (Exception e) {
                    JSONObject errorEvent = new JSONObject();
                    errorEvent.put("type", "error");
                    errorEvent.put("session_id", sessionId);
                    errorEvent.put("error", e.getMessage());
                    errorEvent.put("timestamp", System.currentTimeMillis());

                    broadcastEvent("general", errorEvent);
                }
            });

        } else if (path.equals("/api/add_knowledge")) {
            String content = request.optString("content", "");
            String source = request.optString("source", "web_interface");

            if (content.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Content cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            try {
                assistantService.addKnowledge(content, source);

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "Knowledge added successfully");
                sendJsonResponse(clientChannel, response);

            } catch (Exception e) {
                JSONObject error = new JSONObject();
                error.put("error", "Failed to add knowledge: " + e.getMessage());
                sendJsonResponse(clientChannel, error, 500);
            }

        } else if (path.equals("/api/switch_model")) {
            String modelName = request.optString("model_name", "");

            if (modelName.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Model name cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            boolean success = assistantService.switchModel(modelName);

            JSONObject response = new JSONObject();
            if (success) {
                response.put("status", "success");
                response.put("message", "Model switched to: " + modelName);
            } else {
                response.put("status", "error");
                response.put("message", "Failed to switch model");
            }
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/switch_embedding_model")) {
            String modelName = request.optString("model_name", "");

            if (modelName.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Model name cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            boolean success = assistantService.switchEmbeddingModel(modelName);

            JSONObject response = new JSONObject();
            if (success) {
                response.put("status", "success");
                response.put("message", "Embedding model switched to: " + modelName);
            } else {
                response.put("status", "error");
                response.put("message", "Failed to switch embedding model");
            }
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/pull_model")) {
            String modelName = request.optString("model_name", "");

            if (modelName.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Model name cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            // Отправляем подтверждение
            JSONObject response = new JSONObject();
            response.put("status", "started");
            response.put("message", "Started pulling model: " + modelName);
            sendJsonResponse(clientChannel, response);

            // Загружаем модель в фоне
            executorService.submit(() -> {
                try {
                    boolean success = assistantService.pullModel(modelName);

                    JSONObject resultEvent = new JSONObject();
                    resultEvent.put("type", "model_pull_complete");
                    resultEvent.put("model_name", modelName);
                    resultEvent.put("success", success);
                    resultEvent.put("timestamp", System.currentTimeMillis());

                    broadcastEvent("models", resultEvent);

                } catch (Exception e) {
                    JSONObject errorEvent = new JSONObject();
                    errorEvent.put("type", "model_pull_error");
                    errorEvent.put("model_name", modelName);
                    errorEvent.put("error", e.getMessage());
                    errorEvent.put("timestamp", System.currentTimeMillis());

                    broadcastEvent("models", errorEvent);
                }
            });

        } else if (path.equals("/api/toggle_chat_mode")) {
            boolean useChatMode = request.optBoolean("use_chat_mode", true);
            assistantService.toggleChatMode(useChatMode);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("use_chat_mode", useChatMode);
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/toggle_cache")) {
            boolean useCache = request.optBoolean("use_cache", true);
            assistantService.toggleCache(useCache);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("use_cache", useCache);
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/toggle_speech")) {
            boolean speechEnabled = request.optBoolean("speech_enabled", false);
            assistantService.setSpeechEnabled(speechEnabled);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("speech_enabled", speechEnabled);
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/clear_chat_history")) {
            assistantService.clearChatHistory();

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "Chat history cleared");
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/clear_cache")) {
            boolean success = assistantService.clearCache();

            JSONObject response = new JSONObject();
            response.put("status", success ? "success" : "error");
            response.put("message", success ? "Cache cleared" : "Failed to clear cache");
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/search_knowledge")) {
            String query = request.optString("query", "");

            if (query.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Query cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            // Поиск в базе знаний
            assistantService.searchKnowledgeBase(query);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "Search completed");
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/update_prompt")) {
            String promptType = request.optString("type", "current");
            String newPrompt = request.optString("prompt", "");

            if (newPrompt.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "Prompt cannot be empty");
                sendJsonResponse(clientChannel, error, 400);
                return;
            }

            if (promptType.equals("chat")) {
                assistantService.updateChatPrompt(newPrompt);
            } else if (promptType.equals("generation")) {
                assistantService.updateGenerationPrompt(newPrompt);
            } else {
                assistantService.updateCurrentPrompt(newPrompt);
            }

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "Prompt updated");
            sendJsonResponse(clientChannel, response);

        } else if (path.equals("/api/reset_prompts")) {
            assistantService.resetPromptsToDefault();

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "Prompts reset to default");
            sendJsonResponse(clientChannel, response);

        } else {
            sendError(clientChannel, 404, "Not Found");
        }
    }

    private void handleOptionsRequest(SocketChannel clientChannel) throws IOException {
        String response = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n";

        clientChannel.write(StandardCharsets.UTF_8.encode(response));
    }

    private void serveFile(SocketChannel clientChannel, String filePath, String contentType) throws IOException {

        File fileDir = new File("web");
        File file = new File(fileDir.getAbsolutePath()+"/"+filePath);
        System.out.println(file);
        if (!file.exists() || file.isDirectory()) {
            sendError(clientChannel, 404, "File not found");
            return;
        }

        byte[] fileContent = readAllBytes(file.toPath());

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + fileContent.length + "\r\n" +
                "\r\n";

        ByteBuffer headerBuffer = StandardCharsets.UTF_8.encode(response);
        ByteBuffer contentBuffer = ByteBuffer.wrap(fileContent);

        clientChannel.write(new ByteBuffer[]{headerBuffer, contentBuffer});
    }

    private void sendJsonResponse(SocketChannel clientChannel, JSONObject json) throws IOException {
        sendJsonResponse(clientChannel, json, 200);
    }

    private void sendJsonResponse(SocketChannel clientChannel, JSONObject json, int statusCode) throws IOException {
        String jsonString = json.toString();
        String statusText = getStatusText(statusCode);

        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + jsonString.length() + "\r\n" +
                "\r\n" +
                jsonString;

        clientChannel.write(StandardCharsets.UTF_8.encode(response));
        clientChannel.close();
    }

    private void sendEventSourceMessage(SocketChannel clientChannel, JSONObject event) throws IOException {
        String eventString = "data: " + event.toString() + "\n\n";

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n" +
                eventString;

        clientChannel.write(StandardCharsets.UTF_8.encode(response));
    }

    private void broadcastEvent(String channel, JSONObject event) {
        List<EventSourceClient> clients = eventSources.get(channel);
        if (clients != null) {
            Iterator<EventSourceClient> iter = clients.iterator();
            while (iter.hasNext()) {
                EventSourceClient client = iter.next();
                try {
                    sendEventSourceMessage(client.channel, event);
                } catch (IOException e) {
                    // Клиент отключился
                    iter.remove();
                }
            }
        }
    }

    private void sendError(SocketChannel clientChannel, int statusCode, String message) throws IOException {
        String statusText = getStatusText(statusCode);

        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + message.length() + "\r\n" +
                "\r\n" +
                message;

        clientChannel.write(StandardCharsets.UTF_8.encode(response));
        clientChannel.close();
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".html")) return "text/html";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".gif")) return "image/gif";
        if (filename.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private String getStatusText(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    public void stop() {
        running = false;
        executorService.shutdown();
    }

    private static class EventSourceClient {
        SocketChannel channel;
        String sessionId;

        EventSourceClient(SocketChannel channel, String sessionId) {
            this.channel = channel;
            this.sessionId = sessionId;
        }
    }

    // Вспомогательный метод для чтения файлов
    private static byte[] readAllBytes(Path path) throws IOException {
        try (InputStream is = new FileInputStream(path.toFile())) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            return buffer.toByteArray();
        }
    }
}