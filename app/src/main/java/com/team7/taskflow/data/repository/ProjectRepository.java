package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.dto.CreateProjectRequest;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.ProjectMember;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for Project data operations
 * Handles communication with Supabase API
 */
public class ProjectRepository {

    private static final String TAG = "ProjectRepository";
    private static ProjectRepository instance;
    private final ProjectApi projectApi;

    private ProjectRepository() {
        projectApi = SupabaseClient.getInstance().getService(ProjectApi.class);
    }

    public static synchronized ProjectRepository getInstance() {
        if (instance == null) {
            instance = new ProjectRepository();
        }
        return instance;
    }

    /**
     * Callback interface for async operations
     */
    public interface ProjectCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    /**
     * Get all projects that user participates in (as owner or member)
     * This is the main method to use for Dashboard
     *
     * @param userId   User ID to get projects for
     * @param callback Callback for result
     */
    public void getAllUserProjects(String userId, ProjectCallback<List<Project>> callback) {
        // Query project_members table with nested projects data
        // select=*,projects(*) will include the full project object
        projectApi.getMemberProjects(
                "eq." + userId,
                "*,projects(*)").enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Project> projects = new ArrayList<>();

                            for (ProjectMember member : response.body()) {
                                Project project = member.getProject();
                                if (project != null && !project.isDeleted()) {
                                    // Nếu project là private, chỉ hiển thị cho owner
                                    if (project.isPrivate() && !"OWNER".equalsIgnoreCase(member.getRole())) {
                                        continue; // Bỏ qua project private nếu user không phải owner
                                    }
                                    // Set role info for later use (can edit, etc.)
                                    project.setUserRole(member.getRole());
                                    projects.add(project);
                                }
                            }

                            callback.onSuccess(projects);
                        } else {
                            callback.onError("Failed to load projects: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    /**
     * Get projects owned by user only
     * 
     * @deprecated Use getAllUserProjects to get both owned and member projects
     */
    @Deprecated
    public void getProjects(String userId, ProjectCallback<List<Project>> callback) {
        projectApi.getOwnedProjects(
                "eq." + userId,
                "eq.false",
                "created_at.desc").enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError("Failed to load projects: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    /**
     * Get project by ID
     */
    public void getProjectById(long projectId, ProjectCallback<Project> callback) {
        projectApi.getProjectById("eq." + projectId).enqueue(new Callback<List<Project>>() {
            @Override
            public void onResponse(@NonNull Call<List<Project>> call, @NonNull Response<List<Project>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError("Project not found");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Create a new project
     * Sử dụng CreateProjectRequest để không gửi project_id
     * Database sẽ tự động sinh ID tiếp theo
     * Sau khi tạo, tự động thêm owner vào project_members với role OWNER
     */
    public void createProject(Project project, ProjectCallback<Project> callback) {
        // Tạo DTO request (không bao gồm project_id)
        CreateProjectRequest request = new CreateProjectRequest()
                .setOwnerId(project.getOwnerId())
                .setProjectName(project.getName())
                .setDescription(project.getDescription())
                .setProjectKey(project.getProjectKey())
                .setBackgroundColor(project.getColor())
                .setPrivate(project.isPrivate());

        projectApi.createProjectNew(
                request,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Project createdProject = response.body().get(0);

                            // Tự động thêm owner vào project_members với role OWNER
                            addOwnerAsMember(createdProject, callback);
                        } else {
                            String errorBody = "";
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception e) {
                                errorBody = e.getMessage();
                            }
                            callback.onError("Failed to create project: " + response.code() + " - " + errorBody);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    /**
     * Thêm owner vào project_members sau khi tạo project
     */
    private void addOwnerAsMember(Project project, ProjectCallback<Project> callback) {
        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProjectId(project.getId());
        ownerMember.setUserId(project.getOwnerId());
        ownerMember.setRole("OWNER");

        projectApi.addProjectMember(
                ownerMember,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        // Dù thành công hay thất bại khi thêm member, vẫn trả về project đã tạo
                        // Vì project đã được tạo thành công
                        if (response.isSuccessful()) {
                            project.setUserRole("OWNER");
                        }
                        callback.onSuccess(project);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        // Project đã tạo, chỉ log lỗi khi thêm member
                        android.util.Log.e(TAG, "Failed to add owner as member: " + t.getMessage());
                        callback.onSuccess(project);
                    }
                });
    }

    /**
     * Update an existing project
     */
    public void updateProject(long projectId, Project project, ProjectCallback<Project> callback) {
        projectApi.updateProject(
                "eq." + projectId,
                project,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError("Failed to update project: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    /**
     * Soft delete a project
     */
    public void deleteProject(long projectId, ProjectCallback<Void> callback) {
        projectApi.deleteProject(
                "eq." + projectId,
                new ProjectApi.DeleteBody()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Failed to delete project: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }
}
