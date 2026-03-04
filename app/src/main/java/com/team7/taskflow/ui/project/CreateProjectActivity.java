package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

import androidx.appcompat.app.AppCompatActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import androidx.core.content.ContextCompat;

import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;

/**
 * Màn hình tạo Project mới
 * Chức năng:
 * - Nhập tên, mô tả, project key
 * - Chọn visibility (Public/Private)
 * - Chọn template (Kanban/Scrum/Table)
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

    // Views - Template
    private LinearLayout btnKanban, btnScrum, btnTable;
    private ImageView icKanban, icScrum, icTable;
    private TextView tvKanban, tvScrum, tvTable;

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
    private String selectedTemplate = "KANBAN";
    private String selectedColor = "#2945FF";

    // Colors
    private static final String[] COLORS = {
            "#2945FF", "#FF5722", "#4CAF50", "#9C27B0", "#00BCD4"
    };

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

        // Template
        btnKanban = findViewById(R.id.btnKanban);
        btnScrum = findViewById(R.id.btnScrum);
        btnTable = findViewById(R.id.btnTable);
        icKanban = findViewById(R.id.icKanban);
        icScrum = findViewById(R.id.icScrum);
        icTable = findViewById(R.id.icTable);
        tvKanban = findViewById(R.id.tvKanban);
        tvScrum = findViewById(R.id.tvScrum);
        tvTable = findViewById(R.id.tvTable);

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

        for (int i = 0; i < childCount; i++) {
            final int index = i;
            View colorView = colorPicker.getChildAt(i);
            colorViews[i] = colorView;

            colorView.setOnClickListener(v -> selectColor(index));
        }

        // Mặc định chọn màu đầu tiên
        if (childCount > 0) {
            selectColor(0);
        }
    }

    private void selectColor(int index) {
        // Bỏ chọn tất cả
        for (View v : colorViews) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setAlpha(0.6f);
        }

        // Chọn màu mới
        selectedColorView = colorViews[index];
        selectedColorView.setScaleX(1.3f);
        selectedColorView.setScaleY(1.3f);
        selectedColorView.setAlpha(1.0f);

        if (index < COLORS.length) {
            selectedColor = COLORS[index];
        }
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

        // Template selection
        btnKanban.setOnClickListener(v -> selectTemplate("KANBAN"));
        btnScrum.setOnClickListener(v -> selectTemplate("SCRUM"));
        btnTable.setOnClickListener(v -> selectTemplate("TABLE"));
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

        if (privateSelected) {
            // Private selected
            btnPrivate.setBackgroundResource(R.drawable.bg_option_selected);
            icPrivate.setColorFilter(ContextCompat.getColor(this, R.color.primary));
            tvPrivate.setTextColor(Color.WHITE);

            btnPublic.setBackgroundResource(R.drawable.bg_option_unselected);
            icPublic.setColorFilter(Color.parseColor("#606060"));
            tvPublic.setTextColor(Color.parseColor("#A0A0A0"));
        } else {
            // Public selected
            btnPublic.setBackgroundResource(R.drawable.bg_option_selected);
            icPublic.setColorFilter(ContextCompat.getColor(this, R.color.primary));
            tvPublic.setTextColor(Color.WHITE);

            btnPrivate.setBackgroundResource(R.drawable.bg_option_unselected);
            icPrivate.setColorFilter(Color.parseColor("#606060"));
            tvPrivate.setTextColor(Color.parseColor("#A0A0A0"));
        }
    }

    private void selectTemplate(String template) {
        selectedTemplate = template;

        // Reset all
        btnKanban.setBackgroundResource(R.drawable.bg_option_unselected);
        icKanban.setColorFilter(Color.parseColor("#606060"));
        tvKanban.setTextColor(Color.parseColor("#A0A0A0"));

        btnScrum.setBackgroundResource(R.drawable.bg_option_unselected);
        icScrum.setColorFilter(Color.parseColor("#606060"));
        tvScrum.setTextColor(Color.parseColor("#A0A0A0"));

        btnTable.setBackgroundResource(R.drawable.bg_option_unselected);
        icTable.setColorFilter(Color.parseColor("#606060"));
        tvTable.setTextColor(Color.parseColor("#A0A0A0"));

        // Select chosen
        LinearLayout selectedBtn;
        ImageView selectedIc;
        TextView selectedTv;

        switch (template) {
            case "SCRUM":
                selectedBtn = btnScrum;
                selectedIc = icScrum;
                selectedTv = tvScrum;
                break;
            case "TABLE":
                selectedBtn = btnTable;
                selectedIc = icTable;
                selectedTv = tvTable;
                break;
            default: // KANBAN
                selectedBtn = btnKanban;
                selectedIc = icKanban;
                selectedTv = tvKanban;
                break;
        }

        selectedBtn.setBackgroundResource(R.drawable.bg_option_selected);
        selectedIc.setColorFilter(ContextCompat.getColor(this, R.color.primary));
        selectedTv.setTextColor(Color.WHITE);
    }

    private void createProject() {
        String name = etProjectName.getText().toString().trim();
        String key = etProjectKey.getText().toString().trim();
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

        // Tạo Project object
        Project project = new Project();
        project.setName(name);
        project.setProjectKey(key);
        project.setDescription(description);
        project.setOwnerId(userId);
        project.setColor(selectedColor);
        project.setPrivate(isPrivate);
        project.setTemplate(selectedTemplate);

        // Show loading
        setLoading(true);

        // Gọi API
        projectRepository.createProject(project, new ProjectRepository.ProjectCallback<Project>() {
            @Override
            public void onSuccess(Project result) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(CreateProjectActivity.this,
                            "Tạo project \"" + result.getName() + "\" thành công!",
                            Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "Created project: " + result.getId() + " - " + result.getName());

                    // Quay lại Dashboard
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(CreateProjectActivity.this,
                            "Lỗi: " + error, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Error creating project: " + error);
                });
            }
        });
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
