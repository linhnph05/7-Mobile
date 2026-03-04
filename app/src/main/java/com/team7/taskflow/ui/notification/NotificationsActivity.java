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
import com.team7.taskflow.data.repository.NotificationRepository;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;
import com.team7.taskflow.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity hiển thị danh sách thông báo.
 * Truy xuất từ bảng notifications trong Supabase,
 * lọc theo user_id của người dùng hiện tại.
 */
public class NotificationsActivity extends BaseActivity {

    // Views
    private ImageButton btnBack;
    private ImageButton btnMarkAllRead;
    private ImageButton btnNotificationSettings;
    private AutoCompleteTextView actvFilterType;
    private Chip chipUnread;
    private RecyclerView rvNotifications;
    private View layoutEmptyState;
    private MaterialButton btnClearFilters;

    // Adapter & data
    private NotificationAdapter adapter;
    private List<Notification> allNotifications = new ArrayList<>();
    private final NotificationRepository notificationRepo = NotificationRepository.getInstance();

    // Filter
    private static final String[] FILTER_OPTIONS = {
            "All types",
            "@me",
            "Comment",
            "Join request"
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
        btnBack = findViewById(R.id.btnBack);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        actvFilterType = findViewById(R.id.actvFilterType);
        chipUnread = findViewById(R.id.chipUnread);
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnClearFilters = findViewById(R.id.btnClearFilters);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        setupAdapter();
    }

    private void setupAdapter() {
        adapter = new NotificationAdapter(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                // Mark as read locally + on server
                if (!notification.isRead()) {
                    notification.setRead(true);
                    adapter.notifyDataSetChanged();
                    notificationRepo.markAsRead(notification.getNotificationId(),
                            new NotificationRepository.NotificationCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    /* already updated UI */ }

                                @Override
                                public void onError(String error) {
                                    // Revert on failure
                                    notification.setRead(false);
                                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                                }
                            });
                }
            }

            @Override
            public void onAcceptInvite(Notification notification) {
                // TODO: Call API to accept project invitation using
                // notification.getReferenceId()
                Toast.makeText(NotificationsActivity.this,
                        "Accepted invite to " + notification.getReferenceName(),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeclineInvite(Notification notification) {
                // TODO: Call API to decline project invitation
                Toast.makeText(NotificationsActivity.this,
                        "Declined invite", Toast.LENGTH_SHORT).show();
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

        chipUnread.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showUnreadOnly = isChecked;
            applyFilters();
        });

        btnClearFilters.setOnClickListener(v -> clearFilters());
    }

    // ── Data loading ────────────────────────────────────────────────

    /**
     * Load notifications from Supabase for the current user.
     */
    private void loadNotifications() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            showEmptyState(true);
            return;
        }

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
                            Toast.makeText(NotificationsActivity.this,
                                    error, Toast.LENGTH_SHORT).show();
                            showEmptyState(true);
                        });
                    }
                });
    }

    // ── Filtering ───────────────────────────────────────────────────

    private void applyFilters() {
        List<Notification> filtered = new ArrayList<>();

        for (Notification n : allNotifications) {
            // Type filter
            boolean matchesType = true;
            if ("@me".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.TASK_ASSIGNED
                        || n.getType() == NotificationType.MENTION);
            } else if ("Comment".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.COMMENT);
            } else if ("Join request".equals(currentFilter)) {
                matchesType = (n.getType() == NotificationType.PROJECT_INVITE);
            }

            // Read filter
            boolean matchesRead = !showUnreadOnly || !n.isRead();

            if (matchesType && matchesRead) {
                filtered.add(n);
            }
        }

        adapter.setNotifications(filtered);
        showEmptyState(filtered.isEmpty());
    }

    // ── Actions ─────────────────────────────────────────────────────

    private void markAllAsRead() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty())
            return;

        // Optimistic UI update
        for (Notification n : allNotifications) {
            n.setRead(true);
        }
        applyFilters();

        notificationRepo.markAllAsRead(userId,
                new NotificationRepository.NotificationCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                                "All notifications marked as read", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String error) {
                        // Reload on failure
                        runOnUiThread(() -> loadNotifications());
                    }
                });
    }

    private void openNotificationSettings() {
        // TODO: Show notification settings dialog
    }

    private void clearFilters() {
        currentFilter = "All types";
        showUnreadOnly = false;
        actvFilterType.setText(FILTER_OPTIONS[0], false);
        chipUnread.setChecked(false);
        applyFilters();
    }

    // ── UI helpers ──────────────────────────────────────────────────

    private void showEmptyState(boolean show) {
        if (show) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
        }
    }
}
