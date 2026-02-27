package com.example.project_mobile;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SignUpActivity extends AppCompatActivity {

    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Toggle password visibility
        EditText etPassword = findViewById(R.id.etPassword);
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

        // Styled "Already have an account? Log In"
        TextView tvLogin = findViewById(R.id.tvLogin);
        String fullText = "Already have an account? Log In";
        SpannableString spannable = new SpannableString(fullText);
        int start = fullText.indexOf("Log In");
        int end = start + "Log In".length();
        spannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.primary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
                new StyleSpan(Typeface.BOLD),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogin.setText(spannable);
        tvLogin.setMovementMethod(LinkMovementMethod.getInstance());

        // Sign Up button click (placeholder)
        findViewById(R.id.btnSignUp).setOnClickListener(v -> {
            // TODO: handle sign up
        });
    }
}
