package com.team7.taskflow.ui.profile;

import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.repository.UserRepository;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.utils.SessionManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ProfileActivity extends BaseActivity {

    private static final String PREFS_THEME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    private SwitchCompat switchDarkMode;
    private TextView tvProfileName, btnSave;
    private EditText etName, etBio, etPhone;
    private Button btnLogout;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Khởi tạo SessionManager từ nhánh master
        SessionManager.init(this);

        initViews();
        setupThemeSwitch();
        setupBottomNavigation();
        setupLogout();
        setupSaveButton();

        userRepository = new UserRepository();
        loadUserProfile();
    }

    private void initViews() {
        switchDarkMode = findViewById(R.id.switchDarkMode);
        tvProfileName = findViewById(R.id.tvProfileName);
        btnSave = findViewById(R.id.btnSave);
        etName = findViewById(R.id.etName);
        etBio = findViewById(R.id.etBio);
        etPhone = findViewById(R.id.etPhone);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupThemeSwitch() {
        SharedPreferences prefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDark);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO);
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_settings);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_settings) {
                    return true;
                } else if (id == R.id.nav_home) {
                    startActivity(new Intent(this, DashboardActivity.class));
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupLogout() {
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                SessionManager.clearSession();
                // Tích hợp xóa token Supabase từ nhánh master
                SupabaseClient.getInstance().clearAccessToken();

                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String bio = etBio.getText().toString().trim();
            String userId = SessionManager.getUserId();

            if (userId == null || userId.isEmpty()) return;

            btnSave.setEnabled(false);
            btnSave.setText("...");

            userRepository.updateUserProfile(userId, name, bio, new UserRepository.UpdateCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                        tvProfileName.setText(name);
                        Toast.makeText(ProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                        Toast.makeText(ProfileActivity.this, "Update failed: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void loadUserProfile() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Session expired, please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        userRepository.getUserById(userId, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                runOnUiThread(() -> {
                    tvProfileName.setText(user.getDisplayNameOrEmail());
                    etName.setText(user.getDisplayName());
                    etBio.setText(user.getBio());
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "Error fetching profile: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    public static void applySavedTheme(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }
}