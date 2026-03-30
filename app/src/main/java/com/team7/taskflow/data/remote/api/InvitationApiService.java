package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.ProjectMember;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

public interface InvitationApiService {

    // Lấy thông tin invitation theo id
    @GET("project_invitations")
    Call<java.util.List<java.util.Map<String, Object>>> getInvitation(
            @Query("id") String idFilter,
            @Query("select") String select
    );

    // Cập nhật status của invitation
    @PATCH("project_invitations")
    Call<Void> updateInvitationStatus(
            @Query("id") String idFilter,
            @Body Map<String, String> body
    );

    // Thêm vào project_members sau khi accept
    @POST("project_members")
    Call<Void> addMember(
            @Header("Prefer") String prefer,
            @Body Map<String, Object> body
    );
}