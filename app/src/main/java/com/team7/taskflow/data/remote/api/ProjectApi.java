package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.data.remote.dto.CreateProjectRequest;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.ProjectMember;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Projects
 * Maps to Supabase REST API endpoints
 */
public interface ProjectApi {

    /**
     * Get all projects owned by user
     * Supabase query: /projects?owner_id=eq.{userId}&is_deleted=eq.false
     */
    @GET("projects")
    Call<List<Project>> getOwnedProjects(
            @Query("owner_id") String ownerIdFilter,
            @Query("is_deleted") String isDeletedFilter,
            @Query("order") String order
    );

    /**
     * Get all projects for current user (as owner)
     * @deprecated Use getOwnedProjects instead
     */
    @Deprecated
    @GET("projects")
    Call<List<Project>> getProjects(
            @Query("owner_id") String ownerIdFilter,
            @Query("is_deleted") String isDeletedFilter,
            @Query("order") String order
    );

    /**
     * Get projects that user is a member of (including owned projects)
     * Supabase query: /project_members?user_id=eq.{userId}&select=*,projects(*)
     * This returns ProjectMember with nested Project data
     */
    @GET("project_members")
    Call<List<ProjectMember>> getMemberProjects(
            @Query("user_id") String userIdFilter,
            @Query("select") String select
    );

    /**
     * Add a member to a project
     * POST /project_members
     */
    @POST("project_members")
    Call<List<ProjectMember>> addProjectMember(
            @Body ProjectMember member,
            @Header("Prefer") String prefer
    );

    /**
     * Get project by ID
     * Supabase query: /projects?project_id=eq.{id}
     */
    @GET("projects")
    Call<List<Project>> getProjectById(
            @Query("project_id") String projectIdFilter
    );

    /**
     * Create a new project
     * POST /projects with body
     * @deprecated Use createProjectNew instead
     */
    @Deprecated
    @POST("projects")
    Call<List<Project>> createProject(
            @Body Project project,
            @Header("Prefer") String prefer
    );

    /**
     * Create a new project with DTO (không bao gồm project_id)
     * POST /projects with body
     * project_id sẽ được tự động sinh bởi database
     */
    @POST("projects")
    Call<List<Project>> createProjectNew(
            @Body CreateProjectRequest request,
            @Header("Prefer") String prefer
    );

    /**
     * Update project
     * PATCH /projects?project_id=eq.{id}
     */
    @PATCH("projects")
    Call<List<Project>> updateProject(
            @Query("project_id") String projectIdFilter,
            @Body Project project,
            @Header("Prefer") String prefer
    );

    /**
     * Soft delete project (set is_deleted = true)
     * PATCH /projects?project_id=eq.{id}
     */
    @PATCH("projects")
    Call<Void> deleteProject(
            @Query("project_id") String projectIdFilter,
            @Body DeleteBody body
    );

    /**
     * Hard delete project
     * DELETE /projects?project_id=eq.{id}
     */
    @DELETE("projects")
    Call<Void> hardDeleteProject(
            @Query("project_id") String projectIdFilter
    );

    /**
     * Body for soft delete
     */
    class DeleteBody {
        private boolean is_deleted = true;
        private String deleted_at;

        public DeleteBody() {
            this.deleted_at = java.time.Instant.now().toString();
        }
    }
}

