package com.team7.taskflow.ui.notification;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.InvitationRepository;
import com.team7.taskflow.data.repository.NotificationRepository;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;
import com.team7.taskflow.domain.model.Task;
import android.util.Log;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.ui.project.TaskDetailActivity;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends BaseActivity {

    private static final String PREFS_NOTIFICATION_SEEN = "notification_prefs";
    private static final String KEY_LAST_SEEN_NOTIFICATION_MS = "last_seen_notification_ms";

    private ImageButton btnBack, btnMarkAllRead, btnNotificationSettings;
    private AutoCompleteTextView actvFilterType;
    private Chip chipUnread;
    private RecyclerView rvNotifications;
    private View layoutEmptyState;
    private MaterialButton btnClearFilters;

    private NotificationAdapter adapter;
    private List<Notification> allNotifications = new ArrayList<>();
    private final NotificationRepository notificationRepo = NotificationRepository.getInstance();
    private final InvitationRepository invitationRepo = new InvitationRepository();
    private final TaskRepository taskRepository = TaskRepository.getInstance();

    private static final String[] FILTER_OPTIONS = {
            "All types", "@me", "Comment", "Join request"
    };
    private String currentFilter = "All types";
    private boolean showUnreadOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager.init(this);
        setContentView(R.layout.activity_notifications);
        markNotificationsAsSeen();
        initViews();
        setupFilterDropdown();
        setupClickListeners();
        loadNotifications();
    }

    private void markNotificationsAsSeen() {
        getSharedPreferences(PREFS_NOTIFICATION_SEEN, MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_NOTIFICATION_MS, System.currentTimeMillis())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markNotificationsAsSeen();
    }

    private void initViews() {
        btnBack                 = findViewById(R.id.btnBack);
        btnMarkAllRead          = findViewById(R.id.btnMarkAllRead);
        btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        actvFilterType          = findViewById(R.id.actvFilterType);
        chipUnread              = findViewById(R.id.chipUnread);
        rvNotifications         = findViewById(R.id.rvNotifications);
        layoutEmptyState        = findViewById(R.id.layoutEmptyState);
        btnClearFilters         = findViewById(R.id.btnClearFilters);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        setupAdapter();
    }

    private void setupAdapter() {
        adapter = new NotificationAdapter(new NotificationAdapter.OnNotificationActionListener() {

            @Override
            public void onNotificationClick(Notification notification) {
                if (notification != null && notification.getType() == NotificationType.PROJECT_INVITE) {
                    return;
                }

                if (!notification.isRead()) {
                    notification.setRead(true);
                    adapter.notifyDataSetChanged();
                    notificationRepo.markAsRead(notification.getNotificationId(),
                            new NotificationRepository.NotificationCallback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(String e) {
                                    notification.setRead(false);
                                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                                }
                            });
                }
                navigateFromNotification(notification);
            }

            @Override
            public void onAcceptInvite(Notification notification) {
                // ✅ referenceId = project_id (bigint) theo trigger
                Long projectId = notification.getReferenceId();
                if (projectId == null) {
                    Toast.makeText(NotificationsActivity.this,
                            "Không tìm thấy thông tin lời mời", Toast.LENGTH_SHORT).show();
                    return;
                }

                String userId = SessionManager.getUserId();
                String userEmail = SessionManager.getUserEmail(); // ✅ Cần email để tìm invitation
                if (userId == null || userId.trim().isEmpty() || userEmail == null || userEmail.trim().isEmpty()) {
                    Toast.makeText(NotificationsActivity.this,
                            "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
                    return;
                }

                invitationRepo.acceptInvitation(projectId, userId, userEmail,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                markInviteAsRead(notification);
                                runOnUiThread(() ->
                                        Toast.makeText(NotificationsActivity.this,
                                                "Đã tham gia dự án: " + notification.getReferenceName(),
                                                Toast.LENGTH_SHORT).show());
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> showAcceptInviteErrorDialog(message, projectId, userId, userEmail));
                            }
                        });
            }

            @Override
            public void onDeclineInvite(Notification notification) {
                Long projectId = notification.getReferenceId();
                if (projectId == null) {
                    Toast.makeText(NotificationsActivity.this,
                            "Không tìm thấy thông tin lời mời", Toast.LENGTH_SHORT).show();
                    return;
                }

                String userEmail = SessionManager.getUserEmail(); // ✅ Cần email
                if (userEmail == null) {
                    Toast.makeText(NotificationsActivity.this,
                            "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
                    return;
                }

                invitationRepo.declineInvitation(projectId, userEmail,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                markInviteAsRead(notification);
                                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                        "Đã từ chối lời mời", Toast.LENGTH_SHORT).show());
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                        "Lỗi từ chối: " + message, Toast.LENGTH_SHORT).show());
                            }
                        });
            }
        });
        rvNotifications.setAdapter(adapter);
    }

    private void markInviteAsRead(Notification notification) {
        if (notification == null || notification.isRead()) {
            return;
        }

        notification.setRead(true);
        runOnUiThread(this::applyFilters);

        notificationRepo.markAsRead(notification.getNotificationId(),
                new NotificationRepository.NotificationCallback<Void>() {
                    @Override public void onSuccess(Void result) {}

                    @Override
                    public void onError(String error) {
                        Log.e("Notifications", "Mark invite as read failed: " + error);
                        notification.setRead(false);
                        runOnUiThread(NotificationsActivity.this::applyFilters);
                    }
                });
    }

    private void setupFilterDropdown() {
        ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, FILTER_OPTIONS);
        actvFilterType.setAdapter(dropdownAdapter);
        actvFilterType.setText(FILTER_OPTIONS[0], false);
        actvFilterType.setOnItemClickListener((parent, view, position, id) -> {
            currentFilter = FILTER_OPTIONS[position];
            applyFilters();
        });
    }

    private void showAcceptInviteErrorDialog(String message, Long projectId, String userId, String userEmail) {
        String safeMessage = (message == null || message.trim().isEmpty()) ? "Unknown error" : message.trim();
        String details = "Accept invite failed"
                + "\nproject_id: " + (projectId != null ? projectId : "null")
                + "\nuser_id: " + (userId != null ? userId : "null")
                + "\nemail: " + (userEmail != null ? userEmail : "null")
                + "\n\nerror:\n" + safeMessage;

        new AlertDialog.Builder(this)
                .setTitle("Lỗi tham gia dự án")
                .setMessage(details)
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Copy", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("accept_invite_error", details));
                        Toast.makeText(this, "Đã copy lỗi", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnMarkAllRead.setOnClickListener(v -> markAllAsRead());
        btnNotificationSettings.setOnClickListener(v -> openNotificationSettings());
        chipUnread.setOnCheckedChangeListener((btn, isChecked) -> {
            showUnreadOnly = isChecked;
            applyFilters();
        });
        btnClearFilters.setOnClickListener(v -> clearFilters());
    }

    private void loadNotifications() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) { showEmptyState(true); return; }

        notificationRepo.getNotifications(userId,
                new NotificationRepository.NotificationCallback<List<Notification>>() {
                    @Override
                    public void onSuccess(List<Notification> result) {
                        runOnUiThread(() -> {
                            allNotifications = result;
                            applyFilters();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(NotificationsActivity.this, error, Toast.LENGTH_SHORT).show();
                            showEmptyState(true);
                        });
                    }
                });
    }

    private void applyFilters() {
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : allNotifications) {
            boolean matchesType = true;
            if ("@me".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.TASK_ASSIGNED
                        || n.getType() == NotificationType.MENTION
                        || n.getType() == NotificationType.REACTION
                        || n.getType() == NotificationType.DELETED
                        || n.getType() == NotificationType.TASK_STATUS_CHANGED
                        || n.getType() == NotificationType.ATTACHMENT_ADDED);
            } else if ("Comment".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.COMMENT);
            } else if ("Join request".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.PROJECT_INVITE);
            }
            boolean matchesRead = !showUnreadOnly || !n.isRead();
            if (matchesType && matchesRead) filtered.add(n);
        }
        adapter.setNotifications(filtered);
        showEmptyState(filtered.isEmpty());
    }

    private void markAllAsRead() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) return;
        for (Notification n : allNotifications) {
            if (n.getType() != NotificationType.PROJECT_INVITE) {
                n.setRead(true);
            }
        }
        applyFilters();
        notificationRepo.markAllAsRead(userId,
                new NotificationRepository.NotificationCallback<Void>() {
                    @Override public void onSuccess(Void r) {
                        runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                "All notifications marked as read", Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onError(String e) {
                        runOnUiThread(() -> loadNotifications());
                    }
                });
    }

    private void openNotificationSettings() {}

    private void clearFilters() {
        currentFilter = "All types";
        showUnreadOnly = false;
        actvFilterType.setText(FILTER_OPTIONS[0], false);
        chipUnread.setChecked(false);
        applyFilters();
    }

    private void showEmptyState(boolean show) {
        layoutEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void navigateFromNotification(Notification notification) {
        if (notification == null || notification.getReferenceId() == null) {
            return;
        }

        long referenceId = notification.getReferenceId();
        NotificationType type = notification.getType();

        if (type == NotificationType.PROJECT_INVITE) {
            openProjectDetail(referenceId, notification.getReferenceName());
            return;
        }

        if (type == NotificationType.DELETED) {
            showDeletedContentMessage();
            return;
        }

        if (type == NotificationType.COMMENT || type == NotificationType.REACTION) {
            taskRepository.getCommentById(referenceId, new TaskRepository.TaskCallback<Comment>() {
                @Override
                public void onSuccess(Comment comment) {
                    runOnUiThread(() -> {
                        if (comment == null || comment.isDeleted() || comment.getTaskId() == null || comment.getTaskId() <= 0) {
                            showDeletedContentMessage();
                            return;
                        }
                        openTaskDetail(comment.getTaskId());
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(NotificationsActivity.this::showDeletedContentMessage);
                }
            });
            return;
        }

        openTaskDetail(referenceId);
    }

    private void openProjectDetail(long projectId, String projectName) {
        Intent intent = new Intent(this, ProjectDetailActivity.class);
        intent.putExtra("project_id", projectId);
        if (projectName != null && !projectName.trim().isEmpty()) {
            intent.putExtra("project_name", projectName.trim());
        }
        startActivity(intent);
    }

    private void openTaskDetail(long taskId) {
        taskRepository.getTaskById(taskId, new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task task) {
                runOnUiThread(() -> {
                    if (task == null) {
                        Toast.makeText(NotificationsActivity.this,
                                getString(R.string.notification_related_content_deleted), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String status = task.getStatus() != null ? task.getStatus().trim().toUpperCase() : "";
                    if ("TRASH".equals(status)) {
                        Toast.makeText(NotificationsActivity.this,
                                getString(R.string.notification_related_task_deleted), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (task.getProjectId() <= 0) {
                        Toast.makeText(NotificationsActivity.this,
                                getString(R.string.notification_open_task_failed), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(NotificationsActivity.this, TaskDetailActivity.class);
                    intent.putExtra("project_id", task.getProjectId());
                    intent.putExtra("task_id", task.getId());
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                        getString(R.string.notification_related_content_deleted), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showDeletedContentMessage() {
        Toast.makeText(NotificationsActivity.this,
                getString(R.string.notification_related_content_deleted), Toast.LENGTH_SHORT).show();
    }
}