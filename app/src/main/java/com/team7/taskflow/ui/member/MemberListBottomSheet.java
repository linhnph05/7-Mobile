package com.team7.taskflow.ui.member;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.ProjectMember;

import java.util.ArrayList;
import java.util.List;

public class MemberListBottomSheet extends BottomSheetDialogFragment {

    private final long projectId;
    private MemberRepository repository;
    private MemberAdapter adapter;
    private final List<ProjectMember> memberList = new ArrayList<>();

    public MemberListBottomSheet(long projectId) {
        this.projectId = projectId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_member_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rvMembers   = view.findViewById(R.id.rv_members);
        ProgressBar progressBar  = view.findViewById(R.id.progress_bar);
        Button btnInvite         = view.findViewById(R.id.btn_invite);

        repository = new MemberRepository();

        adapter = new MemberAdapter(requireContext(), memberList, true,
                new MemberAdapter.OnMemberActionListener() {
                    @Override
                    public void onRemoveMember(ProjectMember member) {
                        repository.removeMember(projectId, member.getUserId(),
                                new MemberRepository.ResultCallback<Void>() {
                                    @Override public void onSuccess(Void data) {
                                        requireActivity().runOnUiThread(() -> {
                                            memberList.remove(member);
                                            adapter.notifyDataSetChanged();
                                            Toast.makeText(requireContext(),
                                                    "Đã xóa thành viên", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override public void onError(String message) {
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
                                    @Override public void onSuccess(Void data) {
                                        requireActivity().runOnUiThread(() -> {
                                            member.setRole(newRole);
                                            adapter.notifyDataSetChanged();
                                            Toast.makeText(requireContext(),
                                                    "Đã cập nhật quyền", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override public void onError(String message) {
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(),
                                                        message, Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }
                });

        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(adapter);

        // Nút mời → mở InviteBottomSheet
        btnInvite.setOnClickListener(v -> {
            InviteMemberBottomSheet inviteSheet = new InviteMemberBottomSheet(projectId);
            inviteSheet.show(getParentFragmentManager(), "invite");
        });

        // Load danh sách
        progressBar.setVisibility(View.VISIBLE);
        repository.getMembers(projectId, new MemberRepository.ResultCallback<List<ProjectMember>>() {
            @Override public void onSuccess(List<ProjectMember> data) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    memberList.clear();
                    memberList.addAll(data);
                    adapter.notifyDataSetChanged();
                });
            }
            @Override public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}