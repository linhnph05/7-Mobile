package com.team7.taskflow.ui.auth;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
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
 *
 * Docs: https://supabase.com/docs/reference/javascript/auth-signup
 * POST /auth/v1/signup
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

    // ─────────────────────────────────────────────────────────────
    // Sign Up
    // ─────────────────────────────────────────────────────────────

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
                            String userId = json.optString("id", "");
                            callback.onSuccess(userId);
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

    // ─────────────────────────────────────────────────────────────
    // Sign In (email + password)
    // ─────────────────────────────────────────────────────────────

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

            Log.d(TAG, "signIn request url=" + request.url());

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "signIn onFailure: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "signIn onResponse code=" + response.code() + " body=" + responseBody);

                    try {
                        JSONObject json = new JSONObject(responseBody);

                        if (response.isSuccessful()) {
                            String accessToken = json.optString("access_token", "");
                            String refreshToken = json.optString("refresh_token", "");
                            String userId = json.optJSONObject("user") != null
                                    ? json.optJSONObject("user").optString("id", "")
                                    : "";

                            SessionManager.saveSession(accessToken, refreshToken, userId);
                            callback.onSuccess(userId);
                        } else {
                            String rawMsg = json.optString("error_description",
                                    json.optString("msg",
                                            json.optString("message", "")));
                            Log.e(TAG, "signIn error response: code=" + response.code()
                                    + " error=" + json.optString("error")
                                    + " desc=" + rawMsg);

                            String msg;
                            if (rawMsg.toLowerCase().contains("invalid login credentials")) {
                                msg = "Email hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.";
                            } else if (rawMsg.toLowerCase().contains("email not confirmed")) {
                                msg = "Email chưa được xác nhận. Vui lòng kiểm tra hộp thư để xác nhận.";
                            } else if (rawMsg.toLowerCase().contains("too many requests")
                                    || response.code() == 429) {
                                msg = "Quá nhiều lần thử. Vui lòng đợi một lát rồi thử lại.";
                            } else if (!rawMsg.isEmpty()) {
                                msg = rawMsg;
                            } else {
                                msg = "Đăng nhập thất bại. Vui lòng thử lại.";
                            }
                            callback.onError(msg);
                        }
                    } catch (Exception e) {
                        callback.onError("Unexpected response from server.");
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "signIn build request error: " + e.getMessage(), e);
            callback.onError("Failed to build request: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Sign In with Google (ID-token grant)
    // ─────────────────────────────────────────────────────────────

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
                            String userId = json.optJSONObject("user") != null
                                    ? json.optJSONObject("user").optString("id", "")
                                    : "";
                            SessionManager.saveSession(accessToken, refreshToken, userId);
                            callback.onSuccess(userId);
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

    // ─────────────────────────────────────────────────────────────
    // Forgot / Reset Password
    // ─────────────────────────────────────────────────────────────

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
}
