package com.team7.taskflow.ui.project;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.ProjectHistoryItem;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.attachment.FullscreenImageActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.ai.AiCreateActivity;
import com.team7.taskflow.utils.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class TaskDetailActivity extends BaseActivity {

    private static final String EXTRA_PARENT_TASK_ID = "parent_task_id";
    private static final String EXTRA_PARENT_TASK_TITLE = "parent_task_title";

    private EditText etTitle, etDescription;
    private TextView tvPriority, tvStatus, tvAssignee, tvTag, tvDependency;
    private ImageView ivPriority, ivStatus, ivAssignee, ivTag, ivDependency, ivSubTaskInfo;
    private View cardPriority, cardStatus, cardAssignee, cardAttachment, cardTag, cardDependency;
    private TextView tvAttachment;
    private ImageView ivAttachment;
    private LinearLayout containerAttachments;

    // âœ… File picker + Camera launchers
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private Uri cameraImageUri;

    private List<Uri> attachedFileUris = new ArrayList<>();
    private List<com.team7.taskflow.domain.model.Attachment> existingAttachments = new ArrayList<>();
    private int uploadSuccessCount = 0;

    private String selectedPriority = "MEDIUM";
    private String selectedStatus = "TODO";
    private String selectedAssigneeName = null;

    private TextView tvStartDate, tvDueDate, tvStartTime, tvDueTime, btnSave, tvToolbarTitle;
    private TextView tvSubTaskInfo;
    private View cardSubTaskInfo;
    private ProgressBar progressBar;

    // ÄÃ£ gá»™p conflict khai bÃ¡o biáº¿n á»Ÿ Ä‘Ã¢y
    private View layoutCommentsSection, layoutHistorySection, layoutWorkLogSection;
    private ListView listHistoryFeed;
    private TextView tvHistoryEmpty;
    private HistoryEventAdapter historyAdapter;

    private RecyclerView rvComments;
    private EditText etCommentInput;
    private ImageView btnSendComment;
    private TabLayout tabLayoutActivity;
    private TaskCommentAdapter commentAdapter;
    private String currentUserId;
    private TaskRepository taskRepository;
    private List<User> projectMembers = new ArrayList<>();
    private boolean isMembersLoading = false;
    private android.widget.LinearLayout currentPickerContainer;
    private android.widget.ProgressBar currentPickerProgressBar;
    private String currentSearchFilter = "";
    private ArrayAdapter<String> assigneeAdapter;
    private long projectId;

    private Long taskId = null;
    private String currentAssigneeId = null;
    private String selectedTag = null;
    private Long selectedParentTaskId = null;
    private List<Task> currentSubTasks = new ArrayList<>();

    private static final int COLOR_DEFAULT = R.color.theme_text_secondary;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final String WORKLOG_PREFS = "task_worklog";
    private static final String WORKLOG_TOTAL_PREFIX = "task_total_";
    private static final String WORKLOG_LOGS_PREFIX = "task_logs_";

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar dueCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private TextView tvPomodoroState;
    private TextView tvWorklogTotal;
    private TextView tvWorklogEntries;
    private TextView tvWorklogSummary;
    private View fabFocusMode;
    private ActivityResultLauncher<Intent> focusModeLauncher;

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // onCreate
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_task_detail);

        View header = findViewById(R.id.layoutHeader);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            header.bringToFront();
        }

        taskRepository = TaskRepository.getInstance();
        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        projectId = getIntent().getLongExtra("project_id", -1);
        if (getIntent().hasExtra("task_id")) {
            long id = getIntent().getLongExtra("task_id", -1);
            if (id != -1)
                taskId = id;
        }

        initViews();
        applySubTaskContextFromIntent();
        initFilePickerLauncher();
        initCameraLauncher(); // âœ… ThÃªm
        setupPickers();
        setupDatePickers();
        loadProjectMembers();
        initFocusModeLauncher();
        setupWorkLogSection();

        if (taskId != null) {
            loadTaskDetails();
            setupCommentsSection();
        } else {
            if (layoutCommentsSection != null)
                layoutCommentsSection.setVisibility(View.GONE);
        }

        tvToolbarTitle.setText(R.string.task_detail_title);
        btnSave.setText(R.string.task_save);

        setupActivityTabs();
        setupSubTaskFab();
        setupFocusModeFab();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveTask());
    }

    private void setupSubTaskFab() {
        View fab = findViewById(R.id.fabAddSubTask);
        if (fab == null) {
            return;
        }

        fab.setVisibility(taskId != null ? View.VISIBLE : View.GONE);
        fab.setOnClickListener(v -> openAiCreateSubTask());
        updateSubTaskFabVisibility();
    }

    private void updateSubTaskFabVisibility() {
        View fab = findViewById(R.id.fabAddSubTask);
        if (fab == null)
            return;

        // Hide button if current task is already a subtask (has parent)
        boolean isSubtask = selectedParentTaskId != null && selectedParentTaskId > 0;
        boolean canCreateSubtask = taskId != null && !isSubtask;
        fab.setVisibility(canCreateSubtask ? View.VISIBLE : View.GONE);
    }

    private void setupFocusModeFab() {
        if (fabFocusMode == null) {
            return;
        }

        fabFocusMode.setVisibility(taskId != null ? View.VISIBLE : View.GONE);
        fabFocusMode.setOnClickListener(v -> openFocusMode());
    }

    private void openAiCreateSubTask() {
        if (taskId == null || taskId <= 0) {
            Toast.makeText(this, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show();
            return;
        }

        String parentTitle = etTitle != null && etTitle.getText() != null
                ? etTitle.getText().toString().trim()
                : "";

        Intent intent = new Intent(this, AiCreateActivity.class);
        intent.putExtra("project_id", projectId);
        intent.putExtra(EXTRA_PARENT_TASK_ID, taskId);
        intent.putExtra(EXTRA_PARENT_TASK_TITLE, parentTitle);
        startActivity(intent);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // initViews
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void initViews() {
        etTitle = findViewById(R.id.etTaskTitle);
        etDescription = findViewById(R.id.etTaskDescription);
        tvPriority = findViewById(R.id.tvPriority);
        tvStatus = findViewById(R.id.tvStatus);
        tvAssignee = findViewById(R.id.tvAssignee);
        ivPriority = findViewById(R.id.ivPriority);
        ivStatus = findViewById(R.id.ivStatus);
        ivAssignee = findViewById(R.id.ivAssignee);
        cardPriority = findViewById(R.id.cardPriority);
        cardStatus = findViewById(R.id.cardStatus);
        cardAssignee = findViewById(R.id.cardAssignee);
        cardAttachment = findViewById(R.id.cardAttachment);
        cardTag = findViewById(R.id.cardTag);
        ivTag = findViewById(R.id.ivTag);
        tvTag = findViewById(R.id.tvTag);
        tvAttachment = findViewById(R.id.tvAttachment);
        ivAttachment = findViewById(R.id.ivAttachment);
        containerAttachments = findViewById(R.id.containerAttachments);
        tvStartDate = findViewById(R.id.tvStartDate);
        tvDueDate = findViewById(R.id.tvDueDate);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvDueTime = findViewById(R.id.tvDueTime);
        btnSave = findViewById(R.id.btnSave);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        progressBar = findViewById(R.id.progressBar);
        tvSubTaskInfo = findViewById(R.id.tvSubTaskInfo);
        ivSubTaskInfo = findViewById(R.id.ivSubTaskInfo);
        cardSubTaskInfo = findViewById(R.id.cardSubTaskInfo);
        layoutCommentsSection = findViewById(R.id.layoutCommentsSection);

        // Đã gộp conflict khởi tạo view
        layoutHistorySection = findViewById(R.id.layoutTabHistory);
        layoutWorkLogSection = findViewById(R.id.layoutTabWorkLog);
        listHistoryFeed = findViewById(R.id.listHistoryFeed);
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty);
        tabLayoutActivity = findViewById(R.id.tabLayoutActivity);
        rvComments = findViewById(R.id.rvComments);
        etCommentInput = findViewById(R.id.etCommentInput);
        btnSendComment = findViewById(R.id.btnSendComment);
        // tvPomodoroState = findViewById(R.id.tvPomodoroState);
        tvWorklogTotal = findViewById(R.id.tvWorklogTotal);
        tvWorklogSummary = findViewById(R.id.tvWorklogSummary);
        tvWorklogEntries = findViewById(R.id.tvWorklogEntries);
        View btnEditWorklog = findViewById(R.id.btnEditWorklog);
        if (btnEditWorklog != null) {
            btnEditWorklog.setOnClickListener(v -> showEditWorkLogDialog());
        }
        fabFocusMode = findViewById(R.id.fabFocusMode);

        Intent intent = getIntent();
        if (intent != null) {
            String prefillTitle = intent.getStringExtra("prefill_title");
            String prefillDesc = intent.getStringExtra("prefill_description");
            if (prefillTitle != null && !prefillTitle.trim().isEmpty())
                etTitle.setText(prefillTitle);
            if (prefillDesc != null && !prefillDesc.trim().isEmpty())
                etDescription.setText(prefillDesc);
        }
    }

    private void applySubTaskContextFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            renderSubTaskInfo(null);
            return;
        }

        if (selectedParentTaskId == null && intent.hasExtra(EXTRA_PARENT_TASK_ID)) {
            long parentTaskId = intent.getLongExtra(EXTRA_PARENT_TASK_ID, -1);
            if (parentTaskId > 0) {
                selectedParentTaskId = parentTaskId;
            }
        }

        String parentTaskTitle = intent.getStringExtra(EXTRA_PARENT_TASK_TITLE);
        renderSubTaskInfo(parentTaskTitle);
    }

    private void renderSubTaskInfo(String parentTitle) {
        if (tvSubTaskInfo == null || cardSubTaskInfo == null || ivSubTaskInfo == null)
            return;

        // Banner luôn hiển thị để tạo sự nhất quán về UI (Standard 18dfec)
        cardSubTaskInfo.setVisibility(View.VISIBLE);

        if (selectedParentTaskId != null && selectedParentTaskId > 0) {
            // Case 1: Đang là Task con -> Hiện Task cha (Standard 18dfec icon)
            String displayTitle = parentTitle != null ? parentTitle.trim() : "";
            if (displayTitle.isEmpty())
                displayTitle = "#" + selectedParentTaskId;
            tvSubTaskInfo.setText(getString(R.string.task_subtask_of_format, displayTitle));
            ivSubTaskInfo.setImageResource(R.drawable.ic_grid_view); // Icon cha chuẩn
            setActive(cardSubTaskInfo, tvSubTaskInfo, ivSubTaskInfo, R.color.primary);
            cardSubTaskInfo.setOnClickListener(v -> showDependencyPicker());
        } else if (currentSubTasks != null && !currentSubTasks.isEmpty()) {
            // Case 2: Đang là Task cha -> Hiện số lượng con
            tvSubTaskInfo.setText(getString(R.string.task_subtask_count_format, currentSubTasks.size()));
            ivSubTaskInfo.setImageResource(R.drawable.ic_account_tree); // Icon nhánh chuẩn
            setActive(cardSubTaskInfo, tvSubTaskInfo, ivSubTaskInfo, R.color.project_green);
            cardSubTaskInfo.setOnClickListener(v -> showSubTaskListPicker());
        } else {
            // Case 3: Task độc lập -> Gợi ý gán task cha
            tvSubTaskInfo.setText(R.string.task_add_parent_prompt);
            ivSubTaskInfo.setImageResource(R.drawable.ic_add);
            setDefault(cardSubTaskInfo, tvSubTaskInfo, ivSubTaskInfo);
            cardSubTaskInfo.setOnClickListener(v -> showDependencyPicker());
        }
    }

    private void showSubTaskListPicker() {
        if (currentSubTasks == null || currentSubTasks.isEmpty())
            return;
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null)
            tvTitle.setText(R.string.task_subtask_list_title);

        // Sắp xếp task con theo ID tăng dần để dễ theo dõi
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            currentSubTasks.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        } else {
            java.util.Collections.sort(currentSubTasks, (a, b) -> Long.compare(a.getId(), b.getId()));
        }

        for (Task sub : currentSubTasks) {
            String label = "#" + sub.getId() + " • " + sub.getTitle();
            container.addView(createPickerItem(label, x -> {
                d.dismiss();
                Intent intent = new Intent(this, TaskDetailActivity.class);
                intent.putExtra("task_id", sub.getId());
                intent.putExtra("project_id", projectId);
                startActivity(intent);
            }, R.color.theme_text_primary));
        }

        // Thêm dòng Hint tinh tế dành riêng cho Task cha
        TextView tvHint = new TextView(this);
        tvHint.setText("Đây là các công việc con hiện có. Nhấn để xem chi tiết.");
        tvHint.setTextSize(13);
        tvHint.setPadding(48, 16, 48, 32);
        tvHint.setTextColor(ContextCompat.getColor(this, R.color.theme_text_hint));
        tvHint.setGravity(android.view.Gravity.CENTER);
        container.addView(tvHint);

        d.setContentView(v);
        d.show();
    }

    // File picker & Camera
    private void initFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++)
                                attachedFileUris.add(data.getClipData().getItemAt(i).getUri());
                        } else if (data.getData() != null) {
                            attachedFileUris.add(data.getData());
                        }
                        updateAttachmentUi();
                    }
                });
    }

    private void initCameraLauncher() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        attachedFileUris.add(cameraImageUri);
                        updateAttachmentUi();
                    }
                });
    }

    // âœ… Má»Ÿ dialog chá»n nguá»“n: Camera hoáº·c File
    private void openFilePicker() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.task_attach_label)
                .setItems(new String[] {
                        getString(R.string.task_attach_source_camera),
                        getString(R.string.task_attach_source_files)
                }, (dialog, which) -> {
                    if (which == 0)
                        openCamera();
                    else
                        openFileChooser();
                })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "camera");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            java.io.File imgFile = java.io.File.createTempFile(
                    "img_" + System.currentTimeMillis(), ".jpg", cacheDir);
            cameraImageUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", imgFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.task_camera_open_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.task_attach_chooser)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, R.string.task_camera_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // updateAttachmentUi â€” vá»›i Preview + icon theo loáº¡i file
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void updateAttachmentUi() {
        containerAttachments.removeAllViews();
        if (attachedFileUris.isEmpty() && existingAttachments.isEmpty()) {
            containerAttachments.setVisibility(View.GONE);
            tvAttachment.setText("+");
            setDefault(cardAttachment, tvAttachment, ivAttachment);
            return;
        }

        containerAttachments.setVisibility(View.VISIBLE);
        int count = attachedFileUris.size() + existingAttachments.size();
        tvAttachment.setText(count + " file");
        setActive(cardAttachment, tvAttachment, ivAttachment, R.color.theme_text_primary);

        LayoutInflater inflater = LayoutInflater.from(this);

        // Render existing attachments (Ä‘Ã£ upload)
        for (com.team7.taskflow.domain.model.Attachment attachment : existingAttachments) {
            View itemView = inflater.inflate(R.layout.item_attachment_chip, containerAttachments, false);
            bindAttachmentChip(itemView,
                    attachment.getFileName(),
                    attachment.getFileType(),
                    null, // khÃ´ng cÃ³ URI local
                    attachment.getFileUrl(),
                    v -> {
                        if (attachment.getId() != null) {
                            Toast.makeText(this, "Đang xóa...", Toast.LENGTH_SHORT).show();
                            TaskRepository.getInstance().deleteTaskAttachment(attachment.getId(),
                                    new TaskRepository.TaskCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void r) {
                                            runOnUiThread(() -> {
                                                existingAttachments.remove(attachment);
                                                updateAttachmentUi();
                                            });
                                        }

                                        @Override
                                        public void onError(String err) {
                                            runOnUiThread(() -> Toast.makeText(
                                                    TaskDetailActivity.this,
                                                    "Xóa lỗi: " + err, Toast.LENGTH_SHORT).show());
                                        }
                                    });
                        }
                    });
            containerAttachments.addView(itemView);
        }

        // Render newly added files (chÆ°a upload)
        for (Uri uri : new ArrayList<>(attachedFileUris)) {
            View itemView = inflater.inflate(R.layout.item_attachment_chip, containerAttachments, false);
            String mime = getContentResolver().getType(uri);
            bindAttachmentChip(itemView,
                    getFileNameFromUri(uri),
                    mime,
                    uri, // URI local
                    null, // chÆ°a cÃ³ URL
                    v -> {
                        attachedFileUris.remove(uri);
                        updateAttachmentUi();
                    });
            containerAttachments.addView(itemView);
        }
    }

    /**
     * Bind dá»¯ liá»‡u vÃ o item_attachment_chip.
     * localUri â€” URI local (file chÆ°a upload), null náº¿u Ä‘Ã£ upload
     * remoteUrl â€” URL Supabase (file Ä‘Ã£ upload), null náº¿u chÆ°a upload
     */
    private void bindAttachmentChip(View itemView, String fileName, String mimeType,
            Uri localUri, String remoteUrl, View.OnClickListener onRemove) {

        TextView tvName = itemView.findViewById(R.id.tvFileName);
        ImageView ivIcon = itemView.findViewById(R.id.ivFileIcon);
        ImageView ivThumb = itemView.findViewById(R.id.ivImageThumb);
        ImageView btnPreview = itemView.findViewById(R.id.btnPreview);
        ImageView btnRemove = itemView.findViewById(R.id.btnRemoveFile);

        tvName.setText(fileName != null ? fileName : "file");

        boolean isImage = mimeType != null && mimeType.startsWith("image/");
        boolean isPdf = mimeType != null && mimeType.equals("application/pdf");

        if (isImage) {
            // Hiá»‡n thumbnail, áº©n icon
            ivIcon.setVisibility(View.GONE);
            if (ivThumb != null) {
                ivThumb.setVisibility(View.VISIBLE);
                Object src = localUri != null ? localUri : remoteUrl;

                // Lifecycle-safe Glide call
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this).load(src)
                            .placeholder(R.drawable.ic_attach_file)
                            .error(R.drawable.ic_attach_file)
                            .centerCrop()
                            .into(ivThumb);
                }
            }
        } else {
            if (ivThumb != null)
                ivThumb.setVisibility(View.GONE);
            ivIcon.setVisibility(View.VISIBLE);
            if (isPdf) {
                ivIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.danger),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                ivIcon.clearColorFilter();
            }
            ivIcon.setImageResource(R.drawable.ic_attach_file);
        }

        // Preview click
        View.OnClickListener previewClick = v -> {
            if (isImage) {
                Intent intent = new Intent(this, FullscreenImageActivity.class);
                if (localUri != null)
                    intent.putExtra("image_uri", localUri.toString());
                else
                    intent.putExtra("image_url", remoteUrl);
                intent.putExtra("title", fileName);
                startActivity(intent);
            } else {
                // Má»Ÿ báº±ng app bÃªn ngoÃ i
                try {
                    Uri target = localUri != null ? localUri : Uri.parse(remoteUrl);
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(target, mimeType);
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(viewIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Không thể mở file này", Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (btnPreview != null)
            btnPreview.setOnClickListener(previewClick);
        itemView.setOnClickListener(previewClick);
        if (btnRemove != null)
            btnRemove.setOnClickListener(onRemove);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Upload
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void uploadNextAttachment(int index, long targetTaskId, String baseMsg) {
        if (index >= attachedFileUris.size()) {
            String finalMsg = baseMsg;
            if (uploadSuccessCount > 0)
                finalMsg += " (Kèm " + uploadSuccessCount + " file)";
            Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        Uri uri = attachedFileUris.get(index);
        tvToolbarTitle.setText("Đang tải file " + (index + 1) + "/" + attachedFileUris.size());

        String mimeType = getContentResolver().getType(uri);
        String fileName = getFileNameFromUri(uri);

        TaskRepository.getInstance().uploadTaskAttachment(targetTaskId, uri, fileName, mimeType,
                getContentResolver(),
                new TaskRepository.TaskCallback<com.team7.taskflow.domain.model.Attachment>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Attachment r) {
                        uploadSuccessCount++;
                        runOnUiThread(() -> uploadNextAttachment(index + 1, targetTaskId, baseMsg));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(TaskDetailActivity.this,
                                    "Lỗi file " + (index + 1) + ": " + error, Toast.LENGTH_LONG).show();
                            uploadNextAttachment(index + 1, targetTaskId, baseMsg);
                        });
                    }
                });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Comments
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void setupCommentsSection() {
        if (layoutCommentsSection == null || rvComments == null)
            return;
        layoutCommentsSection.setVisibility(View.VISIBLE);
        commentAdapter = new TaskCommentAdapter(currentUserId, new TaskCommentAdapter.Listener() {
            @Override
            public void onEdit(Comment c) {
                showEditCommentDialog(c);
            }

            @Override
            public void onDelete(Comment c) {
                deleteComment(c);
            }

            @Override
            public void onReact(Comment c, String type) {
                if (commentAdapter != null && c != null && c.getId() != null)
                    commentAdapter.applyLocalReactionToggle(c.getId(), type);
                toggleReaction(c, type);
            }
        });
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);
        if (btnSendComment != null)
            btnSendComment.setOnClickListener(v -> createComment());
        loadComments();
    }

    private void setupActivityTabs() {
        if (tabLayoutActivity == null)
            return;
        if (taskId == null) {
            tabLayoutActivity.setVisibility(View.GONE);
            if (layoutCommentsSection != null)
                layoutCommentsSection.setVisibility(View.GONE);
            if (layoutHistorySection != null)
                layoutHistorySection.setVisibility(View.GONE);
            if (layoutWorkLogSection != null)
                layoutWorkLogSection.setVisibility(View.GONE);
            return;
        }
        tabLayoutActivity.setVisibility(View.VISIBLE);
        tabLayoutActivity.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showActivitySection(tab != null ? tab.getPosition() : 0);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                showActivitySection(tab != null ? tab.getPosition() : 0);
            }
        });
        TabLayout.Tab first = tabLayoutActivity.getTabAt(0);
        if (first != null)
            first.select();
        else
            showActivitySection(0);
    }

    private void showActivitySection(int pos) {
        if (layoutCommentsSection != null)
            layoutCommentsSection.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        if (layoutHistorySection != null)
            layoutHistorySection.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        if (layoutWorkLogSection != null)
            layoutWorkLogSection.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
        if (pos == 1 && taskId != null)
            loadTaskHistoryIntoSection();
        if (pos == 2 && taskId != null)
            refreshWorkLogUi();
    }

    private void setupWorkLogSection() {
        refreshWorkLogUi();
    }

    private void showEditWorkLogDialog() {
        if (taskId == null || taskId <= 0) {
            Toast.makeText(this, getString(R.string.task_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_work_log, null);
        EditText etTimeSpent = dialogView.findViewById(R.id.etWorklogTimeSpent);
        EditText etTimeRemaining = dialogView.findViewById(R.id.etWorklogTimeRemaining);
        EditText etDescription = dialogView.findViewById(R.id.etWorklogDescription);

        TextView tvErrorSpent = dialogView.findViewById(R.id.tvErrorTimeSpent);
        TextView tvErrorRemain = dialogView.findViewById(R.id.tvErrorTimeRemaining);
        TextView tvErrorDesc = dialogView.findViewById(R.id.tvErrorDescription);

        dialogView.setOnClickListener(v -> hideKeyboard(v));
        if (dialogView instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) dialogView;
            if (group.getChildCount() > 0) {
                group.getChildAt(0).setOnClickListener(v -> hideKeyboard(v));
            }
        }

        TextView tvDate = dialogView.findViewById(R.id.tvWorklogDate);
        TextView tvTime = dialogView.findViewById(R.id.tvWorklogTime);

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault());
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

        if (tvDate != null) {
            tvDate.setText(dateFormat.format(calendar.getTime()));
            tvDate.setOnClickListener(v -> {
                new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    calendar.set(java.util.Calendar.YEAR, year);
                    calendar.set(java.util.Calendar.MONTH, month);
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth);
                    tvDate.setText(dateFormat.format(calendar.getTime()));
                }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                        calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });
        }

        if (tvTime != null) {
            tvTime.setText(timeFormat.format(calendar.getTime()));
            tvTime.setOnClickListener(v -> {
                new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(java.util.Calendar.MINUTE, minute);
                    tvTime.setText(timeFormat.format(calendar.getTime()));
                }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show();
            });
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View btnSaveInternal = dialogView.findViewById(R.id.btnWorklogSave);
        View btnCancelInternal = dialogView.findViewById(R.id.btnWorklogCancel);

        if (btnCancelInternal != null) {
            btnCancelInternal.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSaveInternal != null) {
            btnSaveInternal.setOnClickListener(v -> {
                // Reset Errors
                if (tvErrorSpent != null)
                    tvErrorSpent.setVisibility(View.GONE);
                if (tvErrorRemain != null)
                    tvErrorRemain.setVisibility(View.GONE);
                if (tvErrorDesc != null)
                    tvErrorDesc.setVisibility(View.GONE);

                String timeText = etTimeSpent.getText().toString().trim();
                String remainText = etTimeRemaining != null ? etTimeRemaining.getText().toString().trim() : "";
                String descText = etDescription != null ? etDescription.getText().toString().trim() : "";

                long durationMs = parseDurationToMs(timeText);
                long remainingMs = parseDurationToMs(remainText);

                boolean hasError = false;
                if (durationMs <= 0) {
                    if (tvErrorSpent != null) {
                        tvErrorSpent.setText(R.string.worklog_error_duration_invalid);
                        tvErrorSpent.setVisibility(View.VISIBLE);
                    }
                    etTimeSpent.requestFocus();
                    hasError = true;
                }

                if (descText.isEmpty()) {
                    if (tvErrorDesc != null) {
                        tvErrorDesc.setText(R.string.worklog_error_description_required);
                        tvErrorDesc.setVisibility(View.VISIBLE);
                    }
                    if (!hasError)
                        etDescription.requestFocus();
                    hasError = true;
                }

                if (hasError)
                    return;

                hideKeyboard(dialogView);

                SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
                long currentTotal = prefs.getLong(worklogTotalKey(taskId), 0L);

                JSONArray entries = readWorklogEntries(prefs, taskId);
                JSONObject newEntry = new JSONObject();
                long startTime = calendar.getTimeInMillis();
                long endTime = startTime + durationMs;
                try {
                    newEntry.put("started_at", startTime);
                    newEntry.put("ended_at", endTime);
                    newEntry.put("duration_ms", durationMs);
                    newEntry.put("remaining_minutes", remainingMs / 60000);
                    newEntry.put("completed", true);
                    newEntry.put("description", descText);
                    entries.put(newEntry);
                } catch (Exception ignored) {
                }

                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(worklogTotalKey(taskId), currentTotal + durationMs);
                editor.putString(worklogLogsKey(taskId), entries.toString());
                editor.apply();

                String currentUserId = com.team7.taskflow.utils.SessionManager.getUserId();
                if (currentUserId != null && taskId != null) {
                    taskRepository.addWorkLog(taskId, currentUserId, startTime, durationMs, remainingMs,
                            descText, new TaskRepository.TaskCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    Log.d("WorkLog", "Synced to Supabase successfully.");
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e("WorkLog", "Sync failed: " + error);
                                }
                            });
                }

                refreshWorkLogUi();
                Toast.makeText(this, R.string.worklog_success_message, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private long parseDurationToMs(String timeStr) {
        if (timeStr == null || timeStr.isEmpty())
            return 0;
        long totalMs = 0;
        String[] parts = timeStr.toLowerCase().split("\\s+");
        for (String part : parts) {
            try {
                if (part.endsWith("h")) {
                    totalMs += Long.parseLong(part.replace("h", "")) * 3600000L;
                } else if (part.endsWith("m")) {
                    totalMs += Long.parseLong(part.replace("m", "")) * 60000L;
                } else if (part.endsWith("d")) {
                    totalMs += Long.parseLong(part.replace("d", "")) * 86400000L;
                } else if (part.endsWith("w")) {
                    totalMs += Long.parseLong(part.replace("w", "")) * 604800000L;
                }
            } catch (Exception ignored) {
            }
        }
        return totalMs;
    }

    private void openFocusMode() {
        if (taskId == null || taskId <= 0) {
            Toast.makeText(this, getString(R.string.task_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, FocusModeActivity.class);
        intent.putExtra("task_id", taskId);
        intent.putExtra("task_title", etTitle != null ? etTitle.getText().toString().trim() : "Task");
        intent.putExtra("task_description", etDescription != null ? etDescription.getText().toString().trim() : "");
        intent.putExtra("task_status", selectedStatus);
        if (focusModeLauncher != null) {
            focusModeLauncher.launch(intent);
        }
    }

    private void initFocusModeLauncher() {
        focusModeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        return;
                    }
                    Intent data = result.getData();
                    if (data != null && data.getBooleanExtra("task_done", false)) {
                        setStatus("DONE");
                    }
                    refreshWorkLogUi();
                });
    }

    private void refreshWorkLogUi() {
        if (tvPomodoroState != null) {
            tvPomodoroState.setText(R.string.task_focus_mode_label);
        }

        if (taskId == null) {
            if (tvWorklogTotal != null)
                tvWorklogTotal.setText(R.string.task_total_time_format);
            if (tvWorklogSummary != null)
                tvWorklogSummary.setText(R.string.worklog_summary_placeholder);
            if (tvWorklogEntries != null)
                tvWorklogEntries.setText(getString(R.string.task_worklog_empty));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
        long totalMs = prefs.getLong(worklogTotalKey(taskId), 0L);
        if (tvWorklogTotal != null) {
            tvWorklogTotal.setText(getString(R.string.task_total_time_dynamic_format, formatDurationCompact(totalMs)));
        }

        long remainingMs = 0;
        JSONArray entries = readWorklogEntries(prefs, taskId);
        if (entries.length() > 0) {
            JSONObject lastEntry = entries.optJSONObject(entries.length() - 1);
            if (lastEntry != null) {
                remainingMs = lastEntry.optLong("remaining_minutes", 0L) * 60000;
            }
        }

        if (tvWorklogSummary != null) {
            String remainStr = remainingMs > 0
                ? formatDurationCompact(remainingMs)
                : getString(R.string.worklog_remaining_placeholder);
            tvWorklogSummary.setText(getString(
                R.string.worklog_summary_dynamic_format,
                formatDurationCompact(totalMs),
                remainStr));
        }

        if (tvWorklogEntries != null) {
            tvWorklogEntries.setText(buildWorklogEntriesText(taskId));
        }
    }

    private void importLegacyWorklogIfNeeded(long safeTaskId) {
        SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
        JSONArray entries = readWorklogEntries(prefs, safeTaskId);
        boolean changed = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject obj = entries.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            if (obj.has("started_at")) {
                continue;
            }
            long endedAt = obj.optLong("ended_at", 0L);
            long durationMs = obj.optLong("duration_ms", 0L);
            long startedAt = endedAt > 0 && durationMs > 0 ? endedAt - durationMs : endedAt;
            try {
                obj.put("started_at", startedAt);
                changed = true;
            } catch (Exception ignored) {
            }
        }
        if (changed) {
            prefs.edit().putString(worklogLogsKey(safeTaskId), entries.toString()).apply();
        }
    }

    private String buildWorklogEntriesText(long safeTaskId) {
        importLegacyWorklogIfNeeded(safeTaskId);
        SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
        JSONArray entries = readWorklogEntries(prefs, safeTaskId);
        if (entries.length() == 0) {
            return getString(R.string.task_worklog_empty);
        }

        StringBuilder builder = new StringBuilder();
        SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject obj = entries.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            long startedAt = obj.optLong("started_at", 0L);
            long endedAt = obj.optLong("ended_at", 0L);
            long durationMs = obj.optLong("duration_ms", 0L);
            boolean completed = obj.optBoolean("completed", false);
            String startText = startedAt > 0 ? timeFormat.format(new Date(startedAt)) : "-";
            String endText = endedAt > 0 ? timeFormat.format(new Date(endedAt)) : "-";
            String outcome = completed ? "Hoàn thành" : "Chưa xong";
            String description = obj.optString("description", "").trim();

            builder.append("• ")
                    .append(startText)
                    .append(" -> ")
                    .append(endText)
                    .append("  -  ")
                    .append(formatDurationCompact(durationMs))
                    .append("  (")
                    .append(outcome)
                    .append(")");

            if (!description.isEmpty()) {
                builder.append("\n    Mô tả: ").append(description);
            }

            if (i > 0) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private JSONArray readWorklogEntries(SharedPreferences prefs, long safeTaskId) {
        String raw = prefs.getString(worklogLogsKey(safeTaskId), "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private String worklogTotalKey(long safeTaskId) {
        return WORKLOG_TOTAL_PREFIX + safeTaskId;
    }

    private String worklogLogsKey(long safeTaskId) {
        return WORKLOG_LOGS_PREFIX + safeTaskId;
    }

    private String formatDurationCompact(long ms) {
        long totalMinutes = Math.max(0L, ms / 60000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private void loadTaskHistoryIntoSection() {
        if (taskId == null || layoutHistorySection == null)
            return;

        taskRepository.getTaskHistory(taskId, new TaskRepository.TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> result) {
                runOnUiThread(() -> showHistoryFeed(result));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showEmptyHistory(error));
            }
        });
    }

    private void showHistoryFeed(List<TaskActivity> activities) {
        if (layoutHistorySection == null || listHistoryFeed == null || tvHistoryEmpty == null) {
            return;
        }

        List<ProjectHistoryItem> feed = buildHistoryFeed(activities);
        if (feed.isEmpty()) {
            showEmptyHistory(getString(R.string.task_history_empty));
            return;
        }

        tvHistoryEmpty.setVisibility(View.GONE);
        listHistoryFeed.setVisibility(View.VISIBLE);
        listHistoryFeed.setAdapter(new HistoryEventAdapter(this, feed));
    }

    private void showEmptyHistory(String message) {
        if (listHistoryFeed != null) {
            listHistoryFeed.setVisibility(View.GONE);
            listHistoryFeed.setAdapter(null);
        }
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            tvHistoryEmpty.setText(message != null && !message.trim().isEmpty()
                    ? message
                    : getString(R.string.task_history_empty));
        }
    }

    private List<ProjectHistoryItem> buildHistoryFeed(List<TaskActivity> activities) {
        List<ProjectHistoryItem> feed = new ArrayList<>();

        if (activities != null) {
            for (TaskActivity activity : activities) {
                if (activity == null)
                    continue;
                feed.add(buildHistoryItem(activity));
            }
        }

        feed.sort((left, right) -> Long.compare(
                parseHistoryTime(right != null ? right.getCreatedAt() : null),
                parseHistoryTime(left != null ? left.getCreatedAt() : null)));
        return feed;
    }

    private ProjectHistoryItem buildHistoryItem(TaskActivity activity) {
        ProjectHistoryItem item = new ProjectHistoryItem();
        item.setSource(ProjectHistoryItem.SOURCE_TASK_ACTIVITY);
        item.setActorId(activity.getUserId());
        item.setActorName(resolveActorName(activity.getUserId()));
        item.setAvatarUrl(resolveActorAvatar(activity.getUserId()));
        item.setActionLabel(resolveTaskActionLabel(activity.getActionType()));
        item.setTaskTitle(etTitle != null && etTitle.getText() != null && !etTitle.getText().toString().trim().isEmpty()
                ? etTitle.getText().toString().trim()
                : "Task");
        item.setDetail(resolveTaskDetail(activity.getActionType(), activity.getOldValue(), activity.getNewValue()));
        item.setCreatedAt(activity.getCreatedAt());
        return item;
    }

    private String resolveActorName(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Unknown";
        }
        for (User member : projectMembers) {
            if (member != null && userId.equals(member.getUserId())) {
                return member.getDisplayNameOrEmail() != null && !member.getDisplayNameOrEmail().trim().isEmpty()
                        ? member.getDisplayNameOrEmail().trim()
                        : "Unknown";
            }
        }
        return "Unknown";
    }

    private String resolveActorAvatar(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        for (User member : projectMembers) {
            if (member != null && userId.equals(member.getUserId())) {
                return member.getAvatarUrl();
            }
        }
        return null;
    }

    private String resolveTaskActionLabel(String actionType) {
        if (actionType == null || actionType.trim().isEmpty()) {
            return getString(R.string.task_history_action_updated);
        }
        String normalized = actionType.trim().toUpperCase(Locale.US);
        if ("CREATE".equals(normalized))
            return getString(R.string.task_history_action_created);
        if ("UPDATE_STATUS".equals(normalized))
            return getString(R.string.task_history_action_status_changed);
        if ("COMMENT_CREATE".equals(normalized))
            return getString(R.string.task_history_action_comment_created);
        if ("COMMENT_UPDATE".equals(normalized))
            return getString(R.string.task_history_action_comment_updated);
        if ("COMMENT_DELETE".equals(normalized))
            return getString(R.string.task_history_action_comment_deleted);
        if ("ADD_REACTION".equals(normalized))
            return getString(R.string.task_history_action_reaction_added);
        if ("REMOVE_REACTION".equals(normalized))
            return getString(R.string.task_history_action_reaction_removed);
        if ("DELETE".equals(normalized))
            return getString(R.string.task_history_action_moved_to_trash);
        if ("RESTORE".equals(normalized))
            return getString(R.string.task_history_action_restored);
        if ("HARD_DELETE".equals(normalized))
            return getString(R.string.task_history_action_deleted_permanently);
        if (normalized.startsWith("UPDATE"))
            return getString(R.string.task_history_action_edited);
        return getString(R.string.task_history_action_updated);
    }

    private String resolveTaskDetail(String actionType, String oldValue, String newValue) {
        String normalized = actionType != null ? actionType.trim().toUpperCase(Locale.US) : "";

        // Format date/time values for date-related actions
        String oldText, newText;
        if (normalized.contains("DATE") || normalized.contains("TIME")) {
            oldText = oldValue != null && !oldValue.trim().isEmpty()
                    ? com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(oldValue.trim())
                    : "-";
            newText = newValue != null && !newValue.trim().isEmpty()
                    ? com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(newValue.trim())
                    : "-";
        } else {
            oldText = oldValue != null && !oldValue.trim().isEmpty() ? oldValue.trim() : "-";
            newText = newValue != null && !newValue.trim().isEmpty() ? newValue.trim() : "-";
        }

        if ("CREATE".equals(normalized))
            return getString(R.string.task_history_initial_status_format, newText);
        if ("COMMENT_CREATE".equals(normalized))
            return newText;
        if ("COMMENT_UPDATE".equals(normalized))
            return oldText + " -> " + newText;
        if ("COMMENT_DELETE".equals(normalized))
            return oldText;
        if ("ADD_REACTION".equals(normalized) || "REMOVE_REACTION".equals(normalized))
            return newText;
        if ("HARD_DELETE".equals(normalized))
            return getString(R.string.task_history_task_deleted);
        if ("UPDATE_STATUS".equals(normalized) || "DELETE".equals(normalized) || "RESTORE".equals(normalized)) {
            return oldText + " -> " + newText;
        }
        return oldText + " -> " + newText;
    }

    private long parseHistoryTime(String raw) {
        if (raw == null || raw.isEmpty())
            return 0L;
        try {
            return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void loadComments() {
        if (taskId == null || commentAdapter == null)
            return;
        taskRepository.getTaskComments(taskId, new TaskRepository.TaskCallback<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> r) {
                runOnUiThread(() -> {
                    commentAdapter.setComments(r);
                    scrollCommentsToLatest();
                });
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void scrollCommentsToLatest() {
        if (rvComments == null || commentAdapter == null)
            return;
        int n = commentAdapter.getItemCount();
        if (n > 0)
            rvComments.post(() -> rvComments.scrollToPosition(0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (taskId != null && commentAdapter != null)
            loadComments();
        refreshWorkLogUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void createComment() {
        if (taskId == null || etCommentInput == null)
            return;
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty())
            return;
        taskRepository.createTaskComment(taskId, currentUserId, content, new TaskRepository.TaskCallback<Comment>() {
            @Override
            public void onSuccess(Comment r) {
                runOnUiThread(() -> {
                    etCommentInput.setText("");
                    loadComments();
                });
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void shareTask() {
        String title = etTitle != null && etTitle.getText() != null
                ? etTitle.getText().toString().trim()
                : "";
        if (title.isEmpty()) {
            title = "Task";
        }

        StringBuilder shareText = new StringBuilder(title);

        String description = etDescription != null && etDescription.getText() != null
                ? etDescription.getText().toString().trim()
                : "";
        if (!description.isEmpty()) {
            shareText.append("\n\n").append(description);
        }

        if (tvStatus != null && tvStatus.getText() != null) {
            String statusText = tvStatus.getText().toString().trim();
            if (!statusText.isEmpty()) {
                shareText.append("\nTrạng thái: ").append(statusText);
            }
        }

        if (tvPriority != null && tvPriority.getText() != null) {
            String priorityText = tvPriority.getText().toString().trim();
            if (!priorityText.isEmpty()) {
                shareText.append("\nĐộ ưu tiên: ").append(priorityText);
            }
        }

        if (tvAssignee != null && tvAssignee.getText() != null) {
            String assigneeText = tvAssignee.getText().toString().trim();
            if (!assigneeText.isEmpty() && !getString(R.string.task_select_assignee).equalsIgnoreCase(assigneeText)) {
                shareText.append("\nNgười phụ trách: ").append(assigneeText);
            }
        }

        if (tvDueDate != null && tvDueDate.getText() != null) {
            String dueDateText = tvDueDate.getText().toString().trim();
            if (!dueDateText.isEmpty()) {
                shareText.append("\nHạn chót: ").append(dueDateText);
                if (tvDueTime != null && tvDueTime.getText() != null) {
                    String dueTimeText = tvDueTime.getText().toString().trim();
                    if (!dueTimeText.isEmpty()) {
                        shareText.append(" ").append(dueTimeText);
                    }
                }
            }
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(sendIntent, "Chia sẻ công việc"));
    }

    private void showEditCommentDialog(Comment comment) {
        if (comment == null || comment.getId() == null)
            return;
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_edit_comment, null);
        com.google.android.material.textfield.TextInputLayout til = dv.findViewById(R.id.tilEditComment);
        com.google.android.material.textfield.TextInputEditText et = dv.findViewById(R.id.etEditComment);
        if (et != null) {
            et.setText(comment.getContent());
            if (et.getText() != null)
                et.setSelection(et.getText().length());
        }
        AlertDialog dlg = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TaskFlow_MaterialAlertDialog)
            .setTitle(R.string.comment_edit_dialog_title)
            .setView(dv)
            .setNegativeButton(R.string.comment_edit_dialog_cancel, null)
            .setPositiveButton(R.string.comment_edit_dialog_save, null)
            .create();
        dlg.setOnShowListener(d -> {
            android.widget.Button neg = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            if (neg != null) {
                neg.setAllCaps(false);
                neg.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
            }
            if (pos != null) {
                pos.setAllCaps(false);
                pos.setTextColor(ContextCompat.getColor(this, R.color.indigo_600));
                pos.setOnClickListener(v -> {
                    String c = et != null && et.getText() != null ? et.getText().toString().trim() : "";
                    if (c.isEmpty()) {
                        if (til != null)
                            til.setError(getString(R.string.comment_edit_dialog_error_empty));
                        return;
                    }
                    if (til != null)
                        til.setError(null);
                    taskRepository.updateTaskComment(comment.getId(), currentUserId, c,
                            new TaskRepository.TaskCallback<Comment>() {
                                @Override
                                public void onSuccess(Comment r) {
                                    runOnUiThread(() -> {
                                        dlg.dismiss();
                                        loadComments();
                                    });
                                }

                                @Override
                                public void onError(String e) {
                                    runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT)
                                            .show());
                                }
                            });
                });
            }
        });
        dlg.show();
    }

    private void deleteComment(Comment comment) {
        if (comment == null || comment.getId() == null)
            return;
        taskRepository.deleteTaskComment(comment.getId(), currentUserId, new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void r) {
                runOnUiThread(TaskDetailActivity.this::loadComments);
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toggleReaction(Comment comment, String reactionType) {
        if (comment == null || comment.getId() == null)
            return;
        taskRepository.toggleCommentReaction(comment.getId(), currentUserId, reactionType,
                new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                    }

                    @Override
                    public void onError(String e) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed())
                                return;
                            Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show();
                            loadComments();
                        });
                    }
                });
    }

    // ————————————————————————————————————————————————————————————————————————————————
    // Task CRUD
    // ————————————————————————————————————————————————————————————————————————————————
    private void loadTaskDetails() {
        if (taskId == null || projectId <= 0)
            return;

        // 1. THỬ LẤY TỪ RAM CACHE (0ms Response)
        Task cachedTask = taskRepository.getCachedTask(taskId);
        List<Task> cachedProjectTasks = taskRepository.getCachedTasksByProject(projectId);
        final boolean[] cacheHit = { false };

        if (cachedTask != null) {
            cacheHit[0] = true;
            final Task activeCached = cachedTask;
            currentSubTasks.clear();

            // Lọc subtasks từ cache project
            if (cachedProjectTasks != null) {
                for (Task t : cachedProjectTasks) {
                    if (taskId.equals(t.getParentTaskId())) {
                        currentSubTasks.add(t);
                    }
                }
            }

            // Hiển thị ngay lập tức không đợi mạng
            runOnUiThread(() -> {
                currentAssigneeId = activeCached.getAssigneeId();
                displayTaskDetails(activeCached);
                selectedParentTaskId = activeCached.getParentTaskId();

                String parentTitle = null;
                if (selectedParentTaskId != null && cachedProjectTasks != null) {
                    for (Task p : cachedProjectTasks) {
                        if (selectedParentTaskId.equals(p.getId())) {
                            parentTitle = p.getTitle();
                            break;
                        }
                    }
                }
                renderSubTaskInfo(parentTitle);
                updateSubTaskFabVisibility();
                loadAttachments();
                if (currentAssigneeId != null)
                    setAssigneeById(currentAssigneeId);
            });
        }

        // 2. GỌI API ĐỂ ĐỒNG BỘ DỮ LIỆU MỚI NHẤT (Silent Sync)
        if (!cacheHit[0])
            setLoading(true);

        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                if (result == null) {
                    runOnUiThread(() -> setLoading(false));
                    return;
                }

                Task freshTask = null;
                currentSubTasks.clear();

                for (Task t : result) {
                    if (taskId.equals(t.getId())) {
                        freshTask = t;
                    } else if (taskId.equals(t.getParentTaskId())) {
                        currentSubTasks.add(t);
                    }
                }

                if (freshTask != null) {
                    final Task activeTask = freshTask;
                    runOnUiThread(() -> {
                        currentAssigneeId = activeTask.getAssigneeId();
                        displayTaskDetails(activeTask);
                        selectedParentTaskId = activeTask.getParentTaskId();

                        String parentTitle = null;
                        if (selectedParentTaskId != null) {
                            for (Task p : result) {
                                if (selectedParentTaskId.equals(p.getId())) {
                                    parentTitle = p.getTitle();
                                    break;
                                }
                            }
                        }

                        renderSubTaskInfo(parentTitle);
                        updateSubTaskFabVisibility();
                        setLoading(false);
                        loadAttachments();
                        if (currentAssigneeId != null)
                            setAssigneeById(currentAssigneeId);
                    });
                } else {
                    runOnUiThread(() -> setLoading(false));
                }
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    // Nếu đã có cache rồi thì không hiện lỗi phiền người dùng
                    if (!cacheHit[0]) {
                        Toast.makeText(TaskDetailActivity.this, "Lỗi tải dữ liệu: " + e, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void displayTaskDetails(Task t) {
        if (t == null)
            return;
        etTitle.setText(t.getTitle());
        etDescription.setText(t.getDescription());
        setPriority(t.getPriority());
        setStatus(t.getStatus());

        if (t.getStartDate() != null) {
            String rs = t.getStartDate();
            tvStartDate.setText(rs.length() >= 10 ? rs.substring(0, 10) : rs);
            if (tvStartTime != null && rs.length() > 11) {
                String rawTime = rs.substring(11).trim();
                tvStartTime.setText(rawTime.length() >= 5 ? rawTime.substring(0, 5) : rawTime);
            } else if (tvStartTime != null) {
                tvStartTime.setText(getString(R.string.task_time_placeholder));
            }
        } else {
            tvStartDate.setText(getString(R.string.task_date_placeholder));
            if (tvStartTime != null) {
                tvStartTime.setText(getString(R.string.task_time_placeholder));
            }
        }

        if (t.getDueDate() != null) {
            String rd = t.getDueDate();
            tvDueDate.setText(rd.length() >= 10 ? rd.substring(0, 10) : rd);
            if (tvDueTime != null && rd.length() > 11) {
                String rawTime = rd.substring(11).trim();
                tvDueTime.setText(rawTime.length() >= 5 ? rawTime.substring(0, 5) : rawTime);
            } else if (tvDueTime != null) {
                tvDueTime.setText(getString(R.string.task_time_placeholder));
            }
        } else {
            tvDueDate.setText(getString(R.string.task_date_placeholder));
            if (tvDueTime != null) {
                tvDueTime.setText(getString(R.string.task_time_placeholder));
            }
        }
        selectedTag = t.getTag();
        if (selectedTag != null && tvTag != null)
            tvTag.setText(selectedTag);
        updateDependencyUi();
    }

    private void loadAttachments() {
        if (taskId == null)
            return;
        taskRepository.getTaskAttachments(taskId,
                new TaskRepository.TaskCallback<List<com.team7.taskflow.domain.model.Attachment>>() {
                    @Override
                    public void onSuccess(List<com.team7.taskflow.domain.model.Attachment> r) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed())
                                return;
                            existingAttachments = r;
                            updateAttachmentUi();
                        });
                    }

                    @Override
                    public void onError(String e) {
                        /* silent */ }
                });
    }

    private void loadProjectMembers() {
        if (projectId <= 0)
            return;

        // Check cache manually for immediate UI response
        List<User> cached = ProjectRepository.getInstance().getCachedMembers(projectId);
        if (cached != null && !cached.isEmpty()) {
            projectMembers = new ArrayList<>(cached);
            isMembersLoading = false;
            runOnUiThread(() -> {
                syncAssigneeUI();
                if (currentPickerContainer != null) {
                    populateMemberPicker(currentPickerContainer, currentPickerProgressBar, null);
                }
            });
            return;
        }

        isMembersLoading = true;
        if (currentPickerProgressBar != null) {
            currentPickerProgressBar.setVisibility(View.VISIBLE);
        }

        taskRepository.getProjectMembers(projectId, new TaskRepository.TaskCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> members) {
                isMembersLoading = false;
                projectMembers = members != null ? members : new ArrayList<>();
                runOnUiThread(() -> {
                    syncAssigneeUI();
                    if (currentPickerContainer != null) {
                        populateMemberPicker(currentPickerContainer, currentPickerProgressBar, null);
                    }
                });
            }

            @Override
            public void onError(String e) {
                isMembersLoading = false;
                Log.e("MEMBER_LOAD", e);
                runOnUiThread(() -> {
                    if (currentPickerProgressBar != null) {
                        currentPickerProgressBar.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void syncAssigneeUI() {
        if (currentAssigneeId != null) {
            setAssigneeById(currentAssigneeId);
        }
    }

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError(getString(R.string.task_title_required_error));
            return;
        }
        String sd = tvStartDate.getText().toString();
        String st = tvStartTime != null ? tvStartTime.getText().toString() : null;
        String sc = sd.contains("-") ? (st != null && st.matches("\\d{2}:\\d{2}") ? sd + " " + st : sd) : null;
        String dd = tvDueDate.getText().toString();
        String dt = tvDueTime != null ? tvDueTime.getText().toString() : null;
        String dc = dd.contains("-") ? (dt != null && dt.matches("\\d{2}:\\d{2}") ? dd + " " + dt : dd) : null;

        LocalDateTime startDateTime = parseUiDateTime(sd, st, false);
        LocalDateTime dueDateTime = parseUiDateTime(dd, dt, true);
        if (startDateTime != null && dueDateTime != null && startDateTime.isAfter(dueDateTime)) {
            Toast.makeText(
                    this,
                    R.string.task_datetime_order_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        Task task = new Task(projectId, title);
        task.setAssigneeId(selectedAssigneeName != null ? currentAssigneeId : null);
        task.setDescription(etDescription.getText().toString().trim());
        task.setPriority(selectedPriority);
        task.setStatus(selectedStatus);
        task.setStartDate(sc);
        task.setDueDate(dc);
        task.setTag(selectedTag);
        task.setParentTaskId(selectedParentTaskId);

        if (taskId == null)
            taskRepository.createTask(task, handleResult());
        else {
            task.setId(taskId);
            taskRepository.updateTask(taskId, task, handleResult());
        }
    }

    private LocalDateTime parseUiDateTime(String dateText, String timeText, boolean endOfDayIfNoTime) {
        if (dateText == null) {
            return null;
        }

        String date = dateText.trim();
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }

        try {
            LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
            String time = timeText != null ? timeText.trim() : "";
            if (time.matches("\\d{2}:\\d{2}")) {
                return LocalDateTime.of(localDate, LocalTime.parse(time, TIME_FORMATTER));
            }
            return LocalDateTime.of(localDate, endOfDayIfNoTime ? LocalTime.of(23, 59) : LocalTime.MIDNIGHT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TaskRepository.TaskCallback<Task> handleResult() {
        return new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task r) {
                String msg = taskId == null
                        ? getString(R.string.task_created_success)
                        : getString(R.string.task_updated_success);

                // If status changed to DONE and task has no parent, cascade DONE to all
                // subtasks
                if ("DONE".equalsIgnoreCase(selectedStatus)
                        && (selectedParentTaskId == null || selectedParentTaskId <= 0)) {
                    taskRepository.updateSubtasksStatus(r.getId(), new TaskRepository.TaskCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            handleTaskSaveCompletion(r.getId(), msg);
                        }

                        @Override
                        public void onError(String error) {
                            handleTaskSaveCompletion(r.getId(), msg);
                        }
                    });
                } else {
                    handleTaskSaveCompletion(r.getId(), msg);
                }
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(TaskDetailActivity.this,
                            getString(R.string.task_save_failed_format, e),
                            Toast.LENGTH_SHORT).show();
                });
            }
        };
    }

    private void handleTaskSaveCompletion(long savedTaskId, String msg) {
        runOnUiThread(() -> {
            if (!attachedFileUris.isEmpty()) {
                uploadSuccessCount = 0;
                uploadNextAttachment(0, savedTaskId, msg);
            } else {
                setLoading(false);
                Toast.makeText(TaskDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Pickers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void setupPickers() {
        cardPriority.setOnClickListener(v -> showPriorityPicker());
        cardStatus.setOnClickListener(v -> showStatusPicker());
        cardAssignee.setOnClickListener(v -> showAssigneePicker());
        cardAttachment.setOnClickListener(v -> openFilePicker());
        if (cardTag != null)
            cardTag.setOnClickListener(v -> showTagPicker());
        if (cardDependency != null) {
            cardDependency.setVisibility(View.VISIBLE);
            cardDependency.setOnClickListener(v -> showDependencyPicker());
        }
        updateDependencyUi();
        setPriority("MEDIUM");
        setStatus("TODO");
        setAssignee(null, null);
    }

    private void showPriorityPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_priority_picker, null);
        d.setContentView(v);
        v.findViewById(R.id.optHigh).setOnClickListener(x -> {
            setPriority("HIGH");
            d.dismiss();
        });
        v.findViewById(R.id.optMedium).setOnClickListener(x -> {
            setPriority("MEDIUM");
            d.dismiss();
        });
        v.findViewById(R.id.optLow).setOnClickListener(x -> {
            setPriority("LOW");
            d.dismiss();
        });
        v.findViewById(R.id.optNone).setOnClickListener(x -> {
            setPriority("MEDIUM");
            d.dismiss();
        });
        d.show();
    }

    private void setPriority(String priority) {
        if (priority == null)
            priority = "MEDIUM";
        selectedPriority = priority;
        String label = getString(R.string.task_priority_medium);
        int colorRes = R.color.priority_medium;
        if ("HIGH".equals(priority)) {
            label = getString(R.string.task_priority_high);
            colorRes = R.color.priority_high;
        } else if ("LOW".equals(priority)) {
            label = getString(R.string.task_priority_low);
            colorRes = R.color.priority_low;
        }
        tvPriority.setText(label);
        setActive(cardPriority, tvPriority, ivPriority, colorRes);
    }

    private void showStatusPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null)
            tvTitle.setText(R.string.task_status_picker_title);
        String[] statuses = { "TODO", "DOING", "DONE" };
        for (String s : statuses) {
            String label;
            int color;
            switch (s) {
                case "DONE":
                    label = getString(R.string.task_status_done);
                    color = R.color.success;
                    break;
                case "DOING":
                    label = getString(R.string.task_status_in_progress);
                    color = R.color.warning;
                    break;
                default:
                    label = getString(R.string.task_status_todo);
                    color = R.color.slate_700;
            }
            container.addView(createPickerItem(label, x -> {
                attemptSetStatus(s);
                d.dismiss();
            }, color));
        }
        d.setContentView(v);
        d.show();
    }

    private void setStatus(String status) {
        if (status == null)
            status = "TODO";
        selectedStatus = status;
        int colorRes;
        if ("DONE".equals(status)) {
            colorRes = R.color.success;
            tvStatus.setText(R.string.task_status_done);
        } else if ("DOING".equals(status)) {
            colorRes = R.color.warning;
            tvStatus.setText(R.string.task_status_in_progress);
        } else {
            colorRes = R.color.theme_text_primary;
            tvStatus.setText(R.string.task_status_todo);
        }
        setActive(cardStatus, tvStatus, ivStatus, colorRes);
    }

    private void showAssigneePicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_assignee_picker, null);
        d.setContentView(v);

        currentPickerContainer = v.findViewById(R.id.containerMembers);
        currentPickerProgressBar = v.findViewById(R.id.pbLoadingMembers);
        android.widget.EditText etSearch = v.findViewById(R.id.etMemberSearch);
        currentSearchFilter = "";

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchFilter = s.toString().toLowerCase(Locale.getDefault());
                    populateMemberPicker(currentPickerContainer, currentPickerProgressBar, d);
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        }

        d.setOnDismissListener(dialog -> {
            currentPickerContainer = null;
            currentPickerProgressBar = null;
        });

        populateMemberPicker(currentPickerContainer, currentPickerProgressBar, d);

        if (projectMembers.isEmpty() || isMembersLoading) {
            loadProjectMembers();
        }

        d.show();
    }

    private void populateMemberPicker(LinearLayout container, ProgressBar pb, BottomSheetDialog dialog) {
        if (container == null)
            return;
        container.removeAllViews();

        if (isMembersLoading && projectMembers.isEmpty()) {
            if (pb != null)
                pb.setVisibility(View.VISIBLE);
            return;
        }

        if (pb != null)
            pb.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        // Option: Unassign
        if (currentSearchFilter.isEmpty()) {
            View unassignView = inflater.inflate(R.layout.item_picker_member, container, false);
            TextView tvName = unassignView.findViewById(R.id.tvMemberName);
            ImageView ivAvatar = unassignView.findViewById(R.id.ivMemberAvatar);
            ImageView ivCheck = unassignView.findViewById(R.id.ivCheck);

            if (tvName != null)
                tvName.setText(R.string.task_unassign);
            if (ivAvatar != null)
                ivAvatar.setImageResource(R.drawable.ic_person);
            if (ivCheck != null)
                ivCheck.setVisibility(currentAssigneeId == null ? View.VISIBLE : View.GONE);

            unassignView.setOnClickListener(x -> {
                setAssignee(null, null);
                if (dialog != null)
                    dialog.dismiss();
            });
            container.addView(unassignView);
        }

        List<User> filtered = new ArrayList<>();
        for (User u : projectMembers) {
            String name = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(Locale.getDefault()) : "";
            if (currentSearchFilter.isEmpty() || name.contains(currentSearchFilter)) {
                filtered.add(u);
            }
        }

        if (!filtered.isEmpty()) {
            for (User m : filtered) {
                View itemView = inflater.inflate(R.layout.item_picker_member, container, false);
                TextView tvName = itemView.findViewById(R.id.tvMemberName);
                ImageView ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
                ImageView ivCheck = itemView.findViewById(R.id.ivCheck);

                String name = m.getDisplayName();
                if (tvName != null)
                    tvName.setText(name != null ? name : getString(R.string.task_member_fallback));

                if (currentAssigneeId != null && currentAssigneeId.equals(m.getUserId())) {
                    if (ivCheck != null)
                        ivCheck.setVisibility(View.VISIBLE);
                }

                if (ivAvatar != null) {
                    Glide.with(this)
                            .load(m.getAvatarUrl())
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivAvatar);
                }

                itemView.setOnClickListener(x -> {
                    setAssignee(m.getUserId(), name);
                    if (dialog != null)
                        dialog.dismiss();
                });
                container.addView(itemView);
            }
        } else if (!isMembersLoading) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(R.string.task_members_empty);
            tvEmpty.setPadding(32, 64, 32, 64);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
            container.addView(tvEmpty);
        }
    }

    private void showTagPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null)
            tvTitle.setText("Chọn nhãn");
        for (String tag : new String[] { "Design", "Dev", "Study", "Bug", "Review" }) {
            container.addView(createPickerItem(tag, x -> {
                selectedTag = tag;
                if (tvTag != null) {
                    tvTag.setText(tag);
                    setActive(cardTag, tvTag, ivTag, R.color.project_blue);
                }
                d.dismiss();
            }, R.color.theme_text_primary));
        }
        container.addView(createPickerItem("Bỏ chọn nhãn", x -> {
            selectedTag = null;
            if (tvTag != null) {
                tvTag.setText("Nhãn");
                setDefault(cardTag, tvTag, ivTag);
            }
            d.dismiss();
        }, R.color.theme_text_secondary));
        d.setContentView(v);
        d.show();
    }

    private void setAssignee(String id, String name) {
        selectedAssigneeName = name;
        currentAssigneeId = id;
        if (name != null) {
            tvAssignee.setText("@" + name);
            setActive(cardAssignee, tvAssignee, ivAssignee, R.color.project_purple);
        } else {
            tvAssignee.setText(R.string.task_select_assignee);
            setDefault(cardAssignee, tvAssignee, ivAssignee);
        }
    }

    private void updateDependencyUi() {
        if (tvDependency == null) {
            return;
        }

        if (selectedParentTaskId != null && selectedParentTaskId > 0) {
            tvDependency.setText("Phụ thuộc: #" + selectedParentTaskId);
            setActive(cardDependency, tvDependency, ivDependency, R.color.project_green);
        } else {
            tvDependency.setText("Không liên kết");
            setDefault(cardDependency, tvDependency, ivDependency);
        }
    }

    private void attemptSetStatus(String targetStatus) {
        setStatus(targetStatus);
    }

    private void showDependencyPicker() {
        if (projectId <= 0)
            return;
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null)
            tvTitle.setText(R.string.task_link_parent_title);

        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                boolean hasChildren = currentSubTasks != null && !currentSubTasks.isEmpty();

                runOnUiThread(() -> {
                    if (hasChildren) {
                        Toast.makeText(TaskDetailActivity.this, getString(R.string.task_cannot_nest_subtasks),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 1. Đưa tùy chọn "Bỏ liên kết" lên đầu tiên (dễ chọn)
                    container.addView(createPickerItem(getString(R.string.task_unlink_parent), x -> {
                        selectedParentTaskId = null;
                        renderSubTaskInfo(null);
                        updateDependencyUi();
                        updateSubTaskFabVisibility();
                        d.dismiss();
                    }, R.color.theme_text_secondary));

                    // 2. Sắp xếp danh sách Task theo ID tăng dần
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        tasks.sort((a, b) -> Long.compare(a.getId(), b.getId()));
                    } else {
                        java.util.Collections.sort(tasks, (a, b) -> Long.compare(a.getId(), b.getId()));
                    }

                    // 3. Hiển thị danh sách task khả dụng
                    for (Task t : tasks) {
                        if (taskId != null && taskId.equals(t.getId()))
                            continue;
                        if (t.getParentTaskId() != null && t.getParentTaskId() > 0)
                            continue;

                        String label = "#" + t.getId() + " • " + t.getTitle();
                        container.addView(createPickerItem(label, x -> {
                            selectedParentTaskId = t.getId();
                            renderSubTaskInfo(t.getTitle());
                            updateDependencyUi();
                            updateSubTaskFabVisibility();
                            d.dismiss();
                        }, R.color.theme_text_primary));
                    }

                    d.setContentView(v);
                    d.show();
                });
            }

            @Override
            public void onError(String e) {
                runOnUiThread(() -> Toast
                        .makeText(TaskDetailActivity.this, getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                        .show());
            }
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // UI helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void setActive(View container, TextView tv, ImageView icon, int tintColorRes) {
        int color = ContextCompat.getColor(this, tintColorRes);
        tv.setTextColor(color);
        if (icon != null)
            icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);

        if (container != null) {
            float radius = 18 * getResources().getDisplayMetrics().density;
            int bgColor = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 25);
            int strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 76);

            if (container instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) container;
                card.setCardBackgroundColor(bgColor);
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
                card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
                card.setRadius(radius);
            } else {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(bgColor);
                gd.setCornerRadius(radius);
                gd.setStroke((int) (1 * getResources().getDisplayMetrics().density), strokeColor);
                container.setBackground(gd);
            }
        }
    }

    private void setDefault(View container, TextView tv, ImageView icon) {
        int color = ContextCompat.getColor(this, COLOR_DEFAULT);
        tv.setTextColor(color);
        if (icon != null)
            icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);

        if (container != null) {
            if (container instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) container;
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.theme_surface));
                card.setStrokeColor(
                        android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.theme_border)));
                card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
                card.setRadius(18 * getResources().getDisplayMetrics().density);
            } else {
                container.setBackgroundResource(R.drawable.bg_chip_neutral);
                if (container == cardAttachment) {
                    container.setBackgroundResource(R.drawable.bg_input);
                }
            }
        }
    }

    private TextView createPickerItem(String label, View.OnClickListener listener, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        android.util.TypedValue bg = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, bg, true);
        if (bg.resourceId != 0)
            tv.setBackgroundResource(bg.resourceId);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void setupDatePickers() {
        View cs = findViewById(R.id.cardStartDate), cd = findViewById(R.id.cardDueDate);
        View cst = findViewById(R.id.cardStartTime), cdt = findViewById(R.id.cardDueTime);
        if (cs != null)
            cs.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (cd != null)
            cd.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        if (cst != null)
            cst.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (cdt != null)
            cdt.setOnClickListener(v -> showTimePicker(dueCalendar, tvDueTime));
        if (tvStartDate != null)
            tvStartDate.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (tvDueDate != null)
            tvDueDate.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        if (tvStartTime != null)
            tvStartTime.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (tvDueTime != null)
            tvDueTime.setOnClickListener(v -> showTimePicker(dueCalendar, tvDueTime));
    }

    private void showDatePicker(Calendar cal, TextView tv) {
        new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            tv.setText(dateFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar cal, TextView tv) {
        new TimePickerDialog(this, (view, h, min) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, min);
            tv.setText(String.format(Locale.US, "%02d:%02d", h, min));
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void setLoading(boolean loading) {
        if (progressBar != null)
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null)
            btnSave.setEnabled(!loading);
    }

    private void setAssigneeById(String assigneeId) {
        if (assigneeId == null) {
            setAssignee(null, null);
            return;
        }
        for (User m : projectMembers) {
            if (m.getUserId().equals(assigneeId)) {
                setAssignee(assigneeId, m.getDisplayName());
                break;
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "file";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0)
                        result = c.getString(i);
                }
            }
        }
        if ("file".equals(result) && uri.getPath() != null) {
            int cut = uri.getPath().lastIndexOf('/');
            if (cut != -1)
                result = uri.getPath().substring(cut + 1);
        }
        return result;
    }

    private String formatActivityRow(TaskActivity a) {
        String action = a.getActionType() != null ? a.getActionType() : "UPDATE";
        String oldVal = formatActivityDateTimeValue(a.getOldValue(), action);
        String newVal = formatActivityDateTimeValue(a.getNewValue(), action);
        return formatActivityTime(a.getCreatedAt()) + " - " + action + " (" + oldVal + " -> " + newVal + ")";
    }

    private String formatActivityTime(String raw) {
        if (raw == null || raw.isEmpty())
            return getString(R.string.task_history_time_just_now);
        try {
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    .format(java.util.Date.from(java.time.OffsetDateTime.parse(raw).toInstant()));
        } catch (Exception e) {
            // Fallback to substring method if timezone parsing fails
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(raw);
        }
    }

    private String formatActivityDateTimeValue(String value, String actionType) {
        if (value == null || value.isEmpty())
            return "";

        // Format if it looks like a date or datetime
        boolean isDateTimeAction = actionType != null && (actionType.contains("DATE") || actionType.contains("TIME"));
        if (isDateTimeAction) {
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(value.trim());
        }
        return value;
    }
}
