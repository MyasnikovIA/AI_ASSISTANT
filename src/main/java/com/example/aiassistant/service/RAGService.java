package com.example.aiassistant.service;

import com.example.aiassistant.model.KnowledgeDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class RAGService {
    private final VectorDBService vectorDB;
    private final EmbeddingService embeddingService;
    private final OllamaService ollamaService;

    // Шаблон промпта для RAG
    private static final String RAG_PROMPT_TEMPLATE = """
        Используй следующую информацию из базы знаний для ответа на вопрос.
        Если в предоставленной информации есть ответ - используй её.
        Если информации недостаточно - используй свои знания и укажи это.
        
        Информация из базы знаний:
        {context}
        
        Вопрос: {query}
        
        Ответ:""";

    public RAGService(VectorDBService vectorDB,
                      EmbeddingService embeddingService,
                      OllamaService ollamaService) {
        this.vectorDB = vectorDB;
        this.embeddingService = embeddingService;
        this.ollamaService = ollamaService;
    }

    // Основной метод получения ответа с RAG
    public String getAnswerWithRAG(String question) {
        System.out.println("\n[Поиск релевантной информации в базе знаний...]");

        // Получаем эмбеддинг вопроса
        double[] queryEmbedding = embeddingService.getEmbedding(question);

        // Ищем релевантные документы
        String context = vectorDB.getContextForQuery(
                question, queryEmbedding, 5, 0.5
        );

        // Формируем промпт
        String prompt;
        if (context != null) {
            prompt = RAG_PROMPT_TEMPLATE
                    .replace("{context}", context)
                    .replace("{query}", question);

            System.out.println("[Найдено релевантной информации. Формирую ответ...]");
        } else {
            prompt = "Вопрос: " + question + "\n\nОтветь на вопрос, используя свои знания:";
            System.out.println("[Релевантная информация не найдена. Использую знания модели...]");
        }

        // Отправляем запрос к LLM
        return ollamaService.sendGenerateRequest(prompt, false);
    }

    // Добавление новых знаний
    public void addKnowledge(String content, String source) {
        System.out.println("\n[Добавление новых знаний в базу...]");

        // Создаем документ
        KnowledgeDocument document = new KnowledgeDocument(content, source);

        // Получаем эмбеддинг
        double[] embedding = embeddingService.getEmbedding(content);

        // Добавляем в векторную БД
        vectorDB.addDocument(document, embedding);

        System.out.println("[Знания успешно добавлены. ID: " + document.getId() + "]");
    }

    // Получение статистики
    public JSONObject getStatistics() {
        JSONObject stats = new JSONObject();
        stats.put("total_documents", vectorDB.getDocumentCount());
        stats.put("memory_usage_percent",
                String.format("%.2f%%", vectorDB.getMemoryUsagePercentage()));
        stats.put("embedding_model", embeddingService.getEmbeddingModel());
        stats.put("llm_model", ollamaService.getModel());

        return stats;
    }

    // Поиск в базе знаний
    public List<VectorDBService.SearchResult> searchKnowledge(String query, int topK) {
        double[] queryEmbedding = embeddingService.getEmbedding(query);
        return vectorDB.searchSimilar(queryEmbedding, topK, 0.3);
    }
}