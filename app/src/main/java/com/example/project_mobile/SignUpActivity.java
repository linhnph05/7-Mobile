package com.example.project_mobile;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.text.method.PasswordTransformationMethod;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SignUpActivity extends AppCompatActivity {

    private boolean passwordVisible = false;

    private EditText etFullName, etEmail, etPassword;
    private Button   btnSignUp;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionManager.init(this);
        setContentView(R.layout.activity_signup);

        // ── View references ────────────────────────────────────────────
        etFullName   = findViewById(R.id.etFullName);
        etEmail      = findViewById(R.id.etEmail);
        etPassword   = findViewById(R.id.etPassword);
        btnSignUp    = findViewById(R.id.btnSignUp);
        progressBar  = findViewById(R.id.progressBar);

        // ── Back button → go back to login ─────────────────────────────
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // ── Toggle password visibility ─────────────────────────────────
        ImageButton btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnTogglePassword.setOnClickListener(v -> {
            if (passwordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_eye);
                passwordVisible = false;
            } else {
                etPassword.setTransformationMethod(null);
                btnTogglePassword.setImageResource(R.drawable.ic_eye);
                passwordVisible = true;
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // ── "Already have an account? Log In" link ─────────────────────
        TextView tvLogin = findViewById(R.id.tvLogin);
        String fullText = "Already have an account? Log In";
        SpannableString spannable = new SpannableString(fullText);
        int start = fullText.indexOf("Log In");
        int end   = start + "Log In".length();
        spannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
                new StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogin.setText(spannable);
        tvLogin.setMovementMethod(LinkMovementMethod.getInstance());

        // ── Sign Up button ─────────────────────────────────────────────
        btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Validation + network call
    // ─────────────────────────────────────────────────────────────────────

    private void attemptSignUp() {
        String name     = etFullName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // ── Client-side validation ──────────────────────────────────────
        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }
        if (password.length() < 8) {
            etPassword.setError("Password must be at least 8 characters");
            etPassword.requestFocus();
            return;
        }
        if (!password.matches(".*\\d.*")) {
            etPassword.setError("Password must contain at least one number");
            etPassword.requestFocus();
            return;
        }

        // ── Show loading state ─────────────────────────────────────────
        setLoading(true);

        // ── Call Supabase Auth ─────────────────────────────────────────
        AuthRepository.signUp(name, email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(
                            SignUpActivity.this,
                            "Account created! Please check your email to confirm.",
                            Toast.LENGTH_LONG
                    ).show();
                    // Navigate to Login after successful sign-up
                    Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Toggles the loading spinner and disables the sign-up button while loading. */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!loading);
        btnSignUp.setText(loading ? "" : "Sign Up");
    }
}
