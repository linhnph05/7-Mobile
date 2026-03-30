package com.team7.taskflow.ui.member;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.User;

import java.util.List;

public class InviteMemberBottomSheet extends BottomSheetDialogFragment {

    private final long projectId;
    private MemberRepository repository;
    private String foundUserId;

    public InviteMemberBottomSheet(long projectId) {
        this.projectId = projectId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_invite_member, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etEmail         = view.findViewById(R.id.et_email);
        TextView tvResultName    = view.findViewById(R.id.tv_result_name);
        TextView tvResultEmail   = view.findViewById(R.id.tv_result_email);
        TextView tvAvatar        = view.findViewById(R.id.tv_avatar);
        TextView tvError         = view.findViewById(R.id.tv_error);
        CardView cardResult      = view.findViewById(R.id.card_result);
        RadioGroup rgRole        = view.findViewById(R.id.rg_role);
        Button btnSearch         = view.findViewById(R.id.btn_search);
        Button btnAddMember      = view.findViewById(R.id.btn_add_member);

        repository = new MemberRepository();

        btnSearch.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                tvError.setText("Vui lòng nhập email");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            tvError.setVisibility(View.GONE);
            cardResult.setVisibility(View.GONE);

            repository.searchUserByEmail(email, new MemberRepository.ResultCallback<List<User>>() {
                @Override public void onSuccess(List<User> data) {
                    requireActivity().runOnUiThread(() -> {
                        User user = data.get(0);
                        foundUserId = user.getUserId();
                        String name = user.getDisplayName() != null
                                ? user.getDisplayName() : email;
                        tvResultName.setText(name);
                        tvResultEmail.setText(user.getEmail());
                        tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                        cardResult.setVisibility(View.VISIBLE);
                    });
                }
                @Override public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        tvError.setText(message);
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            });
        });

        btnAddMember.setOnClickListener(v -> {
            if (foundUserId == null) return;
            int id = rgRole.getCheckedRadioButtonId();
            String role = "MEMBER";
            if (id == R.id.rb_admin)  role = "ADMIN";
            if (id == R.id.rb_viewer) role = "VIEWER";

            final String finalRole = role;
            repository.addMember(projectId, foundUserId, finalRole,
                    new MemberRepository.ResultCallback<Void>() {
                        @Override public void onSuccess(Void data) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        "Đã thêm thành viên!", Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }
                        @Override public void onError(String message) {
                            requireActivity().runOnUiThread(() -> {
                                tvError.setText("Lỗi: " + message);
                                tvError.setVisibility(View.VISIBLE);
                            });
                        }
                    });
        });
    }
}