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
                System.out.print("Выберите действие (1-8): ");

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
        System.out.println("4. Сменить модель LLM");
        System.out.println("5. Поиск в базе знаний");
        System.out.println("6. Включить/выключить озвучку");
        System.out.println("7. Очистить историю чата");
        System.out.println("8. Выход");
        System.out.println("=".repeat(30));
    }

    private static void askQuestion(Scanner scanner, AssistantService assistant) {
        System.out.print("\nВведите ваш вопрос: ");
        String question = scanner.nextLine().trim();

        if (question.isEmpty()) {
            System.out.println("Вопрос не может быть пустым.");
            return;
        }

        System.out.println("\n" + "=".repeat(60));
        String answer = assistant.askQuestion(question);
        System.out.println("\n" + "=".repeat(60));
        System.out.println("\n✓ Ответ получен и сохранен в истории");
    }

    private static void addKnowledge(Scanner scanner, AssistantService assistant) {
        System.out.println("\n=== Добавление новых знаний ===");
        System.out.print("Введите источник знаний (например, 'книга', 'статья', 'личный опыт'): ");
        String source = scanner.nextLine().trim();

        if (source.isEmpty()) {
            source = "пользовательский ввод";
        }

        System.out.println("Введите текст для добавления в базу знаний:");
        System.out.println("(Для завершения введите 'END' на отдельной строке)");

        StringBuilder content = new StringBuilder();
        String line;

        while (!(line = scanner.nextLine()).equals("END")) {
            content.append(line).append("\n");
        }

        if (content.length() > 0) {
            assistant.addKnowledge(content.toString().trim(), source);
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
        System.out.println("\n=== Смена модели ===");
        System.out.println("Текущая модель: " + assistant.getCurrentModel());
        System.out.print("Введите название новой модели: ");

        String newModel = scanner.nextLine().trim();

        if (!newModel.isEmpty()) {
            if (assistant.switchModel(newModel)) {
                System.out.println("✓ Модель успешно изменена");
            } else {
                System.out.println("✗ Не удалось изменить модель");
            }
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