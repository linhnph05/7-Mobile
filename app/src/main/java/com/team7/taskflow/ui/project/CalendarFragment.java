package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.gridlayout.widget.GridLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_IS_MY_TASKS = "is_my_tasks";
    private static final String ARG_USER_ID = "user_id";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String selectedDateStr;
    private final List<Task> allTasks = new ArrayList<>();
    private Calendar currentCalendar = Calendar.getInstance();

    private long projectId;
    private boolean isMyTasksMode;
    private String currentUserId;

    private GridLayout glCalendar;
    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;

    private final ActivityResultLauncher<Intent> taskLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    loadTasks();
                }
            });

    public static CalendarFragment newInstance(long projectId, boolean isMyTasksMode, String userId) {
        CalendarFragment fragment = new CalendarFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PROJECT_ID, projectId);
        args.putBoolean(ARG_IS_MY_TASKS, isMyTasksMode);
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getLong(ARG_PROJECT_ID, -1);
            isMyTasksMode = getArguments().getBoolean(ARG_IS_MY_TASKS, false);
            currentUserId = getArguments().getString(ARG_USER_ID);
        }
        taskRepository = TaskRepository.getInstance();
        selectedDateStr = dateFormat.format(Calendar.getInstance().getTime());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.view_calendar_content, container, false);
        glCalendar = view.findViewById(R.id.glCalendar);
        rvTasks = view.findViewById(R.id.rvTasks);

        TextView tvMonthYear = view.findViewById(R.id.tvMonthYear);
        View btnToday = view.findViewById(R.id.btnToday);
        View btnPrev = view.findViewById(R.id.btnPrevMonth);
        View btnNext = view.findViewById(R.id.btnNextMonth);

        setupRecyclerView();
        updateMonthLabel(tvMonthYear);
        renderCalendar();
        loadTasks();

        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                currentCalendar = Calendar.getInstance();
                selectedDateStr = dateFormat.format(currentCalendar.getTime());
                updateMonthLabel(tvMonthYear);
                renderCalendar();
                filterTasksBySelectedDate();
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                currentCalendar.add(Calendar.MONTH, -1);
                updateMonthLabel(tvMonthYear);
                renderCalendar();
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                currentCalendar.add(Calendar.MONTH, 1);
                updateMonthLabel(tvMonthYear);
                renderCalendar();
            });
        }

        return view;
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter();
        adapter.setInlineCommentsEnabled(true, currentUserId);
        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTasks.setAdapter(adapter);

        adapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                Intent intent = new Intent(requireContext(), CreateTaskActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                taskLauncher.launch(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                moveTaskToTrash(task);
            }
        });
    }

    private void moveTaskToTrash(Task task) {
        taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Đã chuyển task vào thùng rác", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showTaskHistory(long taskId) {
        taskRepository.getTaskHistory(taskId, new TaskRepository.TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    List<String> rows = new ArrayList<>();
                    if (result != null) {
                        for (TaskActivity activity : result) {
                            rows.add(formatHistoryRow(activity));
                        }
                    }
                    if (rows.isEmpty()) {
                        rows.add("Chưa có lịch sử thay đổi");
                    }

                    View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_bottom_sheet_history, null);
                    BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.Theme_TaskFlow_BottomSheet);
                    sheet.setContentView(sheetView);

                    ListView listHistory = sheetView.findViewById(R.id.listHistory);
                    TextView btnClose = sheetView.findViewById(R.id.btnCloseHistory);

                    if (listHistory != null) {
                        listHistory.setAdapter(new HistoryEventAdapter(requireContext(), rows));
                    }
                    if (btnClose != null) {
                        btnClose.setOnClickListener(v -> sheet.dismiss());
                    }

                    sheet.show();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String formatHistoryRow(TaskActivity activity) {
        String action = activity.getActionType() != null ? activity.getActionType() : "UPDATE";
        String oldVal = activity.getOldValue() != null ? activity.getOldValue() : "";
        String newVal = activity.getNewValue() != null ? activity.getNewValue() : "";
        return formatTimestamp(activity.getCreatedAt()) + " - " + action + " (" + oldVal + " -> " + newVal + ")";
    }

    private String formatTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "Vừa xong";
        try {
            java.time.Instant instant = java.time.OffsetDateTime.parse(raw).toInstant();
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date.from(instant));
        } catch (Exception e) {
            return raw;
        }
    }

    private void loadTasks() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                allTasks.clear();
                if (result != null) {
                    allTasks.addAll(result);
                }
                if (!isAdded()) return;
                requireActivity().runOnUiThread(CalendarFragment.this::filterTasksBySelectedDate);
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show());
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }

    private void filterTasksBySelectedDate() {
        if (selectedDateStr == null) return;

        List<Task> filteredList = new ArrayList<>();
        for (Task t : allTasks) {
            String start = normalizeDate(t.getStartDate());
            String due = normalizeDate(t.getDueDate());

            if (start != null && due != null) {
                if (selectedDateStr.compareTo(start) >= 0 && selectedDateStr.compareTo(due) <= 0) {
                    filteredList.add(t);
                }
            } else if (due != null && selectedDateStr.equals(due)) {
                filteredList.add(t);
            } else if (start != null && selectedDateStr.equals(start)) {
                filteredList.add(t);
            }
        }

        adapter.setTasks(filteredList);
    }

    private String normalizeDate(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        return raw.length() >= 10 ? raw.substring(0, 10) : raw;
    }

    private void updateMonthLabel(TextView tvMonthYear) {
        if (tvMonthYear == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()));
    }

    private void renderCalendar() {
        glCalendar.removeAllViews();
        addWeekdayHeaders();

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int emptySlots = cal.get(Calendar.DAY_OF_WEEK) - 1;

        for (int i = 0; i < emptySlots; i++) {
            TextView space = new TextView(requireContext());
            space.setText(" ");
            space.setMinHeight(dp(36));
            space.setGravity(android.view.Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glCalendar.addView(space, params);
        }

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            TextView tv = new TextView(requireContext());
            tv.setText(String.valueOf(day));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setMinHeight(dp(36));
            tv.setPadding(0, dp(6), 0, dp(6));

            Calendar cellCal = (Calendar) cal.clone();
            cellCal.set(Calendar.DAY_OF_MONTH, day);
            String cellDateStr = dateFormat.format(cellCal.getTime());

            if (cellDateStr.equals(selectedDateStr)) {
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setBackgroundResource(R.drawable.bg_tag);
                tv.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.primary));
            } else {
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.slate_700));
                tv.setBackground(null);
            }

            tv.setOnClickListener(v -> {
                selectedDateStr = cellDateStr;
                renderCalendar();
                filterTasksBySelectedDate();
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glCalendar.addView(tv, params);
        }
    }

    private void addWeekdayHeaders() {
        String[] days = {"S", "M", "T", "W", "T", "F", "S"};
        for (String day : days) {
            TextView tv = new TextView(requireContext());
            tv.setText(day);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(11f);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.slate_400));
            tv.setMinHeight(dp(24));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glCalendar.addView(tv, params);
        }
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }
}
