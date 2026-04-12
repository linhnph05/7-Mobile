package com.team7.taskflow.ui.dashboard;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.team7.taskflow.ui.foryou.ForYouActivity;
import com.team7.taskflow.ui.profile.ProfileActivity;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.team7.taskflow.ui.base.BaseActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.utils.NavigationUtils;
import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.data.repository.NotificationRepository;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.common.AvatarUiUtils;
import com.team7.taskflow.ui.notification.NotificationsActivity;
import com.team7.taskflow.ui.notification.NotificationPushScheduler;
import com.team7.taskflow.ui.project.CreateProjectActivity;
import com.team7.taskflow.ui.system.StickyTaskService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Dashboard chính - hiển thị danh sách projects
 *
 * Chức năng:
 * - Hiển thị danh sách projects mà user tham gia (owner hoặc member)
 * - Click vào project để vào chi tiết/settings
 * - Click FAB (+) để tạo project mới
 */
public class DashboardActivity extends BaseActivity {

    private static final String TAG = "DashboardActivity";

    // Views
    private FloatingActionButton fabAdd;
    private ImageView btnNotification;
    private TextView tvNotificationBadge;
    private ImageView imgAvatar;
    private TextView tvAvatarLetter;
    private TextView tvWorkspaceName;
    private EditText searchBar;
    private MaterialButton btnFilterAll;
    private MaterialButton btnFilterRecent;
    private MaterialButton btnFilterOwned;
    private RecyclerView rvProjects;
    private BottomNavigationView bottomNavigationView;
    private boolean isBottomNavNavigating = false;
    private boolean isWhiteNoisePlaying = false;
    private WhiteNoisePlayer whiteNoisePlayer;

    // Data
    private ProjectAdapter projectAdapter;
    private ProjectRepository projectRepository;
    private String currentUserId;
    private List<Project> allProjects = new ArrayList<>();
    private DashboardProjectFilter activeFilter = DashboardProjectFilter.ALL;

    private static final int RECENT_PROJECT_LIMIT = 8;
    private static final long PROJECTS_CACHE_TTL_MS = 20_000L;
    private static final DateTimeFormatter DB_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static List<Project> cachedProjects = new ArrayList<>();
    private static long cachedProjectsAtMs = 0L;
    private static String cachedProjectsUserId;

    private enum DashboardProjectFilter {
        ALL,
        RECENT,
        OWNED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize session
        SessionManager.init(this);

        // Check if user is logged in
        if (!SessionManager.isLoggedIn()) {
            // Redirect to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Get the user ID from session
        currentUserId = SessionManager.getUserId();
        Log.d(TAG, "Logged in userId=" + currentUserId);

        // Initialize repository
        projectRepository = ProjectRepository.getInstance();
        NotificationPushScheduler.ensureScheduled(this);
        whiteNoisePlayer = new WhiteNoisePlayer();

        initViews();
        applyNavTransitionIfNeeded();
        setupRecyclerView();
        setupListeners();
        loadUserInfo();
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
        // Update bottom navigation selected item to ensure icon highlights correctly
        if (bottomNavigationView != null) {
            bottomNavigationView.setItemIconTintList(null);
            bottomNavigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
        }
        startStickyServiceSafely();
        loadUnreadNotificationCount();
        // Reload projects khi quay lại từ CreateProjectActivity
        // Chỉ load nếu đã có currentUserId
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadProjects();
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

    private boolean canUseProjectsCache() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            return false;
        }
        if (cachedProjectsUserId == null || !currentUserId.equals(cachedProjectsUserId)) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - cachedProjectsAtMs;
        return ageMs >= 0L && ageMs <= PROJECTS_CACHE_TTL_MS;
    }

    private void startStickyServiceSafely() {
        try {
            ContextCompat.startForegroundService(this, new Intent(this, StickyTaskService.class));
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Cannot start sticky foreground service", e);
        }
    }

