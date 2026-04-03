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

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DENIED = "DENIED";

    private final InvitationApiService api;

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public InvitationRepository() {
        this.api = SupabaseClient.getInstance().getService(InvitationApiService.class);
    }

    /**
     * Tạo lời mời mới vào project_invitations.
     * Trigger Supabase sẽ tự động tạo notification cho người được mời.
     *
     * @param projectId  ID của project
     * @param inviterId  UUID của người gửi lời mời (current user)
     * @param email      Email của người được mời
     * @param role       Role: ADMIN / MEMBER / VIEWER
     */
    public void createInvitation(long projectId, String inviterId,
                                 String email, String role,
                                 ResultCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("inviter_id", inviterId);
        body.put("email", email);
        body.put("role", role != null ? role : "MEMBER");

        api.createInvitation("return=minimal", body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi gửi lời mời: " + r.code());
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    /**
     * Chấp nhận lời mời.
     * Flow: tìm invitation theo project_id + email → update accepted → addMember
     *
     * @param projectId     Lấy từ notification.getReferenceId()
     * @param userId        UUID của user hiện tại
     * @param userEmail     Email của user hiện tại (để tìm đúng invitation)
     */
    public void acceptInvitation(long projectId, String userId,
                                 String userEmail, ResultCallback<Void> cb) {
        // Bước 1: Tìm invitation PENDING theo project_id + email
        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + userEmail,
            "eq." + STATUS_PENDING,
                "id,role,status"
        ).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                    cb.onError("Không tìm thấy lời mời hợp lệ");
                    return;
                }

                Map<String, Object> invitation = r.body().get(0);
                String invitationId = (String) invitation.get("id"); // UUID dạng String
                String role = (String) invitation.get("role");

                // Bước 2: Cập nhật status = ACCEPTED
                Map<String, String> statusBody = new HashMap<>();
                statusBody.put("status", STATUS_ACCEPTED);

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

    /**
     * Từ chối lời mời.
     * Flow: tìm invitation theo project_id + email → update declined
     *
     * @param projectId   Lấy từ notification.getReferenceId()
     * @param userEmail   Email của user hiện tại
     */
    public void declineInvitation(long projectId, String userEmail,
                                  ResultCallback<Void> cb) {
        // Bước 1: Tìm invitation PENDING
        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + userEmail,
            "eq." + STATUS_PENDING,
                "id"
        ).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                    cb.onError("Không tìm thấy lời mời hợp lệ");
                    return;
                }

                String invitationId = (String) r.body().get(0).get("id");

                // Bước 2: Cập nhật status = DENIED
                Map<String, String> body = new HashMap<>();
                body.put("status", STATUS_DENIED);

                api.updateInvitationStatus("eq." + invitationId, body)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r2) {
                                if (r2.isSuccessful()) cb.onSuccess(null);
                                else cb.onError("Lỗi từ chối lời mời: " + r2.code());
                            }
                            @Override
                            public void onFailure(Call<Void> c, Throwable t) {
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
}