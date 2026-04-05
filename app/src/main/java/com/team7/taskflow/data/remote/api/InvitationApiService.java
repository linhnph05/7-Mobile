package com.team7.taskflow.data.remote.api;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface InvitationApiService {

    // ✅ Tạo lời mời mới → trigger tự tạo notification
    @POST("project_invitations")
    Call<Void> createInvitation(
            @Header("Prefer") String prefer,
            @Body Map<String, Object> body
    );

        // ✅ Tìm invitation theo project_id + email + status PENDING
    // Dùng khi Accept: biết project_id (từ notification.referenceId) + email người dùng hiện tại
    @GET("project_invitations")
    Call<List<Map<String, Object>>> findPendingInvitation(
            @Query("project_id") String projectIdFilter,
            @Query("email") String emailFilter,
            @Query("status") String statusFilter,
            @Query("select") String select
    );

        // ✅ Cập nhật status invitation (ACCEPTED / DENIED)
    @PATCH("project_invitations")
    Call<Void> updateInvitationStatus(
                    @Query("invitation_id") String invitationIdFilter,
            @Body Map<String, String> body
    );

    // ✅ Thêm vào project_members sau khi accept
    @POST("project_members")
    Call<Void> addMember(
            @Header("Prefer") String prefer,
            @Query("on_conflict") String onConflict,
            @Body Map<String, Object> body
    );
}