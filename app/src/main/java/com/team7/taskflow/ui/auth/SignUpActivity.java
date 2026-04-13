package com.team7.taskflow.ui.auth;

import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.utils.SessionManager;

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
import android.util.Log;
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

public class SignUpActivity extends BaseActivity {

    private static final String TAG = "SignUpActivity";

    private boolean passwordVisible = false;

    private EditText etFullName, etEmail, etPassword;
    private Button btnSignUp;
    private ProgressBar progressBar;
    private AppCompatButton btnGoogle;
    private AppCompatButton btnGitHub;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SessionManager.init(this);
        setContentView(R.layout.activity_signup);

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnGitHub = findViewById(R.id.btnGitHub);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

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

        TextView tvLogin = findViewById(R.id.tvLogin);
        String fullText = getString(R.string.auth_have_account_log_in);
        SpannableString spannable = new SpannableString(fullText);
        String actionText = getString(R.string.auth_login_button);
        int start = fullText.indexOf(actionText);
        int end = start + actionText.length();
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

        btnSignUp.setOnClickListener(v -> attemptSignUp());

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
                                                Toast.makeText(SignUpActivity.this, message,
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                });
                        return;
                    }

                    if (result.getResultCode() != RESULT_CANCELED) {
                        Toast.makeText(SignUpActivity.this,
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
                        Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void attemptSignUp() {
        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError(getString(R.string.auth_full_name_required));
            etFullName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.auth_invalid_email));
            etEmail.requestFocus();
            return;
        }
        if (password.length() < 8) {
            etPassword.setError(getString(R.string.auth_password_min_error));
            etPassword.requestFocus();
            return;
        }
        if (!password.matches(".*\\d.*")) {
            etPassword.setError(getString(R.string.auth_password_number_error));
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        AuthRepository.signUp(name, email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(
                            SignUpActivity.this,
                            R.string.auth_account_created_check_email,
                            Toast.LENGTH_LONG).show();
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

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnSignUp.setEnabled(!loading);
        btnSignUp.setText(loading ? "" : getString(R.string.auth_register_button));
        btnGoogle.setEnabled(!loading);
        btnGitHub.setEnabled(!loading);
    }

    private void setGoogleLoading(boolean loading) {
        btnGoogle.setEnabled(!loading);
        btnGoogle.setText(loading ? R.string.auth_signing_in : R.string.auth_google);
        btnSignUp.setEnabled(!loading);
        btnGitHub.setEnabled(!loading);
        if (!loading) {
            btnGitHub.setText(R.string.auth_github);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setGithubLoading(boolean loading) {
        btnGitHub.setEnabled(!loading);
        btnGitHub.setText(loading ? R.string.auth_connecting : R.string.auth_github);
        btnGoogle.setEnabled(!loading);
        btnSignUp.setEnabled(!loading);
        if (!loading) {
            btnGoogle.setText(R.string.auth_google);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void goToMain() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
