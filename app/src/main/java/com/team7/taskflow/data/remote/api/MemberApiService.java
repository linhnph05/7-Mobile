package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface MemberApiService {

    // Lấy danh sách thành viên của 1 project, kèm thông tin user
    @GET("project_members")
    Call<List<ProjectMember>> getMembers(
            @Query("project_id") String projectIdFilter,  // truyền "eq.123"
            @Query("select") String select                // truyền "*,users(user_id,display_name,email,avatar_url)"
    );

    // Thêm thành viên vào project
    @POST("project_members")
    Call<Void> addMember(
            @Header("Prefer") String prefer,
            @Query("on_conflict") String onConflict,
            @Body Map<String, Object> body
    );

    // Cập nhật role của thành viên
    @PATCH("project_members")
    Call<Void> updateRole(
            @Query("project_id") String projectIdFilter,  // "eq.123"
            @Query("user_id") String userIdFilter,        // "eq.uuid"
            @Body Map<String, String> body
    );

    // Cập nhật trạng thái/role của thành viên (dùng cho soft-delete member)
    @PATCH("project_members")
    Call<Void> updateMember(
            @Query("project_id") String projectIdFilter,
            @Query("user_id") String userIdFilter,
            @Body Map<String, String> body
    );

    // Tìm user theo email (để mời)
    @GET("users")
    Call<List<User>> searchUserByEmail(
            @Query("email") String emailFilter,           // "eq.abc@gmail.com"
            @Query("select") String select                // "user_id,email,display_name"
    );
}