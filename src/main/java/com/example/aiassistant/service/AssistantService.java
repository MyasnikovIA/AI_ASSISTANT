package com.example.aiassistant.service;

import com.example.aiassistant.model.ChatMessage;
import com.example.aiassistant.util.SpeakToText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AssistantService {
    private final RAGService ragService;
    private final OllamaService ollamaService;
    private final EmbeddingService embeddingService;
    private final List<ChatMessage> chatHistory;
    private final ChatHistoryService chatHistoryService;
    private final SpeakToText speakToText;
    private boolean speechEnabled = false;

    // Конфигурация
    private static final String DEFAULT_MODEL = "deepseek-coder-v2:16b";
    private static final String DEFAULT_EMBEDDING_MODEL = "all-minilm:22m";
    private static final String OLLAMA_HOST = "http://192.168.15.7:11434";

    public AssistantService(VectorDBService vectorDB) {
        // Инициализация сервисов
        this.embeddingService = new EmbeddingService(OLLAMA_HOST, DEFAULT_EMBEDDING_MODEL);
        this.ollamaService = new OllamaService(OLLAMA_HOST, DEFAULT_MODEL);
        this.ragService = new RAGService(vectorDB, embeddingService, ollamaService);
        this.chatHistory = new ArrayList<>();
        this.chatHistoryService = new ChatHistoryService();
        this.speakToText = new SpeakToText();

        // Загружаем историю чата из файла
        loadChatHistory();

        // Если истории нет, добавляем системное сообщение
        if (chatHistory.isEmpty()) {
            ChatMessage systemMsg = new ChatMessage(
                    ChatMessage.Role.SYSTEM,
                    "Ты полезный AI ассистент с доступом к базе знаний. " +
                            "Отвечай точно и информативно."
            );
            chatHistory.add(systemMsg);
        }
    }

    // Загрузка истории чата из файла
    private void loadChatHistory() {
        List<ChatMessage> loadedHistory = chatHistoryService.loadChatHistory();
        if (loadedHistory != null && !loadedHistory.isEmpty()) {
            chatHistory.addAll(loadedHistory);
            System.out.println("Загружена история чата: " + (chatHistory.size() - 1) + " сообщений");
        }
    }

    // Основной метод для вопросов с учетом истории и озвучкой
    public String askQuestion(String question) {
        System.out.println("\n=== Вопрос: " + question + " ===");
        System.out.println("Используемая модель: " + getCurrentModel());

        // Добавляем вопрос в историю
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, question);
        chatHistory.add(userMsg);

        // Получаем ответ с использованием RAG и истории чата
        String answer = ragService.getAnswerWithRAGAndHistory(question, chatHistory, speechEnabled);

        // Добавляем ответ в историю
        ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, answer);
        chatHistory.add(assistantMsg);

        // Сохраняем историю в файл
        chatHistoryService.saveChatHistory(chatHistory);

        // Ограничиваем размер истории (максимум 50 сообщений)
        if (chatHistory.size() > 50) {
            // Оставляем системное сообщение и последние 49 сообщений
            ChatMessage systemMsg = chatHistory.get(0);
            List<ChatMessage> newHistory = new ArrayList<>();
            newHistory.add(systemMsg);
            newHistory.addAll(chatHistory.subList(chatHistory.size() - 49, chatHistory.size()));
            chatHistory.clear();
            chatHistory.addAll(newHistory);
        }
        return answer;
    }

    // Озвучивание ответа по предложениям, исключая код
    private void speakAnswer(String answer) {
        System.out.println("\n[Озвучивание ответа...]");

        // Обрабатываем ответ для озвучки
        String textForSpeech = prepareTextForSpeech(answer);

        // Разбиваем на предложения и озвучиваем
        String[] sentences = textForSpeech.split("(?<=[.!?])\\s+");
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                speakToText.speak(sentence.trim());
            }
        }
    }

    // Подготовка текста для озвучки - удаление кода в тройных кавычках
    private String prepareTextForSpeech(String text) {
        // Удаляем блоки кода в тройных кавычках
        String result = text.replaceAll("```[\\s\\S]*?```", "");

        // Также удаляем одинарные кавычки для кода, если они есть
        result = result.replaceAll("`[^`]*`", "");

        // Удаляем лишние пробелы и переносы строк
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    // Включение/выключение озвучки
    public void setSpeechEnabled(boolean enabled) {
        this.speechEnabled = enabled;
        System.out.println("Озвучка " + (enabled ? "включена" : "выключена"));
    }

    public boolean isSpeechEnabled() {
        return speechEnabled;
    }

    // Добавление знаний
    public void addKnowledge(String content, String source) {
        ragService.addKnowledge(content, source);
    }

    // Смена модели для ответов
    public boolean switchModel(String modelName) {
        try {
            List<String> availableModels = ollamaService.getAvailableModels();

            if (availableModels.contains(modelName)) {
                ollamaService.setModel(modelName);
                System.out.println("Модель для ответов изменена на: " + modelName);
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

    // Смена модели для эмбеддингов
    public boolean switchEmbeddingModel(String modelName) {
        try {
            // Проверяем поддержку эмбеддингов
            if (embeddingService.checkEmbeddingSupport(modelName)) {
                embeddingService.setEmbeddingModel(modelName);
                System.out.println("Модель для эмбеддингов изменена на: " + modelName);
                return true;
            } else {
                System.out.println("Модель не поддерживает эмбеддинги или недоступна");

                // Предлагаем доступные модели
                var embeddingModels = embeddingService.getAvailableEmbeddingModels();
                if (!embeddingModels.isEmpty()) {
                    System.out.println("Доступные модели для эмбеддингов:");
                    for (String model : embeddingModels) {
                        System.out.println("  - " + model);
                    }
                }

                return false;
            }
        } catch (Exception e) {
            System.err.println("Ошибка смены модели для эмбеддингов: " + e.getMessage());
            return false;
        }
    }

    // Получение статистики
    public JSONObject getStatistics() {
        JSONObject stats = ragService.getStatistics();

        // Добавляем информацию о чате
        stats.put("chat_history_size", chatHistory.size() - 1); // Без системного
        stats.put("llm_model", getCurrentModel());
        stats.put("embedding_model", getEmbeddingModel());
        stats.put("speech_enabled", speechEnabled);

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

    public String getEmbeddingModel() {
        return embeddingService.getEmbeddingModel();
    }

    public List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    // Новые методы для управления моделями

    public List<String> getAvailableModels() {
        return ollamaService.getAvailableModels();
    }

    public List<String> getAvailableEmbeddingModels() {
        return embeddingService.getAvailableEmbeddingModels();
    }

    public boolean pullModel(String modelName) {
        return ollamaService.pullModel(modelName);
    }

    public boolean deleteModel(String modelName) {
        return ollamaService.deleteModel(modelName);
    }

    public boolean copyModel(String sourceModel, String targetModel) {
        return ollamaService.copyModel(sourceModel, targetModel);
    }

    public JSONObject getModelInfo(String modelName) {
        return ollamaService.getModelInfo(modelName);
    }

    // Очистка истории чата
    public void clearChatHistory() {
        // Оставляем только системное сообщение или создаем новое
        ChatMessage systemMsg;
        if (!chatHistory.isEmpty() && chatHistory.get(0).getRole() == ChatMessage.Role.SYSTEM) {
            systemMsg = chatHistory.get(0);
        } else {
            systemMsg = new ChatMessage(
                    ChatMessage.Role.SYSTEM,
                    "Ты полезный AI ассистент с доступом к базе знаний. " +
                            "Отвечай точно и информативно."
            );
        }

        chatHistory.clear();
        chatHistory.add(systemMsg);

        // Сохраняем очищенную историю
        chatHistoryService.saveChatHistory(chatHistory);

        System.out.println("История чата очищена.");
    }
}