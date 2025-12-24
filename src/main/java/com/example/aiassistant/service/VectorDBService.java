package com.example.aiassistant.service;

import com.example.aiassistant.model.EmbeddingVector;
import com.example.aiassistant.model.KnowledgeDocument;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

    // Константы для бинарного формата
    private static final int MAGIC_NUMBER = 0x56444231; // "VDB1"
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 16; // magic(4) + version(4) + docCount(4) + reserved(4)

    private VectorDBService() {
        this.documents = new ConcurrentHashMap<>();
        this.embeddings = new ConcurrentHashMap<>();
        this.index = new CopyOnWriteArrayList<>();
        this.dataFilePath = "knowledge_base.bin"; // Изменено с .json на .bin

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

    // Сохранение в бинарный формат
    private void saveToDisk() {
        try (RandomAccessFile file = new RandomAccessFile(dataFilePath, "rw");
             FileChannel channel = file.getChannel()) {

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.putInt(MAGIC_NUMBER);
            header.putInt(VERSION);
            header.putInt(documents.size());
            header.putInt(0); // reserved
            header.flip();

            channel.write(header);

            // Сохраняем документы
            for (KnowledgeDocument doc : documents.values()) {
                saveDocument(channel, doc);
            }

            System.out.println("База знаний сохранена в бинарный файл: " + dataFilePath);

        } catch (IOException e) {
            System.err.println("Ошибка сохранения базы знаний: " + e.getMessage());
        }
    }

    private void saveDocument(FileChannel channel, KnowledgeDocument doc) throws IOException {
        // Получаем эмбеддинг
        EmbeddingVector embedding = embeddings.get(doc.getId());
        double[] embeddingArray = (embedding != null) ? embedding.getVector() : new double[0];

        // Подготавливаем данные
        byte[] idBytes = doc.getId().getBytes("UTF-8");
        byte[] contentBytes = doc.getContent().getBytes("UTF-8");
        byte[] sourceBytes = doc.getSource().getBytes("UTF-8");
        byte[] metadataBytes = doc.getMetadata().toString().getBytes("UTF-8");
        byte[] createdAtBytes = doc.getCreatedAt().toString().getBytes("UTF-8");

        // Рассчитываем размер записи
        int recordSize = 4 + // idLength
                4 + // contentLength
                4 + // sourceLength
                4 + // metadataLength
                4 + // createdAtLength
                4 + // embeddingLength
                idBytes.length +
                contentBytes.length +
                sourceBytes.length +
                metadataBytes.length +
                createdAtBytes.length +
                embeddingArray.length * 8; // 8 bytes per double

        ByteBuffer buffer = ByteBuffer.allocate(4 + recordSize); // +4 for recordSize itself
        buffer.putInt(recordSize);
        buffer.putInt(idBytes.length);
        buffer.put(idBytes);
        buffer.putInt(contentBytes.length);
        buffer.put(contentBytes);
        buffer.putInt(sourceBytes.length);
        buffer.put(sourceBytes);
        buffer.putInt(metadataBytes.length);
        buffer.put(metadataBytes);
        buffer.putInt(createdAtBytes.length);
        buffer.put(createdAtBytes);
        buffer.putInt(embeddingArray.length);
        for (double value : embeddingArray) {
            buffer.putDouble(value);
        }

        buffer.flip();
        channel.write(buffer);
    }

    // Загрузка из бинарного формата
    private void loadFromDisk() {
        File file = new File(dataFilePath);
        if (!file.exists()) {
            System.out.println("Файл базы знаний не найден, будет создан новый");
            // Создаем пустую базу знаний
            saveToDisk();
            return;
        }

        if (file.length() == 0) {
            System.out.println("Файл базы знаний пуст, будет создана новая база");
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(dataFilePath, "r");
             FileChannel channel = raf.getChannel()) {

            // Читаем заголовок
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            channel.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int docCount = header.getInt();
            int reserved = header.getInt();

            if (magic != MAGIC_NUMBER) {
                System.err.println("Неверный формат файла базы знаний");
                saveToDisk(); // Создаем новый файл
                return;
            }

            System.out.println("Загрузка " + docCount + " документов из бинарного файла...");

            // Читаем документы
            for (int i = 0; i < docCount; i++) {
                try {
                    KnowledgeDocument doc = loadDocument(channel);
                    if (doc != null) {
                        documents.put(doc.getId(), doc);
                        index.add(doc.getId());
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка загрузки документа " + i + ": " + e.getMessage());
                }
            }

            System.out.println("Загружено " + documents.size() + " документов из бинарного файла");

        } catch (IOException e) {
            System.err.println("Ошибка чтения файла базы знаний: " + e.getMessage());
            System.out.println("Будет создана новая база знаний");
            saveToDisk();
        }
    }

    private KnowledgeDocument loadDocument(FileChannel channel) throws IOException {
        // Читаем размер записи
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        if (channel.read(sizeBuffer) != 4) {
            return null;
        }
        sizeBuffer.flip();
        int recordSize = sizeBuffer.getInt();

        if (recordSize <= 0) {
            return null;
        }

        // Читаем всю запись
        ByteBuffer recordBuffer = ByteBuffer.allocate(recordSize);
        if (channel.read(recordBuffer) != recordSize) {
            return null;
        }
        recordBuffer.flip();

        // Читаем id
        int idLength = recordBuffer.getInt();
        byte[] idBytes = new byte[idLength];
        recordBuffer.get(idBytes);
        String id = new String(idBytes, "UTF-8");

        // Читаем content
        int contentLength = recordBuffer.getInt();
        byte[] contentBytes = new byte[contentLength];
        recordBuffer.get(contentBytes);
        String content = new String(contentBytes, "UTF-8");

        // Читаем source
        int sourceLength = recordBuffer.getInt();
        byte[] sourceBytes = new byte[sourceLength];
        recordBuffer.get(sourceBytes);
        String source = new String(sourceBytes, "UTF-8");

        // Читаем metadata
        int metadataLength = recordBuffer.getInt();
        byte[] metadataBytes = new byte[metadataLength];
        recordBuffer.get(metadataBytes);
        String metadataStr = new String(metadataBytes, "UTF-8");
        JSONObject metadata = new JSONObject(metadataStr);

        // Читаем createdAt
        int createdAtLength = recordBuffer.getInt();
        byte[] createdAtBytes = new byte[createdAtLength];
        recordBuffer.get(createdAtBytes);
        String createdAtStr = new String(createdAtBytes, "UTF-8");

        // Читаем embedding
        int embeddingLength = recordBuffer.getInt();
        double[] embedding = new double[embeddingLength];
        for (int i = 0; i < embeddingLength; i++) {
            embedding[i] = recordBuffer.getDouble();
        }

        // Создаем документ
        KnowledgeDocument doc = new KnowledgeDocument(content, source, metadata);

        // Устанавливаем ID (используем reflection)
        try {
            java.lang.reflect.Field idField = KnowledgeDocument.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(doc, id);

            // Устанавливаем embedding
            doc.setEmbedding(embedding);
            embeddings.put(id, new EmbeddingVector(id, embedding));

            // Устанавливаем createdAt (если нужно)
            java.lang.reflect.Field createdAtField = KnowledgeDocument.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(doc, java.time.LocalDateTime.parse(createdAtStr));

        } catch (Exception e) {
            System.err.println("Ошибка восстановления документа: " + e.getMessage());
            return null;
        }

        return doc;
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