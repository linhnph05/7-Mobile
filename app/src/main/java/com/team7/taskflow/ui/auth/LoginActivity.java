package com.team7.taskflow.ui.auth;

import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.utils.AppConfig;
import com.team7.taskflow.utils.SessionManager;

import com.team7.taskflow.ui.profile.ProfileActivity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Locale;

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    private boolean passwordVisible = false;

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private AppCompatButton btnGoogle;
    private AppCompatButton btnGitHub;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SessionManager.init(this);

        // [DEV] Xóa session mỗi lần mở app để test login
        if (AppConfig.CLEAR_SESSION_ON_START) {
            SessionManager.clearSession();
        }

        // Skip login if already authenticated
        if (SessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        // Apply system bar insets
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnGitHub = findViewById(R.id.btnGitHub);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Password toggle
        ImageButton btnToggle = findViewById(R.id.btnTogglePassword);
        btnToggle.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggle.setImageResource(R.drawable.ic_eye);
                passwordVisible = false;
            } else {
                etPassword.setTransformationMethod(null);
                btnToggle.setImageResource(R.drawable.ic_eye);
                passwordVisible = true;
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // Email/password login
        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "===== btnLogin CLICKED =====");
            attemptLogin();
        });

        // ── Google Sign-In ─────────────────────────────────────────────
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    setGoogleLoading(false);
                    Log.d(TAG, "Google launcher resultCode=" + result.getResultCode());
                    Intent data = result.getData();
                    if (data != null) {
                        GoogleAuthHelper.handleSignInResult(data,
                                new GoogleAuthHelper.GoogleSignInCallback() {
                                    @Override
                                    public void onSuccess(String userId) {
                                        runOnUiThread(() -> goToMain());
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> {
                                            if (!message.equals("Sign-in cancelled.")) {
                                                Toast.makeText(LoginActivity.this, message,
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                });
                        return;
                    }

                    if (result.getResultCode() != RESULT_CANCELED) {
                        Toast.makeText(LoginActivity.this,
                                R.string.auth_google_failed_before_account_data,
                                Toast.LENGTH_LONG).show();
                    }
                });

        btnGoogle.setOnClickListener(v -> {
            Intent signInIntent = GoogleAuthHelper.getSignInIntent(this);
            if (signInIntent == null) {
                Toast.makeText(this,
                        R.string.auth_google_not_configured,
                        Toast.LENGTH_LONG).show();
                return;
            }
            setGoogleLoading(true);
            googleSignInLauncher.launch(signInIntent);
        });

        btnGitHub.setOnClickListener(v -> {
            setGithubLoading(true);
            GithubAuthHelper.startOAuth(this, new GithubAuthHelper.GithubAuthCallback() {
                @Override
                public void onSuccess(String userId) {
                    runOnUiThread(() -> {
                        setGithubLoading(false);
                        goToMain();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        setGithubLoading(false);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword
                .setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));

        // "Don't have an account? Sign Up" link
        TextView tvSignUp = findViewById(R.id.tvSignUp);
        String text = getString(R.string.auth_no_account_sign_up);
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf(getString(R.string.auth_register_button));
        if (start != -1) {
            int end = start + getString(R.string.auth_register_button).length();
            spannable.setSpan(new ForegroundColorSpan(
                    ContextCompat.getColor(this, R.color.primary)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvSignUp.setText(spannable);
            tvSignUp.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            tvSignUp.setText(text);
            tvSignUp.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        Log.d(TAG, "attemptLogin: email=" + email + " passLen=" + password.length());

        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.auth_email_required));
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.auth_invalid_email));
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.auth_password_required));
            etPassword.requestFocus();
            return;
        }

        setLoading(true);
        Log.d(TAG, "Using direct AuthRepository login");

        AuthRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                runOnUiThread(() -> {
                    setLoading(false);
                        Toast.makeText(LoginActivity.this,
                            R.string.auth_login_success, Toast.LENGTH_SHORT).show();
                    goToMain();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "" : getString(R.string.auth_login_button));
        btnGoogle.setEnabled(!loading);
        btnGitHub.setEnabled(!loading);
    }

    private void setGoogleLoading(boolean loading) {
        btnGoogle.setEnabled(!loading);
        btnGoogle.setText(loading ? R.string.auth_signing_in : R.string.auth_google);
        btnLogin.setEnabled(!loading);
        btnGitHub.setEnabled(!loading);
        if (!loading) {
            btnGitHub.setText(R.string.auth_github);
        }
        progressBar.setVisibility(View.GONE);
    }

    private void setGithubLoading(boolean loading) {
        btnGitHub.setEnabled(!loading);
        btnGitHub.setText(loading ? R.string.auth_connecting : R.string.auth_github);
        btnGoogle.setEnabled(!loading);
        btnLogin.setEnabled(!loading);
        if (!loading) {
            btnGoogle.setText(R.string.auth_google);
        }
        progressBar.setVisibility(View.GONE);
    }

    private void goToMain() {
        Log.d(TAG, "goToMain: launching DashboardActivity");
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
