package com.example.aiassistant.service;

import com.example.aiassistant.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AssistantService {
    private final RAGService ragService;
    private final OllamaService ollamaService;
    private final List<ChatMessage> chatHistory;

    // Конфигурация
    //private static final String DEFAULT_MODEL = "llama3.2:latest";
    private static final String DEFAULT_MODEL = "deepseek-coder-v2:16b";
    private static final String DEFAULT_EMBEDDING_MODEL = "all-minilm:22m";
    private static final String OLLAMA_HOST = "http://192.168.15.7:11434";

    public AssistantService(VectorDBService vectorDB) {
        // Инициализация сервисов
        EmbeddingService embeddingService = new EmbeddingService(
                OLLAMA_HOST, DEFAULT_EMBEDDING_MODEL
        );

        this.ollamaService = new OllamaService(OLLAMA_HOST, DEFAULT_MODEL);
        this.ragService = new RAGService(vectorDB, embeddingService, ollamaService);
        this.chatHistory = new ArrayList<>();

        // Добавляем системное сообщение
        ChatMessage systemMsg = new ChatMessage(
                ChatMessage.Role.SYSTEM,
                "Ты полезный AI ассистент с доступом к базе знаний. " +
                        "Отвечай точно и информативно."
        );
        chatHistory.add(systemMsg);
    }

    // Основной метод для вопросов
    public String askQuestion(String question) {
        System.out.println("\n=== Вопрос: " + question + " ===");

        // Добавляем вопрос в историю
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, question);
        chatHistory.add(userMsg);

        // Получаем ответ с использованием RAG
        String answer = ragService.getAnswerWithRAG(question);

        // Добавляем ответ в историю
        ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, answer);
        chatHistory.add(assistantMsg);

        // Сохраняем историю (максимум 20 сообщений)
        if (chatHistory.size() > 20) {
            chatHistory.remove(1); // Удаляем самое старое сообщение (после системного)
        }

        return answer;
    }

    // Добавление знаний
    public void addKnowledge(String content, String source) {
        ragService.addKnowledge(content, source);
    }

    // Смена модели
    public boolean switchModel(String modelName) {
        try {
            List<String> availableModels = ollamaService.getAvailableModels();

            if (availableModels.contains(modelName)) {
                ollamaService.setModel(modelName);
                System.out.println("Модель изменена на: " + modelName);
                return true;
            } else {
                System.out.println("Модель не найдена. Доступные модели:");
                for (String model : availableModels) {
                    System.out.println("  - " + model);
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Ошибка смены модели: " + e.getMessage());
            return false;
        }
    }

    // Получение статистики
    public JSONObject getStatistics() {
        JSONObject stats = ragService.getStatistics();

        // Добавляем информацию о чате
        stats.put("chat_history_size", chatHistory.size() - 1); // Без системного
        stats.put("current_model", getCurrentModel());

        return stats;
    }

    // Поиск в базе знаний
    public void searchKnowledgeBase(String query) {
        System.out.println("\n[Поиск в базе знаний: '" + query + "']");

        List<VectorDBService.SearchResult> results = ragService.searchKnowledge(query, 3);

        if (results.isEmpty()) {
            System.out.println("По вашему запросу ничего не найдено.");
        } else {
            System.out.println("Найдено результатов: " + results.size());
            System.out.println("---");

            for (int i = 0; i < results.size(); i++) {
                VectorDBService.SearchResult result = results.get(i);
                System.out.println("\nРезультат " + (i + 1) + ":");
                System.out.println("Схожесть: " + String.format("%.3f", result.similarity));
                System.out.println("Источник: " + result.document.getSource());
                System.out.println("Содержимое: " + result.document.getContent());
                System.out.println("---");
            }
        }
    }

    // Геттеры
    public String getCurrentModel() {
        return ollamaService.getModel();
    }

    public List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    // Очистка истории чата
    public void clearChatHistory() {
        // Оставляем только системное сообщение
        ChatMessage systemMsg = chatHistory.get(0);
        chatHistory.clear();
        chatHistory.add(systemMsg);
        System.out.println("История чата очищена.");
    }
}