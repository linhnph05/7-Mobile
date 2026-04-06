package com.team7.taskflow.data.repository;

import android.util.Base64;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.InvitationApiService;
import com.team7.taskflow.utils.SessionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

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
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }

        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + normalizedEmail,
                "eq." + STATUS_PENDING,
            "invitation_id,status"
        ).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    cb.onError("Đã có lời mời đang chờ xử lý cho email này");
                    return;
                }

                performCreateInvitation(projectId, inviterId, normalizedEmail, role, cb);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    private void performCreateInvitation(long projectId, String inviterId,
                                         String email, String role,
                                         ResultCallback<Void> cb) {
        String effectiveInviterId = inviterId != null && !inviterId.trim().isEmpty()
            ? inviterId.trim()
            : SessionManager.getUserId();
        String normalizedRole = role != null && !role.trim().isEmpty()
            ? role.trim().toUpperCase(Locale.US)
            : "MEMBER";

        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("inviter_id", effectiveInviterId);
        body.put("email", email);
        body.put("role", normalizedRole);

        api.createInvitation("return=minimal", body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> r) {
                        if (r.isSuccessful()) cb.onSuccess(null);
                        else cb.onError("Lỗi gửi lời mời: " + r.code() + formatErrorBody(r));
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    /**
     * Chấp nhận lời mời.
        * Flow: tìm invitation theo project_id + email → addMember.
        * Trigger DB trên project_members sẽ tự chuyển invitation status về ACCEPTED.
     *
     * @param projectId     Lấy từ notification.getReferenceId()
     * @param userId        UUID của user hiện tại
     * @param userEmail     Email của user hiện tại (để tìm đúng invitation)
     */
    public void acceptInvitation(long projectId, String userId,
                                 String userEmail, ResultCallback<Void> cb) {
        String normalizedUserEmail = normalizeEmail(userEmail);
        String effectiveUserId = resolveAuthUserId(userId);
        if (normalizedUserEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }
        if (effectiveUserId.isEmpty()) {
            cb.onError("Không xác định được tài khoản đăng nhập. Vui lòng đăng nhập lại.");
            return;
        }

        // Bước 1: Tìm invitation PENDING theo project_id + email
        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + normalizedUserEmail,
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
                String role = (String) invitation.get("role");

                Map<String, Object> memberBody = new HashMap<>();
                memberBody.put("project_id", projectId);
                memberBody.put("user_id", effectiveUserId);
                memberBody.put("role", role != null ? role : "MEMBER");

                api.addMember("resolution=merge-duplicates,return=minimal", "project_id,user_id", memberBody)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r3) {
                                if (r3.isSuccessful()) {
                                    cb.onSuccess(null);
                                } else {
                                    cb.onError("Lỗi thêm thành viên: " + r3.code() + formatErrorBody(r3));
                                }
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

    /**
     * Từ chối lời mời.
     * Flow: tìm invitation theo project_id + email → update declined
     *
     * @param projectId   Lấy từ notification.getReferenceId()
     * @param userEmail   Email của user hiện tại
     */
    public void declineInvitation(long projectId, String userEmail,
                                  ResultCallback<Void> cb) {
        String normalizedUserEmail = normalizeEmail(userEmail);
        if (normalizedUserEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }

        // Bước 1: Tìm invitation PENDING
        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + normalizedUserEmail,
            "eq." + STATUS_PENDING,
            "invitation_id"
        ).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().isEmpty()) {
                    cb.onError("Không tìm thấy lời mời hợp lệ");
                    return;
                }

                Object invitationIdValue = r.body().get(0).get("invitation_id");
                if (!(invitationIdValue instanceof Number)) {
                    cb.onError("Dữ liệu lời mời không hợp lệ");
                    return;
                }
                long invitationId = ((Number) invitationIdValue).longValue();

                // Bước 2: Cập nhật status = DENIED
                Map<String, String> body = new HashMap<>();
                body.put("status", STATUS_DENIED);

                api.updateInvitationStatus("eq." + invitationId, body)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r2) {
                                if (r2.isSuccessful()) cb.onSuccess(null);
                                else cb.onError("Lỗi từ chối lời mời: " + r2.code() + formatErrorBody(r2));
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.US);
    }

    public void getLatestInvitationStatus(long projectId, String userEmail, ResultCallback<String> cb) {
        String normalizedUserEmail = normalizeEmail(userEmail);
        if (normalizedUserEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }

        api.findLatestInvitationByProjectAndEmail(
                "eq." + projectId,
                "eq." + normalizedUserEmail,
                "status,invitation_id",
                "invitation_id.desc",
                1
        ).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    cb.onSuccess(STATUS_PENDING);
                    return;
                }

                Object statusRaw = response.body().get(0).get("status");
                String status = statusRaw != null
                        ? String.valueOf(statusRaw).trim().toUpperCase(Locale.US)
                        : STATUS_PENDING;
                cb.onSuccess(status);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                cb.onError(t.getMessage());
            }
        });
    }

    private String resolveAuthUserId(String fallbackUserId) {
        String token = SessionManager.getAccessToken();
        if (token != null && !token.trim().isEmpty()) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                    String payload = new String(payloadBytes, StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(payload);
                    String sub = json.optString("sub", "").trim();
                    if (!sub.isEmpty()) {
                        return sub;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (fallbackUserId != null && !fallbackUserId.trim().isEmpty()) {
            return fallbackUserId.trim();
        }
        return "";
    }

    private String formatErrorBody(Response<?> response) {
        if (response == null || response.errorBody() == null) {
            return "";
        }
        try {
            String body = response.errorBody().string();
            if (body == null || body.trim().isEmpty()) {
                return "";
            }
            return " - " + body;
        } catch (IOException ignored) {
            return "";
        }
    }
}