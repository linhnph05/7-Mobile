package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.team7.taskflow.ui.base.BaseActivity;
import androidx.core.content.ContextCompat;

import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.utils.ProjectColorUtils;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

/**
 * Màn hình tạo Project mới
 * Chức năng:
 * - Nhập tên, mô tả, project key
 * - Chọn visibility (Public/Private)
 * - Chọn màu project
 * - Lưu vào Supabase
 */
public class CreateProjectActivity extends BaseActivity {

    private static final String TAG = "CreateProjectActivity";

    // Views - Input
    private EditText etProjectName;
    private EditText etProjectKey;
    private EditText etProjectDescription;

    // Views - Visibility
    private LinearLayout btnPublic, btnPrivate;
    private ImageView icPublic, icPrivate;
    private TextView tvPublic, tvPrivate;

    // Views - Color
    private LinearLayout colorPicker;
    private View[] colorViews;
    private View selectedColorView;

    // Views - Buttons
    private Button btnCreate, btnCancel;
    private ImageView btnBack;
    private ProgressBar progressBar;

    // Data
    private ProjectRepository projectRepository;
    private boolean isPrivate = false;
    private String selectedColorToken = ProjectColorUtils.DEFAULT_COLOR_TOKEN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_project);

        projectRepository = ProjectRepository.getInstance();

        initViews();
        setupColorPicker();
        setupListeners();
    }

    private void initViews() {
        // Input fields
        etProjectName = findViewById(R.id.etProjectName);
        etProjectKey = findViewById(R.id.etProjectKey);
        etProjectDescription = findViewById(R.id.etProjectDescription);

        // Visibility
        btnPublic = findViewById(R.id.btnPublic);
        btnPrivate = findViewById(R.id.btnPrivate);
        icPublic = findViewById(R.id.icPublic);
        icPrivate = findViewById(R.id.icPrivate);
        tvPublic = findViewById(R.id.tvPublic);
        tvPrivate = findViewById(R.id.tvPrivate);

        // Color picker
        colorPicker = findViewById(R.id.colorPicker);

        // Buttons
        btnCreate = findViewById(R.id.btnCreate);
        btnCancel = findViewById(R.id.btnCancel);
        btnBack = findViewById(R.id.btnBack);

        // Progress - optional, không có trong layout hiện tại
        // progressBar = findViewById(R.id.progressBar);
    }

    private void setupColorPicker() {
        int childCount = colorPicker.getChildCount();
        colorViews = new View[childCount];
        List<ProjectColorUtils.ProjectColorSpec> palette = ProjectColorUtils.getPalette();

        for (int i = 0; i < childCount; i++) {
            final int index = i;
            View colorView = colorPicker.getChildAt(i);
            colorViews[i] = colorView;

            if (colorView instanceof MaterialCardView && index < palette.size()) {
                ((MaterialCardView) colorView).setCardBackgroundColor(Color.parseColor(palette.get(index).getHex()));
            }

            colorView.setOnClickListener(v -> selectColor(index));
        }

        // Mặc định chọn màu đầu tiên
        if (childCount > 0) {
            selectColor(0);
        }
    }

    private void selectColor(int index) {
        int strokePx = (int) (2 * getResources().getDisplayMetrics().density);

        // Bỏ chọn tất cả
        for (View v : colorViews) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setAlpha(1.0f);
            if (v instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) v;
                card.setStrokeWidth(0);
                if (card.getChildCount() > 0) {
                    card.getChildAt(0).setVisibility(View.GONE);
                }
            }
        }

        // Chọn màu mới
        selectedColorView = colorViews[index];
        if (selectedColorView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) selectedColorView;
            card.setStrokeWidth(strokePx);
            card.setStrokeColor(ContextCompat.getColor(this, R.color.primary));
            if (card.getChildCount() > 0) {
                card.getChildAt(0).setVisibility(View.VISIBLE);
            }
        }

        selectedColorToken = ProjectColorUtils.getTokenByIndex(index);
    }

    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // Create button
        btnCreate.setOnClickListener(v -> createProject());

        // Auto-generate project key from name
        etProjectName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String key = generateProjectKey(s.toString());
                etProjectKey.setText(key);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Visibility selection
        btnPublic.setOnClickListener(v -> selectVisibility(false));
        btnPrivate.setOnClickListener(v -> selectVisibility(true));

    }

    private String generateProjectKey(String name) {
        if (name == null || name.isEmpty())
            return "";

        // Lấy chữ cái đầu của mỗi từ, tối đa 5 ký tự
        StringBuilder key = new StringBuilder();
        String[] words = name.trim().split("\\s+");

        for (String word : words) {
            if (!word.isEmpty() && key.length() < 5) {
                key.append(Character.toUpperCase(word.charAt(0)));
            }
        }

        // Nếu chỉ có 1 từ, lấy 3-5 ký tự đầu
        if (key.length() < 2 && !name.isEmpty()) {
            key = new StringBuilder(name.toUpperCase().replaceAll("[^A-Z0-9]", ""));
            if (key.length() > 5) {
                key = new StringBuilder(key.substring(0, 5));
            }
        }

        return key.toString();
    }

    private void selectVisibility(boolean privateSelected) {
        isPrivate = privateSelected;

        int colorSelected = ContextCompat.getColor(this, R.color.primary);
        int colorUnselected = ContextCompat.getColor(this, R.color.theme_text_secondary);

        if (privateSelected) {
            // Private selected
            btnPrivate.setBackgroundResource(R.drawable.bg_option_selected);
            icPrivate.setColorFilter(colorSelected);
            tvPrivate.setTextColor(colorSelected);

            btnPublic.setBackgroundResource(R.drawable.bg_option_unselected);
            icPublic.setColorFilter(colorUnselected);
            tvPublic.setTextColor(colorUnselected);
        } else {
            // Public selected
            btnPublic.setBackgroundResource(R.drawable.bg_option_selected);
            icPublic.setColorFilter(colorSelected);
            tvPublic.setTextColor(colorSelected);

            btnPrivate.setBackgroundResource(R.drawable.bg_option_unselected);
            icPrivate.setColorFilter(colorUnselected);
            tvPrivate.setTextColor(colorUnselected);
        }
    }

    private void createProject() {
        String name = etProjectName.getText().toString().trim();
        String key = etProjectKey.getText().toString().trim().toUpperCase(Locale.US);
        String description = etProjectDescription.getText().toString().trim();

        // Validation
        if (name.isEmpty()) {
            etProjectName.setError("Vui lòng nhập tên project");
            etProjectName.requestFocus();
            return;
        }

        if (key.isEmpty()) {
            etProjectKey.setError("Vui lòng nhập project key");
            etProjectKey.requestFocus();
            return;
        }

        // Lấy user ID từ SessionManager
        SessionManager.init(this);
        String userId = SessionManager.getUserId();

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để tạo project", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        projectRepository.getAllUserProjects(userId, new ProjectRepository.ProjectCallback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> existingProjects) {
                runOnUiThread(() -> {
                    if (isDuplicateProjectName(name, existingProjects)) {
                        setLoading(false);
                        etProjectName.setError("Tên project đã tồn tại");
                        etProjectName.requestFocus();
                        return;
                    }
                    if (isDuplicateProjectKey(key, existingProjects)) {
                        setLoading(false);
                        etProjectKey.setError("Project key đã tồn tại");
                        etProjectKey.requestFocus();
                        return;
                    }
                    performCreateProject(name, key, description, userId);
                });
            }

            @Override
            public void onError(String error) {
                // Nếu không check được danh sách hiện có thì vẫn cho tạo,
                // backend sẽ là lớp bảo vệ cuối cùng.
                runOnUiThread(() -> performCreateProject(name, key, description, userId));
            }
        });
    }

    private void performCreateProject(String name, String key, String description, String userId) {
        Project project = new Project();
        project.setName(name);
        project.setProjectKey(key);
        project.setDescription(description);
        project.setOwnerId(userId);
        project.setColor(selectedColorToken);
        project.setPrivate(isPrivate);

        projectRepository.createProject(project, new ProjectRepository.ProjectCallback<Project>() {
            @Override
            public void onSuccess(Project result) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(CreateProjectActivity.this,
                            "Tạo project \"" + result.getName() + "\" thành công!",
                            Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "Created project: " + result.getId() + " - " + result.getName());
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String normalized = error == null ? "" : error.toLowerCase(Locale.US);
                    if (normalized.contains("project_key") || normalized.contains("projects_project_key")) {
                        etProjectKey.setError("Project key đã tồn tại");
                        etProjectKey.requestFocus();
                    } else if (normalized.contains("project_name") || normalized.contains("duplicate")) {
                        etProjectName.setError("Tên project đã tồn tại");
                        etProjectName.requestFocus();
                    }
                    Toast.makeText(CreateProjectActivity.this,
                            "Lỗi: " + error, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Error creating project: " + error);
                });
            }
        });
    }

    private boolean isDuplicateProjectName(String name, List<Project> existingProjects) {
        if (existingProjects == null || existingProjects.isEmpty()) {
            return false;
        }
        String normalizedName = normalizeProjectName(name);
        for (Project existing : existingProjects) {
            if (existing == null || existing.getName() == null) {
                continue;
            }
            if (normalizeProjectName(existing.getName()).equals(normalizedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDuplicateProjectKey(String key, List<Project> existingProjects) {
        if (existingProjects == null || existingProjects.isEmpty()) {
            return false;
        }
        String normalizedKey = key == null ? "" : key.trim().toUpperCase(Locale.US);
        for (Project existing : existingProjects) {
            if (existing == null || existing.getProjectKey() == null) {
                continue;
            }
            if (existing.getProjectKey().trim().toUpperCase(Locale.US).equals(normalizedKey)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeProjectName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }

    private void setLoading(boolean loading) {
        btnCreate.setEnabled(!loading);
        btnCancel.setEnabled(!loading);

        if (loading) {
            btnCreate.setText("Đang tạo...");
        } else {
            btnCreate.setText("Create Project");
        }

        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }
}
