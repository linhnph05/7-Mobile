package com.team7.taskflow.ui.profile;

import com.bumptech.glide.Glide;
import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.repository.UserRepository;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.common.AvatarUiUtils;
import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.foryou.ForYouActivity;
import com.team7.taskflow.ui.notification.NotificationPushScheduler;
import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.utils.NavigationUtils;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.team7.taskflow.ui.project.CreateProjectActivity;

public class ProfileActivity extends BaseActivity {

    private static final String PREFS_THEME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    private SwitchCompat switchDarkMode;
    private TextView tvProfileName, btnSave;
    private EditText etName, etBio, etEmail;
    private ImageView ivAvatar;
    private CardView avatarCard;
    private Button btnLogout;
    private View rowProjectTrash;
    private FloatingActionButton fabAdd;
    private UserRepository userRepository;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private Uri selectedImageUri;
    private boolean isBottomNavNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Khởi tạo SessionManager
        SessionManager.init(this);
        NavigationUtils.applyTopContentSlideAnimation(this, findViewById(R.id.scrollView));

        initViews();
        setupThemeSwitch();
        setupBottomNavigation();
        setupLogout();
        setupSaveButton();
        setupImagePicker();
        setupProjectTrash();

        userRepository = new UserRepository();
        loadUserProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isBottomNavNavigating = false;
        // Update bottom navigation selected item to ensure icon highlights correctly
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setItemIconTintList(null);
            bottomNav.getMenu().findItem(R.id.nav_settings).setChecked(true);
        }
    }

    private void initViews() {
        switchDarkMode = findViewById(R.id.switchDarkMode);
        tvProfileName = findViewById(R.id.tvProfileName);
        btnSave = findViewById(R.id.btnSave);
        etName = findViewById(R.id.etName);
        etBio = findViewById(R.id.etBio);
        etEmail = findViewById(R.id.etEmail);
        ivAvatar = findViewById(R.id.ivAvatar);
        avatarCard = findViewById(R.id.avatarCard);
        btnLogout = findViewById(R.id.btnLogout);
        fabAdd = findViewById(R.id.fabAdd);
        rowProjectTrash = findViewById(R.id.rowProjectTrash);

        // Hiển thị email từ session ngay lập tức
        String email = SessionManager.getUserEmail();
        if (etEmail != null && !email.isEmpty()) {
            etEmail.setText(email);
        }

        AvatarUiUtils.bindAvatarOrFallback(
            ivAvatar,
            null,
            null,
            SessionManager.getDisplayName());
    }

    private void setupImagePicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                AvatarUiUtils.bindAvatarOrFallback(ivAvatar, null, uri.toString(), SessionManager.getDisplayName());
            }
        });

        if (avatarCard != null) {
            avatarCard.setOnClickListener(v -> {
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> startActivity(new Intent(this, CreateProjectActivity.class)));
        }
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
            bottomNav.setItemIconTintList(null);
            bottomNav.getMenu().findItem(R.id.nav_settings).setChecked(true);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (isBottomNavNavigating) {
                    return true;
                }
                if (id == R.id.nav_settings) {
                    return true;
                } else if (id == R.id.nav_home) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, DashboardActivity.class);
                    NavigationUtils.startActivityWithNavAnimation(this, intent, NavigationUtils.NAV_SETTINGS, NavigationUtils.NAV_HOME);
                    finish();
                    return true;
                } else if (id == R.id.nav_tasks) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, ForYouActivity.class);
                    NavigationUtils.startActivityWithNavAnimation(this, intent, NavigationUtils.NAV_SETTINGS, NavigationUtils.NAV_TASKS);
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
                NotificationPushScheduler.cancel(ProfileActivity.this);
                SessionManager.clearSession();
                SupabaseClient.getInstance().clearAccessToken();

                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void setupProjectTrash() {
        if (rowProjectTrash != null) {
            rowProjectTrash.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileActivity.this, ProjectTrashActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String bio = etBio.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String userId = SessionManager.getUserId();

            if (userId == null || userId.isEmpty()) return;

            btnSave.setEnabled(false);
            btnSave.setText("...");

            if (selectedImageUri != null) {
                // 1. Upload ảnh lên Supabase Storage
                userRepository.uploadAvatar(userId, selectedImageUri, getContentResolver(), new UserRepository.UploadCallback() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        // 2. Cập nhật profile với URL ảnh mới và Email
                        updateProfile(userId, email, name, bio, publicUrl);
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save");
                            Toast.makeText(ProfileActivity.this, "Image upload failed: " + message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } else {
                // Chỉ cập nhật text và Email
                updateProfile(userId, email, name, bio, null);
            }
        });
    }

    private void updateProfile(String userId, String email, String name, String bio, String avatarUrl) {
        userRepository.updateUserProfile(userId, email, name, bio, avatarUrl, new UserRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    tvProfileName.setText(name);
                    selectedImageUri = null;
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
    }

    private void loadUserProfile() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) return;

        userRepository.getUserById(userId, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                runOnUiThread(() -> {
                    tvProfileName.setText(user.getDisplayNameOrEmail());
                    etName.setText(user.getDisplayName());
                    etBio.setText(user.getBio());
                    
                    // Nếu từ server trả về email thì cập nhật, không thì giữ email từ session
                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        etEmail.setText(user.getEmail());
                    }
                    
                    AvatarUiUtils.bindAvatarOrFallback(
                            ivAvatar,
                            null,
                            user.getAvatarUrl(),
                            user.getDisplayNameOrEmail());
                });
            }

            @Override
            public void onError(String message) {
                // Không hiển thị lỗi nếu chỉ là do profile chưa được tạo
                if (!"Profile not found".equals(message)) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this, "Error fetching profile: " + message, Toast.LENGTH_SHORT).show();
                    });
                }
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
