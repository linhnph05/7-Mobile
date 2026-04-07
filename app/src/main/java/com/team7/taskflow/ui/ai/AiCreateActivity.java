package com.team7.taskflow.ui.ai;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import android.view.LayoutInflater;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.AiCallback;
import com.team7.taskflow.data.remote.AiService;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.utils.SessionManager;
import android.util.Log;

/**
 * AI-powered task creation screen.
 *
 * Flow:
 * 1. User types a natural language prompt.
 * 2. After a debounce, Gemini AI parses it into structured fields.
 * 3. User can manually adjust any field via bottom-sheet pickers.
 * 4. Pressing "Send" creates the task in Supabase DB.
 */
public class AiCreateActivity extends AppCompatActivity {

    private static final String EXTRA_PARENT_TASK_ID = "parent_task_id";
    private static final String EXTRA_PARENT_TASK_TITLE = "parent_task_title";

    // ── Constants ────────────────────────────────────────────────────────

    /** Color resource used for buttons that have a value selected */
    private static final int COLOR_ACTIVE = R.color.theme_text_primary;
    /** Color resource used for buttons in their default/empty state */
    private static final int COLOR_DEFAULT = R.color.theme_text_secondary;
    /** Priority label → DB value mapping */
    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    private static final String PRIORITY_LOW = "LOW";

    // ── Views ────────────────────────────────────────────────────────────

    private View bottomSheet, bgOverlay;
    private View cardSubTaskInfo;
    private EditText etPrompt, etParsedTitle, etParsedDescription;
    private ImageButton btnSaveTask;
    private TextView tvTaskId;
    private TextView tvSubTaskInfo;

    // Action buttons (LinearLayout acting as chips)
    private View cardStartDate, cardDueDate, cardPriority, cardAssignee, cardTag, cardAttachment;
    private TextView tvStartDate, tvDueDate, tvPriority, tvAssignee, tvTag, tvAttachment;
    private ImageView ivStartDate, ivDueDate, ivPriority, ivAssignee, ivTag, ivAttachment;

    // Attachment preview
    private LinearLayout containerAttachments;

    // ── State ────────────────────────────────────────────────────────────

    private String projectKey = "PJT";
    private long projectId = -1;
    private int nextTaskNumber = 1;
    private List<Uri> attachedFileUris = new ArrayList<>();
    private int uploadSuccessCount = 0;
    private List<ProjectMember> projectMembers = new ArrayList<>();

    // Selected values (stored for DB write)
    private String selectedPriority = PRIORITY_MEDIUM;
    private String selectedStartDate = null;  // ISO 8601
    private String selectedDueDate = null;    // ISO 8601
    private String selectedAssigneeName = null;
    private String selectedAssigneeId = null;
    private String selectedTag = null;
    private Long selectedParentTaskId = null;
    private String selectedParentTaskTitle = null;

    // AI parsing
    private final Handler parseHandler = new Handler(Looper.getMainLooper());
    private Runnable parseRunnable;
    private boolean aiParsingDone = false;

    // File picker
    private ActivityResultLauncher<Intent> filePickerLauncher;

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        int contentLayout = shouldUseSubTaskLayout(getIntent())
            ? R.layout.activity_ai_create_subtask
            : R.layout.activity_ai_create;
        setContentView(contentLayout);

        // Handle back pressed gesture with OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });

