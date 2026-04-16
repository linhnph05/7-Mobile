package com.team7.taskflow.data.remote;

import android.util.Log;

import com.team7.taskflow.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.team7.taskflow.domain.model.Task;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service to call Gemini API for natural language → structured Task parsing.
 * Uses OkHttp directly (separate from Supabase client) to call Google's Gemini
 * REST API.
 */
public class AiService {

    private static final String TAG = "AiService";
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GEMINI_URL = GEMINI_BASE_URL + BuildConfig.GEMINI_MODEL + ":generateContent";

    private static AiService instance;
    private final OkHttpClient client;

    private AiService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized AiService getInstance() {
        if (instance == null) {
            instance = new AiService();
        }
        return instance;
    }

    /**
     * Parse a natural language prompt into structured task fields.
     */
    public void getTaskPrioritySuggestion(List<Task> tasks, String currentTime, String currentDate, AiStringCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key missing");
            return;
        }

        StringBuilder taskData = new StringBuilder();
        taskData.append("Dữ liệu phân tích tại: ").append(currentTime).append(" ngày ").append(currentDate).append("\n");
        taskData.append("Danh sách công việc đang chờ:\n");
        for (Task t : tasks) {
            taskData.append("- ").append(t.getTitle())
                    .append(" (Dự án: ").append(t.getProjectName() != null ? t.getProjectName() : "N/A").append(")\n")
                    .append("  Mô tả: ").append(t.getDescription() != null ? t.getDescription() : "Không có")
                    .append("\n  Độ ưu tiên: ").append(t.getPriority())
                    .append("\n  Hạn chót: ").append(t.getDueDate() != null ? t.getDueDate() : "Chưa đặt")
                    .append("\n\n");
        }

        String systemPrompt = "Bạn là một Huấn luyện viên Hiệu suất (Productivity Coach) thân thiện. " +
                "Nhiệm vụ: Phân tích danh sách công việc và đưa ra CHIẾN LƯỢC TẬP TRUNG.\n\n" +
                "Phong cách: Trò chuyện tự nhiên, khích lệ (nhận xét ngắn về số lượng task hoặc ngày mới).\n\n" +
                "Cấu trúc phản hồi:\n" +
                "1. Một câu chào hoặc nhận xét ngắn (1-2 câu).\n" +
                "2. Danh sách 3 điểm mấu chốt dùng gạch đầu dòng • :\n" +
                "• **Tâm điểm**: [Tên Task] - [Dự án]\n" +
                "• **Chiến lược**: [Lý do chọn - tối đa 15 từ]\n" +
                "• **Bắt đầu ngay**: [Hành động nhỏ - tối đa 10 từ]\n" +
                "Lưu ý: Tổng văn bản không quá 50 từ để vừa vặn màn hình mobile.";

