package com.example.aiassistant.service;

import com.example.aiassistant.model.ChatMessage;
import com.example.aiassistant.model.KnowledgeDocument;
import com.example.aiassistant.util.SpeakToText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class RAGService {
    private final VectorDBService vectorDB;
    private final EmbeddingService embeddingService;
    private final OllamaService ollamaService;
    private final SpeakToText speakToText;
    private boolean inCodeBlock = false;

    // Шаблон промпта для RAG с историей
    private static final String RAG_PROMPT_TEMPLATE = """
        Ты полезный AI ассистент с доступом к базе знаний.
        
        История предыдущего диалога:
        {history}
        
        Информация из базы знаний для текущего вопроса:
        {context}
        
        Текущий вопрос пользователя: {query}
        
        Учти историю диалога и предоставленную информацию из базы знаний.
        Если в информации из базы знаний есть ответ - используй её.
        Если информации недостаточно - используй свои знания.
        Отвечай точно, информативно и учитывай контекст всего диалога.
        
        Ответ:""";

    public RAGService(VectorDBService vectorDB,
                      EmbeddingService embeddingService,
                      OllamaService ollamaService) {
        this.vectorDB = vectorDB;
        this.embeddingService = embeddingService;
        this.ollamaService = ollamaService;
        this.speakToText = new SpeakToText();
    }

    // Основной метод получения ответа с RAG и историей
    public String getAnswerWithRAGAndHistory(String question, List<ChatMessage> chatHistory, boolean speechEnabled) {
        System.out.println("\n[Поиск релевантной информации в базе знаний...]");
        System.out.println("Модель для эмбеддингов: " + embeddingService.getEmbeddingModel());
        System.out.println("Режим работы: " + (ollamaService.isUseChatMode() ? "ЧАТ" : "ГЕНЕРАЦИЯ"));
        System.out.println("Кэш: " + (ollamaService.isUseCache() ? "ВКЛ" : "ВЫКЛ"));

        // Получаем эмбеддинг вопроса
        double[] queryEmbedding = embeddingService.getEmbedding(question);

        // Ищем релевантные документы
        String context = vectorDB.getContextForQuery(
                question, queryEmbedding, 5, 0.5
        );

        // Формируем историю диалога (исключая системное сообщение и текущий вопрос)
        String historyText = formatChatHistory(chatHistory);

        // Формируем промпт
        String prompt;
        if (context != null && !context.trim().isEmpty()) {
            prompt = RAG_PROMPT_TEMPLATE
                    .replace("{history}", historyText)
                    .replace("{context}", context)
                    .replace("{query}", question);

            System.out.println("[Найдено релевантной информации. Формирую ответ с учетом истории...]");
        } else {
            prompt = "История диалога:\n" + historyText +
                    "\n\nТекущий вопрос пользователя: " + question +
                    "\n\nОтветь на вопрос, используя свои знания и учитывая историю диалога:";
            System.out.println("[Релевантная информация не найдена. Использую знания модели и историю...]");
        }

        // Отправляем запрос к LLM с потоковой передачей
        System.out.println("\n[Генерация ответа...]");
        System.out.println("Модель для ответов: " + ollamaService.getModel());
        System.out.println("-".repeat(50));

        StringBuilder fullResponse = new StringBuilder();
        StringBuilder currentSentence = new StringBuilder();

        // Создаем обработчик для потокового вывода
        ollamaService.setStreamingCallback(token -> {
            // Обработка токенов для определения блоков кода
            if (token.contains("```")) {
                inCodeBlock = !inCodeBlock;
            }

            // Выводим только если не внутри блока кода
            if (!inCodeBlock) {
                System.out.print(token);
                fullResponse.append(token);
                currentSentence.append(token);

                // Если включена озвучка, проверяем конец предложения
                if (speechEnabled) {
                    processSentenceForSpeech(currentSentence, token);
                }
            }
        });

        // Получаем ответ в зависимости от режима работы
        String answer;
        if (ollamaService.isUseChatMode()) {
            // Используем режим чата с историей
            List<JSONObject> messages = prepareChatMessages(question, chatHistory, context);
            answer = ollamaService.sendChatRequest(messages, true);
        } else {
            // Используем режим генерации
            answer = ollamaService.sendGenerateRequest(prompt, true);
        }

        System.out.println("\n" + "-".repeat(50));

        return fullResponse.toString();
    }

    // Подготовка сообщений для режима чата
    private List<JSONObject> prepareChatMessages(String question, List<ChatMessage> chatHistory, String context) {
        // Конвертируем историю в JSON объекты
        List<JSONObject> messages = new java.util.ArrayList<>();

        // Добавляем системное сообщение
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "Ты полезный AI ассистент с доступом к базе знаний. Отвечай точно и информативно.");
        messages.add(systemMessage);

        // Добавляем контекст из базы знаний как системное сообщение
        if (context != null && !context.trim().isEmpty()) {
            JSONObject contextMessage = new JSONObject();
            contextMessage.put("role", "system");
            contextMessage.put("content", "Контекст из базы знаний:\n" + context);
            messages.add(contextMessage);
        }

        // Добавляем историю диалога (кроме последнего вопроса)
        for (int i = 1; i < chatHistory.size() - 1; i++) {
            ChatMessage msg = chatHistory.get(i);
            JSONObject message = new JSONObject();
            message.put("role", msg.getRole() == ChatMessage.Role.USER ? "user" : "assistant");
            message.put("content", msg.getContent());
            messages.add(message);
        }

        // Добавляем текущий вопрос
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", question);
        messages.add(userMessage);

        return messages;
    }

    // Обработка предложений для озвучки
    private void processSentenceForSpeech(StringBuilder currentSentence, String token) {
        // Проверяем, закончилось ли предложение
        if (token.matches(".*[.!?]\\s*$")) {
            String sentence = currentSentence.toString();

            // Удаляем блоки кода из предложения перед озвучкой
            String cleanSentence = prepareTextForSpeech(sentence);

            if (!cleanSentence.trim().isEmpty()) {
                // Озвучиваем предложение
                speakToText.speak(cleanSentence.trim());
            }

            // Очищаем буфер для следующего предложения
            currentSentence.setLength(0);
        }
    }

    // Подготовка текста для озвучки - удаление кода в тройных кавычках
    private String prepareTextForSpeech(String text) {
        // Удаляем блоки кода в тройных кавычках
        String result = text.replaceAll("```[\\s\\S]*?```", "");

        // Также удаляем одинарные кавычки для кода
        result = result.replaceAll("`[^`]*`", "");

        // Удаляем лишние пробелы и переносы строк
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    // Форматирование истории чата для промпта
    private String formatChatHistory(List<ChatMessage> chatHistory) {
        StringBuilder history = new StringBuilder();

        // Пропускаем последнее сообщение (это текущий вопрос)
        int endIndex = chatHistory.size() - 1;
        if (endIndex <= 1) return ""; // Только системное сообщение и текущий вопрос

        for (int i = 1; i < endIndex; i++) { // Начинаем с 1, пропускаем системное сообщение
            ChatMessage message = chatHistory.get(i);
            String role = message.getRole() == ChatMessage.Role.USER ? "Пользователь" : "Ассистент";
            history.append(role).append(": ").append(message.getContent()).append("\n");
        }

        return history.toString();
    }

    // Добавление новых знаний с сохранением в файл
    public void addKnowledge(String content, String source) {
        System.out.println("\n[Добавление новых знаний в базу...]");
        System.out.println("Используемая модель для эмбеддингов: " + embeddingService.getEmbeddingModel());

        // Создаем документ
        KnowledgeDocument document = new KnowledgeDocument(content, source);

        // Получаем эмбеддинг
        double[] embedding = embeddingService.getEmbedding(content);

        // Добавляем в векторную БД (которая сама сохраняет в файл)
        vectorDB.addDocument(document, embedding);

        System.out.println("[Знания успешно добавлены. ID: " + document.getId() + "]");
    }

    // Получение статистики с информацией о кэше
    public JSONObject getStatistics() {
        JSONObject stats = new JSONObject();
        stats.put("total_documents", vectorDB.getDocumentCount());
        stats.put("memory_usage_percent",
                String.format("%.2f%%", vectorDB.getMemoryUsagePercentage()));
        stats.put("embedding_model", embeddingService.getEmbeddingModel());
        stats.put("llm_model", ollamaService.getModel());
        stats.put("use_chat_mode", ollamaService.isUseChatMode());
        stats.put("use_cache", ollamaService.isUseCache());

        // Добавляем информацию о кэше
        JSONObject cacheInfo = ollamaService.getCacheInfo();
        if (cacheInfo != null) {
            stats.put("cache_info", cacheInfo);
        }

        return stats;
    }

    // Управление кэшем
    public boolean clearCache() {
        return ollamaService.clearModelCache();
    }

    // Переключение режима работы
    public void toggleChatMode(boolean useChatMode) {
        ollamaService.setUseChatMode(useChatMode);
    }

    // Переключение использования кэша
    public void toggleCache(boolean useCache) {
        ollamaService.setUseCache(useCache);
    }

    // Поиск в базе знаний
    public List<VectorDBService.SearchResult> searchKnowledge(String query, int topK) {
        double[] queryEmbedding = embeddingService.getEmbedding(query);
        return vectorDB.searchSimilar(queryEmbedding, topK, 0.3);
    }
}