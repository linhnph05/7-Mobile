package com.team7.taskflow.data.repository;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.InvitationApiService;
import com.team7.taskflow.utils.SessionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InvitationRepository extends BaseRepository {

    private static final String STATUS_PENDING  = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DENIED   = "DENIED";

    private static InvitationRepository instance;
    private final InvitationApiService api;

    private InvitationRepository() {
        this.api = SupabaseClient.getInstance().getService(InvitationApiService.class);
    }

    public static synchronized InvitationRepository getInstance() {
        if (instance == null) instance = new InvitationRepository();
        return instance;
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    /**
     * Tạo lời mời mới. DB trigger sẽ tự tạo notification cho người được mời.
     *
     * @param projectId ID của project
     * @param inviterId UUID của người gửi lời mời
     * @param email     Email người được mời
     * @param role      ADMIN / MEMBER / VIEWER
     */
    public void createInvitation(long projectId, String inviterId,
                                 String email, String role, ResultCallback<Void> cb) {
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
                cb.onError(getErrorMessage(t));
            }
        });
    }

    private void performCreateInvitation(long projectId, String inviterId,
                                         String email, String role, ResultCallback<Void> cb) {
        String effectiveInviterId = (inviterId != null && !inviterId.trim().isEmpty())
                ? inviterId.trim() : SessionManager.getUserId();
        String normalizedRole = (role != null && !role.trim().isEmpty())
                ? role.trim().toUpperCase(Locale.US) : "MEMBER";

        Map<String, Object> body = new HashMap<>();
        body.put("project_id", projectId);
        body.put("inviter_id", effectiveInviterId);
        body.put("email", email);
        body.put("role", normalizedRole);

        api.createInvitation("return=minimal", body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> r) {
                if (r.isSuccessful()) {
                    cb.onSuccess(null);
                } else {
                    cb.onError("Lỗi gửi lời mời: " + buildApiError("create_invitation", r));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                cb.onError(getErrorMessage(t));
            }
        });
    }

    /**
     * Chấp nhận lời mời.
     * Flow: tìm invitation PENDING theo project_id + email → addMember.
     * DB trigger tự chuyển invitation status về ACCEPTED.
     */
    public void acceptInvitation(long projectId, String userId,
                                 String userEmail, ResultCallback<Void> cb) {
        String normalizedEmail = normalizeEmail(userEmail);
        // Ưu tiên userId từ session thay vì decode JWT thủ công
        String effectiveUserId = resolveCurrentUserId(userId);
        if (normalizedEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }
        if (effectiveUserId.isEmpty()) {
            cb.onError("Không xác định được tài khoản đăng nhập. Vui lòng đăng nhập lại.");
            return;
        }

        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + normalizedEmail,
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
                                    cb.onError("Lỗi thêm thành viên: " + buildApiError("accept_invitation", r3));
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> c, Throwable t) {
                                cb.onError(getErrorMessage(t));
                            }
                        });
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                cb.onError(getErrorMessage(t));
            }
        });
    }

    /**
     * Từ chối lời mời.
     * Flow: tìm invitation PENDING → update status = DENIED.
     */
    public void declineInvitation(long projectId, String userEmail, ResultCallback<Void> cb) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (normalizedEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }

        api.findPendingInvitation(
                "eq." + projectId,
                "eq." + normalizedEmail,
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

                Object idRaw = r.body().get(0).get("invitation_id");
                if (!(idRaw instanceof Number)) {
                    cb.onError("Dữ liệu lời mời không hợp lệ");
                    return;
                }
                long invitationId = ((Number) idRaw).longValue();

                Map<String, String> body = new HashMap<>();
                body.put("status", STATUS_DENIED);

                api.updateInvitationStatus("eq." + invitationId, body)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r2) {
                                if (r2.isSuccessful()) {
                                    cb.onSuccess(null);
                                } else {
                                    cb.onError("Lỗi từ chối lời mời: " + buildApiError("decline_invitation", r2));
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> c, Throwable t) {
                                cb.onError(getErrorMessage(t));
                            }
                        });
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                cb.onError(getErrorMessage(t));
            }
        });
    }

    public void getLatestInvitationStatus(long projectId, String userEmail, ResultCallback<String> cb) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (normalizedEmail.isEmpty()) {
            cb.onError("Email không hợp lệ");
            return;
        }

        api.findLatestInvitationByProjectAndEmail(
                "eq." + projectId,
                "eq." + normalizedEmail,
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
                cb.onError(getErrorMessage(t));
            }
        });
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.US);
    }

    /**
     * Lấy userId hiện tại từ SessionManager.
     * Fallback về tham số truyền vào nếu session không có.
     * (Thay thế cho decode JWT thủ công — SessionManager đã lưu userId từ lúc đăng nhập.)
     */
    private static String resolveCurrentUserId(String fallbackUserId) {
        String sessionId = SessionManager.getUserId();
        if (sessionId != null && !sessionId.trim().isEmpty()) return sessionId.trim();
        if (fallbackUserId != null && !fallbackUserId.trim().isEmpty()) return fallbackUserId.trim();
        return "";
    }
}
