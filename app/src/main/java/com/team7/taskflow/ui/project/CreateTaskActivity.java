package com.team7.taskflow.ui.project;

import android.app.DatePickerDialog;
import android.content.Intent; // Thêm import này
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
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
    private TextView tvPriority, tvStatus, tvAssignee;
    private ImageView ivPriority, ivStatus, ivAssignee;
    private View cardPriority, cardStatus, cardAssignee, cardAttachment;
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

    private TextView tvStartDate, tvDueDate, btnSave, tvToolbarTitle;
    private ProgressBar progressBar;
    private TaskRepository taskRepository;
    private List<User> projectMembers = new ArrayList<>();
    private ArrayAdapter<String> assigneeAdapter;
    private long projectId;

    // SỬA TẠI ĐÂY: Để null mặc định để phân biệt Create/Update
    private Long taskId = null;
    private String currentAssigneeId = null;
    private String selectedTag = null; // Thêm hỗ trợ tag nếu cần

    private static final int COLOR_DEFAULT = R.color.slate_500;

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar dueCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        taskRepository = TaskRepository.getInstance();

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
        } else {
            tvToolbarTitle.setText("Create Task");
            btnSave.setText("Create");
        }

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
        tvAttachment = findViewById(R.id.tvAttachment);
        ivAttachment = findViewById(R.id.ivAttachment);
        containerAttachments = findViewById(R.id.containerAttachments);
        tvStartDate = findViewById(R.id.tvStartDate);
        tvDueDate = findViewById(R.id.tvDueDate);
        btnSave = findViewById(R.id.btnSave);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        progressBar = findViewById(R.id.progressBar);
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
                            if (t.getStartDate() != null) tvStartDate.setText(t.getStartDate());
                            if (t.getDueDate() != null) tvDueDate.setText(t.getDueDate());
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
        task.setStartDate(tvStartDate.getText().toString().contains("-") ? tvStartDate.getText().toString() : null);
        task.setDueDate(tvDueDate.getText().toString().contains("-") ? tvDueDate.getText().toString() : null);

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
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padY = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(0, padY, 0, padY);

        String[] statuses = {"TODO", "DOING", "DONE"};
        for (String status : statuses) {
            container.addView(createPickerItem(status, v -> {
                setStatus(status);
                dialog.dismiss();
            }, R.color.slate_900));
        }
        dialog.setContentView(container);
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
        
        int[] attrs = {android.R.attr.selectableItemBackground};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        tv.setBackground(ta.getDrawable(0));
        ta.recycle();
        
        tv.setOnClickListener(listener);
        return tv;
    }

    private void setupDatePickers() {
        // Click vào card chứa (area lớn hơn) để mở DatePicker
        View cardStart = findViewById(R.id.cardStartDate);
        View cardDue = findViewById(R.id.cardDueDate);
        if (cardStart != null) cardStart.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (cardDue != null) cardDue.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
        // Fallback: click trực tiếp vào TextView cũng hoạt động
        if (tvStartDate != null) tvStartDate.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        if (tvDueDate != null) tvDueDate.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
    }

    private void showDatePicker(Calendar cal, TextView tv) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            tv.setText(dateFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
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