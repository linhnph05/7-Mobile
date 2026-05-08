package com.team7.taskflow.ui.member;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onRemoveMember(ProjectMember member);
        void onChangeRole(ProjectMember member, String newRole);
    }

    private final Context context;
    private final List<ProjectMember> members;
    private final OnMemberActionListener listener;

    // ✅ FIX #1: Bỏ final để setAdminMode() có thể cập nhật
    private boolean isOwnerOrAdmin;

    public MemberAdapter(Context context, List<ProjectMember> members,
                         boolean isOwnerOrAdmin, OnMemberActionListener listener) {
        this.context        = context;
        this.members        = members;
        this.isOwnerOrAdmin = isOwnerOrAdmin;
        this.listener       = listener;
    }

    // ✅ FIX #2: Thêm setter để MemberListActivity cập nhật sau khi reload
    public void setAdminMode(boolean isAdmin) {
        this.isOwnerOrAdmin = isAdmin;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectMember member = members.get(position);

        // Tên + email
        String name = member.getDisplayName();
        holder.tvName.setText(name);
        holder.tvEmail.setText(member.getEmail());
        
        // Bind avatar with fallback letter
        if (holder.imgMemberAvatar != null) {
            String avatarUrl = member.getAvatarUrl();
            AvatarUiUtils.bindAvatarOrFallback(holder.imgMemberAvatar, holder.tvAvatar, avatarUrl, name);
        }

        // Role badge
        String role = member.getRole() != null ? member.getRole() : "MEMBER";
        holder.tvRole.setText(role);

        // Màu role
        switch (role.toUpperCase()) {
            case "OWNER":  holder.tvRole.setTextColor(0xFFFFD700); break; // vàng
            case "ADMIN":  holder.tvRole.setTextColor(0xFF2945FF); break; // xanh dương
            case "VIEWER": holder.tvRole.setTextColor(0xFFA0A0A0); break; // xám
            default:       holder.tvRole.setTextColor(0xFF4CAF50); break; // xanh lá = MEMBER
        }

        // ✅ FIX #3: Phân quyền đúng — Admin/Owner thấy nút xóa & đổi role
        // OWNER không được xóa chính mình
        boolean canManageThisMember = isOwnerOrAdmin && !"OWNER".equalsIgnoreCase(role);

        if (canManageThisMember) {
            // Hiện nút xóa
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v ->
                    new AlertDialog.Builder(context)
                            .setTitle("Xóa thành viên")
                            .setMessage("Bạn có chắc muốn xóa " + name + " khỏi dự án?")
                            .setPositiveButton("Xóa", (d, w) -> listener.onRemoveMember(member))
                            .setNegativeButton("Hủy", null)
                            .show());

            // Click vào role badge để đổi quyền
            holder.tvRole.setOnClickListener(v -> showRoleDialog(member));

        } else {
            // ✅ FIX #4: Clear listener cũ để tránh RecyclerView tái sử dụng ViewHolder bị sót
            holder.btnRemove.setVisibility(View.GONE);
            holder.btnRemove.setOnClickListener(null);
            holder.tvRole.setOnClickListener(null);
        }
    }

    private void showRoleDialog(ProjectMember member) {
        // OWNER không thể bị hạ cấp — chỉ cho chọn ADMIN / MEMBER / VIEWER
        String[] roles = {"ADMIN", "MEMBER", "VIEWER"};
        new AlertDialog.Builder(context)
                .setTitle("Đổi quyền cho " + member.getDisplayName())
                .setItems(roles, (dialog, which) -> listener.onChangeRole(member, roles[which]))
                .show();
    }

    @Override
    public int getItemCount() { return members.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView   imgMemberAvatar;
        TextView    tvAvatar, tvName, tvEmail, tvRole;
        ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgMemberAvatar = itemView.findViewById(R.id.img_member_avatar);
            tvAvatar  = itemView.findViewById(R.id.tv_avatar);
            tvName    = itemView.findViewById(R.id.tv_member_name);
            tvEmail   = itemView.findViewById(R.id.tv_member_email);
            tvRole    = itemView.findViewById(R.id.tv_role);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}