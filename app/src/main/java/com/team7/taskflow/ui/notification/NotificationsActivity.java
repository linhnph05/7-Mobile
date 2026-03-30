package com.team7.taskflow.ui.notification;

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
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;
import com.team7.taskflow.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends BaseActivity {

    // Views
    private ImageButton btnBack, btnMarkAllRead, btnNotificationSettings;
    private AutoCompleteTextView actvFilterType;
    private Chip chipUnread;
    private RecyclerView rvNotifications;
    private View layoutEmptyState;
    private MaterialButton btnClearFilters;

    // Adapter & data
    private NotificationAdapter adapter;
    private List<Notification> allNotifications = new ArrayList<>();
    private final NotificationRepository notificationRepo = NotificationRepository.getInstance();

    // ✅ FIX: Thêm InvitationRepository
    private final InvitationRepository invitationRepo = new InvitationRepository();

    // Filter
    private static final String[] FILTER_OPTIONS = {
            "All types", "@me", "Comment", "Join request"
    };
    private String currentFilter = "All types";
    private boolean showUnreadOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        initViews();
        setupFilterDropdown();
        setupClickListeners();
        loadNotifications();
    }

    // ── Init ────────────────────────────────────────────────────────

    private void initViews() {
        btnBack                  = findViewById(R.id.btnBack);
        btnMarkAllRead           = findViewById(R.id.btnMarkAllRead);
        btnNotificationSettings  = findViewById(R.id.btnNotificationSettings);
        actvFilterType           = findViewById(R.id.actvFilterType);
        chipUnread               = findViewById(R.id.chipUnread);
        rvNotifications          = findViewById(R.id.rvNotifications);
        layoutEmptyState         = findViewById(R.id.layoutEmptyState);
        btnClearFilters          = findViewById(R.id.btnClearFilters);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        setupAdapter();
    }

    private void setupAdapter() {
        adapter = new NotificationAdapter(new NotificationAdapter.OnNotificationActionListener() {

            @Override
            public void onNotificationClick(Notification notification) {
                if (!notification.isRead()) {
                    notification.setRead(true);
                    adapter.notifyDataSetChanged();
                    notificationRepo.markAsRead(notification.getNotificationId(),
                            new NotificationRepository.NotificationCallback<Void>() {
                                @Override public void onSuccess(Void r) { }
                                @Override public void onError(String e) {
                                    notification.setRead(false);
                                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                                }
                            });
                }
            }

            // ✅ FIX: Implement Accept
            @Override
            public void onAcceptInvite(Notification notification) {
                Long refId = notification.getReferenceId();
                if (refId == null) {
                    Toast.makeText(NotificationsActivity.this,
                            "Không tìm thấy lời mời", Toast.LENGTH_SHORT).show();
                    return;
                }

                String userId = SessionManager.getUserId();
                if (userId == null || userId.isEmpty()) {
                    Toast.makeText(NotificationsActivity.this,
                            "Phiên đăng nhập hết hạn", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Disable buttons để tránh double-tap
                // (Adapter sẽ tự ẩn button sau khi remove notification khỏi list)

                invitationRepo.acceptInvitation(refId, userId,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                runOnUiThread(() -> {
                                    Toast.makeText(NotificationsActivity.this,
                                            "Đã tham gia dự án: " + notification.getReferenceName(),
                                            Toast.LENGTH_SHORT).show();

                                    // Mark notification as read + remove khỏi list
                                    notification.setRead(true);
                                    allNotifications.remove(notification);
                                    applyFilters();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() ->
                                        Toast.makeText(NotificationsActivity.this,
                                                "Lỗi: " + message, Toast.LENGTH_SHORT).show());
                            }
                        });
            }

            // ✅ FIX: Implement Decline
            @Override
            public void onDeclineInvite(Notification notification) {
                Long refId = notification.getReferenceId();
                if (refId == null) {
                    Toast.makeText(NotificationsActivity.this,
                            "Không tìm thấy lời mời", Toast.LENGTH_SHORT).show();
                    return;
                }

                invitationRepo.declineInvitation(refId,
                        new InvitationRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                runOnUiThread(() -> {
                                    Toast.makeText(NotificationsActivity.this,
                                            "Đã từ chối lời mời", Toast.LENGTH_SHORT).show();

                                    // Remove notification khỏi list
                                    allNotifications.remove(notification);
                                    applyFilters();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() ->
                                        Toast.makeText(NotificationsActivity.this,
                                                "Lỗi: " + message, Toast.LENGTH_SHORT).show());
                            }
                        });
            }
        });
        rvNotifications.setAdapter(adapter);
    }

    // ── Filter dropdown ─────────────────────────────────────────────

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

    // ── Click listeners ─────────────────────────────────────────────

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

    // ── Data loading ────────────────────────────────────────────────

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

    // ── Filtering ───────────────────────────────────────────────────

    private void applyFilters() {
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : allNotifications) {
            boolean matchesType = true;
            if ("@me".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.TASK_ASSIGNED
                        || n.getType() == NotificationType.MENTION);
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

    // ── Actions ─────────────────────────────────────────────────────

    private void markAllAsRead() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) return;

        for (Notification n : allNotifications) n.setRead(true);
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

    private void openNotificationSettings() { /* TODO */ }

    private void clearFilters() {
        currentFilter = "All types";
        showUnreadOnly = false;
        actvFilterType.setText(FILTER_OPTIONS[0], false);
        chipUnread.setChecked(false);
        applyFilters();
    }

    // ── UI helpers ──────────────────────────────────────────────────

    private void showEmptyState(boolean show) {
        layoutEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}