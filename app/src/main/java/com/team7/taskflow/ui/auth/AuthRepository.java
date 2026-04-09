package com.team7.taskflow.ui.auth;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.utils.SessionManager;

/**
 * Repository that calls the Supabase Auth REST API.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";

    public interface AuthCallback {
        void onSuccess(String userId);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface TokenCallback {
        void onSuccess(String accessToken, String refreshToken);
        void onError(String message);
    }

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static void signUp(String fullName, String email, String password, AuthCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("password", password);

            JSONObject userMeta = new JSONObject();
            userMeta.put("full_name", fullName);
            data.put("data", userMeta);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/signup")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (response.isSuccessful()) {
                            JSONObject userJson = json.optJSONObject("user");
                            String userId = userJson != null ? userJson.optString("id", "") : json.optString("id", "");
                            String userEmail = userJson != null ? userJson.optString("email", email) : email;
                            String accessToken = json.optString("access_token", "");
                            String refreshToken = json.optString("refresh_token", "");

                            if (!accessToken.isEmpty() && !userId.isEmpty()) {
                                SessionManager.saveSession(accessToken, refreshToken, userId, userEmail, fullName);
                                upsertUserProfile(userId, userEmail, fullName, "", null, accessToken, new ActionCallback() {
                                    @Override
                                    public void onSuccess() {
                                        callback.onSuccess(userId);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                            } else {
                                callback.onSuccess(userId);
                            }
                        } else {
                            String msg = json.optString("msg",
                                    json.optString("message", "Sign up failed. Please try again."));
                            callback.onError(msg);
                        }
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void signIn(String email, String password, AuthCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("password", password);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject json = new JSONObject(responseBody);

                        if (response.isSuccessful()) {
                            String accessToken = json.optString("access_token", "");
                            String refreshToken = json.optString("refresh_token", "");

                            JSONObject userJson = json.optJSONObject("user");
                            String userId = userJson != null ? userJson.optString("id", "") : "";
                            String userEmail = userJson != null ? userJson.optString("email", "") : email;

                            String displayName = extractDisplayName(userJson);
                            String avatarUrl = extractAvatarUrl(userJson);

                            SessionManager.saveSession(accessToken, refreshToken, userId, userEmail, displayName);
                            upsertUserProfile(userId, userEmail, displayName, "auth_managed", avatarUrl, accessToken,
                                    new ActionCallback() {
                                        @Override
                                        public void onSuccess() {
                                            callback.onSuccess(userId);
                                        }

                                        @Override
                                        public void onError(String message) {
                                            callback.onError(message);
                                        }
                                    });
                        } else {
                            String msg = json.optString("error_description",
                                    json.optString("msg",
                                            json.optString("message", "Đăng nhập thất bại.")));
                            callback.onError(msg);
                        }
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void signInWithGoogle(String idToken, AuthCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("provider", "google");
            data.put("id_token", idToken);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=id_token")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (response.isSuccessful()) {
                            String accessToken = json.optString("access_token", "");
                            String refreshToken = json.optString("refresh_token", "");

                            JSONObject userJson = json.optJSONObject("user");
                            String userId = userJson != null ? userJson.optString("id", "") : "";
                            String userEmail = userJson != null ? userJson.optString("email", "") : "";

                            String displayName = extractDisplayName(userJson);
                            String avatarUrl = extractAvatarUrl(userJson);

                            SessionManager.saveSession(accessToken, refreshToken, userId, userEmail, displayName);
                            upsertUserProfile(userId, userEmail, displayName, "auth_managed", avatarUrl, accessToken,
                                    new ActionCallback() {
                                        @Override
                                        public void onSuccess() {
                                            callback.onSuccess(userId);
                                        }

                                        @Override
                                        public void onError(String message) {
                                            callback.onError(message);
                                        }
                                    });
                        } else {
                            String msg = json.optString("error_description",
                                    json.optString("msg",
                                            json.optString("message", "Google sign-in failed.")));
                            callback.onError(msg);
                        }
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void signInWithGithubTokens(String accessToken, String refreshToken, AuthCallback callback) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("Missing GitHub access token.");
            return;
        }

        final String safeRefreshToken = refreshToken != null ? refreshToken : "";

        try {
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .get()
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError(extractErrorMessage(responseBody,
                                "GitHub sign-in failed. Unable to fetch user profile."));
                        return;
                    }

                    try {
                        JSONObject userJson = new JSONObject(responseBody);
                        String userId = userJson.optString("id", "");
                        String userEmail = userJson.optString("email", "");

                        String displayName = extractDisplayName(userJson);
                        String avatarUrl = extractAvatarUrl(userJson);

                        SessionManager.saveSession(accessToken, safeRefreshToken, userId, userEmail, displayName);
                        upsertUserProfile(userId, userEmail, displayName, "auth_managed", avatarUrl, accessToken,
                                new ActionCallback() {
                                    @Override
                                    public void onSuccess() {
                                        callback.onSuccess(userId);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void requestPasswordReset(String email, String redirectTo, ActionCallback callback) {
        try {
            HttpUrl base = HttpUrl.parse(SupabaseConfig.SUPABASE_URL + "/auth/v1/recover");
            if (base == null) {
                callback.onError("Invalid Supabase URL configuration.");
                return;
            }

            HttpUrl url = base.newBuilder()
                    .addQueryParameter("redirect_to", redirectTo)
                    .build();

            JSONObject data = new JSONObject();
            data.put("email", email);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                        return;
                    }
                    callback.onError(extractErrorMessage(responseBody,
                            "Failed to send reset email. Please try again."));
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void updatePassword(String accessToken, String newPassword, ActionCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("password", newPassword);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .put(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                        return;
                    }
                    callback.onError(extractErrorMessage(responseBody,
                            "Failed to reset password. The recovery link may be expired."));
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    public static void verifyRecoveryToken(String token, TokenCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("type", "recovery");
            data.put("token", token);
            data.put("token_hash", token);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/verify")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError(extractErrorMessage(responseBody,
                                "Invalid or expired recovery link."));
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseBody);
                        String at = json.optString("access_token", "");
                        String rt = json.optString("refresh_token", "");
                        if (at.isEmpty()) {
                            callback.onError("Recovery token verified, but no session returned.");
                            return;
                        }
                        callback.onSuccess(at, rt);
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    private static String extractErrorMessage(String responseBody, String fallback) {
        try {
            JSONObject json = new JSONObject(responseBody);
            return json.optString("error_description",
                    json.optString("msg", json.optString("message", fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String extractDisplayName(JSONObject userJson) {
        if (userJson == null) {
            return null;
        }
        JSONObject meta = userJson.optJSONObject("user_metadata");
        if (meta == null) {
            return null;
        }

        String[] keys = new String[] {"full_name", "name", "user_name"};
        for (String key : keys) {
            String value = meta.optString(key, "");
            if (!value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String extractAvatarUrl(JSONObject userJson) {
        if (userJson == null) {
            return null;
        }
        JSONObject meta = userJson.optJSONObject("user_metadata");
        if (meta == null) {
            return null;
        }

        String[] keys = new String[] {"avatar_url", "picture"};
        for (String key : keys) {
            String value = meta.optString(key, "");
            if (!value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static void upsertUserProfile(
            String userId,
            String email,
            String displayName,
            String passwordHash,
            String avatarUrl,
            String accessToken,
            ActionCallback callback
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("Auth succeeded but missing user ID.");
            return;
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("Auth succeeded but missing access token to sync profile.");
            return;
        }

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("user_id", userId);
            bodyMap.put("email", email != null ? email : "");
            bodyMap.put("password_hash", (passwordHash == null || passwordHash.trim().isEmpty()) ? "auth_managed" : passwordHash);
            if (displayName != null && !displayName.trim().isEmpty()) {
                bodyMap.put("display_name", displayName.trim());
            }
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                bodyMap.put("avatar_url", avatarUrl.trim());
            }

            RequestBody body = RequestBody.create(new JSONObject(bodyMap).toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.REST_URL + "users")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Signed in but failed to sync user profile: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    callback.onError(extractErrorMessage(responseBody,
                            "Signed in but failed to sync user profile to users table."));
                }
            });
        } catch (Exception e) {
            callback.onError("Signed in but failed to build profile sync request: " + e.getMessage());
        }
    }
}
