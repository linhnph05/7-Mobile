package com.team7.taskflow.ui.member;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import com.team7.taskflow.data.repository.InvitationRepository;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;

public class InviteMemberBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PROJECT_ID = "project_id";

    public static InviteMemberBottomSheet newInstance(long projectId) {
        InviteMemberBottomSheet sheet = new InviteMemberBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PROJECT_ID, projectId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_TaskFlow_BottomSheet);
    }

    public InviteMemberBottomSheet() {}

    private long projectId;
    private MemberRepository memberRepo;
    private InvitationRepository invitationRepo;
    private EditText etEmail;

    private String foundUserEmail;

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

        if (getArguments() != null) {
            projectId = getArguments().getLong(ARG_PROJECT_ID, -1);
        }
        if (projectId == -1) { dismiss(); return; }

        etEmail                = view.findViewById(R.id.et_email);
        TextView tvResultName  = view.findViewById(R.id.tv_result_name);
        TextView tvResultEmail = view.findViewById(R.id.tv_result_email);
        TextView tvAvatar      = view.findViewById(R.id.tv_avatar);
        TextView tvError       = view.findViewById(R.id.tv_error);
        CardView cardResult    = view.findViewById(R.id.card_result);
        RadioGroup rgRole      = view.findViewById(R.id.rg_role);
        Button btnSearch       = view.findViewById(R.id.btn_search);
        Button btnAddMember    = view.findViewById(R.id.btn_add_member);

        memberRepo = new MemberRepository();
        invitationRepo = InvitationRepository.getInstance();

        // ── Tìm kiếm user theo email ────────────────────────────
        etEmail.post(() -> {
            etEmail.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etEmail, InputMethodManager.SHOW_IMPLICIT);
        });

        btnSearch.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                tvError.setText(getString(R.string.auth_email_required));
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            tvError.setVisibility(View.GONE);
            cardResult.setVisibility(View.GONE);
            foundUserEmail = null;

            memberRepo.searchUserByEmail(email, new MemberRepository.ResultCallback<List<User>>() {
                @Override
                public void onSuccess(List<User> data) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        User user = data.get(0);
                        foundUserEmail = user.getEmail();
                        String name = user.getDisplayName() != null
                                ? user.getDisplayName() : email;
                        tvResultName.setText(name);
                        tvResultEmail.setText(foundUserEmail);
                        tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                        cardResult.setVisibility(View.VISIBLE);
                    });
                }
                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        tvError.setText(message);
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            });
        });

        // ── Gửi lời mời ────────────────────────────────────────
        btnAddMember.setOnClickListener(v -> {
            if (foundUserEmail == null || foundUserEmail.trim().isEmpty()) {
                tvError.setText(getString(R.string.search));
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            SessionManager.init(requireContext());
            String inviterId = SessionManager.getUserId();
            if (inviterId == null || inviterId.trim().isEmpty()) {
                tvError.setText(getString(R.string.notification_session_expired));
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            int checkedId = rgRole.getCheckedRadioButtonId();
            String role = "MEMBER";
            if (checkedId == R.id.rb_admin)  role = "ADMIN";
            if (checkedId == R.id.rb_viewer) role = "VIEWER";

            final String finalRole = role;

            invitationRepo.createInvitation(projectId, inviterId, foundUserEmail, finalRole,
                    new InvitationRepository.ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        getString(R.string.activity_invitation_sent),
                                        Toast.LENGTH_SHORT).show();
                                dismiss();
                            });
                        }
                        @Override
                        public void onError(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                tvError.setText(getString(R.string.error) + ": " + message);
                                tvError.setVisibility(View.VISIBLE);
                            });
                        }
                    });
        });
    }
}