        try {
            JSONObject requestJson = new JSONObject();
            JSONObject systemInstruction = new JSONObject();
            systemInstruction.put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt)));
            requestJson.put("system_instruction", systemInstruction);

            JSONArray contents = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            userContent.put("parts", new JSONArray().put(new JSONObject().put("text", taskData.toString())));
            contents.put(userContent);
            requestJson.put("contents", contents);

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.7); // Tăng độ sáng tạo cho Coach
            requestJson.put("generation_config", genConfig);

            String url = GEMINI_URL + "?key=" + apiKey;
            RequestBody body = RequestBody.create(requestJson.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            callback.onError("Error: " + response.code());
                            return;
                        }
                        JSONObject json = new JSONObject(responseBody);
                        if (!json.has("candidates") || json.getJSONArray("candidates").length() == 0) {
                            callback.onError("No candidates");
                            return;
                        }
                        String text = json.getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                                .getString("text").trim();
                        callback.onSuccess(text);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public interface AiStringCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public void parsePrompt(String prompt, String membersCsv, String tagsCsv, String parentTaskTitle,
            AiCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_GEMINI_KEY_HERE")) {
            callback.onError("GEMINI_API_KEY not configured");
            return;
        }

        String systemPrompt = buildSystemPrompt(membersCsv, tagsCsv, parentTaskTitle);
        // ... existing code for building JSON ...
        try {
            JSONObject requestJson = new JSONObject();

            // System instruction
            JSONObject systemInstruction = new JSONObject();
            JSONArray systemParts = new JSONArray();
            systemParts.put(new JSONObject().put("text", systemPrompt));
            systemInstruction.put("parts", systemParts);
            requestJson.put("system_instruction", systemInstruction);

            // User content
            JSONArray contents = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            JSONArray userParts = new JSONArray();
            userParts.put(new JSONObject().put("text", prompt));
            userContent.put("parts", userParts);
            contents.put(userContent);
            requestJson.put("contents", contents);

            // Generation config
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.1);
            genConfig.put("response_mime_type", "application/json");
            requestJson.put("generation_config", genConfig);

            String url = GEMINI_URL + "?key=" + apiKey;
            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            callback.onError("AI error: " + response.code());
                            return;
                        }
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("promptFeedback") && json.getJSONObject("promptFeedback").has("blockReason")) {
                            callback.onError("Safety block");
                            return;
                        }
                        if (!json.has("candidates") || json.getJSONArray("candidates").length() == 0) {
                            callback.onError("No result");
                            return;
                        }
                        JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                        String text = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                                .getString("text");
                        text = cleanJsonString(text);
                        JSONObject parsed = new JSONObject(text);
                        ParsedTask result = new ParsedTask();
                        result.title = parsed.optString("title", "");
                        result.description = parsed.optString("description", "");
                        result.priority = parsed.optString("priority", "MEDIUM").toUpperCase();
                        result.assigneeName = parsed.optString("assignee_name", "");
                        result.tag = parsed.optString("tag", "");
                        result.startDate = parsed.optString("start_date", "");
                        result.dueDate = parsed.optString("due_date", "");
                        callback.onSuccess(result);
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }

    private String cleanJsonString(String input) {
        String result = input.trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        return result.trim();
    }

    private String buildSystemPrompt(String membersCsv, String tagsCsv, String parentTitle) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm",
                java.util.Locale.ENGLISH);
        String currentDateTime = sdf.format(cal.getTime());

        StringBuilder sb = new StringBuilder();
        sb.append("You are a task extraction assistant for 'TaskFlow'. ");
        sb.append(
                "The user writes in Vietnamese or English. Extract ONLY what is explicitly stated or clearly implied.\n\n");

        // Core principle
        sb.append("## GOLDEN RULE\n");
        sb.append(
                "Do NOT guess or fabricate. If the user does not mention something, leave the field as empty string \"\".\n");
        sb.append("Only fill a field if you are confident the user intended it.\n\n");

        // Available values
        sb.append("## AVAILABLE VALUES (only use these if applicable)\n");
        sb.append("- Priorities: HIGH, MEDIUM, LOW, NONE\n");
        sb.append("- Members: [").append(membersCsv.isEmpty() ? "None" : membersCsv).append("]\n");
        sb.append("- Tags: [").append(tagsCsv.isEmpty() ? "None" : tagsCsv).append("]\n\n");

        if (parentTitle != null && !parentTitle.isEmpty()) {
            sb.append("## CONTEXT\n");
            sb.append("This is a SUBTASK of: '").append(parentTitle).append("'.\n\n");
        }

        // Field-by-field rules
        sb.append("## FIELD RULES\n");
        sb.append(
                "- title: Short, actionable summary. Do NOT copy the entire input. E.g. 'Đi chợ mua rau cho mẹ' → 'Mua rau'.\n");
        sb.append(
                "- description: Provide helpful context or details ONLY if the user's input contains extra info beyond the title. ");
        sb.append("Do NOT just repeat the title. If there is nothing extra to say, leave it as \"\".\n");
        sb.append(
                "- priority: Set ONLY if the user explicitly mentions urgency (gấp, khẩn, quan trọng → HIGH; chậm, khi nào rảnh → LOW). ");
        sb.append("Otherwise leave as \"\".\n");
        sb.append(
                "- assignee_name: Set ONLY if the user explicitly names a person AND that person is in the Members list. Otherwise \"\".\n");
        sb.append(
                "- tag: Set ONLY if the user's task clearly relates to a tag (e.g. fix bug → Bug, code API → Backend, thiết kế → Design). ");
        sb.append("Do NOT guess. Otherwise \"\".\n");
        sb.append(
                "- start_date / due_date: Set ONLY if the user mentions a specific time/date (ngày mai, chiều nay, thứ 2, 15/4...). ");
        sb.append("Use ISO 8601 format (yyyy-MM-ddTHH:mm:ss). If only one date is mentioned, put it in due_date. ");
        sb.append(
                "IMPORTANT: due_date MUST be after start_date. If both are the same day, due_date's time must be later. ");
        sb.append("If no time/date is mentioned at all, leave both as \"\".\n\n");

        // JSON schema
        sb.append("## JSON OUTPUT (return ONLY this, no markdown, no explanation)\n");
        sb.append("{\n");
        sb.append("  \"title\": \"\",\n");
        sb.append("  \"description\": \"\",\n");
        sb.append("  \"priority\": \"\",\n");
        sb.append("  \"assignee_name\": \"\",\n");
        sb.append("  \"tag\": \"\",\n");
        sb.append("  \"start_date\": \"\",\n");
        sb.append("  \"due_date\": \"\"\n");
        sb.append("}\n\n");

        sb.append("Current date/time: ").append(currentDateTime).append("\n");

        return sb.toString();
    }

    // ── Data classes ────────────────────────────────────────────────────

    public static class ParsedTask {
        public String title = "";
        public String description = "";
        public String priority = "MEDIUM";
        public String assigneeName = "";
        public String tag = "";
        public String startDate = "";
        public String dueDate = "";
    }
}