        readIntentExtras();
        bindViews();
        initFilePickerLauncher();
        animateEntrance();
        setupClickListeners();
        setupAiParsing();
        resetStatusBadge();
    }

    private void readIntentExtras() {
        String key = getIntent().getStringExtra("project_key");
        if (key != null && !key.isEmpty()) projectKey = key;

        projectId = getIntent().getLongExtra("project_id", -1);
        int num = getIntent().getIntExtra("next_task_number", 1);
        if (num > 0) nextTaskNumber = num;

        long parentTaskId = getIntent().getLongExtra(EXTRA_PARENT_TASK_ID, -1);
        if (parentTaskId > 0) {
            selectedParentTaskId = parentTaskId;
            // Validate that parent task doesn't already have a parent (prevent nested subtasks)
            validateParentTaskHierarchy();
        }
        selectedParentTaskTitle = getIntent().getStringExtra(EXTRA_PARENT_TASK_TITLE);

        if (projectId != -1) {
            loadProjectMembers();
        }
    }

    private void validateParentTaskHierarchy() {
        if (selectedParentTaskId == null || selectedParentTaskId <= 0) {
            return;
        }

        TaskRepository.getInstance().getTaskById(selectedParentTaskId, new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task parentTask) {
                if (parentTask != null && parentTask.getParentTaskId() != null && parentTask.getParentTaskId() > 0) {
                    // Parent task already has a parent - disallow nesting beyond 1 level
                    runOnUiThread(() -> {
                        Toast.makeText(AiCreateActivity.this, 
                            "Không thể tạo task con từ một task con. Chỉ cho phép một cấp độ con.", 
                            Toast.LENGTH_LONG).show();
                        selectedParentTaskId = null;
                        selectedParentTaskTitle = null;
                        closeActivity();
                    });
                }
            }

            @Override
            public void onError(String error) {
                // Log error but allow proceeding
                Log.e("AiCreateActivity", "Error validating parent task: " + error);
            }
        });
    }

    private void loadProjectMembers() {
        ProjectRepository.getInstance()
                .getProjectMembers(projectId, new ProjectRepository.ProjectCallback<List<ProjectMember>>() {
                    @Override
                    public void onSuccess(List<ProjectMember> result) {
                        projectMembers = result;
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("AiCreateActivity", "Failed to load project members: " + error);
                    }
                });
    }

    // ── View binding ─────────────────────────────────────────────────────

    private void bindViews() {
        bottomSheet = findViewById(R.id.bottomSheet);
        bgOverlay = findViewById(R.id.bgOverlay);
        cardSubTaskInfo = findViewById(R.id.cardSubTaskInfo);
        btnSaveTask = findViewById(R.id.btnSaveTask);
        etPrompt = findViewById(R.id.etPrompt);
        etParsedTitle = findViewById(R.id.etParsedTitle);
        etParsedDescription = findViewById(R.id.etParsedDescription);
        tvTaskId = findViewById(R.id.tvTaskId);
        tvSubTaskInfo = findViewById(R.id.tvSubTaskInfo);

        cardStartDate = findViewById(R.id.cardStartDate);
        cardDueDate = findViewById(R.id.cardDueDate);
        cardPriority = findViewById(R.id.cardPriority);
        cardAssignee = findViewById(R.id.cardAssignee);
        cardTag = findViewById(R.id.cardTag);
        cardAttachment = findViewById(R.id.cardAttachment);

        tvStartDate = findViewById(R.id.tvParsedStartDate);
        tvDueDate = findViewById(R.id.tvParsedDueDate);
        tvPriority = findViewById(R.id.tvParsedPriority);
        tvAssignee = findViewById(R.id.tvParsedAssignee);
        tvTag = findViewById(R.id.tvParsedTag);
        tvAttachment = findViewById(R.id.tvAttachment);

        ivStartDate = findViewById(R.id.ivParsedStartDate);
        ivDueDate = findViewById(R.id.ivParsedDueDate);
        ivPriority = findViewById(R.id.ivParsedPriority);
        ivAssignee = findViewById(R.id.ivParsedAssignee);
        ivTag = findViewById(R.id.ivParsedTag);
        ivAttachment = findViewById(R.id.ivAttachment);

        containerAttachments = findViewById(R.id.containerAttachments);
        renderSubTaskInfo();
        applyPrefillFromIntent();
    }

    private void applyPrefillFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }

        String prefillTitle = intent.getStringExtra("prefill_title");
        String prefillDescription = intent.getStringExtra("prefill_description");

        if (prefillTitle != null && !prefillTitle.trim().isEmpty() && etParsedTitle != null) {
            etParsedTitle.setText(prefillTitle.trim());
        }

        if (prefillDescription != null && !prefillDescription.trim().isEmpty()) {
            String trimmed = prefillDescription.trim();
            if (etParsedDescription != null) {
                etParsedDescription.setText(trimmed);
            }
            if (etPrompt != null && etPrompt.getText().toString().trim().isEmpty()) {
                etPrompt.setText(trimmed);
            }
        }
    }

    private void renderSubTaskInfo() {
        if (tvSubTaskInfo == null) {
            return;
        }
        if (selectedParentTaskId == null || selectedParentTaskId <= 0
                || selectedParentTaskTitle == null || selectedParentTaskTitle.trim().isEmpty()) {
            if (cardSubTaskInfo != null) {
                cardSubTaskInfo.setVisibility(View.GONE);
            }
            tvSubTaskInfo.setVisibility(View.GONE);
            return;
        }
        TextView tvParentTaskLabel = findViewById(R.id.tvParentTaskLabel);
        String safeParentTitle = selectedParentTaskTitle.trim();
        if (tvParentTaskLabel != null) {
            tvSubTaskInfo.setText(safeParentTitle);
            if (cardSubTaskInfo != null) {
                cardSubTaskInfo.setVisibility(View.VISIBLE);
            }
        } else {
            tvSubTaskInfo.setText(getString(R.string.task_subtask_of_format, safeParentTitle));
        }
        tvSubTaskInfo.setVisibility(View.VISIBLE);
    }

    private boolean shouldUseSubTaskLayout(Intent intent) {
        if (intent == null) {
            return false;
        }
        long parentTaskId = intent.getLongExtra(EXTRA_PARENT_TASK_ID, -1L);
        String parentTaskTitle = intent.getStringExtra(EXTRA_PARENT_TASK_TITLE);
        return parentTaskId > 0 && parentTaskTitle != null && !parentTaskTitle.trim().isEmpty();
    }

    // ── Animations ───────────────────────────────────────────────────────

    private void animateEntrance() {
        bgOverlay.setAlpha(0f);
        bgOverlay.animate().alpha(1f).setDuration(250).start();
        bottomSheet.setTranslationY(1500f);
        bottomSheet.animate().translationY(0f).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        etPrompt.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etPrompt, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    // ── Click listeners ──────────────────────────────────────────────────

    private void setupClickListeners() {
        bgOverlay.setOnClickListener(v -> closeActivity());
        // Global touch listener (dispatchTouchEvent) covers keyboard hiding now.

        cardStartDate.setOnClickListener(v -> showDatePicker(true));
        cardDueDate.setOnClickListener(v -> showDatePicker(false));
        cardPriority.setOnClickListener(v -> showPriorityPicker());
        cardAssignee.setOnClickListener(v -> showAssigneePicker());
        cardTag.setOnClickListener(v -> showTagPicker());
        cardAttachment.setOnClickListener(v -> openFilePicker());

        btnSaveTask.setOnClickListener(v -> saveTask());
    }

    // ── AI Parsing (Gemini) ──────────────────────────────────────────────

    private void setupAiParsing() {
        parseRunnable = this::callAiParse;
        etPrompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                parseHandler.removeCallbacks(parseRunnable);
                aiParsingDone = false;
                if (s.length() > 5) {
                    parseHandler.postDelayed(parseRunnable, 800); // Faster 0.8s debounce
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void callAiParse() {
        String prompt = etPrompt.getText().toString().trim();
        if (prompt.isEmpty() || aiParsingDone) return;

        // Show subtle loading indicator
        tvTaskId.setText("AI đang phân tích...");

        // Build members CSV for AI context
        StringBuilder sb = new StringBuilder();
        for (ProjectMember m : projectMembers) {
            if (m.getUser() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(m.getUser().getDisplayNameOrEmail());
            }
        }
        String membersCsv = sb.toString();

        AiService.getInstance().parsePrompt(prompt, membersCsv, new AiCallback() {
            @Override
            public void onSuccess(AiService.ParsedTask result) {
                runOnUiThread(() -> applyAiResult(result));
            }

            @Override
            public void onError(String error) {
                // If AI fails, fall back to local simple parsing
                runOnUiThread(() -> {
                    fallbackLocalParse(prompt);
                    if (error.contains("429")) {
                        tvTaskId.setText("⚠ Hết lượt AI - Hãy thử lại sau");
                        tvTaskId.setBackgroundResource(R.drawable.bg_task_id_red); 
                    } else if (error.contains("Safety")) {
                        tvTaskId.setText("❌ AI từ chối (Gợi ý nhạy cảm)");
                        tvTaskId.setBackgroundResource(R.drawable.bg_task_id_red); 
                        parseHandler.postDelayed(AiCreateActivity.this::resetStatusBadge, 2500);
                    } else {
                        tvTaskId.setText("❌ AI Lỗi: " + error);
                        tvTaskId.setBackgroundResource(R.drawable.bg_task_id_red);
                        // Don't reset if it's an error so user can see what's wrong 
                    }
                });
            }
        });
    }

    /** Apply AI-parsed result to all form fields */
    private void applyAiResult(AiService.ParsedTask result) {
        aiParsingDone = true;
        resetStatusBadge();

        if (!result.title.isEmpty() && etParsedTitle.getText().toString().isEmpty()) {
            etParsedTitle.setText(result.title);
        }
        if (!result.description.isEmpty() && etParsedDescription.getText().toString().isEmpty()) {
            etParsedDescription.setText(result.description);
        }

        // Priority
        if (!result.priority.isEmpty()) {
            setPriority(result.priority);
        }

        // Assignee
        if (!result.assigneeName.isEmpty()) {
            selectedAssigneeName = result.assigneeName;
            // Try matching ID
            for (ProjectMember m : projectMembers) {
                if (m.getUser() != null && m.getUser().getDisplayNameOrEmail().equalsIgnoreCase(result.assigneeName)) {
                    selectedAssigneeId = m.getUserId();
                    break;
                }
            }
            tvAssignee.setText("@" + result.assigneeName);
            setActive(cardAssignee, tvAssignee, ivAssignee, R.color.text_purple_600);
        }

        // Due date
        if (!result.dueDate.isEmpty()) {
            selectedDueDate = result.dueDate;
            tvDueDate.setText(formatDateForDisplay(result.dueDate));
            setActive(cardDueDate, tvDueDate, ivDueDate, R.color.project_blue);
        }

        // Start date
        if (!result.startDate.isEmpty()) {
            selectedStartDate = result.startDate;
            tvStartDate.setText(formatDateForDisplay(result.startDate));
            setActive(cardStartDate, tvStartDate, ivStartDate, R.color.project_blue);
        }

        // Tag
        if (!result.tag.isEmpty()) {
            selectedTag = result.tag;
            tvTag.setText("#" + result.tag);
            setActive(cardTag, tvTag, ivTag, R.color.text_orange_600);
        }
    }

    /** Simple keyword-based fallback when Gemini is unavailable */
    private void fallbackLocalParse(String prompt) {
        String text = prompt.toLowerCase();

        // Auto-title
        if (!prompt.isEmpty() && etParsedTitle.getText().toString().isEmpty()) {
            String line = prompt.split("\n")[0];
            // If the prompt is starting with "hủy diệt", just take the title
            String cleanTitle = line.replace("ngày mai", "").replace("hôm nay", "").replace("lúc", "").trim();
            if (cleanTitle.length() > 60) cleanTitle = cleanTitle.substring(0, 60) + "...";
            etParsedTitle.setText(cleanTitle);
        }

        if (text.contains("gấp") || text.contains("khẩn") || text.contains("high") || text.contains("hủy diệt")) {
            setPriority(PRIORITY_HIGH);
        } else if (text.contains("chậm") || text.contains("low")) {
            setPriority(PRIORITY_LOW);
        }

        // Basic date detection
        java.util.Calendar c = java.util.Calendar.getInstance();
        if (text.contains("ngày mai") || text.contains("mai")) {
            c.add(java.util.Calendar.DAY_OF_YEAR, 1);
            c.set(java.util.Calendar.HOUR_OF_DAY, 8); c.set(java.util.Calendar.MINUTE, 0);
            selectedDueDate = String.format("%04d-%02d-%02dT%02d:%02d:00", 
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH)+1, c.get(java.util.Calendar.DAY_OF_MONTH), 8, 0);
            tvDueDate.setText(formatDateForDisplay(selectedDueDate));
            setActive(cardDueDate, tvDueDate, ivDueDate, R.color.project_blue);
        } else if (text.contains("chiều")) {
            c.set(java.util.Calendar.HOUR_OF_DAY, 15); c.set(java.util.Calendar.MINUTE, 0);
            selectedDueDate = String.format("%04d-%02d-%02dT%02d:%02d:00", 
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH)+1, c.get(java.util.Calendar.DAY_OF_MONTH), 15, 0);
            tvDueDate.setText(formatDateForDisplay(selectedDueDate));
            setActive(cardDueDate, tvDueDate, ivDueDate, R.color.project_blue);
        }

        if (text.contains("đức")) { selectedAssigneeName = "Đức"; tvAssignee.setText("@Đức"); setActive(cardAssignee, tvAssignee, ivAssignee, R.color.text_purple_600); }
        else if (text.contains("linh")) { selectedAssigneeName = "Linh"; tvAssignee.setText("@Linh"); setActive(cardAssignee, tvAssignee, ivAssignee, R.color.text_purple_600); }

        if (text.contains("code") || text.contains("dev") || text.contains("fix")) { selectedTag = "Backend"; tvTag.setText("#Backend"); setActive(cardTag, tvTag, ivTag, R.color.text_orange_600); }
    }

    // ── Save Task to Supabase ────────────────────────────────────────────

    private void saveTask() {
        String title = etParsedTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_field), Toast.LENGTH_SHORT).show();
            etParsedTitle.requestFocus();
            return;
        }
        if (projectId == -1) {
            Toast.makeText(this, "Project ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double tap
        btnSaveTask.setEnabled(false);

        Task task = new Task(projectId, title);
        task.setDescription(etParsedDescription.getText().toString().trim());
        task.setPriority(selectedPriority);
        task.setStatus("TODO");
        if (selectedDueDate != null) task.setDueDate(selectedDueDate);
        if (selectedStartDate != null) task.setStartDate(selectedStartDate);
        if (selectedTag != null) task.setTag(selectedTag);
        if (selectedAssigneeId != null) task.setAssigneeId(selectedAssigneeId);
        task.setParentTaskId(selectedParentTaskId);

        TaskRepository.getInstance().createTask(task, new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task created) {
                runOnUiThread(() -> {
                    String msg = projectKey + "-" + created.getId() + " đã lưu!";
                    if (!attachedFileUris.isEmpty()) {
                        uploadSuccessCount = 0;
                        uploadNextAttachment(0, created.getId(), msg);
                    } else {
                        Toast.makeText(AiCreateActivity.this, msg, Toast.LENGTH_LONG).show();
                        closeActivity();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnSaveTask.setEnabled(true);
                    if (error.contains("401")) {
                        Toast.makeText(AiCreateActivity.this, "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(AiCreateActivity.this, "Lỗi tạo Task: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // ── Pickers (BottomSheet) ────────────────────────────────────────────

    private void showDatePicker(boolean isStartDate) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_datetime_picker, null);
        dialog.setContentView(view);

        android.widget.CalendarView calendarView = view.findViewById(R.id.calendarView);
        LinearLayout layoutTimePicker = view.findViewById(R.id.layoutTimePicker);
        TextView tvSelectedTime = view.findViewById(R.id.tvSelectedTime);
        android.widget.Button btnSaveDateTime = view.findViewById(R.id.btnSaveDateTime);

        final int[] sel = {
                java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
                8, 0
        };

        tvSelectedTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", sel[3], sel[4]));

        calendarView.setOnDateChangeListener((v, y, m, d) -> { sel[0] = y; sel[1] = m; sel[2] = d; });

        layoutTimePicker.setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (tp, h, mi) -> {
                    sel[3] = h; sel[4] = mi;
                    tvSelectedTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", h, mi));
                }, sel[3], sel[4], true).show()
        );

        btnSaveDateTime.setOnClickListener(v -> {
            // Build ISO datetime string
            String iso = String.format(java.util.Locale.getDefault(),
                    "%04d-%02d-%02dT%02d:%02d:00", sel[0], sel[1] + 1, sel[2], sel[3], sel[4]);
            String display = formatShortDate(sel[2], sel[1] + 1, sel[0], sel[3], sel[4]);

            if (isStartDate) {
                selectedStartDate = iso;
                tvStartDate.setText(display);
                setActive(cardStartDate, tvStartDate, ivStartDate, R.color.project_blue);
            } else {
                selectedDueDate = iso;
                tvDueDate.setText(display);
                setActive(cardDueDate, tvDueDate, ivDueDate, R.color.project_blue);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showPriorityPicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_priority_picker, null);
        dialog.setContentView(view);

        view.findViewById(R.id.optHigh).setOnClickListener(v -> {
            setPriority(PRIORITY_HIGH); dialog.dismiss();
        });
        view.findViewById(R.id.optMedium).setOnClickListener(v -> {
            setPriority(PRIORITY_MEDIUM); dialog.dismiss();
        });
        view.findViewById(R.id.optLow).setOnClickListener(v -> {
            setPriority(PRIORITY_LOW); dialog.dismiss();
        });
        view.findViewById(R.id.optNone).setOnClickListener(v -> {
            selectedPriority = PRIORITY_MEDIUM;
            tvPriority.setText(getString(R.string.task_priority_label));
            setDefault(cardPriority, tvPriority, ivPriority);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setPriority(String priority) {
        selectedPriority = priority;
        tvPriority.setText(getPriorityLabel(priority));
        int colorRes = R.color.theme_text_primary;
        if (PRIORITY_HIGH.equals(priority)) colorRes = R.color.priority_high;
        else if (PRIORITY_MEDIUM.equals(priority)) colorRes = R.color.priority_medium;
        else if (PRIORITY_LOW.equals(priority)) colorRes = R.color.priority_low;
        setActive(cardPriority, tvPriority, ivPriority, colorRes);
    }

    private void showAssigneePicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_assignee_picker, null);
        dialog.setContentView(view);

        LinearLayout container = view.findViewById(R.id.containerMembers);

        for (ProjectMember member : projectMembers) {
            if (member.getUser() == null) continue;
            String name = member.getUser().getDisplayNameOrEmail();
            container.addView(createPickerItem(name, v -> {
                selectedAssigneeName = name;
                selectedAssigneeId = member.getUserId();
                tvAssignee.setText("@" + name);
                setActive(cardAssignee, tvAssignee, ivAssignee, R.color.text_purple_600);
                dialog.dismiss();
            }));
        }

        // Clear option
        container.addView(createPickerItem(getString(R.string.cancel), v -> {
            selectedAssigneeName = null;
            selectedAssigneeId = null;
            tvAssignee.setText(getString(R.string.task_assignee_label));
            setDefault(cardAssignee, tvAssignee, ivAssignee);
            dialog.dismiss();
        }, R.color.theme_text_hint));

        dialog.show();
    }

    private void showTagPicker() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.dialog_tag_picker, null);
        dialog.setContentView(view);

        String[] tags = {"Backend", "Frontend", "Design", "Bug"};
        LinearLayout container = view.findViewById(R.id.containerTags);

        for (String tag : tags) {
            container.addView(createPickerItem("#" + tag, v -> {
                selectedTag = tag;
                tvTag.setText("#" + tag);
                setActive(cardTag, tvTag, ivTag, R.color.text_orange_600);
                dialog.dismiss();
            }));
        }

        container.addView(createPickerItem(getString(R.string.cancel), v -> {
            selectedTag = null;
            tvTag.setText(getString(R.string.task_tag_label));
            setDefault(cardTag, tvTag, ivTag);
            dialog.dismiss();
        }, R.color.theme_text_hint));

        dialog.show();
    }

    // ── File Attachment ──────────────────────────────────────────────────

    private void initFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
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
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.task_attach_chooser)));
    }

    private void updateAttachmentUi() {
        containerAttachments.removeAllViews();
        if (attachedFileUris.isEmpty()) {
            containerAttachments.setVisibility(View.GONE);
            tvAttachment.setText(getString(R.string.task_attach_label));
            setDefault(cardAttachment, tvAttachment, ivAttachment);
            return;
        }

        containerAttachments.setVisibility(View.VISIBLE);
        int count = attachedFileUris.size();
        tvAttachment.setText(count + " file");
        setActive(cardAttachment, tvAttachment, ivAttachment, R.color.theme_text_primary);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < attachedFileUris.size(); i++) {
            Uri uri = attachedFileUris.get(i);
            int index = i;
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

    private void uploadNextAttachment(int index, long taskId, String baseMsg) {
        if (index >= attachedFileUris.size()) {
            String finalMsg = baseMsg;
            if (uploadSuccessCount > 0) {
                finalMsg += " (Kèm " + uploadSuccessCount + " file)";
            }
            Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
            closeActivity();
            return;
        }

        Uri uri = attachedFileUris.get(index);
        tvTaskId.setText("⏳ Đang tải file " + (index + 1) + "/" + attachedFileUris.size());

        String mimeType = getContentResolver().getType(uri);
        String fileName = getFileNameFromUri(uri);

        TaskRepository.getInstance().uploadTaskAttachment(
                taskId,
                uri,
                fileName,
                mimeType,
                getContentResolver(),
                new TaskRepository.TaskCallback<com.team7.taskflow.domain.model.Attachment>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Attachment result) {
                        uploadSuccessCount++;
                        runOnUiThread(() -> uploadNextAttachment(index + 1, taskId, baseMsg));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(AiCreateActivity.this, "Lỗi file " + (index + 1) + ": " + error, Toast.LENGTH_LONG).show();
                            uploadNextAttachment(index + 1, taskId, baseMsg);
                        });
                    }
                }
        );
    }

    // ── UI Helpers (Color management) ────────────────────────────────────

    /** Mark a button as "active" — text becomes primary (dark), subtle and clean */
    private void setActive(View container, TextView tv, ImageView icon, int tintColorRes) {
        int color = ContextCompat.getColor(this, tintColorRes);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 25)); // 10% opacity
            gd.setCornerRadius(dp(8));
            gd.setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 76)); // 30% alpha
            container.setBackground(gd);
        }
    }

    /** Reset a button to "default" — secondary gray */
    private void setDefault(View container, TextView tv, ImageView icon) {
        int color = ContextCompat.getColor(this, COLOR_DEFAULT);
        tv.setTextColor(color);
        if (icon != null) icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        if (container != null) container.setBackgroundResource(R.drawable.bg_chip_neutral);
    }

    private String getPriorityLabel(String priority) {
        switch (priority) {
            case PRIORITY_HIGH:   return getString(R.string.task_priority_high);
            case PRIORITY_LOW:    return getString(R.string.task_priority_low);
            default:              return getString(R.string.task_priority_medium);
        }
    }

    private void resetStatusBadge() {
        tvTaskId.setText("✨ Tạo task với AI");
        tvTaskId.setBackgroundResource(R.drawable.bg_task_id);
    }

    /** Create a clickable text row for BottomSheet pickers */
    private TextView createPickerItem(String label, View.OnClickListener listener) {
        return createPickerItem(label, listener, R.color.theme_text_primary);
    }

    private TextView createPickerItem(String label, View.OnClickListener listener, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        tv.setPadding(dp(20), dp(14), dp(20), dp(14));
        // Ripple effect
        int[] attrs = new int[] {android.R.attr.selectableItemBackground};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        tv.setBackground(ta.getDrawable(0));
        ta.recycle();
        tv.setOnClickListener(listener);
        return tv;
    }

    // ── Formatting ───────────────────────────────────────────────────────

    private String formatShortDate(int day, int month, int year, int hour, int minute) {
        int curYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (year == curYear) {
            return String.format(java.util.Locale.getDefault(), "%02d/%02d %02d:%02d", day, month, hour, minute);
        }
        return String.format(java.util.Locale.getDefault(), "%02d/%02d/%04d %02d:%02d", day, month, year, hour, minute);
    }

    /** Convert ISO date to short display */
    private String formatDateForDisplay(String isoDate) {
        try {
            if (isoDate.length() >= 16) {
                String[] parts = isoDate.split("T");
                String[] dateParts = parts[0].split("-");
                String[] timeParts = parts[1].substring(0, 5).split(":");
                return formatShortDate(
                        Integer.parseInt(dateParts[2]),
                        Integer.parseInt(dateParts[1]),
                        Integer.parseInt(dateParts[0]),
                        Integer.parseInt(timeParts[0]),
                        Integer.parseInt(timeParts[1])
                );
            }
        } catch (Exception ignored) {}
        return isoDate;
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        etPrompt.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(bottomSheet.getWindowToken(), 0);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            boolean isTouchInsideAnyEditText = false;
            for (EditText et : new EditText[]{etPrompt, etParsedTitle, etParsedDescription}) {
                if (et == null) continue;
                android.graphics.Rect outRect = new android.graphics.Rect();
                et.getGlobalVisibleRect(outRect);
                if (outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    isTouchInsideAnyEditText = true;
                    break;
                }
            }
            if (!isTouchInsideAnyEditText) {
                View v = getCurrentFocus();
                if (v != null) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ── Navigation ───────────────────────────────────────────────────────

    private void closeActivity() {
        bgOverlay.animate().alpha(0f).setDuration(250).start();
        bottomSheet.animate().translationY(1500f).setDuration(250).withEndAction(() -> {
            finish();
            overridePendingTransition(0, 0);
        }).start();
    }
}
