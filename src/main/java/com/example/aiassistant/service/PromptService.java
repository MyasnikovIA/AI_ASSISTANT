package com.example.aiassistant.service;

import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PromptService {
    private static final String PROMPTS_FILE = "prompts.json";

    public PromptService() {
        // Создаем директорию если не существует
        try {
            Files.createDirectories(Paths.get("."));
        } catch (IOException e) {
            System.err.println("Ошибка создания директории: " + e.getMessage());
        }
    }

    // Сохранение промптов в файл
    public synchronized boolean savePrompts(JSONObject prompts) {
        try (FileWriter file = new FileWriter(PROMPTS_FILE)) {
            file.write(prompts.toString(4)); // 4 - количество пробелов для отступов
            return true;
        } catch (IOException e) {
            System.err.println("Ошибка сохранения промптов: " + e.getMessage());
            return false;
        }
    }

    // Загрузка промптов из файла
    public synchronized JSONObject loadPrompts() {
        File file = new File(PROMPTS_FILE);
        if (!file.exists() || file.length() == 0) {
            System.out.println("Файл промптов не найден или пуст");
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(Paths.get(PROMPTS_FILE)));
            return new JSONObject(content);
        } catch (Exception e) {
            System.err.println("Ошибка загрузки промптов: " + e.getMessage());
            return null;
        }
    }

    // Проверка существования файла промптов
    public boolean promptsFileExists() {
        File file = new File(PROMPTS_FILE);
        return file.exists() && file.length() > 0;
    }

    // Удаление файла промптов
    public boolean deletePromptsFile() {
        File file = new File(PROMPTS_FILE);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    // Получение информации о файле промптов
    public String getPromptsFileInfo() {
        File file = new File(PROMPTS_FILE);
        if (file.exists()) {
            long size = file.length();
            long lastModified = file.lastModified();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

            return String.format("Файл: %s, Размер: %d байт, Изменен: %s",
                    PROMPTS_FILE, size, sdf.format(new java.util.Date(lastModified)));
        } else {
            return "Файл промптов не найден";
        }
    }
}