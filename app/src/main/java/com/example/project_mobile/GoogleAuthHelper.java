package com.example.project_mobile;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

/**
 * Wraps Google Sign-In (play-services-auth) to obtain a Google ID token,
 * then exchanges it with Supabase via AuthRepository.signInWithGoogle().
 *
 * Usage (from an Activity):
 *   // 1. In onCreate(), register a launcher:
 *   ActivityResultLauncher<Intent> launcher = registerForActivityResult(
 *       new ActivityResultContracts.StartActivityForResult(), result -> {
 *           if (result.getResultCode() == RESULT_OK && result.getData() != null)
 *               GoogleAuthHelper.handleSignInResult(result.getData(), callback);
 *       });
 *   // 2. On button click:
 *   launcher.launch(GoogleAuthHelper.getSignInIntent(this));
 */
public final class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";

    public interface GoogleSignInCallback {
        void onSuccess(String userId);
        void onError(String message);
    }

    /**
     * Returns the Intent to launch for the Google account picker.
     * Pass this to an ActivityResultLauncher started from an Activity.
     */
    public static Intent getSignInIntent(Context context) {
        return buildClient(context).getSignInIntent();
    }

    /**
     * Call this inside your ActivityResultLauncher callback with the returned data Intent.
     * Extracts the Google ID token and exchanges it with Supabase.
     * The callback methods may be invoked on a background thread — use runOnUiThread for UI work.
     */
    public static void handleSignInResult(Intent data, GoogleSignInCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken == null) {
                Log.e(TAG, "ID token is null — ensure requestIdToken() is set and "
                        + "the Web Client ID is correct in Google Cloud Console.");
                callback.onError("Failed to get Google ID token. Check your OAuth configuration.");
                return;
            }
            Log.d(TAG, "Got Google ID token, exchanging with Supabase...");
            AuthRepository.signInWithGoogle(idToken, new AuthRepository.AuthCallback() {
                @Override public void onSuccess(String userId) { callback.onSuccess(userId); }
                @Override public void onError(String message)  { callback.onError(message); }
            });
        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in ApiException: statusCode=" + e.getStatusCode()
                    + " message=" + e.getMessage());
            if (e.getStatusCode() == 12501) { // SIGN_IN_CANCELLED
                callback.onError("Sign-in cancelled.");
            } else if (e.getStatusCode() == 10) { // DEVELOPER_ERROR
                callback.onError("Google sign-in configuration error (code 10). "
                        + "Register an Android OAuth 2.0 client in Google Cloud Console "
                        + "with SHA-1 fingerprint for package 'com.example.project_mobile'.");
            } else {
                callback.onError("Google sign-in failed (code " + e.getStatusCode() + ").");
            }
        }
    }

    private static GoogleSignInClient buildClient(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(SupabaseConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    private GoogleAuthHelper() {}
}
