package com.team7.taskflow.ui.member;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import android.widget.Button;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class MemberListBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROJECT_ID = "project_id";

    // ✅ FIX #1: Dùng newInstance() + Bundle thay vì constructor có argument
    public static MemberListBottomSheet newInstance(long projectId) {
        MemberListBottomSheet sheet = new MemberListBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PROJECT_ID, projectId);
        sheet.setArguments(args);
        return sheet;
    }

    // Constructor rỗng bắt buộc cho Fragment
    public MemberListBottomSheet() {}

    private long projectId;
    private MemberRepository repository;
    private MemberAdapter adapter;
    private Button btnInvite;
    private final List<ProjectMember> memberList = new ArrayList<>();
    private boolean isOwnerOrAdmin = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // ✅ FIX #2: Dùng layout riêng cho BottomSheet
        return inflater.inflate(R.layout.bottom_sheet_member_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Lấy projectId từ arguments
        if (getArguments() != null) {
            projectId = getArguments().getLong(ARG_PROJECT_ID, -1);
        }
        if (projectId == -1) {
            dismiss();
            return;
        }

        RecyclerView rvMembers = view.findViewById(R.id.rv_members);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        btnInvite = view.findViewById(R.id.btn_invite);

        repository = new MemberRepository();

        // Adapter khởi tạo với isOwnerOrAdmin = false,
        // sẽ được cập nhật sau khi load xong danh sách
        adapter = new MemberAdapter(requireContext(), memberList, false,
                new MemberAdapter.OnMemberActionListener() {
                    @Override
                    public void onRemoveMember(ProjectMember member) {
                        repository.removeMember(projectId, member.getUserId(),
                                new MemberRepository.ResultCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void data) {
                                        // ✅ FIX #3: Kiểm tra isAdded() trước khi dùng requireActivity()
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() -> {
                                            memberList.remove(member);
                                            adapter.notifyDataSetChanged();
                                            Toast.makeText(requireContext(),
                                                    "Đã xóa thành viên", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override
                                    public void onError(String message) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(),
                                                        message, Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }

                    @Override
                    public void onChangeRole(ProjectMember member, String newRole) {
                        repository.updateRole(projectId, member.getUserId(), newRole,
                                new MemberRepository.ResultCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void data) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() -> {
                                            member.setRole(newRole);
                                            adapter.notifyDataSetChanged();
                                            Toast.makeText(requireContext(),
                                                    "Đã cập nhật quyền", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override
                                    public void onError(String message) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(),
                                                        message, Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }
                });

        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(adapter);

        // Nút mời → mở InviteBottomSheet (ẩn mặc định, hiện sau khi biết role)
        btnInvite.setOnClickListener(v -> {
            InviteMemberBottomSheet inviteSheet = InviteMemberBottomSheet.newInstance(projectId);
            inviteSheet.show(getParentFragmentManager(), "invite");
        });

        loadMembers(progressBar);
    }

    private void loadMembers(ProgressBar progressBar) {
        progressBar.setVisibility(View.VISIBLE);

        repository.getMembers(projectId, new MemberRepository.ResultCallback<List<ProjectMember>>() {
            @Override
            public void onSuccess(List<ProjectMember> data) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    memberList.clear();
                    memberList.addAll(data);

                    // ✅ FIX #4: Xác định đúng quyền user hiện tại
                    String currentUserId = SessionManager.getUserId();
                    isOwnerOrAdmin = false;

                    for (ProjectMember m : data) {
                        if (m.getUserId() != null && m.getUserId().equals(currentUserId)) {
                            isOwnerOrAdmin = m.canEdit(); // OWNER hoặc ADMIN
                            break;
                        }
                    }

                    // Cập nhật quyền vào adapter
                    adapter.setAdminMode(isOwnerOrAdmin);
                    adapter.notifyDataSetChanged();

                    // Hiện/ẩn nút mời
                    btnInvite.setVisibility(isOwnerOrAdmin ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}