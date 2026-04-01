package com.team7.taskflow.ui.project;

import android.content.ClipData;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/**
 * Fragment hiển thị bảng Kanban (Board).
 * Được tách ra từ ProjectBoardActivity cũ.
 */
public class BoardFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_IS_MY_TASKS = "is_my_tasks";
    private static final String ARG_USER_ID = "user_id";
    private long projectId;
    private TaskRepository taskRepository;
    private boolean isMyTasksMode;
    private String currentUserId;

    private TextView tvCountTodo, tvCountDoing, tvCountDone;
    private RecyclerView rvTodo, rvDoing, rvDone;
    private TaskAdapter adapterTodo, adapterDoing, adapterDone;

    public static BoardFragment newInstance(long projectId, boolean isMyTasksMode, String userId) {
        BoardFragment fragment = new BoardFragment();
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
            projectId = getArguments().getLong(ARG_PROJECT_ID);
            isMyTasksMode = getArguments().getBoolean(ARG_IS_MY_TASKS, false);
            currentUserId = getArguments().getString(ARG_USER_ID);
        }
        taskRepository = TaskRepository.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.view_board_content, container, false);
        
        initViews(view);
        setupBoards();
        loadTaskCounts();
        
        return view;
    }

    private void initViews(View view) {
        tvCountTodo = view.findViewById(R.id.tvCountTodo);
        tvCountDoing = view.findViewById(R.id.tvCountDoing);
        tvCountDone = view.findViewById(R.id.tvCountDone);
        rvTodo = view.findViewById(R.id.rvTodo);
        rvDoing = view.findViewById(R.id.rvDoing);
        rvDone = view.findViewById(R.id.rvDone);
    }

    private void setupBoards() {
        adapterTodo = new TaskAdapter();
        adapterDoing = new TaskAdapter();
        adapterDone = new TaskAdapter();

        adapterTodo.setInlineCommentsEnabled(true, currentUserId);
        adapterDoing.setInlineCommentsEnabled(true, currentUserId);
        adapterDone.setInlineCommentsEnabled(true, currentUserId);

        rvTodo.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTodo.setAdapter(adapterTodo);

        rvDoing.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDoing.setAdapter(adapterDoing);

        rvDone.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDone.setAdapter(adapterDone);

        TaskAdapter.OnTaskClickListener listener = new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                if (getContext() == null) return;
                android.content.Intent intent = new android.content.Intent(getContext(), CreateTaskActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                startActivity(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                showTaskMenu(task, view);
            }
        };

        adapterTodo.setOnTaskClickListener(listener);
        adapterDoing.setOnTaskClickListener(listener);
        adapterDone.setOnTaskClickListener(listener);

        TaskAdapter.OnTaskLongPressListener longPressListener = (task, itemView) -> startLaneDrag(task, itemView);
        adapterTodo.setOnTaskLongPressListener(longPressListener);
        adapterDoing.setOnTaskLongPressListener(longPressListener);
        adapterDone.setOnTaskLongPressListener(longPressListener);

        setupLaneDragTargets();
    }

    private void startLaneDrag(Task task, View itemView) {
        if (task == null || task.getId() == null || itemView == null) return;
        ClipData clipData = ClipData.newPlainText("task_id", String.valueOf(task.getId()));
        itemView.startDragAndDrop(clipData, new View.DragShadowBuilder(itemView), task, 0);
    }

    private void setupLaneDragTargets() {
        if (rvTodo != null) {
            rvTodo.setOnDragListener((v, event) -> handleLaneDrag(event, "TODO", rvTodo));
        }
        if (rvDoing != null) {
            rvDoing.setOnDragListener((v, event) -> handleLaneDrag(event, "DOING", rvDoing));
        }
        if (rvDone != null) {
            rvDone.setOnDragListener((v, event) -> handleLaneDrag(event, "DONE", rvDone));
        }
    }

    private boolean handleLaneDrag(android.view.DragEvent event, String targetStatus, RecyclerView targetView) {
        switch (event.getAction()) {
            case android.view.DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof Task;
            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                targetView.setAlpha(0.85f);
                return true;
            case android.view.DragEvent.ACTION_DRAG_EXITED:
                targetView.setAlpha(1f);
                return true;
            case android.view.DragEvent.ACTION_DROP:
                targetView.setAlpha(1f);
                if (!(event.getLocalState() instanceof Task)) return false;
                Task draggedTask = (Task) event.getLocalState();
                String currentStatus = draggedTask.getStatus() != null ? draggedTask.getStatus().toUpperCase() : "TODO";
                if (targetStatus.equals(currentStatus)) return true;
                taskRepository.updateTaskStatus(draggedTask.getId(), currentStatus, targetStatus,
                        new TaskRepository.TaskCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Đã chuyển task sang " + targetStatus, Toast.LENGTH_SHORT).show();
                                    loadTaskCounts();
                                });
                            }

                            @Override
                            public void onError(String error) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
                            }
                        });
                return true;
            case android.view.DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void showTaskMenu(Task task, View anchor) {
        if (getContext() == null || task == null || task.getId() == null) return;
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_bottom_sheet_task_actions, null);
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), R.style.Theme_TaskFlow_BottomSheet);
        sheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        TextView btnHistory = sheetView.findViewById(R.id.btnActionHistory);
        TextView btnTrash = sheetView.findViewById(R.id.btnActionTrash);
        TextView btnCancel = sheetView.findViewById(R.id.btnActionCancel);

        if (tvTitle != null) {
            tvTitle.setText(task.getTitle() != null ? task.getTitle() : "Task Actions");
        }

        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                sheet.dismiss();
                showTaskHistory(task.getId());
            });
        }

        if (btnTrash != null) {
            btnTrash.setOnClickListener(v -> {
                sheet.dismiss();
                taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Đã chuyển task vào thùng rác", Toast.LENGTH_SHORT).show();
                            loadTaskCounts();
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

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
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
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
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
            Date date = java.util.Date.from(java.time.OffsetDateTime.parse(raw).toInstant());
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
        } catch (Exception e) {
            return raw;
        }
    }

    private void loadTaskCounts() {
        if (!isMyTasksMode && projectId == -1) return;

        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> todoList = new ArrayList<>();
                List<Task> doingList = new ArrayList<>();
                List<Task> doneList = new ArrayList<>();

                for (Task t : result) {
                    if (t.getStatus() == null) continue;
                    String status = t.getStatus().toUpperCase();
                    switch (status) {
                        case "TODO": todoList.add(t); break;
                        case "DOING": doingList.add(t); break;
                        case "DONE": doneList.add(t); break;
                    }
                }

                if (isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (tvCountTodo != null) tvCountTodo.setText(String.valueOf(todoList.size()));
                        if (tvCountDoing != null) tvCountDoing.setText(String.valueOf(doingList.size()));
                        if (tvCountDone != null) tvCountDone.setText(String.valueOf(doneList.size()));

                        adapterTodo.setTasks(todoList);
                        adapterDoing.setTasks(doingList);
                        adapterDone.setTasks(doneList);
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e("BoardFragment", "Load failed: " + error);
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }
}
