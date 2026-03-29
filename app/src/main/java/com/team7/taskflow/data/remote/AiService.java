package com.team7.taskflow.data.remote;

import android.util.Log;

import com.team7.taskflow.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service to call Gemini API for natural language → structured Task parsing.
 * Uses OkHttp directly (separate from Supabase client) to call Google's Gemini REST API.
 */
public class AiService {

    private static final String TAG = "AiService";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

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
     *
     * @param prompt     Raw text from user (e.g. "Gửi email cho Đức ngày mai, khẩn cấp")
     * @param membersCsv Comma-separated list of project member names for assignee matching
     * @param callback   Returns a ParsedTask on success
     */
    public void parsePrompt(String prompt, String membersCsv, AiCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_GEMINI_KEY_HERE")) {
            // Fallback to local parsing if no API key configured
            callback.onError("GEMINI_API_KEY not configured");
            return;
        }

        String systemPrompt = buildSystemPrompt(membersCsv);

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

            // Generation config — force JSON output
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.1);
            genConfig.put("response_mime_type", "application/json");
            requestJson.put("generation_config", genConfig);

            String url = GEMINI_URL + "?key=" + apiKey;
            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini API call failed", e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "Gemini API error: " + response.code() + " — " + responseBody);
                            callback.onError("AI error: " + response.code());
                            return;
                        }

                        JSONObject json = new JSONObject(responseBody);

                        // Check for safety block or missing candidates
                        if (json.has("promptFeedback") && json.getJSONObject("promptFeedback").has("blockReason")) {
                            callback.onError("Nội dung bị chặn (Safety filter)");
                            return;
                        }

                        if (!json.has("candidates") || json.getJSONArray("candidates").length() == 0) {
                            callback.onError("AI không trả về kết quả");
                            return;
                        }

                        JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                        
                        // Check if candidate was blocked post-generation
                        if (candidate.has("finishReason") && candidate.getString("finishReason").equals("SAFETY")) {
                            callback.onError("Kết quả bị chặn (Safety filter)");
                            return;
                        }

                        String text = candidate
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        // Clean potential markdown formatting
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

                        // Validate priority
                        if (!result.priority.equals("HIGH") && !result.priority.equals("MEDIUM") && !result.priority.equals("LOW")) {
                            result.priority = "MEDIUM";
                        }

                        callback.onSuccess(result);

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse Gemini response", e);
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to build Gemini request", e);
            callback.onError("Request error: " + e.getMessage());
        }
    }

    private String cleanJsonString(String input) {
        String result = input.trim();
        if (result.startsWith("```")) {
            // Remove ```json and ```
            result = result.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        return result.trim();
    }

    private String buildSystemPrompt(String membersCsv) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH);
        String currentDateTime = sdf.format(cal.getTime());

        return "You are a task extraction assistant for a project management app called TaskFlow. " +
                "The user writes a sentence in Vietnamese or English. " +
                "Extract structured task information and return ONLY valid JSON. " +
                "\n\nJSON schema:\n" +
                "{\n" +
                "  \"title\": \"string (A short, summarized task title. DO NOT just copy the user's input. Make it concise and actionable)\",\n" +
                "  \"description\": \"string (additional details or the full original prompt if the title is too short)\",\n" +
                "  \"priority\": \"HIGH | MEDIUM | LOW\",\n" +
                "  \"assignee_name\": \"string (matched from team members list)\",\n" +
                "  \"tag\": \"string (Backend, Frontend, Design, Bug, or empty)\",\n" +
                "  \"start_date\": \"string (ISO 8601 yyyy-MM-ddTHH:mm:ss)\",\n" +
                "  \"due_date\": \"string (ISO 8601 yyyy-MM-ddTHH:mm:ss)\"\n" +
                "}\n\n" +
                "Example:\n" +
                "Input: 'Nhắn tin cho anh Đức bảo fix bug login gấp chiều mai'\n" +
                "Output: {\n" +
                "  \"title\": \"Fix bug login\",\n" +
                "  \"description\": \"Nhắn tin cho anh Đức bảo fix bug login gấp\",\n" +
                "  \"priority\": \"HIGH\",\n" +
                "  \"assignee_name\": \"Đức\",\n" +
                "  \"tag\": \"Bug\",\n" +
                "  \"start_date\": \"\",\n" +
                "  \"due_date\": \"[Calculate correctly based on tomorrow afternoon]\"\n" +
                "}\n\n" +
                "Context:\n" +
                "- Current date and time: " + currentDateTime + "\n" +
                "- Team members: " + (membersCsv.isEmpty() ? "unknown" : membersCsv) + "\n\n" +
                "Strict Rules:\n" +
                "- TITLE: Summarize. If input is 'Hôm nay đi chợ mua rau', title should be 'Mua rau'.\n" +
                "- DATES: Relative dates (tomorrow, next week, chiều, tối) MUST be converted to absolute ISO 8601 format using the current context.\n" +
                "- Return ONLY raw JSON.";
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
