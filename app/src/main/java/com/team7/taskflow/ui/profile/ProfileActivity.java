package com.team7.taskflow.ui.profile;
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
import com.team7.taskflow.utils.LanguageManager;
import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.utils.NavigationUtils;
import com.team7.taskflow.BuildConfig;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.team7.taskflow.ui.project.CreateProjectActivity;

public class ProfileActivity extends BaseActivity {

    private static final String PREFS_THEME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    private SwitchCompat switchDarkMode;
    private TextView tvProfileName, btnSave, tvJoinedDate, tvLanguageValue;
    private EditText etName, etBio, etEmail;
    private ImageView ivAvatar;
    private CardView avatarCard;
    private View btnLogout;
    private View rowProjectTrash;
    private View rowLanguage;
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
        applyNavTransitionIfNeeded();

        initViews();
        setupThemeSwitch();
        setupLanguageRow();
        setupBottomNavigation();
        setupLogout();
        setupSaveButton();
        setupImagePicker();
        setupProjectTrash();

        userRepository = new UserRepository();
        loadUserProfile();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyNavTransitionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isBottomNavNavigating = false;
        applyNavTransitionIfNeeded();
        updateLanguageLabel();
        
        // Update bottom navigation selected item to ensure icon highlights correctly
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setItemIconTintList(null);
            bottomNav.getMenu().findItem(R.id.nav_settings).setChecked(true);
        }
    }

    private void applyNavTransitionIfNeeded() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        if (!intent.hasExtra(NavigationUtils.EXTRA_NAV_FROM)
                || !intent.hasExtra(NavigationUtils.EXTRA_NAV_TO)) {
            return;
        }

        NavigationUtils.applyTopContentSlideAnimation(this, findViewById(R.id.scrollView));

        intent.removeExtra(NavigationUtils.EXTRA_NAV_FROM);
        intent.removeExtra(NavigationUtils.EXTRA_NAV_TO);
    }

    private void initViews() {
        switchDarkMode = findViewById(R.id.switchDarkMode);
        tvProfileName = findViewById(R.id.tvProfileName);
        btnSave = findViewById(R.id.btnSave);
        tvJoinedDate = findViewById(R.id.tvJoinedDate);
        tvLanguageValue = findViewById(R.id.tvLanguageValue);
        etName = findViewById(R.id.etName);
        etBio = findViewById(R.id.etBio);
        etEmail = findViewById(R.id.etEmail);
        ivAvatar = findViewById(R.id.ivAvatar);
        avatarCard = findViewById(R.id.avatarCard);
        btnLogout = findViewById(R.id.btnLogout);
        fabAdd = findViewById(R.id.fabAdd);
        rowLanguage = findViewById(R.id.rowLanguage);
        rowProjectTrash = findViewById(R.id.rowProjectTrash);

        // Hiển thị email từ session ngay lập tức
        String email = SessionManager.getUserEmail();
        if (etEmail != null && !android.text.TextUtils.isEmpty(email)) {
            etEmail.setText(email);
        }

        AvatarUiUtils.bindAvatarOrFallback(
            ivAvatar,
            null,
            null,
            SessionManager.getDisplayName());

        if (btnSave != null) {
            btnSave.setText(R.string.profile_save);
        }

        if (tvJoinedDate != null) {
            // Placeholder initially
            tvJoinedDate.setText("");
        }

        updateLanguageLabel();
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
                if (pickMedia != null) {
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                }
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> startActivity(new Intent(this, CreateProjectActivity.class)));
        }
    }

    private void setupThemeSwitch() {
        SharedPreferences prefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(KEY_DARK_MODE, false);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(isDark);
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(
                        isChecked ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }
    }

    private void setupLanguageRow() {
        if (rowLanguage != null) {
            rowLanguage.setOnClickListener(v -> showLanguageDialog());
        }
    }

    private void updateLanguageLabel() {
        if (tvLanguageValue != null) {
            tvLanguageValue.setText(LanguageManager.getCurrentLanguageLabel(this));
        }
    }

    private void showLanguageDialog() {
        final String[] languageTags = new String[] {
                LanguageManager.LANGUAGE_ENGLISH,
                LanguageManager.LANGUAGE_VIETNAMESE
        };
        final String[] languageLabels = new String[] {
                getString(R.string.language_english),
                getString(R.string.language_vietnamese)
        };
        int checkedItem = LanguageManager.getSelectedIndex(this);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_language_dialog_title)
                .setSingleChoiceItems(languageLabels, checkedItem, (dialog, which) -> {
                    LanguageManager.setLanguage(ProfileActivity.this, languageTags[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
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
                    boolean started = NavigationUtils.startActivityWithNavAnimation(
                            this, intent, NavigationUtils.NAV_SETTINGS, NavigationUtils.NAV_HOME);
                    if (!started) {
                        isBottomNavNavigating = false;
                    }
                    return true;
                } else if (id == R.id.nav_tasks) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, ForYouActivity.class);
                    boolean started = NavigationUtils.startActivityWithNavAnimation(
                            this, intent, NavigationUtils.NAV_SETTINGS, NavigationUtils.NAV_TASKS);
                    if (!started) {
                        isBottomNavNavigating = false;
                    }
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
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (etName == null || etBio == null || etEmail == null) return;
                
                String name = etName.getText().toString().trim();
                String bio = etBio.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String userId = SessionManager.getUserId();

                if (android.text.TextUtils.isEmpty(userId)) return;

                btnSave.setEnabled(false);
                btnSave.setText(R.string.profile_saving);

                if (selectedImageUri != null) {
                    userRepository.uploadAvatar(userId, selectedImageUri, getContentResolver(), new UserRepository.UploadCallback() {
                        @Override
                        public void onSuccess(String publicUrl) {
                            updateProfile(userId, email, name, bio, publicUrl);
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                btnSave.setText(R.string.profile_save);
                                Toast.makeText(ProfileActivity.this,
                                        getString(R.string.profile_image_upload_failed, message),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                } else {
                    updateProfile(userId, email, name, bio, null);
                }
            });
        }
    }

    private void updateProfile(String userId, String email, String name, String bio, String avatarUrl) {
        userRepository.updateUserProfile(userId, email, name, bio, avatarUrl, new UserRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText(R.string.profile_save);
                    tvProfileName.setText(name);
                    selectedImageUri = null;
                    Toast.makeText(ProfileActivity.this,
                            R.string.profile_updated_successfully,
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText(R.string.profile_save);
                    Toast.makeText(ProfileActivity.this,
                            getString(R.string.profile_update_failed, message),
                            Toast.LENGTH_SHORT).show();
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
                    
                    if (tvJoinedDate != null && user.getCreatedAt() != null) {
                        tvJoinedDate.setText(getString(R.string.profile_joined_date_format, 
                                formatDisplayDate(user.getCreatedAt())));
                    }
                });
            }

            @Override
            public void onError(String message) {
                // Không hiển thị lỗi nếu chỉ là do profile chưa được tạo
                if (!"Profile not found".equals(message)) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this,
                                getString(R.string.profile_fetch_failed, message),
                                Toast.LENGTH_SHORT).show();
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

    private String formatDisplayDate(String isoDate) {
        try {
            // isoDate looks like "2024-04-14T09:22:15.123456+00:00"
            // We just need the YYYY-MM-DD part for a quick parse
            if (isoDate.length() >= 10) {
                String datePart = isoDate.substring(0, 10);
                String[] parts = datePart.split("-");
                if (parts.length == 3) {
                    return parts[2] + "/" + parts[1] + "/" + parts[0];
                }
            }
            return isoDate;
        } catch (Exception e) {
            return isoDate;
        }
    }
}
