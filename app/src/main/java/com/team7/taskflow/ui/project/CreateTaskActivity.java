package com.team7.taskflow.ui.project;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent; // Thêm import này
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateTaskActivity extends BaseActivity {

    private EditText etTitle, etDescription;
    private TextView tvPriority, tvStatus, tvAssignee, tvTag, tvDependency;
    private ImageView ivPriority, ivStatus, ivAssignee, ivTag, ivDependency;
    private View cardPriority, cardStatus, cardAssignee, cardAttachment, cardTag, cardDependency;
    private TextView tvAttachment;
    private ImageView ivAttachment;
    private LinearLayout containerAttachments;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private List<Uri> attachedFileUris = new ArrayList<>();
    private List<com.team7.taskflow.domain.model.Attachment> existingAttachments = new ArrayList<>();
    private int uploadSuccessCount = 0;

    private String selectedPriority = "MEDIUM";
    private String selectedStatus = "TODO";
    private String selectedAssigneeName = null;

    private TextView tvStartDate, tvDueDate, tvStartTime, tvDueTime, btnSave, tvToolbarTitle;
    private ProgressBar progressBar;
    private View layoutCommentsSection;
    private View layoutHistorySection;
    private View layoutWorkLogSection;
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

    // SỬA TẠI ĐÂY: Để null mặc định để phân biệt Create/Update
    private Long taskId = null;
    private String currentAssigneeId = null;
    private String selectedTag = null; // Label/Tag
    private Long selectedParentTaskId = null; // Linked task dependency

    private static final int COLOR_DEFAULT = R.color.slate_500;

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar dueCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        taskRepository = TaskRepository.getInstance();
        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        // Lấy dữ liệu từ Intent an toàn
        projectId = getIntent().getLongExtra("project_id", -1);

        // KIỂM TRA NẾU CÓ ID THÌ MỚI GÁN (EDIT MODE)
        if (getIntent().hasExtra("task_id")) {
            long id = getIntent().getLongExtra("task_id", -1);
            if (id != -1) {
                taskId = id;
            }
        }

        initViews();
        initFilePickerLauncher();
        setupPickers();
        setupDatePickers();
        loadProjectMembers();

        // LOGIC PHÂN BIỆT GIAO DIỆN
        if (taskId != null) {
            tvToolbarTitle.setText("Edit Task");
            btnSave.setText("Update"); // Đổi chữ nút cho rõ ràng
            loadTaskDetails();
            setupCommentsSection();
        } else {
            tvToolbarTitle.setText("Create Task");
            btnSave.setText("Create");
            if (layoutCommentsSection != null) {
                layoutCommentsSection.setVisibility(View.GONE);
            }
        }

        setupActivityTabs();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveTask());
    }

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
        cardDependency = findViewById(R.id.cardDependency);
        ivTag = findViewById(R.id.ivTag);
        ivDependency = findViewById(R.id.ivDependency);
        tvTag = findViewById(R.id.tvTag);
        tvDependency = findViewById(R.id.tvDependency);
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
        layoutCommentsSection = findViewById(R.id.layoutCommentsSection);
        layoutHistorySection = findViewById(R.id.layoutTabHistory);
        layoutWorkLogSection = findViewById(R.id.layoutTabWorkLog);
        tabLayoutActivity = findViewById(R.id.tabLayoutActivity);
        rvComments = findViewById(R.id.rvComments);
        etCommentInput = findViewById(R.id.etCommentInput);
        btnSendComment = findViewById(R.id.btnSendComment);

        Intent intent = getIntent();
        if (intent != null) {
            String prefillTitle = intent.getStringExtra("prefill_title");
            String prefillDescription = intent.getStringExtra("prefill_description");

            if (prefillTitle != null && !prefillTitle.trim().isEmpty()) {
                etTitle.setText(prefillTitle);
            }
            if (prefillDescription != null && !prefillDescription.trim().isEmpty()) {
                etDescription.setText(prefillDescription);
            }
        }
    }

    private void setupCommentsSection() {
        if (layoutCommentsSection == null || rvComments == null) return;

        layoutCommentsSection.setVisibility(View.VISIBLE);
        commentAdapter = new TaskCommentAdapter(currentUserId, new TaskCommentAdapter.Listener() {
            @Override
            public void onEdit(Comment comment) {
                showEditCommentDialog(comment);
            }

            @Override
            public void onDelete(Comment comment) {
                deleteComment(comment);
            }

            @Override
            public void onReact(Comment comment, String reactionType) {
                if (commentAdapter != null && comment != null && comment.getId() != null) {
                    commentAdapter.applyLocalReactionToggle(comment.getId(), reactionType);
                }
                toggleReaction(comment, reactionType);
            }
        });

        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);

        if (btnSendComment != null) {
            btnSendComment.setOnClickListener(v -> createComment());
        }

        loadComments();
    }

    private void setupActivityTabs() {
        if (tabLayoutActivity == null) {
            return;
        }

        if (taskId == null) {
            tabLayoutActivity.setVisibility(View.GONE);
            if (layoutCommentsSection != null) {
                layoutCommentsSection.setVisibility(View.GONE);
            }
            if (layoutHistorySection != null) {
                layoutHistorySection.setVisibility(View.GONE);
            }
            if (layoutWorkLogSection != null) {
                layoutWorkLogSection.setVisibility(View.GONE);
            }
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

        TabLayout.Tab firstTab = tabLayoutActivity.getTabAt(0);
        if (firstTab != null) {
            firstTab.select();
        } else {
            showActivitySection(0);
        }
    }

    private void showActivitySection(int position) {
        if (layoutCommentsSection != null) {
            layoutCommentsSection.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        }
        if (layoutHistorySection != null) {
            layoutHistorySection.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        }
        if (layoutWorkLogSection != null) {
            layoutWorkLogSection.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        }

        if (position == 1 && taskId != null) {
            loadTaskHistoryIntoSection();
        }
    }

    private void loadTaskHistoryIntoSection() {
        if (taskId == null || layoutHistorySection == null) return;

        taskRepository.getTaskHistory(taskId, new TaskRepository.TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> result) {
                taskRepository.getTaskComments(taskId, new TaskRepository.TaskCallback<List<Comment>>() {
                    @Override
                    public void onSuccess(List<Comment> comments) {
                        runOnUiThread(() -> renderHistoryFeed(result, comments));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> renderHistoryFeed(result, new ArrayList<>()));
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    TextView tvHistoryEmpty = layoutHistorySection.findViewById(R.id.tvHistoryEmpty);
                    if (tvHistoryEmpty != null) {
                        tvHistoryEmpty.setText(error);
                    }
                });
            }
        });
    }

    private void renderHistoryFeed(List<TaskActivity> activities, List<Comment> comments) {
        TextView tvHistoryEmpty = layoutHistorySection != null
                ? layoutHistorySection.findViewById(R.id.tvHistoryEmpty)
                : null;
        if (tvHistoryEmpty == null) return;

        List<HistoryFeedRow> rows = new ArrayList<>();

        if (activities != null) {
            for (TaskActivity activity : activities) {
                if (activity == null) continue;
                rows.add(HistoryFeedRow.forActivity(activity, formatActivityRow(activity), parseHistoryTime(activity.getCreatedAt())));
            }
        }

        if (comments != null) {
            for (Comment comment : comments) {
                if (comment == null) continue;
                rows.add(HistoryFeedRow.forComment(comment, formatCommentRow(comment), parseHistoryTime(comment.getCreatedAt())));
            }
        }

        rows.sort((left, right) -> Long.compare(left.timestamp, right.timestamp));

        if (rows.isEmpty()) {
            tvHistoryEmpty.setText(getString(R.string.task_history_empty));
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (HistoryFeedRow row : rows) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(row.text);
        }

        tvHistoryEmpty.setText(sb.toString().trim());
    }

    private String formatCommentRow(Comment comment) {
        String author = "Unknown";
        if (comment.getUser() != null && comment.getUser().getDisplayNameOrEmail() != null
                && !comment.getUser().getDisplayNameOrEmail().trim().isEmpty()) {
            author = comment.getUser().getDisplayNameOrEmail().trim();
        } else if (comment.getUserId() != null && !comment.getUserId().trim().isEmpty()) {
            author = comment.getUserId().trim();
        }

        String content = comment.getContent() != null ? comment.getContent().trim() : "";
        if (content.isEmpty()) {
            content = "(No content)";
        }

        return formatActivityTime(comment.getCreatedAt()) + " - COMMENT: " + author + " nói: " + content;
    }

    private long parseHistoryTime(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        try {
            return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static class HistoryFeedRow {
        final String text;
        final long timestamp;

        private HistoryFeedRow(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }

        static HistoryFeedRow forActivity(TaskActivity activity, String text, long timestamp) {
            return new HistoryFeedRow(text, timestamp);
        }

        static HistoryFeedRow forComment(Comment comment, String text, long timestamp) {
            return new HistoryFeedRow(text, timestamp);
        }
    }

    private void loadComments() {
        if (taskId == null || commentAdapter == null) return;

        taskRepository.getTaskComments(taskId, new TaskRepository.TaskCallback<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> result) {
                runOnUiThread(() -> {
                    commentAdapter.setComments(result);
                    scrollCommentsToLatest();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void scrollCommentsToLatest() {
        if (rvComments == null || commentAdapter == null) return;
        int count = commentAdapter.getItemCount();
        if (count <= 0) return;
        rvComments.post(() -> rvComments.scrollToPosition(count - 1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (taskId != null && commentAdapter != null) {
            loadComments();
        }
    }

    private void createComment() {
        if (taskId == null || etCommentInput == null) return;
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty()) return;

        taskRepository.createTaskComment(taskId, currentUserId, content, new TaskRepository.TaskCallback<Comment>() {
            @Override
            public void onSuccess(Comment result) {
                runOnUiThread(() -> {
                    etCommentInput.setText("");
                    loadComments();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showEditCommentDialog(Comment comment) {
        if (comment == null || comment.getId() == null) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_comment, null);
        com.google.android.material.textfield.TextInputLayout tilInput =
                dialogView.findViewById(R.id.tilEditComment);
        com.google.android.material.textfield.TextInputEditText etInput =
                dialogView.findViewById(R.id.etEditComment);

        if (etInput != null) {
            etInput.setText(comment.getContent());
            if (etInput.getText() != null) {
                etInput.setSelection(etInput.getText().length());
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa bình luận")
                .setView(dialogView)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            if (btnNegative != null) {
                btnNegative.setAllCaps(false);
                btnNegative.setTextColor(ContextCompat.getColor(this, R.color.slate_500));
            }
            if (btnPositive != null) {
                btnPositive.setAllCaps(false);
                btnPositive.setTextColor(ContextCompat.getColor(this, R.color.indigo_600));
                btnPositive.setOnClickListener(v -> {
                    String content = etInput != null && etInput.getText() != null
                            ? etInput.getText().toString().trim() : "";
                    if (content.isEmpty()) {
                        if (tilInput != null) {
                            tilInput.setError("Bình luận không được để trống");
                        }
                        return;
                    }

                    if (tilInput != null) {
                        tilInput.setError(null);
                    }

                    taskRepository.updateTaskComment(comment.getId(), currentUserId, content,
                            new TaskRepository.TaskCallback<Comment>() {
                                @Override
                                public void onSuccess(Comment result) {
                                    runOnUiThread(() -> {
                                        dialog.dismiss();
                                        loadComments();
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show());
                                }
                            });
                });
            }
        });

        dialog.show();
    }

    private void deleteComment(Comment comment) {
        if (comment == null || comment.getId() == null) return;
        taskRepository.deleteTaskComment(comment.getId(), currentUserId, new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(CreateTaskActivity.this::loadComments);
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toggleReaction(Comment comment, String reactionType) {
        if (comment == null || comment.getId() == null) return;
        taskRepository.toggleCommentReaction(comment.getId(), currentUserId, reactionType,
                new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show();
                            loadComments();
                        });
                    }
                });
    }

    private void showTaskActivityHistory() {
        if (taskId == null) return;
        taskRepository.getTaskHistory(taskId, new TaskRepository.TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> result) {
                runOnUiThread(() -> {
                    List<String> rows = new ArrayList<>();
                    if (result != null) {
                        for (TaskActivity activity : result) {
                            rows.add(formatActivityRow(activity));
                        }
                    }
                    if (rows.isEmpty()) {
                        rows.add(getString(R.string.task_history_empty));
                    }

                    View sheetView = LayoutInflater.from(CreateTaskActivity.this)
                            .inflate(R.layout.layout_bottom_sheet_history, null);
                    BottomSheetDialog sheet = new BottomSheetDialog(CreateTaskActivity.this, R.style.Theme_TaskFlow_BottomSheet);
                    sheet.setContentView(sheetView);

                    ListView listHistory = sheetView.findViewById(R.id.listHistory);
                    TextView btnClose = sheetView.findViewById(R.id.btnCloseHistory);

                    if (listHistory != null) {
                        listHistory.setAdapter(new HistoryEventAdapter(CreateTaskActivity.this, rows));
                    }
                    if (btnClose != null) {
                        btnClose.setOnClickListener(v -> sheet.dismiss());
                    }

                    sheet.show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String formatActivityRow(TaskActivity activity) {
        String action = activity.getActionType() != null ? activity.getActionType() : "UPDATE";
        String oldValue = activity.getOldValue() != null ? activity.getOldValue() : "";
        String newValue = activity.getNewValue() != null ? activity.getNewValue() : "";
        return formatActivityTime(activity.getCreatedAt()) + " - " + action + " (" + oldValue + " -> " + newValue + ")";
    }

    private String formatActivityTime(String raw) {
        if (raw == null || raw.isEmpty()) return getString(R.string.task_history_time_just_now);
        try {
            java.time.Instant instant = java.time.OffsetDateTime.parse(raw).toInstant();
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date.from(instant));
        } catch (Exception e) {
            return raw;
        }
    }

    private void loadTaskDetails() {
        setLoading(true);
        // Dùng đúng ID taskId để lấy dữ liệu từ Repo
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                for (Task t : result) {
                    if (taskId != null && taskId.equals(t.getId())) {
                        currentAssigneeId = t.getAssigneeId();
                        runOnUiThread(() -> {
                            etTitle.setText(t.getTitle());
                            etDescription.setText(t.getDescription());
                            setPriority(t.getPriority());
                            setStatus(t.getStatus());
                            if (t.getStartDate() != null) {
                                String rawStart = t.getStartDate();
                                String startDatePart = rawStart.length() >= 10 ? rawStart.substring(0, 10) : rawStart;
                                tvStartDate.setText(startDatePart);
                                if (tvStartTime != null && rawStart.length() > 11) {
                                    String startTimePart = rawStart.substring(11);
                                    tvStartTime.setText(startTimePart);
                                }
                            }
                            if (t.getDueDate() != null) {
                                String raw = t.getDueDate();
                                String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
                                tvDueDate.setText(datePart);
                                if (tvDueTime != null && raw.length() > 11) {
                                    String timePart = raw.substring(11);
                                    tvDueTime.setText(timePart);
                                }
                            }
                            // Tag/label
                            selectedTag = t.getTag();
                            if (selectedTag != null && tvTag != null) {
                                tvTag.setText(selectedTag);
                            }
                            // Dependency
                            selectedParentTaskId = t.getParentTaskId();
                            if (selectedParentTaskId != null && tvDependency != null) {
                                tvDependency.setText("Phụ thuộc: #" + selectedParentTaskId);
                            }
                            if (currentAssigneeId != null) {
                                setAssigneeById(currentAssigneeId);
                            }
                            setLoading(false);
                            loadAttachments();
                        });
                        break;
                    }
                }
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> setLoading(false));
            }
        });
    }

    private void loadAttachments() {
        if (taskId == null) return;
        taskRepository.getTaskAttachments(taskId, new TaskRepository.TaskCallback<List<com.team7.taskflow.domain.model.Attachment>>() {
            @Override
            public void onSuccess(List<com.team7.taskflow.domain.model.Attachment> result) {
                runOnUiThread(() -> {
                    existingAttachments = result;
                    updateAttachmentUi();
                });
            }
            @Override
            public void onError(String error) {
                // Ignore silent fail
            }
        });
    }

    private void loadProjectMembers() {
        taskRepository.getProjectMembers(projectId, new TaskRepository.TaskCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> members) {
                projectMembers = members;
                runOnUiThread(() -> {
                    // Nếu đang ở chế độ Edit, hãy chọn đúng người sau khi load xong list
                    if (taskId != null && currentAssigneeId != null) {
                        setAssigneeById(currentAssigneeId);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e("MEMBER_LOAD", error);            }
        });
    }
    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        setLoading(true);
        Task task = new Task(projectId, title);

        // Xử lý lấy Assignee ID
        task.setAssigneeId(selectedAssigneeName != null ? currentAssigneeId : null);

        // Gán các thông tin khác
        task.setDescription(etDescription.getText().toString().trim());
        task.setPriority(selectedPriority);
        task.setStatus(selectedStatus);

        String startDateText = tvStartDate.getText().toString();
        String startTimeText = tvStartTime != null ? tvStartTime.getText().toString() : null;
        String startCombined = null;
        if (startDateText != null && startDateText.contains("-")) {
            if (startTimeText != null && startTimeText.matches("\\d{2}:\\d{2}")) {
                startCombined = startDateText + " " + startTimeText;
            } else {
                startCombined = startDateText;
            }
        }
        task.setStartDate(startCombined);

        String dueDateText = tvDueDate.getText().toString();
        String dueTimeText = tvDueTime != null ? tvDueTime.getText().toString() : null;
        String dueCombined = null;
        if (dueDateText != null && dueDateText.contains("-")) {
            if (dueTimeText != null && dueTimeText.matches("\\d{2}:\\d{2}")) {
                dueCombined = dueDateText + " " + dueTimeText;
            } else {
                dueCombined = dueDateText;
            }
        }
        task.setDueDate(dueCombined);
        task.setTag(selectedTag);
        task.setParentTaskId(selectedParentTaskId);

        // QUAN TRỌNG: Kiểm tra taskId để quyết định gọi hàm nào
        if (taskId == null) {
            // TẠO MỚI
            taskRepository.createTask(task, handleResult());
        } else {
            // CẬP NHẬT
            task.setId(taskId); // Phải gán ID vào object task để server biết update dòng nào
            taskRepository.updateTask(taskId, task, handleResult());
        }
    }

    private TaskRepository.TaskCallback<Task> handleResult() {
        return new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task result) {
                runOnUiThread(() -> {
                    String msg = taskId == null ? "Task Created" : "Task Updated";
                    if (!attachedFileUris.isEmpty()) {
                        uploadSuccessCount = 0;
                        uploadNextAttachment(0, result.getId(), msg);
                    } else {
                        setLoading(false);
                        Toast.makeText(CreateTaskActivity.this, msg, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(CreateTaskActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        };
    }
    private void setAssigneeById(String assigneeId) {
        if (assigneeId == null) {
            setAssignee(null, null);
            return;
        }
        for (int i = 0; i < projectMembers.size(); i++) {
            if (projectMembers.get(i).getUserId().equals(assigneeId)) {
                setAssignee(assigneeId, projectMembers.get(i).getDisplayName());
                break;
            }
        }
    }
    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null) btnSave.setEnabled(!loading);
    }

    private void setupPickers() {
        cardPriority.setOnClickListener(v -> showPriorityPicker());
        cardStatus.setOnClickListener(v -> showStatusPicker());
        cardAssignee.setOnClickListener(v -> showAssigneePicker());
        cardAttachment.setOnClickListener(v -> openFilePicker());

        if (cardTag != null) {
            cardTag.setOnClickListener(v -> showTagPicker());
        }
        if (cardDependency != null) {
            cardDependency.setOnClickListener(v -> showDependencyPicker());
        }

        // Default UI states
        setPriority("MEDIUM");
        setStatus("TODO");
        setAssignee(null, null);
    }

    private void showPriorityPicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_priority_picker, null);
        dialog.setContentView(view);
        view.findViewById(R.id.optHigh).setOnClickListener(v -> { setPriority("HIGH"); dialog.dismiss(); });
        view.findViewById(R.id.optMedium).setOnClickListener(v -> { setPriority("MEDIUM"); dialog.dismiss(); });
        view.findViewById(R.id.optLow).setOnClickListener(v -> { setPriority("LOW"); dialog.dismiss(); });
        view.findViewById(R.id.optNone).setOnClickListener(v -> { setPriority("MEDIUM"); dialog.dismiss(); });
        dialog.show();
    }

    private void setPriority(String priority) {
        if (priority == null) priority = "MEDIUM";
        selectedPriority = priority;
        String label = "Medium";
        int colorRes = R.color.priority_medium;
        if ("HIGH".equals(priority)) { label = "High"; colorRes = R.color.priority_high; }
        else if ("LOW".equals(priority)) { label = "Low"; colorRes = R.color.priority_low; }

        tvPriority.setText(label);
        setActive(cardPriority, tvPriority, ivPriority, colorRes);
    }

    private void showStatusPicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);

        View view = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        LinearLayout container = view.findViewById(R.id.containerItems);
        if (tvTitle != null) tvTitle.setText("Chọn trạng thái");

        String[] statuses = {"TODO", "DOING", "DONE"};
        for (String status : statuses) {
            String label;
            int color;
            switch (status) {
                case "DONE":
                    label = "Hoàn thành";
                    color = R.color.success;
                    break;
                case "DOING":
                    label = "Đang làm";
                    color = R.color.warning;
                    break;
                default:
                    label = "Cần làm";
                    color = R.color.slate_700;
                    break;
            }
            TextView item = createPickerItem(label, v -> {
                attemptSetStatus(status);
                dialog.dismiss();
            }, color);
            container.addView(item);
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void setStatus(String status) {
        if (status == null) status = "TODO";
        selectedStatus = status;
        tvStatus.setText(status);
        int colorRes = R.color.primary;
        if ("DONE".equals(status)) {
            colorRes = R.color.success;
            tvStatus.setText("Done");
        } else if ("DOING".equals(status)) {
            colorRes = R.color.warning;
            tvStatus.setText("Doing");
        } else {
            colorRes = R.color.slate_400;
            tvStatus.setText("To Do");
        }
        setActive(cardStatus, tvStatus, ivStatus, colorRes);
    }

    private void showAssigneePicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_assignee_picker, null);
        dialog.setContentView(view);
        android.widget.LinearLayout container = view.findViewById(R.id.containerMembers);

        for (User member : projectMembers) {
            String name = member.getDisplayName();
            container.addView(createPickerItem(name, v -> {
                setAssignee(member.getUserId(), name);
                dialog.dismiss();
            }, R.color.slate_900));
        }

        container.addView(createPickerItem("Bỏ chọn", v -> {
            setAssignee(null, null);
            dialog.dismiss();
        }, R.color.slate_500));

        dialog.show();
    }

    private void showTagPicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);

        View view = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        LinearLayout container = view.findViewById(R.id.containerItems);

        if (tvTitle != null) tvTitle.setText("Chọn nhãn");

        String[] tags = {"Design", "Dev", "Study", "Bug", "Review"};
        for (String tag : tags) {
            TextView item = createPickerItem(tag, v -> {
                selectedTag = tag;
                if (tvTag != null) {
                    tvTag.setText(tag);
                    setActive(cardTag, tvTag, ivTag, R.color.project_blue);
                }
                dialog.dismiss();
            }, R.color.slate_900);
            container.addView(item);
        }

        TextView clearItem = createPickerItem("Bỏ chọn nhãn", v -> {
            selectedTag = null;
            if (tvTag != null) {
                tvTag.setText("Nhãn");
                setDefault(cardTag, tvTag, ivTag);
            }
            dialog.dismiss();
        }, R.color.slate_500);
        container.addView(clearItem);

        dialog.setContentView(view);
        dialog.show();
    }

    private void setAssignee(String id, String name) {
        selectedAssigneeName = name;
        currentAssigneeId = id; 
        if (name != null) {
            tvAssignee.setText("@" + name);
            setActive(cardAssignee, tvAssignee, ivAssignee, R.color.project_purple); 
        } else {
            tvAssignee.setText("Phân công");
            setDefault(cardAssignee, tvAssignee, ivAssignee);
        }
    }

    /**
     * Apply business rule: if this task depends on another task, that linked
     * task must be DONE before this one can be marked DONE.
     */
    private void attemptSetStatus(String targetStatus) {
        // Only guard transitions TO DONE
        if (!"DONE".equalsIgnoreCase(targetStatus) || selectedParentTaskId == null) {
            setStatus(targetStatus);
            return;
        }

        setLoading(true);
        taskRepository.getTaskById(selectedParentTaskId, new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task depTask) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (depTask != null && "DONE".equalsIgnoreCase(depTask.getStatus())) {
                        setStatus("DONE");
                    } else {
                        Toast.makeText(CreateTaskActivity.this,
                                "Task liên kết phải hoàn thành trước khi đóng task này",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(CreateTaskActivity.this,
                            "Không kiểm tra được trạng thái task liên kết",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showDependencyPicker() {
        if (projectId <= 0) return;

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_simple_list, null);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        LinearLayout container = view.findViewById(R.id.containerItems);
        if (tvTitle != null) tvTitle.setText("Liên kết tác vụ");

        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                runOnUiThread(() -> {
                    for (Task t : tasks) {
                        if (taskId != null && taskId.equals(t.getId())) continue; // don't depend on itself
                        String label = "#" + t.getId() + " • " + t.getTitle();
                        TextView item = createPickerItem(label, v -> {
                            selectedParentTaskId = t.getId();
                            if (tvDependency != null) {
                                tvDependency.setText("Phụ thuộc: " + label);
                                setActive(cardDependency, tvDependency, ivDependency, R.color.project_green);
                            }
                            dialog.dismiss();
                        }, R.color.slate_900);
                        container.addView(item);
                    }

                    TextView clearItem = createPickerItem("Không liên kết", v -> {
                        selectedParentTaskId = null;
                        if (tvDependency != null) {
                            tvDependency.setText("Không liên kết");
                            setDefault(cardDependency, tvDependency, ivDependency);
                        }
                        dialog.dismiss();
                    }, R.color.slate_500);
                    container.addView(clearItem);

                    dialog.setContentView(view);
                    dialog.show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, "Không tải được danh sách task", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setActive(View container, TextView tv, ImageView icon, int tintColorRes) {
        int color = ContextCompat.getColor(this, tintColorRes);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 25)); // 10% opacity
            gd.setCornerRadius(10 * getResources().getDisplayMetrics().density); 
            gd.setStroke((int) (1 * getResources().getDisplayMetrics().density), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 76));
            container.setBackground(gd); 
        }
    }

    private void setDefault(View container, TextView tv, ImageView icon) {
        int color = ContextCompat.getColor(this, COLOR_DEFAULT);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) {
            container.setBackgroundResource(R.drawable.bg_chip_neutral);
        }
    }

    private TextView createPickerItem(String label, View.OnClickListener listener, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        android.util.TypedValue selectableBg = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBg, true);
        if (selectableBg.resourceId != 0) {
            tv.setBackgroundResource(selectableBg.resourceId);
        }
        
        tv.setOnClickListener(listener);
        return tv;
    }

    private void setupDatePickers() {
        // Click vào card chứa (area lớn hơn) để mở Date/Time Picker
        View cardStart = findViewById(R.id.cardStartDate);
        View cardDue = findViewById(R.id.cardDueDate);
        View cardStartTime = findViewById(R.id.cardStartTime);
        View cardDueTime = findViewById(R.id.cardDueTime);
        if (cardStart != null) cardStart.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (cardDue != null) cardDue.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        if (cardStartTime != null) cardStartTime.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (cardDueTime != null) cardDueTime.setOnClickListener(v -> showTimePicker(dueCalendar, tvDueTime));
        // Fallback: click trực tiếp vào TextView cũng hoạt động
        if (tvStartDate != null) tvStartDate.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (tvDueDate != null) tvDueDate.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        if (tvStartTime != null) tvStartTime.setOnClickListener(v -> showTimePicker(startCalendar, tvStartTime));
        if (tvDueTime != null) tvDueTime.setOnClickListener(v -> showTimePicker(dueCalendar, tvDueTime));
    }

    private void showDatePicker(Calendar cal, TextView tv) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            tv.setText(dateFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar cal, TextView tv) {
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            cal.set(Calendar.HOUR_OF_DAY, selectedHour);
            cal.set(Calendar.MINUTE, selectedMinute);
            tv.setText(String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute));
        }, hour, minute, true).show();
    }

    private void initFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                attachedFileUris.add(data.getClipData().getItemAt(i).getUri());
                            }
                        } else if (data.getData() != null) {
                            attachedFileUris.add(data.getData());
                        }
                        updateAttachmentUi();
                    }
                }
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "Chọn file đính kèm"));
    }

    private void updateAttachmentUi() {
        containerAttachments.removeAllViews();
        if (attachedFileUris.isEmpty() && existingAttachments.isEmpty()) {
            containerAttachments.setVisibility(View.GONE);
            tvAttachment.setText("Đính kèm");
            setDefault(cardAttachment, tvAttachment, ivAttachment);
            return;
        }

        containerAttachments.setVisibility(View.VISIBLE);
        int count = attachedFileUris.size() + existingAttachments.size();
        tvAttachment.setText(count + " file");
        setActive(cardAttachment, tvAttachment, ivAttachment, R.color.slate_900);

        LayoutInflater inflater = LayoutInflater.from(this);
        
        // Render existing attachments
        for (int i = 0; i < existingAttachments.size(); i++) {
            com.team7.taskflow.domain.model.Attachment attachment = existingAttachments.get(i);
            View itemView = inflater.inflate(R.layout.item_attachment_chip, containerAttachments, false);
            TextView tvName = itemView.findViewById(R.id.tvFileName);
            ImageView btnRemove = itemView.findViewById(R.id.btnRemoveFile);
            
            tvName.setText(attachment.getFileName());
            
            itemView.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(attachment.getFileUrl()));
                    startActivity(browserIntent);
                } catch(Exception e){}
            });

            btnRemove.setOnClickListener(v -> {
                if (attachment.getId() != null) {
                    Toast.makeText(CreateTaskActivity.this, "Đang xoá...", Toast.LENGTH_SHORT).show();
                    TaskRepository.getInstance().deleteTaskAttachment(attachment.getId(), new TaskRepository.TaskCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(() -> {
                                existingAttachments.remove(attachment);
                                updateAttachmentUi();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, "Xoá lỗi: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                }
            });
            containerAttachments.addView(itemView);
        }

        // Render newly added files
        for (int i = 0; i < attachedFileUris.size(); i++) {
            Uri uri = attachedFileUris.get(i);
            View itemView = inflater.inflate(R.layout.item_attachment_chip, containerAttachments, false);
            
            TextView tvName = itemView.findViewById(R.id.tvFileName);
            ImageView btnRemove = itemView.findViewById(R.id.btnRemoveFile);
            
            tvName.setText(getFileNameFromUri(uri));
            btnRemove.setOnClickListener(v -> {
                attachedFileUris.remove(uri);
                updateAttachmentUi();
            });
            
            containerAttachments.addView(itemView);
        }
    }

    private void uploadNextAttachment(int index, long targetTaskId, String baseMsg) {
        if (index >= attachedFileUris.size()) {
            String finalMsg = baseMsg;
            if (uploadSuccessCount > 0) {
                finalMsg += " (Kèm " + uploadSuccessCount + " file)";
            }
            Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        Uri uri = attachedFileUris.get(index);
        tvToolbarTitle.setText("⏳ Đang tải file " + (index + 1) + "/" + attachedFileUris.size());

        String mimeType = getContentResolver().getType(uri);
        String fileName = getFileNameFromUri(uri);

        TaskRepository.getInstance().uploadTaskAttachment(
                targetTaskId,
                uri,
                fileName,
                mimeType,
                getContentResolver(),
                new TaskRepository.TaskCallback<com.team7.taskflow.domain.model.Attachment>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Attachment result) {
                        uploadSuccessCount++;
                        runOnUiThread(() -> uploadNextAttachment(index + 1, targetTaskId, baseMsg));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(CreateTaskActivity.this, "Lỗi file " + (index + 1) + ": " + error, Toast.LENGTH_LONG).show();
                            uploadNextAttachment(index + 1, targetTaskId, baseMsg);
                        });
                    }
                }
        );
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "file";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if ("file".equals(result) && uri.getPath() != null) {
            String path = uri.getPath();
            int cut = path.lastIndexOf('/');
            if (cut != -1) result = path.substring(cut + 1);
        }
        return result;
    }

}