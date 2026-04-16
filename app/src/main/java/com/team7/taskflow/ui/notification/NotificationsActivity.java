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
import androidx.core.content.ContextCompat;

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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationsActivity extends BaseActivity {

    private static final String PREFS_NOTIFICATION_SEEN = "notification_prefs";
    private static final String KEY_LAST_SEEN_NOTIFICATION_MS = "last_seen_notification_ms";

    private ImageButton btnBack, btnMarkAllRead;
    private AutoCompleteTextView actvFilterType;
    private Chip chipUnread;
    private RecyclerView rvNotifications;
    private View layoutEmptyState;
    private MaterialButton btnClearFilters;

    private NotificationAdapter adapter;
    private List<Notification> allNotifications = new ArrayList<>();
    private final NotificationRepository notificationRepo = NotificationRepository.getInstance();
    private final InvitationRepository invitationRepo = InvitationRepository.getInstance();
    private final TaskRepository taskRepository = TaskRepository.getInstance();

        private String[] filterOptions;
        private String currentFilter;
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
        actvFilterType          = findViewById(R.id.actvFilterType);
        chipUnread              = findViewById(R.id.chipUnread);
        rvNotifications         = findViewById(R.id.rvNotifications);
        layoutEmptyState        = findViewById(R.id.layoutEmptyState);
        btnClearFilters         = findViewById(R.id.btnClearFilters);

        filterOptions = new String[] {
            getString(R.string.notification_filter_all_types),
            getString(R.string.notification_filter_me),
            getString(R.string.notification_filter_comment),
            getString(R.string.notification_filter_join_request),
            getString(R.string.notification_filter_system)
        };
        currentFilter = filterOptions[0];

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.addItemDecoration(new NotificationItemDividerDecoration(
            ContextCompat.getColor(this, R.color.theme_divider),
            Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
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
                            getString(R.string.notification_invite_missing_info), Toast.LENGTH_SHORT).show();
                    return;
                }

                String userId = SessionManager.getUserId();
                String userEmail = SessionManager.getUserEmail(); // ✅ Cần email để tìm invitation
                if (userId == null || userId.trim().isEmpty() || userEmail == null || userEmail.trim().isEmpty()) {
                    Toast.makeText(NotificationsActivity.this,
                            getString(R.string.notification_session_expired), Toast.LENGTH_SHORT).show();
                    return;
                }

                invitationRepo.acceptInvitation(projectId, userId, userEmail,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                adapter.markInviteDecision(notification.getNotificationId(), true);
                                markInviteAsRead(notification);
                                runOnUiThread(() ->
                                        Toast.makeText(NotificationsActivity.this,
                                        getString(R.string.notification_joined_project_success,
                                            notification.getReferenceName() != null
                                                ? notification.getReferenceName()
                                                : ""),
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
                            getString(R.string.notification_invite_missing_info), Toast.LENGTH_SHORT).show();
                    return;
                }

                String userEmail = SessionManager.getUserEmail(); // ✅ Cần email
                if (userEmail == null) {
                    Toast.makeText(NotificationsActivity.this,
                            getString(R.string.notification_session_expired), Toast.LENGTH_SHORT).show();
                    return;
                }

                invitationRepo.declineInvitation(projectId, userEmail,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                adapter.markInviteDecision(notification.getNotificationId(), false);
                                markInviteAsRead(notification);
                                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                        getString(R.string.notification_decline_success), Toast.LENGTH_SHORT).show());
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                        getString(R.string.notification_decline_error, message), Toast.LENGTH_SHORT).show());
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
                this, android.R.layout.simple_dropdown_item_1line, filterOptions);
        actvFilterType.setAdapter(dropdownAdapter);
        actvFilterType.setText(filterOptions[0], false);
        actvFilterType.setOnItemClickListener((parent, view, position, id) -> {
            currentFilter = filterOptions[position];
            applyFilters();
        });
    }

    private void showAcceptInviteErrorDialog(String message, Long projectId, String userId, String userEmail) {
        String safeMessage = (message == null || message.trim().isEmpty())
            ? getString(R.string.notification_accept_error_unknown)
            : message.trim();
        String details = "Accept invite failed"
                + "\nproject_id: " + (projectId != null ? projectId : "null")
                + "\nuser_id: " + (userId != null ? userId : "null")
                + "\nemail: " + (userEmail != null ? userEmail : "null")
                + "\n\nerror:\n" + safeMessage;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.notification_accept_error_title))
                .setMessage(details)
                .setPositiveButton(getString(R.string.notification_accept_error_close), null)
                .setNeutralButton(getString(R.string.notification_accept_error_copy), (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("accept_invite_error", details));
                        Toast.makeText(this, getString(R.string.notification_accept_error_copied), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnMarkAllRead.setOnClickListener(v -> markAllAsRead());
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
                            hydrateInviteStatusesAndApplyFilters();
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

    private void hydrateInviteStatusesAndApplyFilters() {
        String userEmail = SessionManager.getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            applyFilters();
            return;
        }

        List<Notification> inviteNotifications = new ArrayList<>();
        for (Notification notification : allNotifications) {
            if (notification != null
                    && notification.getType() == NotificationType.PROJECT_INVITE
                    && notification.getReferenceId() != null
                    && notification.getReferenceId() > 0) {
                inviteNotifications.add(notification);
            }
        }

        if (inviteNotifications.isEmpty()) {
            applyFilters();
            return;
        }

        AtomicInteger pending = new AtomicInteger(inviteNotifications.size());
        String normalizedEmail = userEmail.trim().toLowerCase(Locale.US);
        for (Notification inviteNotification : inviteNotifications) {
            invitationRepo.getLatestInvitationStatus(
                    inviteNotification.getReferenceId(),
                    normalizedEmail,
                    new InvitationRepository.ResultCallback<String>() {
                        @Override
                        public void onSuccess(String status) {
                            runOnUiThread(() -> {
                                inviteNotification.setInviteStatus(status);
                                if ("ACCEPTED".equalsIgnoreCase(status) || "DENIED".equalsIgnoreCase(status)) {
                                    markInviteAsRead(inviteNotification);
                                }
                                if (pending.decrementAndGet() == 0) {
                                    applyFilters();
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                if (pending.decrementAndGet() == 0) {
                                    applyFilters();
                                }
                            });
                        }
                    });
        }
    }

    private void applyFilters() {
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : allNotifications) {
            boolean matchesType = matchesTypeFilter(n);
            boolean matchesRead = !showUnreadOnly || !n.isRead();
            if (matchesType && matchesRead) filtered.add(n);
        }
        adapter.setNotifications(filtered);
        showEmptyState(filtered.isEmpty());
    }

    private boolean matchesTypeFilter(Notification notification) {
        if (notification == null) {
            return false;
        }
        NotificationType type = notification.getType();
        if (getString(R.string.notification_filter_me).equals(currentFilter)) {
            return type == NotificationType.TASK_ASSIGNED
                    || type == NotificationType.MENTION
                    || type == NotificationType.REACTION
                    || type == NotificationType.DELETED
                    || type == NotificationType.TASK_STATUS_CHANGED
                    || type == NotificationType.ATTACHMENT_ADDED;
        }
        if (getString(R.string.notification_filter_comment).equals(currentFilter)) {
            return type == NotificationType.COMMENT;
        }
        if (getString(R.string.notification_filter_join_request).equals(currentFilter)) {
            return type == NotificationType.PROJECT_INVITE;
        }
        if (getString(R.string.notification_filter_system).equals(currentFilter)) {
            return type == NotificationType.SYSTEM_ALERT;
        }
        return true;
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
                                getString(R.string.notification_mark_all_read_success), Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onError(String e) {
                        runOnUiThread(() -> loadNotifications());
                    }
                });
    }

    private void clearFilters() {
        currentFilter = filterOptions[0];
        showUnreadOnly = false;
        actvFilterType.setText(filterOptions[0], false);
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