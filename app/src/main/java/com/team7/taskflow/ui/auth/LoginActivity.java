package com.team7.taskflow.ui.auth;

import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.auth.LoginCallback;
import com.team7.taskflow.auth.LoginStrategy;
import com.team7.taskflow.auth.LoginStrategyFactory;
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

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    private boolean passwordVisible = false;

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private AppCompatButton btnGoogle;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Restore saved dark/light preference before any view is inflated
        ProfileActivity.applySavedTheme(this);

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
                                "Google sign-in failed before receiving account data.",
                                Toast.LENGTH_LONG).show();
                    }
                });

        btnGoogle.setOnClickListener(v -> {
            Intent signInIntent = GoogleAuthHelper.getSignInIntent(this);
            if (signInIntent == null) {
                Toast.makeText(this,
                        "Google Sign-In chưa được cấu hình. Vui lòng thêm GOOGLE_WEB_CLIENT_ID vào file .env",
                        Toast.LENGTH_LONG).show();
                return;
            }
            setGoogleLoading(true);
            googleSignInLauncher.launch(signInIntent);
        });

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword
                .setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));

        // "Don't have an account? Sign Up" link
        TextView tvSignUp = findViewById(R.id.tvSignUp);
        String text = "Don't have an account? Sign Up";
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf("Sign Up");
        int end = start + "Sign Up".length();
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
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        Log.d(TAG, "attemptLogin: email=" + email + " passLen=" + password.length());

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Vui lòng nhập email");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Định dạng email không hợp lệ");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Vui lòng nhập mật khẩu");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);
        LoginStrategy strategy = LoginStrategyFactory.create();
        Log.d(TAG, "Using auth strategy: " + strategy.getClass().getSimpleName());

        strategy.login(email, password, new LoginCallback() {
            @Override
            public void onSuccess(String userId, String displayName) {
                runOnUiThread(() -> {
                    setLoading(false);
                    SessionManager.saveSession(
                            SessionManager.getAccessToken(),
                            SessionManager.getRefreshToken(),
                            userId,
                            displayName);
                    Toast.makeText(LoginActivity.this,
                            "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
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
        btnLogin.setText(loading ? "" : "Log In");
        btnGoogle.setEnabled(!loading);
    }

    private void setGoogleLoading(boolean loading) {
        btnGoogle.setEnabled(!loading);
        btnGoogle.setText(loading ? "Signing in…" : "Google");
        btnLogin.setEnabled(!loading);
        progressBar.setVisibility(View.GONE);
    }

    private void goToMain() {
        Log.d(TAG, "goToMain: launching DashboardActivity");
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
