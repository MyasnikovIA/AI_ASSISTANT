package com.example.aiassistant.service;

import com.example.aiassistant.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryService {
    private static final String CHAT_HISTORY_FILE = "chat_history.bin";
    private static final int MAGIC_NUMBER = 0x43484154; // "CHAT"
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 16;

    public ChatHistoryService() {
        // Создаем директорию если не существует
        try {
            Files.createDirectories(Paths.get("."));
        } catch (IOException e) {
            System.err.println("Ошибка создания директории: " + e.getMessage());
        }
    }

    // Сохранение истории чата в бинарный файл
    public synchronized void saveChatHistory(List<ChatMessage> chatHistory) {
        try (RandomAccessFile file = new RandomAccessFile(CHAT_HISTORY_FILE, "rw");
             FileChannel channel = file.getChannel()) {

            // Очищаем файл
            channel.truncate(0);

            // Пишем заголовок
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.putInt(MAGIC_NUMBER);
            header.putInt(VERSION);
            header.putInt(chatHistory.size());
            header.putInt(0); // reserved
            header.flip();
            channel.write(header);

            // Сохраняем каждое сообщение
            for (ChatMessage message : chatHistory) {
                saveMessage(channel, message);
            }

            System.out.println("История чата сохранена в файл: " + CHAT_HISTORY_FILE);

        } catch (IOException e) {
            System.err.println("Ошибка сохранения истории чата: " + e.getMessage());
        }
    }

    // Загрузка истории чата из бинарного файла
    public synchronized List<ChatMessage> loadChatHistory() {
        List<ChatMessage> history = new ArrayList<>();

        File file = new File(CHAT_HISTORY_FILE);
        if (!file.exists() || file.length() == 0) {
            System.out.println("Файл истории чата не найден или пуст");
            return history;
        }

        try (RandomAccessFile raf = new RandomAccessFile(CHAT_HISTORY_FILE, "r");
             FileChannel channel = raf.getChannel()) {

            // Читаем заголовок
            if (channel.size() < HEADER_SIZE) {
                System.err.println("Файл истории чата поврежден (слишком маленький)");
                return history;
            }

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            channel.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int messageCount = header.getInt();
            int reserved = header.getInt();

            if (magic != MAGIC_NUMBER) {
                System.err.println("Неверный формат файла истории чата");
                return history;
            }

            System.out.println("Загрузка истории чата: " + messageCount + " сообщений");

            // Читаем сообщения
            for (int i = 0; i < messageCount; i++) {
                try {
                    ChatMessage message = loadMessage(channel);
                    if (message != null) {
                        history.add(message);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка загрузки сообщения " + i + ": " + e.getMessage());
                }
            }

            System.out.println("Загружено " + history.size() + " сообщений из истории чата");

        } catch (IOException e) {
            System.err.println("Ошибка чтения файла истории чата: " + e.getMessage());
        }

        return history;
    }

    private void saveMessage(FileChannel channel, ChatMessage message) throws IOException {
        JSONObject json = message.toJSON();

        byte[] roleBytes = message.getRole().name().getBytes("UTF-8");
        byte[] contentBytes = message.getContent().getBytes("UTF-8");
        byte[] metadataBytes = json.getJSONObject("metadata").toString().getBytes("UTF-8");

        int recordSize = 4 + // roleLength
                4 + // contentLength
                4 + // metadataLength
                roleBytes.length +
                contentBytes.length +
                metadataBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(4 + recordSize); // +4 for recordSize
        buffer.putInt(recordSize);
        buffer.putInt(roleBytes.length);
        buffer.put(roleBytes);
        buffer.putInt(contentBytes.length);
        buffer.put(contentBytes);
        buffer.putInt(metadataBytes.length);
        buffer.put(metadataBytes);

        buffer.flip();
        channel.write(buffer);
    }

    private ChatMessage loadMessage(FileChannel channel) throws IOException {
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

        // Читаем роль
        int roleLength = recordBuffer.getInt();
        byte[] roleBytes = new byte[roleLength];
        recordBuffer.get(roleBytes);
        String roleStr = new String(roleBytes, "UTF-8");

        // Читаем контент
        int contentLength = recordBuffer.getInt();
        byte[] contentBytes = new byte[contentLength];
        recordBuffer.get(contentBytes);
        String content = new String(contentBytes, "UTF-8");

        // Читаем метаданные
        int metadataLength = recordBuffer.getInt();
        byte[] metadataBytes = new byte[metadataLength];
        recordBuffer.get(metadataBytes);
        String metadataStr = new String(metadataBytes, "UTF-8");

        // Создаем сообщение
        ChatMessage.Role role = ChatMessage.Role.valueOf(roleStr);
        ChatMessage message = new ChatMessage(role, content);

        // Восстанавливаем метаданные
        try {
            JSONObject metadata = new JSONObject(metadataStr);
            message.getMetadata().put("timestamp", metadata.getLong("timestamp"));
        } catch (Exception e) {
            System.err.println("Ошибка восстановления метаданных: " + e.getMessage());
        }

        return message;
    }
}