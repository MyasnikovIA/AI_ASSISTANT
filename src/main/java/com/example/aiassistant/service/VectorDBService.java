package com.example.aiassistant.service;

import com.example.aiassistant.model.EmbeddingVector;
import com.example.aiassistant.model.KnowledgeDocument;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class VectorDBService {
    private static VectorDBService instance;

    // Хранилища в оперативной памяти
    private final Map<String, KnowledgeDocument> documents;
    private final Map<String, EmbeddingVector> embeddings;
    private final List<String> index; // Для быстрого поиска

    // Настройки
    private final String dataFilePath;
    private final long maxMemoryBytes = 70L * 1024 * 1024 * 1024; // 70 ГБ

    private VectorDBService() {
        this.documents = new ConcurrentHashMap<>();
        this.embeddings = new ConcurrentHashMap<>();
        this.index = new CopyOnWriteArrayList<>();
        this.dataFilePath = "knowledge_base.json";

        loadFromDisk();
        System.out.println("Векторная БД инициализирована в памяти");
        System.out.println("Документов: " + documents.size());
        System.out.println("Эмбеддингов: " + embeddings.size());
        System.out.println("Выделено памяти: " + (maxMemoryBytes / (1024*1024*1024)) + " ГБ");
    }

    public static synchronized VectorDBService getInstance() {
        if (instance == null) {
            instance = new VectorDBService();
        }
        return instance;
    }

    // Добавление документа с эмбеддингом
    public synchronized void addDocument(KnowledgeDocument document, double[] embedding) {
        if (documents.containsKey(document.getId())) {
            System.out.println("Документ уже существует: " + document.getId());
            return;
        }

        // Проверка памяти
        long estimatedSize = estimateMemoryUsage(document, embedding);
        if (getCurrentMemoryUsage() + estimatedSize > maxMemoryBytes) {
            System.out.println("Предупреждение: Близко к лимиту памяти. Рассмотрите очистку старых документов.");
        }

        documents.put(document.getId(), document);
        embeddings.put(document.getId(), new EmbeddingVector(document.getId(), embedding));
        index.add(document.getId());

        // Автосохранение
        saveToDisk();
    }

    // Поиск похожих документов
    public List<SearchResult> searchSimilar(double[] queryEmbedding, int topK, double threshold) {
        List<SearchResult> results = new ArrayList<>();
        EmbeddingVector queryVector = new EmbeddingVector("query", queryEmbedding);

        for (String docId : index) {
            EmbeddingVector docVector = embeddings.get(docId);
            if (docVector != null) {
                double similarity = queryVector.cosineSimilarity(docVector);

                if (similarity >= threshold) {
                    results.add(new SearchResult(
                            documents.get(docId),
                            docVector,
                            similarity
                    ));
                }
            }
        }

        // Сортировка по схожести
        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // Возвращаем топ-K результатов
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    // Получение контекста для RAG
    public String getContextForQuery(String query, double[] queryEmbedding, int topK, double threshold) {
        List<SearchResult> similarDocs = searchSimilar(queryEmbedding, topK, threshold);

        if (similarDocs.isEmpty()) {
            return null;
        }

        StringBuilder context = new StringBuilder();
        context.append("Релевантная информация из базы знаний:\n\n");

        for (SearchResult result : similarDocs) {
            context.append("=== Документ из ").append(result.document.getSource()).append(" ===\n");
            context.append("Схожесть: ").append(String.format("%.3f", result.similarity)).append("\n");
            context.append(result.document.getContent()).append("\n\n");
        }

        return context.toString();
    }

    // Утилиты
    public int getDocumentCount() {
        return documents.size();
    }

    public int getEmbeddingCount() {
        return embeddings.size();
    }

    public long getCurrentMemoryUsage() {
        // Приблизительная оценка использования памяти
        long docMemory = documents.size() * 1024L; // ~1KB на документ
        long embedMemory = embeddings.size() * 4096L; // ~4KB на эмбеддинг (512 измерений)
        return docMemory + embedMemory;
    }

    public double getMemoryUsagePercentage() {
        long currentUsage = getCurrentMemoryUsage();
        if (currentUsage == 0 || maxMemoryBytes == 0) {
            return 0.0;
        }
        return (double) currentUsage / maxMemoryBytes * 100;
    }

    // Сохранение/загрузка
    private void saveToDisk() {
        try {
            JSONObject data = new JSONObject();
            JSONArray docsArray = new JSONArray();

            for (KnowledgeDocument doc : documents.values()) {
                docsArray.put(doc.toJSON());
            }

            data.put("documents", docsArray);
            data.put("version", "1.0");
            data.put("savedAt", System.currentTimeMillis());

            // Создаем директорию, если ее нет
            File file = new File(dataFilePath);
            File fileDir = new File(file.getAbsolutePath()).getParentFile();
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }


            Files.write(Paths.get(dataFilePath), data.toString(2).getBytes());
            System.out.println("База знаний сохранена в файл: " + dataFilePath);

        } catch (IOException e) {
            System.err.println("Ошибка сохранения базы знаний: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        try {
            File file = new File(dataFilePath);
            if (!file.exists()) {
                System.out.println("Файл базы знаний не найден, будет создан новый");
                // Создаем пустую базу знаний
                saveToDisk();
                return;
            }

            // Проверяем, не пустой ли файл
            if (file.length() == 0) {
                System.out.println("Файл базы знаний пуст, будет создана новая база");
                return;
            }

            String content = new String(Files.readAllBytes(Paths.get(dataFilePath)));

            // Проверяем, что файл содержит валидный JSON
            if (content.trim().isEmpty()) {
                System.out.println("Файл базы знаний пуст");
                return;
            }

            // Пытаемся загрузить JSON
            try {
                JSONObject data = new JSONObject(content);

                if (data.has("documents")) {
                    JSONArray docsArray = data.getJSONArray("documents");

                    for (int i = 0; i < docsArray.length(); i++) {
                        try {
                            JSONObject docJson = docsArray.getJSONObject(i);
                            KnowledgeDocument doc = KnowledgeDocument.fromJSON(docJson);
                            documents.put(doc.getId(), doc);
                            index.add(doc.getId());
                        } catch (Exception e) {
                            System.err.println("Ошибка загрузки документа " + i + ": " + e.getMessage());
                        }
                    }

                    System.out.println("Загружено " + documents.size() + " документов из файла");
                }
            } catch (Exception e) {
                System.err.println("Ошибка парсинга JSON из файла: " + e.getMessage());
                System.out.println("Будет создана новая база знаний");
                // Очищаем файл и создаем новый
                saveToDisk();
            }

        } catch (IOException e) {
            System.err.println("Ошибка чтения файла базы знаний: " + e.getMessage());
            System.out.println("Будет создана новая база знаний");
        }
    }

    private long estimateMemoryUsage(KnowledgeDocument doc, double[] embedding) {
        // Примерная оценка: размер строки + размер массива double
        long size = 0;

        if (doc.getContent() != null) {
            size += doc.getContent().length() * 2L; // UTF-16
        }

        if (embedding != null) {
            size += embedding.length * 8L; // 8 bytes per double
        }

        size += 1024; // Накладные расходы
        return size;
    }

    // Внутренний класс для результатов поиска
    public static class SearchResult {
        public final KnowledgeDocument document;
        public final EmbeddingVector embedding;
        public final double similarity;

        public SearchResult(KnowledgeDocument document, EmbeddingVector embedding, double similarity) {
            this.document = document;
            this.embedding = embedding;
            this.similarity = similarity;
        }
    }
}