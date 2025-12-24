package com.example.aiassistant;

import com.example.aiassistant.service.AssistantService;
import com.example.aiassistant.service.VectorDBService;
import org.json.JSONObject;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Локальный AI Ассистент с RAG ===");
        System.out.println("Версия с сохранением истории, потоковым выводом и озвучкой");
        System.out.println("Инициализация...\n");

        try {
            // Инициализация сервисов
            VectorDBService vectorDB = VectorDBService.getInstance();
            AssistantService assistant = new AssistantService(vectorDB);

            System.out.println("✓ База знаний загружена в память");
            System.out.println("✓ Документов в базе: " + vectorDB.getDocumentCount());
            System.out.println("✓ Текущая модель: " + assistant.getCurrentModel());
            System.out.println("✓ Модель для эмбеддингов: " + assistant.getEmbeddingModel());
            System.out.println("✓ История чата загружена");
            System.out.println("✓ Озвучка: " + (assistant.isSpeechEnabled() ? "ВКЛ" : "ВЫКЛ"));
            System.out.println("✓ Доступно памяти для RAG: ~70 ГБ");
            System.out.println("\n" + "=".repeat(50) + "\n");

            Scanner scanner = new Scanner(System.in);

            // Загрузка начальных знаний (пример)
            loadInitialKnowledge(assistant);

            // Основной цикл взаимодействия
            boolean running = true;
            while (running) {
                printMenu();
                System.out.print("Выберите действие (1-12): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        askQuestion(scanner, assistant);
                        break;
                    case "2":
                        addKnowledge(scanner, assistant);
                        break;
                    case "3":
                        showStatistics(assistant);
                        break;
                    case "4":
                        changeModel(scanner, assistant);
                        break;
                    case "5":
                        searchInKnowledgeBase(scanner, assistant);
                        break;
                    case "6":
                        toggleSpeech(scanner, assistant);
                        break;
                    case "7":
                        clearChatHistory(scanner, assistant);
                        break;
                    case "8":
                        manageModels(scanner, assistant);
                        break;
                    case "9":
                        changeEmbeddingModel(scanner, assistant);
                        break;
                    case "10":
                        listAllModels(scanner, assistant);
                        break;
                    case "11":
                        pullNewModel(scanner, assistant);
                        break;
                    case "12":
                        System.out.println("\nВыход из программы...");
                        running = false;
                        break;
                    default:
                        System.out.println("Неверный выбор. Попробуйте снова.");
                }
            }

            scanner.close();

        } catch (Exception e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Меню ===");
        System.out.println("1. Задать вопрос ассистенту (с историей и потоковым выводом)");
        System.out.println("2. Добавить новые знания в базу");
        System.out.println("3. Показать статистику");
        System.out.println("4. Сменить модель LLM для ответов");
        System.out.println("5. Поиск в базе знаний");
        System.out.println("6. Включить/выключить озвучку");
        System.out.println("7. Очистить историю чата");
        System.out.println("8. Управление моделями");
        System.out.println("9. Сменить модель для эмбеддингов");
        System.out.println("10. Показать все доступные модели");
        System.out.println("11. Загрузить новую модель");
        System.out.println("12. Выход");
        System.out.println("=".repeat(30));
    }

    private static void askQuestion(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Задать вопрос ассистенту ===");
        System.out.println("Текущая модель: " + assistant.getCurrentModel());
        System.out.println("Введите ваш вопрос (можно многострочный):");
        System.out.println("Для завершения введите 'end' на отдельной строке");
        System.out.println("=".repeat(50));

        StringBuilder question = new StringBuilder();
        String line;
        int lineCount = 0;

        while (true) {
            line = scanner.nextLine();

            if (line.trim().equalsIgnoreCase("end")) {
                break;
            }

            if (lineCount == 0 && line.trim().isEmpty()) {
                System.out.println("Вопрос не может начинаться с пустой строки. Продолжайте ввод...");
                continue;
            }

            question.append(line);

            // Если это не последняя строка, добавляем перенос
            if (!line.trim().equalsIgnoreCase("end")) {
                question.append("\n");
            }

            lineCount++;
        }

        String finalQuestion = question.toString().trim();

        if (finalQuestion.isEmpty()) {
            System.out.println("Вопрос не может быть пустым.");
            return;
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Отправка запроса...");
        String answer = assistant.askQuestion(finalQuestion);
        System.out.println("\n" + "=".repeat(60));
        System.out.println("\n✓ Ответ получен и сохранен в истории");
    }

    private static void addKnowledge(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Добавление новых знаний ===");
        System.out.println("Текущая модель для эмбеддингов: " + assistant.getEmbeddingModel());
        System.out.print("Введите источник знаний (например, 'книга', 'статья', 'личный опыт'): ");
        String source = scanner.nextLine().trim();

        if (source.isEmpty()) {
            source = "пользовательский ввод";
        }

        System.out.println("\nВведите текст для добавления в базу знаний:");
        System.out.println("Для завершения введите 'end' на отдельной строке");
        System.out.println("=".repeat(50));

        StringBuilder content = new StringBuilder();
        String line;

        while (!(line = scanner.nextLine()).equalsIgnoreCase("end")) {
            content.append(line).append("\n");
        }

        String finalContent = content.toString().trim();
        if (finalContent.length() > 0) {
            assistant.addKnowledge(finalContent, source);
            System.out.println("✓ Знания успешно добавлены и сохранены в файл");
        } else {
            System.out.println("Текст не был введен.");
        }
    }

    private static void showStatistics(AssistantService assistant) {
        System.out.println("\n=== Статистика системы ===");

        JSONObject stats = assistant.getStatistics();

        System.out.println("Документов в базе знаний: " + stats.getInt("total_documents"));
        System.out.println("Использование памяти: " + stats.getString("memory_usage_percent"));
        System.out.println("Текущая LLM модель: " + stats.getString("llm_model"));
        System.out.println("Модель для эмбеддингов: " + stats.getString("embedding_model"));
        System.out.println("Сообщений в истории чата: " + stats.getInt("chat_history_size"));
        System.out.println("Озвучка: " + (stats.getBoolean("speech_enabled") ? "ВКЛ" : "ВЫКЛ"));

        // Показываем первые 5 сообщений истории
        System.out.println("\nПоследние сообщения из истории:");
        var history = assistant.getChatHistory();
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            var msg = history.get(i);
            String role = msg.getRole().name();
            String content = msg.getContent();
            if (content.length() > 50) {
                content = content.substring(0, 47) + "...";
            }
            System.out.println("  " + role + ": " + content);
        }
    }

    private static void changeModel(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Смена модели для ответов ===");
        System.out.println("Текущая модель: " + assistant.getCurrentModel());

        System.out.println("\nДоступные модели для ответов:");
        var models = assistant.getAvailableModels();
        if (models.isEmpty()) {
            System.out.println("Не удалось получить список моделей");
            return;
        }

        for (int i = 0; i < models.size(); i++) {
            System.out.println((i + 1) + ". " + models.get(i));
        }

        System.out.print("\nВведите номер модели или название новой модели: ");
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        // Проверяем, ввели ли номер
        try {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= models.size()) {
                String selectedModel = models.get(index - 1);
                if (assistant.switchModel(selectedModel)) {
                    System.out.println("✓ Модель успешно изменена на: " + selectedModel);
                } else {
                    System.out.println("✗ Не удалось изменить модель");
                }
                return;
            }
        } catch (NumberFormatException e) {
            // Не число, значит ввели название модели
        }

        // Если ввели название модели
        if (assistant.switchModel(input)) {
            System.out.println("✓ Модель успешно изменена на: " + input);
        } else {
            System.out.println("✗ Не удалось изменить модель");
        }
    }

    private static void searchInKnowledgeBase(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВведите запрос для поиска в базе знаний: ");
        String query = scanner.nextLine().trim();

        if (!query.isEmpty()) {
            assistant.searchKnowledgeBase(query);
        }
    }

    private static void toggleSpeech(Scanner scanner, AssistantService assistant) {
        boolean currentState = assistant.isSpeechEnabled();
        boolean newState = !currentState;

        assistant.setSpeechEnabled(newState);

        System.out.println("✓ Озвучка " + (newState ? "включена" : "выключена"));

        // Демонстрация озвучки, если включили
        if (newState) {
            System.out.println("\n[Демонстрация озвучки...]");
            com.example.aiassistant.util.SpeakToText.speak("Озвучка активирована. Теперь я буду озвучивать ответы.");
            System.out.println("Демонстрация завершена.");
        }
    }

    private static void clearChatHistory(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВы уверены, что хотите очистить историю чата? (y/N): ");
        String confirm = scanner.nextLine().trim();

        if (confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes")) {
            assistant.clearChatHistory();
            System.out.println("✓ История чата очищена");
        } else {
            System.out.println("Очистка отменена");
        }
    }

    // Новый метод для управления моделями
    private static void manageModels(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Управление моделями ===");
        System.out.println("1. Показать информацию о текущей модели");
        System.out.println("2. Удалить модель");
        System.out.println("3. Скопировать модель");
        System.out.println("4. Назад");

        System.out.print("Выберите действие: ");
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                showModelInfo(scanner, assistant);
                break;
            case "2":
                deleteModel(scanner, assistant);
                break;
            case "3":
                copyModel(scanner, assistant);
                break;
            case "4":
                return;
            default:
                System.out.println("Неверный выбор");
        }
    }

    // Новый метод для смены модели эмбеддингов
    private static void changeEmbeddingModel(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Смена модели для эмбеддингов ===");
        System.out.println("Текущая модель: " + assistant.getEmbeddingModel());

        var embeddingModels = assistant.getAvailableEmbeddingModels();
        if (embeddingModels.isEmpty()) {
            System.out.println("Не удалось получить список моделей для эмбеддингов");
            return;
        }

        System.out.println("\nДоступные модели для эмбеддингов:");
        for (int i = 0; i < embeddingModels.size(); i++) {
            System.out.println((i + 1) + ". " + embeddingModels.get(i));
        }

        System.out.print("\nВведите номер модели или название новой модели: ");
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        // Проверяем, ввели ли номер
        try {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= embeddingModels.size()) {
                String selectedModel = embeddingModels.get(index - 1);
                if (assistant.switchEmbeddingModel(selectedModel)) {
                    System.out.println("✓ Модель для эмбеддингов успешно изменена на: " + selectedModel);
                } else {
                    System.out.println("✗ Не удалось изменить модель для эмбеддингов");
                }
                return;
            }
        } catch (NumberFormatException e) {
            // Не число, значит ввели название модели
        }

        // Если ввели название модели
        if (assistant.switchEmbeddingModel(input)) {
            System.out.println("✓ Модель для эмбеддингов успешно изменена на: " + input);
        } else {
            System.out.println("✗ Не удалось изменить модель для эмбеддингов");
        }
    }

    // Новый метод для отображения всех моделей
    private static void listAllModels(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Все доступные модели ===");

        var allModels = assistant.getAvailableModels();
        var embeddingModels = assistant.getAvailableEmbeddingModels();

        System.out.println("\nМодели для ответов (" + allModels.size() + "):");
        for (int i = 0; i < allModels.size(); i++) {
            String model = allModels.get(i);
            String currentMarker = model.equals(assistant.getCurrentModel()) ? " [ТЕКУЩАЯ]" : "";
            System.out.println((i + 1) + ". " + model + currentMarker);
        }

        System.out.println("\nМодели для эмбеддингов (" + embeddingModels.size() + "):");
        for (int i = 0; i < embeddingModels.size(); i++) {
            String model = embeddingModels.get(i);
            String currentMarker = model.equals(assistant.getEmbeddingModel()) ? " [ТЕКУЩАЯ]" : "";
            System.out.println((i + 1) + ". " + model + currentMarker);
        }

        System.out.println("\nНажмите Enter для продолжения...");
        scanner.nextLine();
    }

    // Новый метод для загрузки новой модели
    private static void pullNewModel(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Загрузка новой модели ===");

        System.out.println("Примеры популярных моделей:");
        System.out.println("- deepseek-coder:6.7b");
        System.out.println("- llama2:7b");
        System.out.println("- mistral:7b");
        System.out.println("- codellama:7b");
        System.out.println("- phi:2.7b");
        System.out.println("- qwen:7b");

        System.out.print("\nВведите название модели для загрузки (например, 'deepseek-coder:6.7b'): ");
        String modelName = scanner.nextLine().trim();

        if (modelName.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        System.out.println("Начинаю загрузку модели: " + modelName);
        System.out.println("Это может занять несколько минут в зависимости от размера модели...");

        if (assistant.pullModel(modelName)) {
            System.out.println("✓ Модель успешно загружена: " + modelName);
        } else {
            System.out.println("✗ Не удалось загрузить модель");
        }
    }

    // Новый метод для отображения информации о модели
    private static void showModelInfo(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВведите название модели для просмотра информации (оставьте пустым для текущей): ");
        String modelName = scanner.nextLine().trim();

        if (modelName.isEmpty()) {
            modelName = assistant.getCurrentModel();
        }

        var modelInfo = assistant.getModelInfo(modelName);
        if (modelInfo != null) {
            System.out.println("\n=== Информация о модели: " + modelName + " ===");
            System.out.println("Размер модели: " + modelInfo.optString("size", "неизвестно"));
            System.out.println("Квантование: " + modelInfo.optString("quantization", "неизвестно"));
            System.out.println("Архитектура: " + modelInfo.optString("architecture", "неизвестно"));

            if (modelInfo.has("parameters")) {
                System.out.println("Параметры: " + modelInfo.getString("parameters"));
            }

            if (modelInfo.has("template")) {
                System.out.println("Шаблон промпта: " + modelInfo.getString("template"));
            }
        } else {
            System.out.println("Не удалось получить информацию о модели");
        }
    }

    // Новый метод для удаления модели
    private static void deleteModel(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВведите название модели для удаления: ");
        String modelName = scanner.nextLine().trim();

        if (modelName.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        System.out.print("Вы уверены, что хотите удалить модель '" + modelName + "'? (y/N): ");
        String confirm = scanner.nextLine().trim();

        if (confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes")) {
            if (assistant.deleteModel(modelName)) {
                System.out.println("✓ Модель успешно удалена: " + modelName);
            } else {
                System.out.println("✗ Не удалось удалить модель");
            }
        } else {
            System.out.println("Удаление отменено");
        }
    }

    // Новый метод для копирования модели
    private static void copyModel(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВведите название исходной модели: ");
        String sourceModel = scanner.nextLine().trim();

        if (sourceModel.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        System.out.print("Введите название новой модели: ");
        String targetModel = scanner.nextLine().trim();

        if (targetModel.isEmpty()) {
            System.out.println("Операция отменена");
            return;
        }

        if (assistant.copyModel(sourceModel, targetModel)) {
            System.out.println("✓ Модель успешно скопирована: " + sourceModel + " -> " + targetModel);
        } else {
            System.out.println("✗ Не удалось скопировать модель");
        }
    }

    private static void loadInitialKnowledge(AssistantService assistant) {
        // Проверяем, не пуста ли база знаний
        // Если пуста, загружаем начальные данные
        if (assistant.getStatistics().getInt("total_documents") == 0) {
            System.out.println("База знаний пуста. Загрузка начальных знаний...");

            String[] initialKnowledge = {
                    "Компания ООО 'ТехноСистемы' основана в 2010 году в Москве.",
                    "Основной продукт компании - система автоматизации бизнес-процессов 'БизнесПоток'.",
                    "Сервис OLLAMA позволяет запускать локальные языковые модели на своем компьютере.",
                    "Векторная база данных хранит информацию в виде числовых векторов для быстрого поиска.",
                    "RAG (Retrieval-Augmented Generation) - это техника, которая объединяет поиск информации и генерацию текста.",
                    "Java - объектно-ориентированный язык программирования, созданный Джеймсом Гослингом в 1995 году.",
                    "Для работы с HTTP в Java 11+ можно использовать HttpClient из стандартной библиотеки.",
                    "JSON - текстовый формат обмена данными, основанный на JavaScript.",
                    "Локальный AI ассистент может работать без подключения к интернету, используя локальные модели.",
                    "Эмбеддинг - это представление текста в виде числового вектора для машинного обучения."
            };

            for (int i = 0; i < initialKnowledge.length; i++) {
                assistant.addKnowledge(initialKnowledge[i], "начальная база знаний");
            }

            System.out.println("✓ Загружено " + initialKnowledge.length + " начальных фактов\n");
        }
    }
}