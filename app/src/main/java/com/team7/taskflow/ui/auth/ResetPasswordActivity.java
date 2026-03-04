package com.team7.taskflow.ui.auth;

import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class ResetPasswordActivity extends BaseActivity {

    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnResetPassword;
    private ProgressBar progressBar;

    private String accessToken;
    private boolean verifyingLink = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);

        SessionManager.init(this);

        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        progressBar = findViewById(R.id.progressBar);

        Intent intent = getIntent();
        accessToken = extractAccessToken(intent);
        if (TextUtils.isEmpty(accessToken)) {
            String recoveryToken = extractRecoveryToken(intent);
            if (TextUtils.isEmpty(recoveryToken)) {
                Toast.makeText(this,
                        "Invalid or expired reset link. Please request a new one.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            verifyRecoveryToken(recoveryToken);
        }

        btnResetPassword.setOnClickListener(v -> submit());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        accessToken = extractAccessToken(intent);
        if (TextUtils.isEmpty(accessToken)) {
            String recoveryToken = extractRecoveryToken(intent);
            if (!TextUtils.isEmpty(recoveryToken)) {
                verifyRecoveryToken(recoveryToken);
            }
        }
    }

    private void submit() {
        if (verifyingLink) {
            Toast.makeText(this, "Please wait while verifying recovery link.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(accessToken)) {
            Toast.makeText(this,
                    "Recovery session not found. Please open the reset link again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String password = etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (password.length() < 8) {
            etNewPassword.setError("Password must be at least 8 characters");
            etNewPassword.requestFocus();
            return;
        }
        if (!password.matches(".*\\d.*")) {
            etNewPassword.setError("Password must contain at least one number");
            etNewPassword.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        setLoading(true);
        AuthRepository.updatePassword(accessToken, password, new AuthRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setLoading(false);
                    SessionManager.clearSession();
                    Toast.makeText(ResetPasswordActivity.this,
                            "Password updated successfully. Please log in.",
                            Toast.LENGTH_LONG).show();
                    goToLogin();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!loading);
        btnResetPassword.setText(loading ? "" : "Update Password");
    }

    private void verifyRecoveryToken(String token) {
        verifyingLink = true;
        setLoading(true);
        AuthRepository.verifyRecoveryToken(token, new AuthRepository.TokenCallback() {
            @Override
            public void onSuccess(String newAccessToken, String refreshToken) {
                runOnUiThread(() -> {
                    verifyingLink = false;
                    accessToken = newAccessToken;
                    setLoading(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    verifyingLink = false;
                    setLoading(false);
                    Toast.makeText(ResetPasswordActivity.this,
                            message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String extractAccessToken(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null)
            return "";

        String tokenFromQuery = data.getQueryParameter("access_token");
        if (!TextUtils.isEmpty(tokenFromQuery))
            return tokenFromQuery;

        String tokenFromFragment = getParamFromFragment(data.getFragment(), "access_token");
        if (!TextUtils.isEmpty(tokenFromFragment))
            return tokenFromFragment;

        String raw = data.toString();
        int hash = raw.indexOf('#');
        if (hash >= 0 && hash + 1 < raw.length()) {
            return getParamFromFragment(raw.substring(hash + 1), "access_token");
        }
        return "";
    }

    private String extractRecoveryToken(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null)
            return "";

        String fromQuery = data.getQueryParameter("token");
        if (!TextUtils.isEmpty(fromQuery))
            return fromQuery;

        String fromQueryHash = data.getQueryParameter("token_hash");
        if (!TextUtils.isEmpty(fromQueryHash))
            return fromQueryHash;

        return "";
    }

    private String getParamFromFragment(String fragment, String key) {
        if (TextUtils.isEmpty(fragment))
            return "";

        String[] parts = fragment.split("&");
        for (String part : parts) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                try {
                    return URLDecoder.decode(pair[1], "UTF-8");
                } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
                    return pair[1];
                }
            }
        }
        return "";
    }
}
