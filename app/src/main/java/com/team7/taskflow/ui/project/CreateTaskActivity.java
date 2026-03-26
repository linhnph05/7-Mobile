package com.team7.taskflow.ui.project;

import android.app.DatePickerDialog;
import android.content.Intent; // Thêm import này
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateTaskActivity extends BaseActivity {

    private EditText etTitle, etDescription;
    private Spinner spinnerPriority, spinnerStatus;
    private TextView tvStartDate, tvDueDate, btnSave, tvToolbarTitle;
    private ProgressBar progressBar;
    private TaskRepository taskRepository;
    private long projectId;

    // SỬA TẠI ĐÂY: Để null mặc định để phân biệt Create/Update
    private Long taskId = null;

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
        setupSpinners();
        setupDatePickers();

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
        spinnerPriority = findViewById(R.id.spinnerPriority);
        spinnerStatus = findViewById(R.id.spinnerStatus);
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
                        runOnUiThread(() -> {
                            etTitle.setText(t.getTitle());
                            etDescription.setText(t.getDescription());
                            setSpinnerValue(spinnerPriority, t.getPriority());
                            setSpinnerValue(spinnerStatus, t.getStatus());
                            if (t.getStartDate() != null) tvStartDate.setText(t.getStartDate());
                            if (t.getDueDate() != null) tvDueDate.setText(t.getDueDate());
                            setLoading(false);
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

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            return;
        }

        setLoading(true);
        Task task = new Task(projectId, title);

        // Gán các thông tin khác
        task.setDescription(etDescription.getText().toString().trim());
        task.setPriority(spinnerPriority.getSelectedItem().toString());
        task.setStatus(spinnerStatus.getSelectedItem().toString());
        task.setStartDate(tvStartDate.getText().toString().contains("-") ? tvStartDate.getText().toString() : null);
        task.setDueDate(tvDueDate.getText().toString().contains("-") ? tvDueDate.getText().toString() : null);
        task.setAssigneeId(SessionManager.getUserId());

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
                    setLoading(false);
                    Toast.makeText(CreateTaskActivity.this, taskId == null ? "Task Created" : "Task Updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
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

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null) btnSave.setEnabled(!loading);
    }

    // Các hàm setupSpinners, setupDatePickers, setSpinnerValue giữ nguyên như cũ của bạn
    private void setSpinnerValue(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        if (value != null) {
            int pos = adapter.getPosition(value.toUpperCase());
            if (pos >= 0) spinner.setSelection(pos);
        }
    }

    private void setupSpinners() {
        String[] priorities = {"LOW", "MEDIUM", "HIGH"};
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, priorities);
        spinnerPriority.setAdapter(priorityAdapter);

        String[] statuses = {"TODO", "DOING", "DONE"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses);
        spinnerStatus.setAdapter(statusAdapter);
    }

    private void setupDatePickers() {
        tvStartDate.setOnClickListener(v -> showDatePicker(startCalendar, tvStartDate));
        tvDueDate.setOnClickListener(v -> showDatePicker(dueCalendar, tvDueDate));
    }

    private void showDatePicker(Calendar cal, TextView tv) {
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            tv.setText(dateFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}