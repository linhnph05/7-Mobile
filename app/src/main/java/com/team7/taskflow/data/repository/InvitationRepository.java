package com.team7.taskflow.data.repository;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.InvitationApiService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InvitationRepository {

    private final InvitationApiService api;

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public InvitationRepository() {
        this.api = SupabaseClient.getInstance().getService(InvitationApiService.class);
    }

    // Chấp nhận lời mời
    public void acceptInvitation(long invitationId, String userId, ResultCallback<Void> cb) {
        api.getInvitation("eq." + invitationId, "id,project_id,role,email,status")
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> r) {
                        if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                            cb.onError("Không tìm thấy lời mời");
                            return;
                        }

                        Map<String, Object> invitation = r.body().get(0);
                        String status = (String) invitation.get("status");

                        if ("accepted".equals(status)) {
                            cb.onError("Lời mời này đã được chấp nhận trước đó");
                            return;
                        }

                        // ✅ FIX: An toàn khi parse Number — Gson có thể trả về Double hoặc Long
                        long projectId = tolong(invitation.get("project_id"));
                        if (projectId == -1) {
                            cb.onError("Dữ liệu lời mời không hợp lệ");
                            return;
                        }

                        String role = (String) invitation.get("role");

                        // Bước 2: Cập nhật status = accepted
                        Map<String, String> statusBody = new HashMap<>();
                        statusBody.put("status", "accepted");

                        api.updateInvitationStatus("eq." + invitationId, statusBody)
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> call, Response<Void> r2) {
                                        if (!r2.isSuccessful()) {
                                            cb.onError("Lỗi cập nhật lời mời: " + r2.code());
                                            return;
                                        }

                                        // Bước 3: Thêm vào project_members
                                        Map<String, Object> memberBody = new HashMap<>();
                                        memberBody.put("project_id", projectId);
                                        memberBody.put("user_id", userId);
                                        memberBody.put("role", role != null ? role : "MEMBER");

                                        api.addMember("return=minimal", memberBody)
                                                .enqueue(new Callback<Void>() {
                                                    @Override
                                                    public void onResponse(Call<Void> c, Response<Void> r3) {
                                                        if (r3.isSuccessful()) cb.onSuccess(null);
                                                        else cb.onError("Lỗi thêm thành viên: " + r3.code());
                                                    }
                                                    @Override
                                                    public void onFailure(Call<Void> c, Throwable t) {
                                                        cb.onError(t.getMessage());
                                                    }
                                                });
                                    }
                                    @Override
                                    public void onFailure(Call<Void> call, Throwable t) {
                                        cb.onError(t.getMessage());
                                    }
                                });
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // Từ chối lời mời
    public void declineInvitation(long invitationId, ResultCallback<Void> cb) {
        Map<String, String> body = new HashMap<>();
        body.put("status", "declined");

        api.updateInvitationStatus("eq." + invitationId, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi từ chối lời mời: " + r.code());
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    // ✅ Helper: parse Number an toàn, tránh ClassCastException
    private long tolong(Object value) {
        if (value == null) return -1;
        if (value instanceof Long)   return (Long) value;
        if (value instanceof Double) return ((Double) value).longValue();
        if (value instanceof Integer) return ((Integer) value).longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return -1; }
    }
}