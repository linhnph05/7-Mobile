package com.team7.taskflow.ui.project;

import android.Manifest;
import android.app.AlertDialog;
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
import com.google.android.material.tabs.TabLayout;
import com.team7.taskflow.R;
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
    private ImageView ivPriority, ivStatus, ivAssignee, ivTag, ivDependency;
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
    private ArrayAdapter<String> assigneeAdapter;
    private long projectId;

    private Long taskId = null;
    private String currentAssigneeId = null;
    private String selectedTag = null;
    private Long selectedParentTaskId = null;

    private static final int COLOR_DEFAULT = R.color.theme_text_secondary;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final String WORKLOG_PREFS = "task_worklog";
    private static final String WORKLOG_TOTAL_PREFIX = "task_total_";
    private static final String WORKLOG_LOGS_PREFIX = "task_logs_";

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar dueCalendar   = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private TextView tvPomodoroState;
    private TextView tvWorklogTotal;
    private TextView tvWorklogEntries;
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
            if (id != -1) taskId = id;
        }

        initViews();
        applySubTaskContextFromIntent();
        initFilePickerLauncher();
        initCameraLauncher();       // âœ… ThÃªm
        setupPickers();
        setupDatePickers();
        loadProjectMembers();
        initFocusModeLauncher();
        setupWorkLogSection();

        if (taskId != null) {
            loadTaskDetails();
            setupCommentsSection();
        } else {
            if (layoutCommentsSection != null) layoutCommentsSection.setVisibility(View.GONE);
        }

        tvToolbarTitle.setText("Chi tiết công việc");
        btnSave.setText("Lưu");

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
        etTitle            = findViewById(R.id.etTaskTitle);
        etDescription      = findViewById(R.id.etTaskDescription);
        tvPriority         = findViewById(R.id.tvPriority);
        tvStatus           = findViewById(R.id.tvStatus);
        tvAssignee         = findViewById(R.id.tvAssignee);
        ivPriority         = findViewById(R.id.ivPriority);
        ivStatus           = findViewById(R.id.ivStatus);
        ivAssignee         = findViewById(R.id.ivAssignee);
        cardPriority       = findViewById(R.id.cardPriority);
        cardStatus         = findViewById(R.id.cardStatus);
        cardAssignee       = findViewById(R.id.cardAssignee);
        cardAttachment     = findViewById(R.id.cardAttachment);
        cardTag            = findViewById(R.id.cardTag);
        cardDependency     = findViewById(R.id.cardDependency);
        ivTag              = findViewById(R.id.ivTag);
        ivDependency       = findViewById(R.id.ivDependency);
        tvTag              = findViewById(R.id.tvTag);
        tvDependency       = findViewById(R.id.tvDependency);
        tvAttachment       = findViewById(R.id.tvAttachment);
        ivAttachment       = findViewById(R.id.ivAttachment);
        containerAttachments = findViewById(R.id.containerAttachments);
        tvStartDate        = findViewById(R.id.tvStartDate);
        tvDueDate          = findViewById(R.id.tvDueDate);
        tvStartTime        = findViewById(R.id.tvStartTime);
        tvDueTime          = findViewById(R.id.tvDueTime);
        btnSave            = findViewById(R.id.btnSave);
        tvToolbarTitle     = findViewById(R.id.tvToolbarTitle);
        progressBar        = findViewById(R.id.progressBar);
        tvSubTaskInfo      = findViewById(R.id.tvSubTaskInfo);
        cardSubTaskInfo    = findViewById(R.id.cardSubTaskInfo);
        layoutCommentsSection = findViewById(R.id.layoutCommentsSection);
        
        // ÄÃ£ gá»™p conflict khá»Ÿi táº¡o view
        layoutHistorySection = findViewById(R.id.layoutTabHistory);
        layoutWorkLogSection = findViewById(R.id.layoutTabWorkLog);
        listHistoryFeed = findViewById(R.id.listHistoryFeed);
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty);
        tabLayoutActivity = findViewById(R.id.tabLayoutActivity);
        rvComments = findViewById(R.id.rvComments);
        etCommentInput = findViewById(R.id.etCommentInput);
        btnSendComment = findViewById(R.id.btnSendComment);
        tvPomodoroState = findViewById(R.id.tvPomodoroState);
        tvWorklogTotal = findViewById(R.id.tvWorklogTotal);
        tvWorklogEntries = findViewById(R.id.tvWorklogEntries);
        fabFocusMode = findViewById(R.id.fabFocusMode);

        Intent intent = getIntent();
        if (intent != null) {
            String prefillTitle = intent.getStringExtra("prefill_title");
            String prefillDesc  = intent.getStringExtra("prefill_description");
            if (prefillTitle != null && !prefillTitle.trim().isEmpty()) etTitle.setText(prefillTitle);
            if (prefillDesc  != null && !prefillDesc.trim().isEmpty())  etDescription.setText(prefillDesc);
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

    private void renderSubTaskInfo(String parentTaskTitle) {
        if (tvSubTaskInfo == null || cardSubTaskInfo == null) {
            return;
        }

        if (selectedParentTaskId == null || selectedParentTaskId <= 0) {
            cardSubTaskInfo.setVisibility(View.GONE);
            return;
        }

        String safeTitle = parentTaskTitle != null ? parentTaskTitle.trim() : "";
        if (safeTitle.isEmpty()) {
            safeTitle = "#" + selectedParentTaskId;
        }

        cardSubTaskInfo.setVisibility(View.VISIBLE);
        tvSubTaskInfo.setText(getString(R.string.task_subtask_of_format, safeTitle));
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // File picker & Camera
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                .setTitle("Đính kèm file")
                .setItems(new String[]{"Chụp ảnh từ Camera", "Thư viện / File"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openFileChooser();
                })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            java.io.File imgFile = java.io.File.createTempFile(
                    "img_" + System.currentTimeMillis(), ".jpg", cacheDir);
            cameraImageUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", imgFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Chọn file đính kèm"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Cần cấp quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
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
                                        @Override public void onSuccess(Void r) {
                                            runOnUiThread(() -> {
                                                existingAttachments.remove(attachment);
                                                updateAttachmentUi();
                                            });
                                        }
                                        @Override public void onError(String err) {
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
                    uri,    // URI local
                    null,   // chÆ°a cÃ³ URL
                    v -> { attachedFileUris.remove(uri); updateAttachmentUi(); });
            containerAttachments.addView(itemView);
        }
    }

    /**
     * Bind dá»¯ liá»‡u vÃ o item_attachment_chip.
     * localUri  â€” URI local (file chÆ°a upload), null náº¿u Ä‘Ã£ upload
     * remoteUrl â€” URL Supabase (file Ä‘Ã£ upload), null náº¿u chÆ°a upload
     */
    private void bindAttachmentChip(View itemView, String fileName, String mimeType,
                                    Uri localUri, String remoteUrl, View.OnClickListener onRemove) {

        TextView  tvName   = itemView.findViewById(R.id.tvFileName);
        ImageView ivIcon   = itemView.findViewById(R.id.ivFileIcon);
        ImageView ivThumb  = itemView.findViewById(R.id.ivImageThumb);
        ImageView btnPreview = itemView.findViewById(R.id.btnPreview);
        ImageView btnRemove  = itemView.findViewById(R.id.btnRemoveFile);

        tvName.setText(fileName != null ? fileName : "file");

        boolean isImage = mimeType != null && mimeType.startsWith("image/");
        boolean isPdf   = mimeType != null && mimeType.equals("application/pdf");

        if (isImage) {
            // Hiá»‡n thumbnail, áº©n icon
            ivIcon.setVisibility(View.GONE);
            if (ivThumb != null) {
                ivThumb.setVisibility(View.VISIBLE);
                Object src = localUri != null ? localUri : remoteUrl;
                Glide.with(this).load(src)
                        .placeholder(R.drawable.ic_attach_file)
                        .error(R.drawable.ic_attach_file)
                        .centerCrop()
                        .into(ivThumb);
            }
        } else {
            if (ivThumb != null) ivThumb.setVisibility(View.GONE);
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
                if (localUri != null) intent.putExtra("image_uri", localUri.toString());
                else                  intent.putExtra("image_url", remoteUrl);
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

        if (btnPreview != null) btnPreview.setOnClickListener(previewClick);
        itemView.setOnClickListener(previewClick);
        if (btnRemove != null) btnRemove.setOnClickListener(onRemove);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Upload
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void uploadNextAttachment(int index, long targetTaskId, String baseMsg) {
        if (index >= attachedFileUris.size()) {
            String finalMsg = baseMsg;
            if (uploadSuccessCount > 0) finalMsg += " (Kèm " + uploadSuccessCount + " file)";
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
                    @Override public void onSuccess(com.team7.taskflow.domain.model.Attachment r) {
                        uploadSuccessCount++;
                        runOnUiThread(() -> uploadNextAttachment(index + 1, targetTaskId, baseMsg));
                    }
                    @Override public void onError(String error) {
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
        if (layoutCommentsSection == null || rvComments == null) return;
        layoutCommentsSection.setVisibility(View.VISIBLE);
        commentAdapter = new TaskCommentAdapter(currentUserId, new TaskCommentAdapter.Listener() {
            @Override public void onEdit(Comment c)   { showEditCommentDialog(c); }
            @Override public void onDelete(Comment c) { deleteComment(c); }
            @Override public void onReact(Comment c, String type) {
                if (commentAdapter != null && c != null && c.getId() != null)
                    commentAdapter.applyLocalReactionToggle(c.getId(), type);
                toggleReaction(c, type);
            }
        });
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);
        if (btnSendComment != null) btnSendComment.setOnClickListener(v -> createComment());
        loadComments();
    }

    private void setupActivityTabs() {
        if (tabLayoutActivity == null) return;
        if (taskId == null) {
            tabLayoutActivity.setVisibility(View.GONE);
            if (layoutCommentsSection != null) layoutCommentsSection.setVisibility(View.GONE);
            if (layoutHistorySection  != null) layoutHistorySection.setVisibility(View.GONE);
            if (layoutWorkLogSection  != null) layoutWorkLogSection.setVisibility(View.GONE);
            return;
        }
        tabLayoutActivity.setVisibility(View.VISIBLE);
        tabLayoutActivity.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab)   { showActivitySection(tab != null ? tab.getPosition() : 0); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { showActivitySection(tab != null ? tab.getPosition() : 0); }
        });
        TabLayout.Tab first = tabLayoutActivity.getTabAt(0);
        if (first != null) first.select(); else showActivitySection(0);
    }

    private void showActivitySection(int pos) {
        if (layoutCommentsSection != null) layoutCommentsSection.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        if (layoutHistorySection  != null) layoutHistorySection.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        if (layoutWorkLogSection  != null) layoutWorkLogSection.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
        if (pos == 1 && taskId != null) loadTaskHistoryIntoSection();
        if (pos == 2 && taskId != null) refreshWorkLogUi();
    }

    private void setupWorkLogSection() {
        refreshWorkLogUi();
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
            tvPomodoroState.setText("Focus mode");
        }

        if (taskId == null) {
            if (tvWorklogTotal != null) tvWorklogTotal.setText("Total tracked: 0m");
            if (tvWorklogEntries != null) tvWorklogEntries.setText(getString(R.string.task_worklog_empty));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
        long totalMs = prefs.getLong(worklogTotalKey(taskId), 0L);
        if (tvWorklogTotal != null) {
            tvWorklogTotal.setText("Total tracked: " + formatDurationCompact(totalMs));
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
            String outcome = completed ? "done" : "not done";
            builder.append("• ")
                    .append(startText)
                    .append(" -> ")
                    .append(endText)
                    .append("  -  ")
                    .append(formatDurationCompact(durationMs))
                    .append("  (")
                    .append(outcome)
                    .append(")");
            if (i > 0) {
                builder.append("\n");
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
        if (taskId == null || layoutHistorySection == null) return;
        
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
                if (activity == null) continue;
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
            return "đã cập nhật";
        }
        String normalized = actionType.trim().toUpperCase(Locale.US);
        if ("CREATE".equals(normalized)) return "đã tạo";
        if ("UPDATE_STATUS".equals(normalized)) return "đã đổi trạng thái";
        if ("COMMENT_CREATE".equals(normalized)) return "đã bình luận";
        if ("COMMENT_UPDATE".equals(normalized)) return "đã chỉnh sửa bình luận";
        if ("COMMENT_DELETE".equals(normalized)) return "đã xóa bình luận";
        if ("ADD_REACTION".equals(normalized)) return "đã thêm cảm xúc";
        if ("REMOVE_REACTION".equals(normalized)) return "đã bỏ cảm xúc";
        if ("DELETE".equals(normalized)) return "đã đưa vào thùng rác";
        if ("RESTORE".equals(normalized)) return "đã khôi phục";
        if ("HARD_DELETE".equals(normalized)) return "đã xóa vĩnh viễn";
        if (normalized.startsWith("UPDATE")) return "đã chỉnh sửa";
        return "đã cập nhật";
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
        
        if ("CREATE".equals(normalized)) return "Trạng thái ban đầu: " + newText;
        if ("COMMENT_CREATE".equals(normalized)) return newText;
        if ("COMMENT_UPDATE".equals(normalized)) return oldText + " -> " + newText;
        if ("COMMENT_DELETE".equals(normalized)) return oldText;
        if ("ADD_REACTION".equals(normalized) || "REMOVE_REACTION".equals(normalized)) return newText;
        if ("HARD_DELETE".equals(normalized)) return "Task đã bị xóa";
        if ("UPDATE_STATUS".equals(normalized) || "DELETE".equals(normalized) || "RESTORE".equals(normalized)) {
            return oldText + " -> " + newText;
        }
        return oldText + " -> " + newText;
    }

    private long parseHistoryTime(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        try {
            return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void loadComments() {
        if (taskId == null || commentAdapter == null) return;
        taskRepository.getTaskComments(taskId, new TaskRepository.TaskCallback<List<Comment>>() {
            @Override public void onSuccess(List<Comment> r) { runOnUiThread(() -> { commentAdapter.setComments(r); scrollCommentsToLatest(); }); }
            @Override public void onError(String e) { runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show()); }
        });
    }

    private void scrollCommentsToLatest() {
        if (rvComments == null || commentAdapter == null) return;
        int n = commentAdapter.getItemCount();
        if (n > 0) rvComments.post(() -> rvComments.scrollToPosition(0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (taskId != null && commentAdapter != null) loadComments();
        refreshWorkLogUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void createComment() {
        if (taskId == null || etCommentInput == null) return;
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty()) return;
        taskRepository.createTaskComment(taskId, currentUserId, content, new TaskRepository.TaskCallback<Comment>() {
            @Override public void onSuccess(Comment r) { runOnUiThread(() -> { etCommentInput.setText(""); loadComments(); }); }
            @Override public void onError(String e) { runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show()); }
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
            if (!assigneeText.isEmpty() && !"Chọn người".equalsIgnoreCase(assigneeText)) {
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
        if (comment == null || comment.getId() == null) return;
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_edit_comment, null);
        com.google.android.material.textfield.TextInputLayout til = dv.findViewById(R.id.tilEditComment);
        com.google.android.material.textfield.TextInputEditText et = dv.findViewById(R.id.etEditComment);
        if (et != null) { et.setText(comment.getContent()); if (et.getText() != null) et.setSelection(et.getText().length()); }
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Chỉnh sửa bình luận").setView(dv).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        dlg.setOnShowListener(d -> {
            android.widget.Button neg = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            if (neg != null) { neg.setAllCaps(false); neg.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary)); }
            if (pos != null) {
                pos.setAllCaps(false); pos.setTextColor(ContextCompat.getColor(this, R.color.indigo_600));
                pos.setOnClickListener(v -> {
                    String c = et != null && et.getText() != null ? et.getText().toString().trim() : "";
                    if (c.isEmpty()) { if (til != null) til.setError("Bình luận không được để trống"); return; }
                    if (til != null) til.setError(null);
                    taskRepository.updateTaskComment(comment.getId(), currentUserId, c, new TaskRepository.TaskCallback<Comment>() {
                        @Override public void onSuccess(Comment r) { runOnUiThread(() -> { dlg.dismiss(); loadComments(); }); }
                        @Override public void onError(String e) { runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show()); }
                    });
                });
            }
        });
        dlg.show();
    }

    private void deleteComment(Comment comment) {
        if (comment == null || comment.getId() == null) return;
        taskRepository.deleteTaskComment(comment.getId(), currentUserId, new TaskRepository.TaskCallback<Void>() {
            @Override public void onSuccess(Void r) { runOnUiThread(TaskDetailActivity.this::loadComments); }
            @Override public void onError(String e) { runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show()); }
        });
    }

    private void toggleReaction(Comment comment, String reactionType) {
        if (comment == null || comment.getId() == null) return;
        taskRepository.toggleCommentReaction(comment.getId(), currentUserId, reactionType, new TaskRepository.TaskCallback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(String e) { runOnUiThread(() -> { Toast.makeText(TaskDetailActivity.this, e, Toast.LENGTH_SHORT).show(); loadComments(); }); }
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Task CRUD
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void loadTaskDetails() {
        setLoading(true);
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override public void onSuccess(List<Task> result) {
                for (Task t : result) {
                    if (taskId != null && taskId.equals(t.getId())) {
                        currentAssigneeId = t.getAssigneeId();
                        runOnUiThread(() -> {
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
                            if (selectedTag != null && tvTag != null) tvTag.setText(selectedTag);
                            selectedParentTaskId = t.getParentTaskId();
                            updateDependencyUi();
                            renderSubTaskInfo(t.getParentTaskId() != null ? ("#" + t.getParentTaskId()) : null);
                            if (currentAssigneeId != null) setAssigneeById(currentAssigneeId);
                            setLoading(false);
                            loadAttachments();
                        });
                        break;
                    }
                }
            }
            @Override public void onError(String e) { runOnUiThread(() -> setLoading(false)); }
        });
    }

    private void loadAttachments() {
        if (taskId == null) return;
        taskRepository.getTaskAttachments(taskId, new TaskRepository.TaskCallback<List<com.team7.taskflow.domain.model.Attachment>>() {
            @Override public void onSuccess(List<com.team7.taskflow.domain.model.Attachment> r) { runOnUiThread(() -> { existingAttachments = r; updateAttachmentUi(); }); }
            @Override public void onError(String e) { /* silent */ }
        });
    }

    private void loadProjectMembers() {
        taskRepository.getProjectMembers(projectId, new TaskRepository.TaskCallback<List<User>>() {
            @Override public void onSuccess(List<User> members) {
                projectMembers = members;
                runOnUiThread(() -> { if (taskId != null && currentAssigneeId != null) setAssigneeById(currentAssigneeId); });
            }
            @Override public void onError(String e) { Log.e("MEMBER_LOAD", e); }
        });
    }

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) { etTitle.setError("Vui lòng nhập tên công việc"); return; }
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
                    "Giờ bắt đầu không được lớn hơn giờ kết thúc. Vui lòng chỉnh lại thời gian.",
                    Toast.LENGTH_LONG
            ).show();
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

        if (taskId == null) taskRepository.createTask(task, handleResult());
        else { task.setId(taskId); taskRepository.updateTask(taskId, task, handleResult()); }
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
            @Override public void onSuccess(Task r) {
                runOnUiThread(() -> {
                    String msg = taskId == null ? "Đã tạo công việc" : "Đã cập nhật công việc";
                    if (!attachedFileUris.isEmpty()) { uploadSuccessCount = 0; uploadNextAttachment(0, r.getId(), msg); }
                    else { setLoading(false); Toast.makeText(TaskDetailActivity.this, msg, Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish(); }
                });
            }
            @Override public void onError(String e) { runOnUiThread(() -> { setLoading(false); Toast.makeText(TaskDetailActivity.this, "Không thành công: " + e, Toast.LENGTH_SHORT).show(); }); }
        };
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Pickers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void setupPickers() {
        cardPriority.setOnClickListener(v -> showPriorityPicker());
        cardStatus.setOnClickListener(v -> showStatusPicker());
        cardAssignee.setOnClickListener(v -> showAssigneePicker());
        cardAttachment.setOnClickListener(v -> openFilePicker());
        if (cardTag != null) cardTag.setOnClickListener(v -> showTagPicker());
        if (cardDependency != null) {
            cardDependency.setVisibility(View.VISIBLE);
            cardDependency.setOnClickListener(v -> showDependencyPicker());
        }
        updateDependencyUi();
        setPriority("MEDIUM"); setStatus("TODO"); setAssignee(null, null);
    }

    private void showPriorityPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_priority_picker, null); d.setContentView(v);
        v.findViewById(R.id.optHigh).setOnClickListener(x -> { setPriority("HIGH"); d.dismiss(); });
        v.findViewById(R.id.optMedium).setOnClickListener(x -> { setPriority("MEDIUM"); d.dismiss(); });
        v.findViewById(R.id.optLow).setOnClickListener(x -> { setPriority("LOW"); d.dismiss(); });
        v.findViewById(R.id.optNone).setOnClickListener(x -> { setPriority("MEDIUM"); d.dismiss(); });
        d.show();
    }

    private void setPriority(String priority) {
        if (priority == null) priority = "MEDIUM";
        selectedPriority = priority;
        String label = "Trung bình"; int colorRes = R.color.priority_medium;
        if ("HIGH".equals(priority)) { label = "Cao"; colorRes = R.color.priority_high; }
        else if ("LOW".equals(priority)) { label = "Thấp"; colorRes = R.color.priority_low; }
        tvPriority.setText(label); setActive(cardPriority, tvPriority, ivPriority, colorRes);
    }

    private void showStatusPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle); LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null) tvTitle.setText("Chọn trạng thái");
        String[] statuses = {"TODO", "DOING", "DONE"};
        for (String s : statuses) {
            String label; int color;
            switch (s) { case "DONE": label = "Hoàn thành"; color = R.color.success; break; case "DOING": label = "Đang làm"; color = R.color.warning; break; default: label = "Cần làm"; color = R.color.theme_text_primary; }
            container.addView(createPickerItem(label, x -> { attemptSetStatus(s); d.dismiss(); }, color));
        }
        d.setContentView(v); d.show();
    }

    private void setStatus(String status) {
        if (status == null) status = "TODO"; selectedStatus = status;
        int colorRes;
        if ("DONE".equals(status)) { colorRes = R.color.success; tvStatus.setText("Hoàn thành"); }
        else if ("DOING".equals(status)) { colorRes = R.color.warning; tvStatus.setText("Đang làm"); }
        else { colorRes = R.color.theme_text_secondary; tvStatus.setText("Cần làm"); }
        setActive(cardStatus, tvStatus, ivStatus, colorRes);
    }

    private void showAssigneePicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_assignee_picker, null); d.setContentView(v);
        LinearLayout container = v.findViewById(R.id.containerMembers);
        for (User m : projectMembers) { String name = m.getDisplayName(); container.addView(createPickerItem(name, x -> { setAssignee(m.getUserId(), name); d.dismiss(); }, R.color.theme_text_primary)); }
        container.addView(createPickerItem("Bỏ chọn", x -> { setAssignee(null, null); d.dismiss(); }, R.color.theme_text_secondary));
        d.show();
    }

    private void showTagPicker() {
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle); LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null) tvTitle.setText("Chọn nhãn");
        for (String tag : new String[]{"Design", "Dev", "Study", "Bug", "Review"}) {
            container.addView(createPickerItem(tag, x -> { selectedTag = tag; if (tvTag != null) { tvTag.setText(tag); setActive(cardTag, tvTag, ivTag, R.color.project_blue); } d.dismiss(); }, R.color.theme_text_primary));
        }
        container.addView(createPickerItem("Bỏ chọn nhãn", x -> { selectedTag = null; if (tvTag != null) { tvTag.setText("Nhãn"); setDefault(cardTag, tvTag, ivTag); } d.dismiss(); }, R.color.theme_text_secondary));
        d.setContentView(v); d.show();
    }

    private void setAssignee(String id, String name) {
        selectedAssigneeName = name; currentAssigneeId = id;
        if (name != null) { tvAssignee.setText("@" + name); setActive(cardAssignee, tvAssignee, ivAssignee, R.color.project_purple); }
        else { tvAssignee.setText("Phân công"); setDefault(cardAssignee, tvAssignee, ivAssignee); }
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
        if (!"DONE".equalsIgnoreCase(targetStatus) || selectedParentTaskId == null) {
            setStatus(targetStatus);
            return;
        }
        setLoading(true);
        taskRepository.getTaskById(selectedParentTaskId, new TaskRepository.TaskCallback<Task>() {
            @Override public void onSuccess(Task dep) {
                runOnUiThread(() -> { setLoading(false);
                    if (dep != null && "DONE".equalsIgnoreCase(dep.getStatus())) setStatus("DONE");
                    else Toast.makeText(TaskDetailActivity.this, "Task liên kết phải hoàn thành trước khi đóng task này", Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onError(String e) { runOnUiThread(() -> { setLoading(false); Toast.makeText(TaskDetailActivity.this, "Không kiểm tra được trạng thái task liên kết", Toast.LENGTH_SHORT).show(); }); }
        });
    }

    private void showDependencyPicker() {
        if (projectId <= 0) return;
        BottomSheetDialog d = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View v = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = v.findViewById(R.id.tvTitle); LinearLayout container = v.findViewById(R.id.containerItems);
        if (tvTitle != null) tvTitle.setText("Liên kết tác vụ");
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override public void onSuccess(List<Task> tasks) {
                runOnUiThread(() -> {
                    for (Task t : tasks) {
                        if (taskId != null && taskId.equals(t.getId())) continue;
                        String label = "#" + t.getId() + " • " + t.getTitle();
                        container.addView(createPickerItem(label, x -> { selectedParentTaskId = t.getId(); updateDependencyUi(); d.dismiss(); }, R.color.theme_text_primary));
                    }
                    container.addView(createPickerItem("Không liên kết", x -> { selectedParentTaskId = null; updateDependencyUi(); d.dismiss(); }, R.color.theme_text_secondary));
                    d.setContentView(v); d.show();
                });
            }
            @Override public void onError(String e) { runOnUiThread(() -> Toast.makeText(TaskDetailActivity.this, "Không tải được danh sách task", Toast.LENGTH_SHORT).show()); }
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // UI helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void setActive(View container, TextView tv, ImageView icon, int tintColorRes) {
        int color = ContextCompat.getColor(this, tintColorRes);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 25));
            gd.setCornerRadius(10 * getResources().getDisplayMetrics().density);
            gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 76));
            container.setBackground(gd);
        }
    }

    private void setDefault(View container, TextView tv, ImageView icon) {
        int color = ContextCompat.getColor(this, COLOR_DEFAULT);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) container.setBackgroundResource(R.drawable.bg_chip_neutral);
        if (container == cardAttachment) {
            container.setBackgroundResource(R.drawable.bg_input);
        }
    }

    private TextView createPickerItem(String label, View.OnClickListener listener, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(label); tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        android.util.TypedValue bg = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, bg, true);
        if (bg.resourceId != 0) tv.setBackgroundResource(bg.resourceId);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void setupDatePickers() {
        View cs = findViewById(R.id.cardStartDate), cd = findViewById(R.id.cardDueDate);
        View cst = findViewById(R.id.cardStartTime), cdt = findViewById(R.id.cardDueTime);
        if (cs != null) cs.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (cd != null) cd.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        if (cst != null) cst.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (cdt != null) cdt.setOnClickListener(v -> showTimePicker(dueCalendar, tvDueTime));
        if (tvStartDate != null) tvStartDate.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (tvDueDate   != null) tvDueDate.setOnClickListener(v   -> showDatePicker(dueCalendar, tvDueDate));
        if (tvStartTime != null) tvStartTime.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (tvDueTime   != null) tvDueTime.setOnClickListener(v   -> showTimePicker(dueCalendar, tvDueTime));
    }

    private void showDatePicker(Calendar cal, TextView tv) {
        new DatePickerDialog(this, (view, y, m, d) -> { cal.set(y, m, d); tv.setText(dateFormat.format(cal.getTime())); }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar cal, TextView tv) {
        new TimePickerDialog(this, (view, h, min) -> { cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); tv.setText(String.format(Locale.US, "%02d:%02d", h, min)); }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null) btnSave.setEnabled(!loading);
    }

    private void setAssigneeById(String assigneeId) {
        if (assigneeId == null) { setAssignee(null, null); return; }
        for (User m : projectMembers) { if (m.getUserId().equals(assigneeId)) { setAssignee(assigneeId, m.getDisplayName()); break; } }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "file";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) result = c.getString(i); }
            }
        }
        if ("file".equals(result) && uri.getPath() != null) { int cut = uri.getPath().lastIndexOf('/'); if (cut != -1) result = uri.getPath().substring(cut + 1); }
        return result;
    }

    private String formatActivityRow(TaskActivity a) {
        String action = a.getActionType() != null ? a.getActionType() : "UPDATE";
        String oldVal = formatActivityDateTimeValue(a.getOldValue(), action);
        String newVal = formatActivityDateTimeValue(a.getNewValue(), action);
        return formatActivityTime(a.getCreatedAt()) + " - " + action + " (" + oldVal + " -> " + newVal + ")";
    }

    private String formatActivityTime(String raw) {
        if (raw == null || raw.isEmpty()) return getString(R.string.task_history_time_just_now);
        try {
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date.from(java.time.OffsetDateTime.parse(raw).toInstant()));
        } catch (Exception e) {
            // Fallback to substring method if timezone parsing fails
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(raw);
        }
    }

    private String formatActivityDateTimeValue(String value, String actionType) {
        if (value == null || value.isEmpty()) return "";
        
        // Format if it looks like a date or datetime
        boolean isDateTimeAction = actionType != null && (actionType.contains("DATE") || actionType.contains("TIME"));
        if (isDateTimeAction) {
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(value.trim());
        }
        return value;
    }

}