    /**
     * Khởi tạo các view
     */
    private void initViews() {
        fabAdd = findViewById(R.id.fabAdd); // defined only in dashboard layout include
        btnNotification = findViewById(R.id.btnNotification);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        imgAvatar = findViewById(R.id.imgAvatar);
        tvAvatarLetter = findViewById(R.id.tvAvatarLetter);
        tvWorkspaceName = findViewById(R.id.tvWorkspaceName);
        searchBar = findViewById(R.id.searchBar);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterRecent = findViewById(R.id.btnFilterRecent);
        btnFilterOwned = findViewById(R.id.btnFilterOwned);
        rvProjects = findViewById(R.id.projectRecyclerView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        if (bottomNavigationView != null) {
            bottomNavigationView.setItemIconTintList(null);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        View bottomBarContainer = findViewById(R.id.includeBottomBar);
        if (bottomBarContainer != null) {
            bottomBarContainer.bringToFront();
        }
        if (fabAdd != null) {
            fabAdd.bringToFront();
        }
    }

    /**
     * Setup RecyclerView với GridLayoutManager (2 cột)
     */
    private void setupRecyclerView() {
        projectAdapter = new ProjectAdapter();

        // Click vào project để mở ProjectDetailActivity
        projectAdapter.setOnProjectClickListener(project -> {
            Intent intent = new Intent(this, com.team7.taskflow.ui.timeline.ProjectDetailActivity.class);
            intent.putExtra("project_id", project.getId());
            intent.putExtra("project_name", project.getName());
            intent.putExtra("project_key", project.getProjectKey());
            intent.putExtra("project_desc", project.getDescription());
            intent.putExtra("project_color", project.getColor());
            startActivity(intent);
        });

        rvProjects.setLayoutManager(new GridLayoutManager(this, 2));
        rvProjects.setAdapter(projectAdapter);
        rvProjects.setNestedScrollingEnabled(false); // Vì nằm trong NestedScrollView
    }

    /**
     * Setup các click listeners
     */
    private void setupListeners() {
        // FAB chỉ có ở Dashboard, check null để an toàn
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(this, CreateProjectActivity.class);
                startActivity(intent);
            });
        }

        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            startActivity(intent);
        });

        if (searchBar != null) {
            searchBar.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyProjectSearchFilter(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        setupFilterButtons();

        // Bottom navigation bar
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (isBottomNavNavigating) {
                    return true;
                }
                if (id == R.id.nav_settings) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, ProfileActivity.class);
                    boolean started = NavigationUtils.startActivityWithNavAnimation(
                            this, intent, NavigationUtils.NAV_HOME, NavigationUtils.NAV_SETTINGS);
                    if (!started) {
                        isBottomNavNavigating = false;
                    }
                    return true;
                } else if (id == R.id.nav_home) {
                    // Already on home
                    return true;
                } else if (id == R.id.nav_tasks) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, ForYouActivity.class);
                    boolean started = NavigationUtils.startActivityWithNavAnimation(
                            this, intent, NavigationUtils.NAV_HOME, NavigationUtils.NAV_TASKS);
                    if (!started) {
                        isBottomNavNavigating = false;
                    }
                    return true;
                } else if (id == R.id.nav_assistant) {
                    toggleWhiteNoise();
                    if (bottomNavigationView != null) {
                        bottomNavigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
                    }
                    return false;
                }
                return false;
            });
        }
    }

    private void toggleWhiteNoise() {
        if (whiteNoisePlayer == null) {
            whiteNoisePlayer = new WhiteNoisePlayer();
        }

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null
                && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                && !isWhiteNoisePlaying) {
            Toast.makeText(this, "Media volume is 0. Increase volume to hear white noise.", Toast.LENGTH_LONG).show();
        }

        if (isWhiteNoisePlaying) {
            whiteNoisePlayer.stop();
            isWhiteNoisePlaying = false;
            Toast.makeText(this, "White noise off", Toast.LENGTH_SHORT).show();
        } else {
            isWhiteNoisePlaying = whiteNoisePlayer.start();
            if (isWhiteNoisePlaying) {
                Toast.makeText(this, "White noise on", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unable to start white noise", Toast.LENGTH_SHORT).show();
            }
        }
        syncAssistantIconState();
    }

    private void syncAssistantIconState() {
        if (bottomNavigationView == null) {
            return;
        }
        if (bottomNavigationView.getMenu().findItem(R.id.nav_assistant) == null) {
            return;
        }
        bottomNavigationView.getMenu().findItem(R.id.nav_assistant).setIcon(
                isWhiteNoisePlaying
                        ? R.drawable.ic_nav_assistant_checked
                        : R.drawable.ic_nav_assistant_unchecked);
    }

    @Override
    protected void onDestroy() {
        if (whiteNoisePlayer != null) {
            whiteNoisePlayer.stop();
        }
        isWhiteNoisePlaying = false;
        super.onDestroy();
    }

    private void setupFilterButtons() {
        if (btnFilterAll == null || btnFilterRecent == null || btnFilterOwned == null) {
            return;
        }

        btnFilterAll.setOnClickListener(v -> updateActiveFilter(DashboardProjectFilter.ALL));
        btnFilterRecent.setOnClickListener(v -> updateActiveFilter(DashboardProjectFilter.RECENT));
        btnFilterOwned.setOnClickListener(v -> updateActiveFilter(DashboardProjectFilter.OWNED));

        syncFilterButtonState();
    }

    private void updateActiveFilter(DashboardProjectFilter filter) {
        if (filter == null || activeFilter == filter) {
            return;
        }
        activeFilter = filter;
        syncFilterButtonState();
        
        // Kích hoạt animation cho danh sách dự án
        if (rvProjects != null) {
            rvProjects.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down));
            rvProjects.scheduleLayoutAnimation();
        }

        applyProjectSearchFilter(searchBar != null && searchBar.getText() != null
                ? searchBar.getText().toString()
                : "");
    }

    private void syncFilterButtonState() {
        if (btnFilterAll == null || btnFilterRecent == null || btnFilterOwned == null) {
            return;
        }
        btnFilterAll.setChecked(activeFilter == DashboardProjectFilter.ALL);
        btnFilterRecent.setChecked(activeFilter == DashboardProjectFilter.RECENT);
        btnFilterOwned.setChecked(activeFilter == DashboardProjectFilter.OWNED);
    }

    /**
     * Load thông tin user đã đăng nhập
     * Ưu tiên dùng displayName từ SessionManager (đã lưu khi login)
     * Sau đó gọi API để cập nhật nếu có mạng
     */
    private void loadUserInfo() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "No userId in session, cannot load user info");
            if (tvWorkspaceName != null) {
                tvWorkspaceName.setText(getString(R.string.dashboard_workspace_guest));
            }
            loadProjects();
            return;
        }

        // Hiển thị tên từ session ngay lập tức (không cần đợi API)
        String savedName = SessionManager.getDisplayName();
        if (savedName != null && !savedName.isEmpty()) {
            if (tvWorkspaceName != null) {
                tvWorkspaceName.setText(getString(R.string.dashboard_workspace_format, savedName));
            }
            if (imgAvatar != null) {
                AvatarUiUtils.bindAvatarOrFallback(imgAvatar, tvAvatarLetter, null, savedName);
            }
        } else if (imgAvatar != null) {
            AvatarUiUtils.bindAvatarOrFallback(
                    imgAvatar,
                    tvAvatarLetter,
                    null,
                    getString(R.string.dashboard_workspace_guest));
        }

        // Gọi API để cập nhật tên mới nhất từ database (nếu có mạng)
        fetchUserFromDatabase(currentUserId);
    }

    /**
     * Lấy user từ database Supabase bằng userId
     * Cập nhật tên hiển thị nếu thành công, bỏ qua nếu lỗi mạng
     */
    private void fetchUserFromDatabase(String userId) {
        UserApi userApi = SupabaseClient.getInstance().getService(UserApi.class);

        userApi.getUserById("eq." + userId, "*").enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        User user = response.body().get(0);
                        currentUserId = user.getUserId();

                        String displayName = user.getDisplayNameOrEmail();
                        if (tvWorkspaceName != null) {
                            tvWorkspaceName.setText(getString(R.string.dashboard_workspace_format, displayName));
                        }

                        if (imgAvatar != null) {
                            AvatarUiUtils.bindAvatarOrFallback(
                                    imgAvatar,
                                    tvAvatarLetter,
                                    user.getAvatarUrl(),
                                    displayName);
                        }

                        Log.d(TAG, "Loaded user: " + user.getEmail() + ", ID: " + currentUserId);
                    } else {
                        Log.d(TAG, "User not found with userId: " + userId + ", code: " + response.code());
                        // Không đổi text vì đã hiển thị tên từ session
                    }

                    // Load projects (luôn chạy dù API user thành công hay thất bại)
                    loadProjects();
                });
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Error fetching user (sẽ dùng tên từ session): " + t.getMessage());
                    // Không đổi text vì đã hiển thị tên từ session
                    // Vẫn load projects
                    loadProjects();
                });
            }
        });
    }

    /**
     * Load danh sách projects từ Supabase
     */
    private void loadProjects() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, getString(R.string.dashboard_login_required), Toast.LENGTH_SHORT).show();
            return;
        }

        if (canUseProjectsCache()) {
            allProjects = new ArrayList<>(cachedProjects);
            applyProjectSearchFilter(searchBar != null && searchBar.getText() != null
                    ? searchBar.getText().toString()
                    : "");
            return;
        }

        Log.d(TAG, "Loading projects for user: " + currentUserId);

        // Gọi API lấy tất cả projects mà user tham gia
        projectRepository.getAllUserProjects(currentUserId, new ProjectRepository.ProjectCallback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> projects) {
                runOnUiThread(() -> {
                    if (projects == null || projects.isEmpty()) {
                        Toast.makeText(
                                DashboardActivity.this,
                                getString(R.string.dashboard_no_projects_message),
                                Toast.LENGTH_SHORT).show();
                        allProjects = new ArrayList<>();
                        projectAdapter.setProjects(new java.util.ArrayList<>());
                    } else {
                        allProjects = new ArrayList<>(projects);
                        cachedProjects = new ArrayList<>(projects);
                        cachedProjectsAtMs = System.currentTimeMillis();
                        cachedProjectsUserId = currentUserId;
                        applyProjectSearchFilter(searchBar != null && searchBar.getText() != null
                                ? searchBar.getText().toString()
                                : "");
                        Log.d(TAG, "Loaded " + projects.size() + " projects from database");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Error loading projects: " + error);
                    Toast.makeText(DashboardActivity.this,
                            getString(R.string.dashboard_load_projects_error, error), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadUnreadNotificationCount() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            updateNotificationBadge(0);
            return;
        }

        NotificationRepository.getInstance().getNotifications(
                currentUserId,
                new NotificationRepository.NotificationCallback<List<Notification>>() {
                    @Override
                    public void onSuccess(List<Notification> result) {
                        int unread = 0;
                        if (result != null) {
                            for (Notification notification : result) {
                                if (notification != null && !notification.isRead()) {
                                    unread++;
                                }
                            }
                        }
                        final int unreadCount = unread;
                        runOnUiThread(() -> updateNotificationBadge(unreadCount));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> updateNotificationBadge(0));
                    }
                });
    }

    private void updateNotificationBadge(int unreadCount) {
        if (tvNotificationBadge == null) {
            return;
        }

        if (unreadCount <= 0) {
            tvNotificationBadge.setVisibility(View.GONE);
            return;
        }

        tvNotificationBadge.setVisibility(View.VISIBLE);
        tvNotificationBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
    }

    private void applyProjectSearchFilter(String keyword) {
        if (projectAdapter == null) {
            return;
        }

        List<Project> baseList = applyBaseFilter(allProjects, activeFilter);

        if (keyword == null || keyword.trim().isEmpty()) {
            projectAdapter.setProjects(baseList);
            return;
        }

        String query = keyword.trim().toLowerCase(Locale.US);
        List<Project> filtered = new ArrayList<>();
        for (Project project : baseList) {
            if (project == null) {
                continue;
            }
            String name = project.getName() != null ? project.getName() : "";
            if (name.toLowerCase(Locale.US).contains(query)) {
                filtered.add(project);
            }
        }
        projectAdapter.setProjects(filtered);
    }

    private List<Project> applyBaseFilter(List<Project> source, DashboardProjectFilter filter) {
        List<Project> safeSource = source != null ? source : new ArrayList<>();
        if (filter == DashboardProjectFilter.OWNED) {
            List<Project> ownedProjects = new ArrayList<>();
            for (Project project : safeSource) {
                if (isOwnedByCurrentUser(project)) {
                    ownedProjects.add(project);
                }
            }
            return ownedProjects;
        }

        if (filter == DashboardProjectFilter.RECENT) {
            List<Project> sortedByRecent = new ArrayList<>();
            for (Project project : safeSource) {
                if (project != null) {
                    sortedByRecent.add(project);
                }
            }
            sortedByRecent
                    .sort((left, right) -> Long.compare(resolveCreatedAtEpoch(right), resolveCreatedAtEpoch(left)));

            int endIndex = Math.min(RECENT_PROJECT_LIMIT, sortedByRecent.size());
            return new ArrayList<>(sortedByRecent.subList(0, endIndex));
        }

        List<Project> all = new ArrayList<>();
        for (Project project : safeSource) {
            if (project != null) {
                all.add(project);
            }
        }
        return all;
    }

    private boolean isOwnedByCurrentUser(Project project) {
        if (project == null) {
            return false;
        }
        if (project.isOwner()) {
            return true;
        }
        String ownerId = project.getOwnerId();
        return ownerId != null
                && currentUserId != null
                && ownerId.trim().equalsIgnoreCase(currentUserId.trim());
    }

    private long resolveCreatedAtEpoch(Project project) {
        if (project == null || project.getCreatedAt() == null) {
            return Long.MIN_VALUE;
        }
        String createdAt = project.getCreatedAt().trim();
        if (createdAt.isEmpty()) {
            return Long.MIN_VALUE;
        }

        try {
            return Instant.parse(createdAt).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(createdAt, DB_DATETIME_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(createdAt, DB_DATE_FORMAT)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        return Long.MIN_VALUE;
    }

    private static class WhiteNoisePlayer {
        private static final int SAMPLE_RATE = 22050;
        private static final float OUTPUT_GAIN = 0.32f;
        private volatile boolean playing = false;
        private AudioTrack audioTrack;
        private Thread audioThread;

        boolean start() {
            if (playing) {
                return true;
            }
            int minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) {
                return false;
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioTrack = new AudioTrack(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build(),
                            new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build(),
                            Math.max(minBuffer, SAMPLE_RATE),
                            AudioTrack.MODE_STREAM,
                            AudioManager.AUDIO_SESSION_ID_GENERATE);
                } else {
                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minBuffer, SAMPLE_RATE),
                            AudioTrack.MODE_STREAM);
                }

                audioTrack.play();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioTrack.setVolume(1.0f);
                }
                playing = true;

                audioThread = new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                    Random random = new Random();
                    short[] buffer = new short[1024];
                    while (playing) {
                        for (int i = 0; i < buffer.length; i++) {
                            int sample = random.nextInt(65536) - 32768;
                            int amplified = (int) (sample * OUTPUT_GAIN);
                            if (amplified > Short.MAX_VALUE) {
                                amplified = Short.MAX_VALUE;
                            } else if (amplified < Short.MIN_VALUE) {
                                amplified = Short.MIN_VALUE;
                            }
                            buffer[i] = (short) amplified;
                        }
                        if (audioTrack != null) {
                            int written = audioTrack.write(buffer, 0, buffer.length);
                            if (written < 0) {
                                break;
                            }
                        }
                    }
                }, "dashboard-white-noise-thread");
                audioThread.start();
                return true;
            } catch (Exception ignored) {
                stop();
                return false;
            }
        }

        void stop() {
            playing = false;
            if (audioThread != null) {
                try {
                    audioThread.join(250);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                audioThread = null;
            }
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                } catch (Exception ignored) {
                }
                audioTrack.release();
                audioTrack = null;
            }
        }
    }
}
