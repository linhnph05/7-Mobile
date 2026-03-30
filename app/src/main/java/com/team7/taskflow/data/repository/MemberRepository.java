package com.team7.taskflow.data.repository;

import com.team7.taskflow.BuildConfig;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.MemberApiService;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MemberRepository {

    private final MemberApiService api;

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public MemberRepository() {
        this.api = SupabaseClient.getInstance().getService(MemberApiService.class);
    }

    // Lấy danh sách thành viên kèm thông tin user
    public void getMembers(long projectId, ResultCallback<List<ProjectMember>> cb) {
        api.getMembers(
                "eq." + projectId,
                "*,users(user_id,display_name,email,avatar_url)"
        ).enqueue(new Callback<List<ProjectMember>>() {
            @Override
            public void onResponse(Call<List<ProjectMember>> call, Response<List<ProjectMember>> r) {
                if (r.isSuccessful() && r.body() != null) cb.onSuccess(r.body());
                else cb.onError("Lỗi tải danh sách: " + r.code());
            }
            @Override
            public void onFailure(Call<List<ProjectMember>> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    // Thêm thành viên (đã biết userId)
    public void addMember(long projectId, String userId, String role, ResultCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("user_id", userId);
        body.put("role", role);

        api.addMember("return=minimal", body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi thêm thành viên: " + r.code());
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // Cập nhật role
    public void updateRole(long projectId, String userId, String newRole, ResultCallback<Void> cb) {
        Map<String, String> body = new HashMap<>();
        body.put("role", newRole);

        api.updateRole("eq." + projectId, "eq." + userId, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi cập nhật role: " + r.code());
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // Xóa thành viên
    public void removeMember(long projectId, String userId, ResultCallback<Void> cb) {
        api.removeMember( "eq." + projectId, "eq." + userId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi xóa thành viên: " + r.code());
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // Tìm user theo email
    public void searchUserByEmail(String email, ResultCallback<List<User>> cb) {
        api.searchUserByEmail(
                        "eq." + email,
                        "user_id,email,display_name,avatar_url")
                .enqueue(new Callback<List<User>>() {
                    @Override
                    public void onResponse(Call<List<User>> call, Response<List<User>> r) {
                        if (r.isSuccessful() && r.body() != null && !r.body().isEmpty())
                            cb.onSuccess(r.body());
                        else cb.onError("Không tìm thấy user với email này");
                    }
                    @Override
                    public void onFailure(Call<List<User>> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }
}