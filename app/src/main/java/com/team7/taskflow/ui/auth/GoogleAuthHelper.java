package com.team7.taskflow.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.team7.taskflow.data.remote.SupabaseConfig;

/**
 * Wraps Google Sign-In (play-services-auth) to obtain a Google ID token,
 * then exchanges it with Supabase via AuthRepository.signInWithGoogle().
 */
public final class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";

    public interface GoogleSignInCallback {
        void onSuccess(String userId);

        void onError(String message);
    }

    public static boolean isConfigured() {
        String clientId = SupabaseConfig.GOOGLE_WEB_CLIENT_ID;
        return clientId != null && !clientId.isEmpty();
    }

    public static Intent getSignInIntent(Context context) {
        if (!isConfigured()) {
            Log.e(TAG, "GOOGLE_WEB_CLIENT_ID is empty — cannot start Google Sign-In. "
                    + "See docs/GOOGLE_OAUTH_SETUP.md for setup instructions.");
            return null;
        }
        GoogleSignInClient client = buildClient(context);
        return client.getSignInIntent();
    }

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
                @Override
                public void onSuccess(String userId) {
                    callback.onSuccess(userId);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in ApiException: statusCode=" + e.getStatusCode()
                    + " message=" + e.getMessage());
            if (e.getStatusCode() == 12501) {
                callback.onError("Sign-in cancelled.");
            } else if (e.getStatusCode() == 10) {
                callback.onError("Google sign-in configuration error (code 10). "
                        + "Register an Android OAuth 2.0 client in Google Cloud Console "
                        + "with SHA-1 fingerprint for package 'com.team7.taskflow'.");
            } else {
                callback.onError("Google sign-in failed (code " + e.getStatusCode() + ").");
            }
        }
    }

    private static GoogleSignInClient buildClient(Context context) {
        String clientId = SupabaseConfig.GOOGLE_WEB_CLIENT_ID;
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (clientId != null && !clientId.isEmpty()) {
            builder.requestIdToken(clientId);
        }

        return GoogleSignIn.getClient(context, builder.build());
    }

    private GoogleAuthHelper() {
    }
}
