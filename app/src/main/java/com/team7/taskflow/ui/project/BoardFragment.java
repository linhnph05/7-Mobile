package com.team7.taskflow.ui.project;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;

import java.util.ArrayList;
import java.util.List;

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
                // BoardFragment currently shows counts only; no per-task menu.
            }
        };

        adapterTodo.setOnTaskClickListener(listener);
        adapterDoing.setOnTaskClickListener(listener);
        adapterDone.setOnTaskClickListener(listener);
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
