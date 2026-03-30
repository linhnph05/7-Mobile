package com.team7.taskflow.ui.member;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.domain.model.User;

import java.util.List;

public class InviteMemberActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "project_id";

    private EditText etEmail;
    private TextView tvResultName, tvResultEmail, tvAvatar, tvError;
    private CardView cardResult;
    private RadioGroup rgRole;
    private MemberRepository repository;

    private long projectId;
    private String foundUserId; // lưu userId sau khi tìm thấy

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite_member);

        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1);
        if (projectId == -1) { finish(); return; }

        // Views
        etEmail      = findViewById(R.id.et_email);
        tvResultName = findViewById(R.id.tv_result_name);
        tvResultEmail= findViewById(R.id.tv_result_email);
        tvAvatar     = findViewById(R.id.tv_avatar);
        tvError      = findViewById(R.id.tv_error);
        cardResult   = findViewById(R.id.card_result);
        rgRole       = findViewById(R.id.rg_role);

        Button btnSearch    = findViewById(R.id.btn_search);
        Button btnAddMember = findViewById(R.id.btn_add_member);

        repository = new MemberRepository();

        // Tìm user
        btnSearch.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                showError("Vui lòng nhập email");
                return;
            }
            searchUser(email);
        });

        // Thêm thành viên
        btnAddMember.setOnClickListener(v -> {
            if (foundUserId == null) return;
            String role = getSelectedRole();
            addMember(foundUserId, role);
        });
    }

    private void searchUser(String email) {
        tvError.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);

        repository.searchUserByEmail(email, new MemberRepository.ResultCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> data) {
                runOnUiThread(() -> {
                    User user = data.get(0);
                    foundUserId = user.getUserId();

                    String name = user.getDisplayName() != null ? user.getDisplayName() : email;
                    tvResultName.setText(name);
                    tvResultEmail.setText(user.getEmail());
                    tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    cardResult.setVisibility(View.VISIBLE);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError(message));
            }
        });
    }

    private void addMember(String userId, String role) {
        repository.addMember(projectId, userId, role, new MemberRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> {
                    Toast.makeText(InviteMemberActivity.this,
                            "Đã thêm thành viên thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError("Lỗi: " + message));
            }
        });
    }

    private String getSelectedRole() {
        int id = rgRole.getCheckedRadioButtonId();
        if (id == R.id.rb_admin)  return "ADMIN";
        if (id == R.id.rb_viewer) return "VIEWER";
        return "MEMBER";
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}