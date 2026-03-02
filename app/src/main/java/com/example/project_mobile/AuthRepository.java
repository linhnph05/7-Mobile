package com.example.project_mobile;

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

/**
 * Repository that calls the Supabase Auth REST API.
 *
 * Docs: https://supabase.com/docs/reference/javascript/auth-signup
 * POST /auth/v1/signup
 */
public class AuthRepository {

    public interface AuthCallback {
        /** Called on success with the signed-in user's id */
        void onSuccess(String userId);
        /** Called on any error with a human-readable message */
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
    //  Sign Up
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new user account via Supabase Auth.
     *
     * @param fullName display name stored in user_metadata
     * @param email    user e-mail
     * @param password raw password (min 8 chars)
     * @param callback result callback (always on background thread – post to main if updating UI)
     */
    public static void signUp(String fullName, String email, String password, AuthCallback callback) {
        try {
            // Build JSON body
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
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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
                            // Supabase returns { id, email, ... } on success
                            String userId = json.optString("id", "");
                            callback.onSuccess(userId);
                        } else {
                            // Error body: { "code": 400, "error_code": "...", "msg": "..." }
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
    //  Sign In  (email + password)
    // ─────────────────────────────────────────────────────────────

    /**
     * Signs in an existing user via Supabase Auth (password grant).
     *
     * @param email    user e-mail
     * @param password raw password
     * @param callback result callback – always on a background thread
     */
    public static void signIn(String email, String password, AuthCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("password", password);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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
                            // Persist session tokens
                            String accessToken  = json.optString("access_token", "");
                            String refreshToken = json.optString("refresh_token", "");
                            String userId       = json.optJSONObject("user") != null
                                    ? json.optJSONObject("user").optString("id", "")
                                    : "";

                            SessionManager.saveSession(accessToken, refreshToken, userId);
                            callback.onSuccess(userId);
                        } else {
                            // { "error": "invalid_grant", "error_description": "..." }
                            String msg = json.optString("error_description",
                                    json.optString("msg",
                                            json.optString("message", "Login failed. Please try again.")));
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
    //  Sign In with Google  (ID-token grant)
    // ─────────────────────────────────────────────────────────────

    /**
     * Exchanges a Google ID token for a Supabase session.
     * Call this after obtaining the ID token from Google's Credential Manager.
     *
     * @param idToken  the Google ID token string
     * @param callback result callback – always on a background thread
     */
    public static void signInWithGoogle(String idToken, AuthCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("provider", "google");
            data.put("id_token", idToken);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=id_token")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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
                            String accessToken  = json.optString("access_token", "");
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
    //  Forgot / Reset Password
    // ─────────────────────────────────────────────────────────────

    /**
     * Sends a Supabase password reset email.
     *
     * @param email      user e-mail
     * @param redirectTo deep-link URL that should open the app reset screen
     */
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
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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

    /**
     * Updates the authenticated user's password via Supabase.
     * Requires the access token from the recovery deep link.
     */
    public static void updatePassword(String accessToken, String newPassword, ActionCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("password", newPassword);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .put(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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

    /**
     * Verifies a recovery token from Supabase reset email link and returns a session.
     * This supports deep links where access_token is not present yet.
     */
    public static void verifyRecoveryToken(String token, TokenCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("type", "recovery");
            // Some flows/documentation refer to token, others token_hash.
            // Send both for compatibility.
            data.put("token", token);
            data.put("token_hash", token);

            RequestBody body = RequestBody.create(data.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/verify")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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
                        String accessToken = json.optString("access_token", "");
                        String refreshToken = json.optString("refresh_token", "");
                        if (accessToken.isEmpty()) {
                            callback.onError("Recovery token verified, but no session returned.");
                            return;
                        }
                        callback.onSuccess(accessToken, refreshToken);
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
