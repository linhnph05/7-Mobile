package com.team7.taskflow.ui.member;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.ProjectMember;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onRemoveMember(ProjectMember member);
        void onChangeRole(ProjectMember member, String newRole);
    }

    private final Context context;
    private final List<ProjectMember> members;
    private final OnMemberActionListener listener;
    private final boolean isOwnerOrAdmin; // chỉ owner/admin mới thấy nút xóa/đổi role

    public MemberAdapter(Context context, List<ProjectMember> members,
                         boolean isOwnerOrAdmin, OnMemberActionListener listener) {
        this.context = context;
        this.members = members;
        this.isOwnerOrAdmin = isOwnerOrAdmin;
        this.listener = listener;
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

        // Tên + avatar chữ cái đầu
        String name = member.getDisplayName();
        holder.tvName.setText(name);
        holder.tvEmail.setText(member.getEmail());
        holder.tvAvatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());

        // Role badge
        String role = member.getRole() != null ? member.getRole() : "MEMBER";
        holder.tvRole.setText(role);

        // Màu role
        switch (role.toUpperCase()) {
            case "OWNER":
                holder.tvRole.setTextColor(0xFFFFD700); // vàng
                break;
            case "ADMIN":
                holder.tvRole.setTextColor(0xFF2945FF); // xanh
                break;
            case "VIEWER":
                holder.tvRole.setTextColor(0xFFA0A0A0); // xám
                break;
            default:
                holder.tvRole.setTextColor(0xFF4CAF50); // xanh lá = member
        }

        // Nút xóa: chỉ hiện nếu là owner/admin VÀ không phải chính owner
        if (isOwnerOrAdmin && !"OWNER".equalsIgnoreCase(role)) {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Xóa thành viên")
                        .setMessage("Bạn có chắc muốn xóa " + name + " khỏi dự án?")
                        .setPositiveButton("Xóa", (d, w) -> listener.onRemoveMember(member))
                        .setNegativeButton("Hủy", null)
                        .show();
            });

            // Nhấn vào role để đổi
            holder.tvRole.setOnClickListener(v -> showRoleDialog(member));
        } else {
            holder.btnRemove.setVisibility(View.GONE);
        }
    }

    private void showRoleDialog(ProjectMember member) {
        String[] roles = {"ADMIN", "MEMBER", "VIEWER"};
        new AlertDialog.Builder(context)
                .setTitle("Đổi quyền cho " + member.getDisplayName())
                .setItems(roles, (dialog, which) -> {
                    listener.onChangeRole(member, roles[which]);
                })
                .show();
    }

    @Override
    public int getItemCount() { return members.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvEmail, tvRole;
        ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar  = itemView.findViewById(R.id.tv_avatar);
            tvName    = itemView.findViewById(R.id.tv_member_name);
            tvEmail   = itemView.findViewById(R.id.tv_member_email);
            tvRole    = itemView.findViewById(R.id.tv_role);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}