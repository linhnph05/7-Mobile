package com.team7.taskflow.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.team7.taskflow.ui.profile.ProfileActivity;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import com.team7.taskflow.ui.base.BaseActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.notification.NotificationsActivity;
import com.team7.taskflow.ui.project.CreateProjectActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

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
    private TextView tvWorkspaceName;
    private RecyclerView rvProjects;
    private BottomNavigationView bottomNavigationView;

    // Data
    private ProjectAdapter projectAdapter;
    private ProjectRepository projectRepository;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        // Xử lý insets: chỉ thêm padding top cho status bar
        // Bottom bar không cần padding vì nằm trên thanh điều hướng hệ thống
        View scrollView = findViewById(R.id.scrollView);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

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

        initViews();
        setupRecyclerView();
        setupListeners();
        loadUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload projects khi quay lại từ CreateProjectActivity
        // Chỉ load nếu đã có currentUserId
        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadProjects();
        }
    }

    /**
     * Khởi tạo các view
     */
    private void initViews() {
        fabAdd = findViewById(R.id.fabAdd); // defined only in dashboard layout include
        btnNotification = findViewById(R.id.btnNotification);
        tvWorkspaceName = findViewById(R.id.tvWorkspaceName);
        rvProjects = findViewById(R.id.projectRecyclerView);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
    }

    /**
     * Setup RecyclerView với GridLayoutManager (2 cột)
     */
    private void setupRecyclerView() {
        projectAdapter = new ProjectAdapter();

        // Click vào project để mở Timeline (Calendar)
        projectAdapter.setOnProjectClickListener(project -> {
            Intent intent = new Intent(this, com.team7.taskflow.ui.timeline.TimelineActivity.class);
            intent.putExtra("project_id", project.getId());
            intent.putExtra("project_name", project.getName());
            intent.putExtra("project_key", project.getProjectKey());
            intent.putExtra("project_desc", project.getDescription());
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

        // Bottom navigation bar
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    return true;
                } else if (id == R.id.nav_home) {
                    // Already on home
                    return true;
                }
                // TODO: Handle nav_tasks, nav_assistant
                return false;
            });
        }
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
                tvWorkspaceName.setText("Hello, Guest");
            }
            loadProjects();
            return;
        }

        // Hiển thị tên từ session ngay lập tức (không cần đợi API)
        String savedName = SessionManager.getDisplayName();
        if (savedName != null && !savedName.isEmpty()) {
            if (tvWorkspaceName != null) {
                tvWorkspaceName.setText("Hello, " + savedName);
            }
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
                            tvWorkspaceName.setText("Hello, " + displayName);
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
            Toast.makeText(this, "Vui lòng đăng nhập để xem projects", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Loading projects for user: " + currentUserId);

        // Gọi API lấy tất cả projects mà user tham gia
        projectRepository.getAllUserProjects(currentUserId, new ProjectRepository.ProjectCallback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> projects) {
                runOnUiThread(() -> {
                    if (projects == null || projects.isEmpty()) {
                        Toast.makeText(DashboardActivity.this,
                                "Bạn chưa có project nào. Nhấn + để tạo mới!",
                                Toast.LENGTH_SHORT).show();
                        projectAdapter.setProjects(new java.util.ArrayList<>());
                    } else {
                        projectAdapter.setProjects(projects);
                        Log.d(TAG, "Loaded " + projects.size() + " projects from database");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Error loading projects: " + error);
                    Toast.makeText(DashboardActivity.this,
                            "Lỗi tải projects: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
