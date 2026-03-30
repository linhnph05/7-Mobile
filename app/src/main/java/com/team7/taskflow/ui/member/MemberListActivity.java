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
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.ProjectMember;

import java.util.ArrayList;
import java.util.List;

public class MemberListActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID   = "project_id";
    public static final String EXTRA_PROJECT_NAME = "project_name";

    private RecyclerView rvMembers;
    private ProgressBar progressBar;
    private MemberAdapter adapter;
    private MemberRepository repository;

    private final List<ProjectMember> memberList = new ArrayList<>();
    private long projectId;
    private final androidx.activity.result.ActivityResultLauncher<Intent> activityResultLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            loadMembers();
                        }
                    }
            );
    private boolean isOwnerOrAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_list);

        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1);
        if (projectId == -1) { finish(); return; }

        // Views
        rvMembers   = findViewById(R.id.rv_members);
        progressBar = findViewById(R.id.progress_bar);
        Button btnInvite = findViewById(R.id.btn_invite);

        // Repository
        repository = new MemberRepository();

        // RecyclerView
        adapter = new MemberAdapter(this, memberList, isOwnerOrAdmin, new MemberAdapter.OnMemberActionListener() {
            @Override
            public void onRemoveMember(ProjectMember member) {
                removeMember(member);
            }
            @Override
            public void onChangeRole(ProjectMember member, String newRole) {
                changeRole(member, newRole);
            }
        });
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(adapter);

        // Nút mời
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

                    // Kiểm tra quyền của user hiện tại
                    String currentUserId = SupabaseClient.getInstance().getAccessToken();
                    for (ProjectMember m : data) {
                        if (m.getUserId() != null && m.getUserId().equals(currentUserId)) {
                            isOwnerOrAdmin = m.canEdit();
                            break;
                        }
                    }

                    adapter.notifyDataSetChanged();
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
        repository.removeMember(projectId, member.getUserId(), new MemberRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> {
                    memberList.remove(member);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(MemberListActivity.this, "Đã xóa thành viên", Toast.LENGTH_SHORT).show();
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
        repository.updateRole(projectId, member.getUserId(), newRole, new MemberRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> {
                    member.setRole(newRole);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(MemberListActivity.this, "Đã cập nhật quyền", Toast.LENGTH_SHORT).show();
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