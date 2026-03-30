package com.team7.taskflow.ui.member;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.utils.SessionManager; // ✅ FIX: Dùng SessionManager

import java.util.ArrayList;
import java.util.List;

public class MemberListActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID   = "project_id";
    public static final String EXTRA_PROJECT_NAME = "project_name";

    private RecyclerView rvMembers;
    private ProgressBar progressBar;
    private Button btnInvite;

    // ✅ FIX: Không khởi tạo adapter ở đây — chờ sau khi biết role
    private MemberAdapter adapter;
    private MemberRepository repository;

    private final List<ProjectMember> memberList = new ArrayList<>();
    private long projectId;
    private boolean isOwnerOrAdmin = false;

    private final androidx.activity.result.ActivityResultLauncher<Intent> activityResultLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) loadMembers();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_list);

        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1);
        if (projectId == -1) { finish(); return; }

        rvMembers   = findViewById(R.id.rv_members);
        progressBar = findViewById(R.id.progress_bar);
        btnInvite   = findViewById(R.id.btn_invite);

        repository = new MemberRepository();

        rvMembers.setLayoutManager(new LinearLayoutManager(this));

        btnInvite.setOnClickListener(v -> {
            Intent intent = new Intent(this, InviteMemberActivity.class);
            intent.putExtra(InviteMemberActivity.EXTRA_PROJECT_ID, projectId);
            activityResultLauncher.launch(intent);
        });

        loadMembers();
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);

        repository.getMembers(projectId, new MemberRepository.ResultCallback<List<ProjectMember>>() {
            @Override
            public void onSuccess(List<ProjectMember> data) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    memberList.clear();
                    memberList.addAll(data);

                    // ✅ FIX #1: Dùng SessionManager thay vì getAccessToken()
                    String currentUserId = SessionManager.getUserId();
                    isOwnerOrAdmin = false;

                    for (ProjectMember m : data) {
                        if (m.getUserId() != null && m.getUserId().equals(currentUserId)) {
                            isOwnerOrAdmin = m.canEdit(); // ADMIN hoặc OWNER
                            break;
                        }
                    }

                    // ✅ FIX #2: Ẩn/hiện nút Invite theo quyền
                    btnInvite.setVisibility(isOwnerOrAdmin ? View.VISIBLE : View.GONE);

                    // ✅ FIX #3: Tạo/cập nhật adapter SAU KHI đã biết isOwnerOrAdmin
                    if (adapter == null) {
                        adapter = new MemberAdapter(
                                MemberListActivity.this,
                                memberList,
                                isOwnerOrAdmin,
                                new MemberAdapter.OnMemberActionListener() {
                                    @Override
                                    public void onRemoveMember(ProjectMember member) {
                                        removeMember(member);
                                    }
                                    @Override
                                    public void onChangeRole(ProjectMember member, String newRole) {
                                        changeRole(member, newRole);
                                    }
                                });
                        rvMembers.setAdapter(adapter);
                    } else {
                        // Cập nhật quyền trong adapter khi reload
                        adapter.setAdminMode(isOwnerOrAdmin);
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MemberListActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void removeMember(ProjectMember member) {
        repository.removeMember(projectId, member.getUserId(),
                new MemberRepository.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        runOnUiThread(() -> {
                            memberList.remove(member);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(MemberListActivity.this,
                                    "Đã xóa thành viên", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                Toast.makeText(MemberListActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void changeRole(ProjectMember member, String newRole) {
        repository.updateRole(projectId, member.getUserId(), newRole,
                new MemberRepository.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        runOnUiThread(() -> {
                            member.setRole(newRole);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(MemberListActivity.this,
                                    "Đã cập nhật quyền", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                Toast.makeText(MemberListActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }
}