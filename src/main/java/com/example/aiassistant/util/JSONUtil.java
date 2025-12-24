package com.example.aiassistant.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JSONUtil {

    public static JSONObject safeLoadJSON(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists() || file.length() == 0) {
                return new JSONObject();
            }

            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            if (content.trim().isEmpty()) {
                return new JSONObject();
            }

            return new JSONObject(content);
        } catch (Exception e) {
            System.err.println("Ошибка загрузки JSON из файла " + filePath + ": " + e.getMessage());
            return new JSONObject();
        }
    }

    public static void safeSaveJSON(String filePath, JSONObject json) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            Files.write(Paths.get(filePath), json.toString(2).getBytes());
        } catch (Exception e) {
            System.err.println("Ошибка сохранения JSON в файл " + filePath + ": " + e.getMessage());
        }
    }

    public static JSONArray optJSONArray(JSONObject json, String key) {
        if (json.has(key) && !json.isNull(key)) {
            try {
                return json.getJSONArray(key);
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }
}