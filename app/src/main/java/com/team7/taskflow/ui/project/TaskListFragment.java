package com.team7.taskflow.ui.project;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskListFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_IS_MY_TASKS = "is_my_tasks";
    private static final String ARG_USER_ID = "user_id";
    private static final String STATUS_TRASH = "TRASH";

    private long projectId;
    private boolean isMyTasksMode;
    private String currentUserId;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvTasks;
    private LinearLayout layoutEmptyState;
    private TaskAdapter taskAdapter;
    private TaskRepository taskRepository;

    public static TaskListFragment newInstance(long projectId, boolean isMyTasksMode, String userId) {
        TaskListFragment fragment = new TaskListFragment();
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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_list, container, false);
        initViews(view);
        setupRecyclerView();
        loadTasks();
        return view;
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvTasks = view.findViewById(R.id.rvTasks);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyStateTaskList);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadTasks);
        }
    }

    public void openTrashFromHeader() {
        openTrashDialog();
    }

    private void setupRecyclerView() {
        if (rvTasks == null) return;

        taskAdapter = new TaskAdapter();
        taskAdapter.setInlineCommentsEnabled(true, currentUserId);
        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTasks.setAdapter(taskAdapter);

        taskAdapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                if (getContext() == null || task.getId() == null) return;
                Intent intent = new Intent(getContext(), com.team7.taskflow.ui.project.TaskDetailActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                startActivity(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                moveTaskToTrash(task);
            }
        });
    }

    private void moveTaskToTrash(Task task) {
        if (getContext() == null || task == null || task.getId() == null) return;
        taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Đã chuyển task vào thùng rác", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
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
                    if (rows.isEmpty()) rows.add("Chưa có lịch sử thay đổi");

                    View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_bottom_sheet_history, null);
                    BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.Theme_TaskFlow_BottomSheet);
                    sheet.setContentView(sheetView);

                    ListView listHistory = sheetView.findViewById(R.id.listHistory);
                    android.widget.TextView btnClose = sheetView.findViewById(R.id.btnCloseHistory);

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
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String formatHistoryRow(TaskActivity activity) {
        String action = activity.getActionType() != null ? activity.getActionType() : "UPDATE";
        String oldVal = formatDateTimeValue(activity.getOldValue(), action);
        String newVal = formatDateTimeValue(activity.getNewValue(), action);
        return formatTimestamp(activity.getCreatedAt()) + " - " + action + " (" + oldVal + " -> " + newVal + ")";
    }

    private String formatTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "Vừa xong";
        try {
            Date date = java.util.Date.from(java.time.OffsetDateTime.parse(raw).toInstant());
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
        } catch (Exception e) {
            // Fallback to substring method if timezone parsing fails
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(raw);
        }
    }

    private String formatDateTimeValue(String value, String actionType) {
        if (value == null || value.isEmpty()) return "";
        
        // Format if it looks like a date or datetime
        boolean isDateTimeAction = actionType != null && (actionType.contains("DATE") || actionType.contains("TIME"));
        if (isDateTimeAction) {
            return com.team7.taskflow.util.DateTimeFormatterUtil.formatDateDisplay(value.trim());
        }
        return value;
    }

    private void openTrashDialog() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> showTrashItemsDialog(result != null ? result : new ArrayList<>()));
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectNameByStatus(currentUserId, "TRASH", callback);
        } else {
            taskRepository.getTasksByProjectAndStatus(projectId, "TRASH", callback);
        }
    }

    private void showTrashItemsDialog(List<Task> trashedTasks) {
        if (!isAdded()) return;
        if (trashedTasks.isEmpty()) {
            Toast.makeText(getContext(), "Thùng rác đang trống", Toast.LENGTH_SHORT).show();
            return;
        }

        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_bottom_sheet_trash, null);
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.Theme_TaskFlow_BottomSheet);
        sheet.setContentView(sheetView);

        ListView listTrash = sheetView.findViewById(R.id.listTrash);
        android.widget.TextView btnEmpty = sheetView.findViewById(R.id.btnEmptyTrash);
        android.widget.TextView btnClose = sheetView.findViewById(R.id.btnCloseTrash);

        if (listTrash != null) {
            listTrash.setAdapter(new TrashTaskAdapter(trashedTasks));
            listTrash.setOnItemClickListener((parent, view, position, id) -> {
                sheet.dismiss();
                showTrashTaskActionDialog(trashedTasks.get(position));
            });
        }

        if (btnEmpty != null) {
            btnEmpty.setOnClickListener(v -> {
                sheet.dismiss();
                confirmEmptyTrash(trashedTasks);
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
    }

    private class TrashTaskAdapter extends BaseAdapter {
        private final List<Task> items;

        TrashTaskAdapter(List<Task> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items != null ? items.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            Task task = items.get(position);
            return task.getId() != null ? task.getId() : position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_bottom_sheet_trash_task, parent, false);
            }

            Task task = items.get(position);
            TextView tvTitle = view.findViewById(R.id.tvTrashTaskTitle);
            TextView tvMeta = view.findViewById(R.id.tvTrashTaskMeta);
            TextView tvDescription = view.findViewById(R.id.tvTrashTaskDescription);

            String title = task.getTitle() != null && !task.getTitle().trim().isEmpty()
                    ? task.getTitle() : "(Không tên)";
            String status = task.getStatus() != null ? task.getStatus().toUpperCase() : "TRASH";
            String due = formatDueDate(task.getDueDate());
            String project = task.getProjectName() != null && !task.getProjectName().isEmpty()
                    ? task.getProjectName() : "No project";
            String description = task.getDescription() != null && !task.getDescription().trim().isEmpty()
                    ? task.getDescription() : "Không có mô tả";

            if (tvTitle != null) tvTitle.setText(title);
            if (tvMeta != null) tvMeta.setText(status + " • " + due + " • " + project);
            if (tvDescription != null) tvDescription.setText(description);

            return view;
        }

        private String formatDueDate(String raw) {
            if (raw == null || raw.isEmpty()) return "No due date";
            try {
                String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = parser.parse(datePart);
                return date != null ? formatter.format(date) : datePart;
            } catch (Exception e) {
                return raw;
            }
        }
    }

    private void confirmEmptyTrash(List<Task> trashedTasks) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
            .setTitle("Dọn sạch thùng rác")
            .setMessage("Bạn có chắc muốn xóa vĩnh viễn toàn bộ task trong thùng rác không?")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa hết", (dialog, which) -> emptyTrash(trashedTasks))
            .show();
    }

    private void showTrashTaskActionDialog(Task task) {
        if (task.getId() == null || !isAdded()) return;
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_bottom_sheet_trash_actions, null);
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.Theme_TaskFlow_BottomSheet);
        sheet.setContentView(sheetView);

        android.widget.TextView tvTitle = sheetView.findViewById(R.id.tvTrashTaskTitle);
        android.widget.TextView btnDelete = sheetView.findViewById(R.id.btnDeleteForever);
        android.widget.TextView btnClose = sheetView.findViewById(R.id.btnCloseTrashActions);

        if (tvTitle != null) {
            tvTitle.setText(task.getTitle() != null ? task.getTitle() : "Task");
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                sheet.dismiss();
                taskRepository.permanentlyDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Đã xóa vĩnh viễn task", Toast.LENGTH_SHORT).show();
                            loadTasks();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
    }

    private void emptyTrash(List<Task> trashedTasks) {
        if (trashedTasks.isEmpty()) return;
        final int[] remaining = {trashedTasks.size()};
        final int[] failed = {0};

        for (Task task : trashedTasks) {
            if (task.getId() == null) {
                remaining[0]--;
                continue;
            }
            taskRepository.permanentlyDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    onDone();
                }

                @Override
                public void onError(String error) {
                    failed[0]++;
                    onDone();
                }

                private void onDone() {
                    remaining[0]--;
                    if (remaining[0] == 0 && isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (failed[0] == 0) {
                                Toast.makeText(getContext(), "Đã dọn sạch thùng rác", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Dọn thùng rác xong, có " + failed[0] + " task lỗi", Toast.LENGTH_SHORT).show();
                            }
                            loadTasks();
                        });
                    }
                }
            });
        }
    }

    private void loadTasks() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (taskAdapter != null) {
                        taskAdapter.setSubtaskProgressSource(result != null ? result : new ArrayList<>());
                        List<Task> visibleTasks = new ArrayList<>();
                        if (result != null) {
                            for (Task task : result) {
                                if (!isTrashTask(task)) {
                                    visibleTasks.add(task);
                                }
                            }
                        }
                        visibleTasks.sort((left, right) -> Long.compare(
                                parseTaskCreatedTime(right),
                                parseTaskCreatedTime(left)));
                        taskAdapter.setTasks(visibleTasks);
                        updateEmptyState(visibleTasks.isEmpty());
                    }
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }

    private long parseTaskCreatedTime(Task task) {
        if (task == null || task.getCreatedAt() == null || task.getCreatedAt().trim().isEmpty()) {
            return 0L;
        }
        try {
            return java.time.OffsetDateTime.parse(task.getCreatedAt()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (rvTasks != null) {
            rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isTrashTask(Task task) {
        if (task == null) {
            return false;
        }
        String status = task.getStatus() != null
                ? task.getStatus().trim().toUpperCase(Locale.US)
                : "";
        return STATUS_TRASH.equals(status);
    }
}
