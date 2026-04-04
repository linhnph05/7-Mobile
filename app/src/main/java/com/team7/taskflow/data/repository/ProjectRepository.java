package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ActivityApi;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.data.remote.dto.CreateProjectRequest;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.ProjectActivity;
import com.team7.taskflow.domain.model.ProjectHistoryItem;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.utils.SessionManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final TaskApi taskApi;
    private final ActivityApi activityApi;

    private ProjectRepository() {
        projectApi = SupabaseClient.getInstance().getService(ProjectApi.class);
        taskApi = SupabaseClient.getInstance().getService(TaskApi.class);
        activityApi = SupabaseClient.getInstance().getService(ActivityApi.class);
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

                            enrichDashboardData(projects, callback);
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

    private void enrichDashboardData(List<Project> projects, ProjectCallback<List<Project>> callback) {
        if (projects == null || projects.isEmpty()) {
            callback.onSuccess(projects);
            return;
        }

        AtomicInteger pending = new AtomicInteger(projects.size() * 3);
        for (Project project : projects) {
            loadProjectTaskProgress(project, pending, projects, callback);
            loadProjectActivityCount(project, pending, projects, callback);
            loadProjectMemberPreviews(project, pending, projects, callback);
        }
    }

    private void loadProjectTaskProgress(
            Project project,
            AtomicInteger pending,
            List<Project> projects,
            ProjectCallback<List<Project>> callback) {
        projectApi.getProjectTasks(
                "eq." + project.getId(),
                "task_id,status").enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int total = 0;
                            int done = 0;
                            for (Task task : response.body()) {
                                String status = task.getStatus();
                                if (status != null && "TRASH".equalsIgnoreCase(status)) {
                                    continue;
                                }
                                total++;
                                if (status != null && "DONE".equalsIgnoreCase(status)) {
                                    done++;
                                }
                            }
                            project.setTotalTasks(total);
                            project.setCompletedTasks(done);
                        }
                        completeEnrichStep(pending, projects, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        completeEnrichStep(pending, projects, callback);
                    }
                });
    }

    private void loadProjectActivityCount(
            Project project,
            AtomicInteger pending,
            List<Project> projects,
            ProjectCallback<List<Project>> callback) {
        projectApi.getProjectActivities(
                "eq." + project.getId(),
                "activity_id,entity_type,action_type,created_at",
                "created_at.desc").enqueue(new Callback<List<ProjectActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectActivity>> call,
                            @NonNull Response<List<ProjectActivity>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int todayTotal = 0;
                            int todayTask = 0;
                            int todayComment = 0;

                            for (ProjectActivity activity : response.body()) {
                                if (!isTodayActivity(activity.getCreatedAt())) {
                                    continue;
                                }
                                todayTotal++;
                                if (isCommentActivity(activity)) {
                                    todayComment++;
                                } else if (isTaskActivity(activity)) {
                                    todayTask++;
                                }
                            }

                            project.setNewActivitiesCount(todayTotal);
                            project.setTaskActivitiesToday(todayTask);
                            project.setCommentActivitiesToday(todayComment);
                        } else {
                            project.setNewActivitiesCount(0);
                            project.setTaskActivitiesToday(0);
                            project.setCommentActivitiesToday(0);
                        }
                        completeEnrichStep(pending, projects, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectActivity>> call, @NonNull Throwable t) {
                        project.setNewActivitiesCount(0);
                        project.setTaskActivitiesToday(0);
                        project.setCommentActivitiesToday(0);
                        completeEnrichStep(pending, projects, callback);
                    }
                });
    }

    private boolean isTodayActivity(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return false;
        }
        try {
            LocalDate activityDate = OffsetDateTime.parse(createdAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            return LocalDate.now(ZoneId.systemDefault()).equals(activityDate);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isTaskActivity(ProjectActivity activity) {
        String entity = normalize(activity.getEntityType());
        String action = normalize(activity.getActionType());
        return entity.contains("task")
                || action.contains("task")
                || action.contains("status")
                || action.contains("assign")
                || action.contains("due");
    }

    private boolean isCommentActivity(ProjectActivity activity) {
        String entity = normalize(activity.getEntityType());
        String action = normalize(activity.getActionType());
        return entity.contains("comment")
                || entity.contains("reaction")
                || action.contains("comment")
                || action.contains("reaction");
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.US);
    }

    private void loadProjectMemberPreviews(
            Project project,
            AtomicInteger pending,
            List<Project> projects,
            ProjectCallback<List<Project>> callback) {
        projectApi.getProjectMembers(
                "eq." + project.getId(),
                "user_id,users(user_id,display_name,email,avatar_url)").enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        List<User> previews = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null) {
                            for (ProjectMember member : response.body()) {
                                if (member.getUserInfo() == null) {
                                    continue;
                                }
                                User user = new User();
                                user.setUserId(member.getUserInfo().userId != null
                                        ? member.getUserInfo().userId
                                        : member.getUserId());
                                user.setDisplayName(member.getUserInfo().displayName);
                                user.setEmail(member.getUserInfo().email);
                                user.setAvatarUrl(member.getUserInfo().avatarUrl);
                                previews.add(user);
                                if (previews.size() == 3) {
                                    break;
                                }
                            }
                        }
                        project.setMemberPreviews(previews);
                        completeEnrichStep(pending, projects, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        project.setMemberPreviews(new ArrayList<>());
                        completeEnrichStep(pending, projects, callback);
                    }
                });
    }

    private void completeEnrichStep(
            AtomicInteger pending,
            List<Project> projects,
            ProjectCallback<List<Project>> callback) {
        if (pending.decrementAndGet() == 0) {
            callback.onSuccess(projects);
        }
    }

    public void getProjectActivities(long projectId, ProjectCallback<List<ProjectActivity>> callback) {
        projectApi.getProjectActivities(
                "eq." + projectId,
                "*",
                "created_at.desc").enqueue(new Callback<List<ProjectActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectActivity>> call,
                            @NonNull Response<List<ProjectActivity>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError("Failed to load project activities: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectActivity>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    public void getProjectHistoryFeed(long projectId, ProjectCallback<List<ProjectHistoryItem>> callback) {
        projectApi.getProjectTasks("eq." + projectId, "task_id,title")
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        Map<Long, String> taskTitleMap = new HashMap<>();
                        if (response.isSuccessful() && response.body() != null) {
                            List<Task> tasks = response.body();
                            for (Task task : tasks) {
                                if (task == null) {
                                    continue;
                                }
                                long taskId = task.getId();
                                String title = task.getTitle() != null && !task.getTitle().trim().isEmpty()
                                        ? task.getTitle().trim()
                                        : "Task #" + taskId;
                                taskTitleMap.put(taskId, title);
                            }
                        }

                        continueProjectHistoryFeed(projectId, taskTitleMap, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        continueProjectHistoryFeed(projectId, new HashMap<>(), callback);
                    }
                });
    }

    private void continueProjectHistoryFeed(long projectId,
            Map<Long, String> taskTitleMap,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        fetchMemberProfileMap(projectId, new ProjectCallback<Map<String, UserProfile>>() {
            @Override
            public void onSuccess(Map<String, UserProfile> userProfileMap) {
                loadProjectActivityFeed(projectId, taskTitleMap, userProfileMap,
                        new ProjectCallback<List<ProjectHistoryItem>>() {
                            @Override
                            public void onSuccess(List<ProjectHistoryItem> projectFeed) {
                                String taskFilter = buildTaskFilterForHistory(taskTitleMap);
                                if (taskFilter == null) {
                                    callback.onSuccess(projectFeed);
                                    return;
                                }

                                fetchTaskHistoryAndComments(
                                        taskFilter,
                                        taskTitleMap,
                                        userProfileMap,
                                        new ProjectCallback<List<ProjectHistoryItem>>() {
                                            @Override
                                            public void onSuccess(List<ProjectHistoryItem> taskFeed) {
                                                List<ProjectHistoryItem> merged = new ArrayList<>();
                                                if (projectFeed != null) {
                                                    merged.addAll(projectFeed);
                                                }
                                                if (taskFeed != null) {
                                                    merged.addAll(taskFeed);
                                                }
                                                sortFeedByTimeDesc(merged);
                                                callback.onSuccess(merged);
                                            }

                                            @Override
                                            public void onError(String error) {
                                                // Keep project-level history even if task-level queries fail.
                                                callback.onSuccess(projectFeed != null ? projectFeed : new ArrayList<>());
                                            }
                                        });
                            }

                            @Override
                            public void onError(String error) {
                                String taskFilter = buildTaskFilterForHistory(taskTitleMap);
                                if (taskFilter == null) {
                                    callback.onError(error);
                                    return;
                                }

                                // Fallback to task-level history so screen still has useful data.
                                fetchTaskHistoryAndComments(
                                        taskFilter,
                                        taskTitleMap,
                                        userProfileMap,
                                        new ProjectCallback<List<ProjectHistoryItem>>() {
                                            @Override
                                            public void onSuccess(List<ProjectHistoryItem> taskFeed) {
                                                callback.onSuccess(taskFeed != null ? taskFeed : new ArrayList<>());
                                            }

                                            @Override
                                            public void onError(String taskError) {
                                                callback.onError(error);
                                            }
                                        });
                            }
                        });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private String buildTaskFilterForHistory(Map<Long, String> taskTitleMap) {
        if (taskTitleMap == null || taskTitleMap.isEmpty()) {
            return null;
        }
        return "in.(" + joinTaskIds(taskTitleMap.keySet()) + ")";
    }

    public void logProjectActivity(long projectId, String userId, String actionType, String entityType,
            Long entityId, String oldValue, String newValue) {
        if (projectId <= 0) {
            return;
        }

        ProjectActivity activity = new ProjectActivity(
                projectId,
                userId != null && !userId.trim().isEmpty() ? userId : SessionManager.getUserId(),
                actionType,
                entityType,
                entityId,
                oldValue,
                newValue);

        projectApi.createProjectActivity(activity).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // Fire-and-forget.
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Ignore logging failures to avoid blocking the primary action.
            }
        });
    }

    private void loadProjectActivityFeed(long projectId,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        projectApi.getProjectActivities(
                "eq." + projectId,
                "activity_id,project_id,user_id,action_type,entity_type,entity_id,old_value,new_value,created_at",
                "created_at.desc").enqueue(new Callback<List<ProjectActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectActivity>> call,
                            @NonNull Response<List<ProjectActivity>> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("Failed to load project activities: " + response.code());
                            return;
                        }

                        List<ProjectActivity> activities = response.body() != null ? response.body() : new ArrayList<>();
                        List<ProjectHistoryItem> feed = mapProjectActivities(activities, taskTitleMap, userProfileMap);
                        sortFeedByTimeDesc(feed);
                        callback.onSuccess(feed);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectActivity>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    private List<ProjectHistoryItem> mapProjectActivities(
            List<ProjectActivity> activities,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap) {
        List<ProjectHistoryItem> result = new ArrayList<>();
        if (activities == null) {
            return result;
        }

        for (ProjectActivity activity : activities) {
            if (activity == null) {
                continue;
            }

            ProjectHistoryItem item = new ProjectHistoryItem();
            item.setSource(ProjectHistoryItem.SOURCE_PROJECT_ACTIVITY);
            item.setActorId(activity.getUserId());
            item.setActorName(resolveUserName(activity.getUserId(), userProfileMap));
            item.setAvatarUrl(resolveAvatarUrl(activity.getUserId(), userProfileMap));
            item.setActionLabel(mapProjectAction(activity.getActionType(), activity.getEntityType()));
            item.setTaskTitle(resolveProjectTargetTitle(activity, taskTitleMap));
            item.setDetail(buildProjectActivityDetail(activity));
            if (isCommentActivity(activity)) {
                item.setCommentContent(resolveProjectCommentContent(activity));
            }
            item.setCreatedAt(activity.getCreatedAt());
            result.add(item);
        }

        return result;
    }

    private String resolveProjectTargetTitle(ProjectActivity activity, Map<Long, String> taskTitleMap) {
        if (activity == null) {
            return "";
        }

        String entityType = normalize(activity.getEntityType());
        if (entityType.contains("task") && activity.getEntityId() != null && taskTitleMap != null) {
            long taskId = activity.getEntityId();
            return taskTitleMap.getOrDefault(taskId, "Task #" + taskId);
        }
        return "";
    }

    private String resolveProjectCommentContent(ProjectActivity activity) {
        if (activity == null) {
            return null;
        }

        String action = normalize(activity.getActionType());
        if (action.contains("delete")) {
            return activity.getOldValue();
        }
        return activity.getNewValue();
    }

    private String mapProjectAction(String actionTypeRaw, String entityTypeRaw) {
        String action = actionTypeRaw != null ? actionTypeRaw.trim().toUpperCase(Locale.US) : "";
        String entity = normalize(entityTypeRaw);

        if (action.contains("COMMENT_DELETE")) return "da xoa binh luan";
        if (action.contains("COMMENT_UPDATE")) return "da chinh sua binh luan";
        if (action.contains("COMMENT")) return "da binh luan";
        if (action.contains("ADD_REACTION")) return "da them cam xuc";
        if (action.contains("REMOVE_REACTION")) return "da bo cam xuc";
        if (action.contains("MEMBER_JOINED") || action.contains("MEMBER_ADDED") || action.contains("OWNER_JOINED")) {
            return "da them thanh vien";
        }
        if (action.contains("MEMBER_REMOVED") || action.contains("MEMBER_LEFT")) {
            return "da xoa thanh vien";
        }
        if ("CREATE".equals(action)) {
            if (entity.contains("project")) return "da tao project";
            if (entity.contains("task")) return "da tao task";
            return "da tao moi";
        }
        if ("UPDATE_STATUS".equals(action)) return "da doi trang thai";
        if (action.startsWith("UPDATE")) {
            if (entity.contains("project")) return "da cap nhat project";
            if (entity.contains("task")) return "da cap nhat task";
            if (entity.contains("member")) return "da cap nhat thanh vien";
            return "da cap nhat";
        }
        if (action.contains("DELETE") || action.contains("TRASH")) {
            if (entity.contains("project")) return "da xoa project";
            if (entity.contains("task")) return "da xoa task";
            return "da xoa";
        }
        if (action.contains("RESTORE")) {
            if (entity.contains("task")) return "da khoi phuc task";
            if (entity.contains("project")) return "da khoi phuc project";
            return "da khoi phuc";
        }
        return "da cap nhat";
    }

    private String buildProjectActivityDetail(ProjectActivity activity) {
        if (activity == null) {
            return "";
        }

        String action = normalize(activity.getActionType());
        String entity = normalize(activity.getEntityType());
        String oldText = trimOrDash(activity.getOldValue());
        String newText = trimOrDash(activity.getNewValue());

        if (action.contains("COMMENT")) {
            return "";
        }

        if (action.contains("MEMBER")) {
            return newText;
        }

        if (action.contains("ADD_REACTION") || action.contains("REMOVE_REACTION")) {
            return newText;
        }

        if ("UPDATE_STATUS".equals(action) || "DELETE".equals(action) || "RESTORE".equals(action)
                || "HARD_DELETE".equals(action)) {
            return oldText + " -> " + newText;
        }

        if (entity.contains("project") && ("CREATE".equals(action) || action.startsWith("UPDATE"))) {
            return newText;
        }

        return "";
    }

    private String resolveProjectTargetTitle(long entityId, Map<Long, String> taskTitleMap) {
        if (entityId <= 0 || taskTitleMap == null) {
            return "";
        }
        return taskTitleMap.getOrDefault(entityId, "Task #" + entityId);
    }

    private String trimOrDash(String raw) {
        return raw != null && !raw.trim().isEmpty() ? raw.trim() : "-";
    }

    private void fetchTaskHistoryAndComments(
            String taskFilter,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        activityApi.getActivitiesByTaskFilter(
                        taskFilter,
                        "activity_id,task_id,user_id,action_type,old_value,new_value,created_at",
                        "created_at.desc")
                .enqueue(new Callback<List<TaskActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<TaskActivity>> call,
                            @NonNull Response<List<TaskActivity>> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("Failed to load task activities: " + response.code());
                            return;
                        }

                        List<TaskActivity> taskActivities = response.body() != null ? response.body() : new ArrayList<>();
                        fetchTaskComments(taskFilter, taskTitleMap, userProfileMap, taskActivities, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<TaskActivity>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    private void fetchTaskComments(
            String taskFilter,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap,
            List<TaskActivity> taskActivities,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        String select = "comment_id,task_id,user_id,content,created_at,users(user_id,display_name,email,avatar_url)";
        taskApi.getCommentsByTask(taskFilter, select, "created_at.desc")
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("Failed to load comments: " + response.code());
                            return;
                        }

                        List<Comment> comments = response.body() != null ? response.body() : new ArrayList<>();
                        List<ProjectHistoryItem> feed = new ArrayList<>();
                        feed.addAll(mapTaskActivities(taskActivities, taskTitleMap, userProfileMap));
                        feed.addAll(mapComments(comments, taskTitleMap, userProfileMap));
                        sortFeedByTimeDesc(feed);
                        callback.onSuccess(feed);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    private void fetchMemberProfileMap(long projectId, ProjectCallback<Map<String, UserProfile>> callback) {
        projectApi.getProjectMembers(
                        "eq." + projectId,
                        "user_id,users(user_id,display_name,email,avatar_url)")
                .enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("Failed to load project members: " + response.code());
                            return;
                        }

                        Map<String, UserProfile> profiles = new HashMap<>();
                        List<ProjectMember> members = response.body() != null ? response.body() : new ArrayList<>();
                        for (ProjectMember member : members) {
                            if (member == null || member.getUserId() == null || member.getUserId().trim().isEmpty()) {
                                continue;
                            }
                            String userId = member.getUserId();
                            UserProfile profile = new UserProfile();
                            profile.displayName = userId;
                            profile.avatarUrl = null;
                            if (member.getUserInfo() != null) {
                                if (member.getUserInfo().displayName != null
                                        && !member.getUserInfo().displayName.trim().isEmpty()) {
                                    profile.displayName = member.getUserInfo().displayName.trim();
                                } else if (member.getUserInfo().email != null
                                        && !member.getUserInfo().email.trim().isEmpty()) {
                                    profile.displayName = member.getUserInfo().email.trim();
                                }
                                profile.avatarUrl = member.getUserInfo().avatarUrl;
                            }
                            profiles.put(userId, profile);
                        }
                        callback.onSuccess(profiles);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    private List<ProjectHistoryItem> mapTaskActivities(
            List<TaskActivity> taskActivities,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap) {
        List<ProjectHistoryItem> result = new ArrayList<>();
        if (taskActivities == null) {
            return result;
        }

        for (TaskActivity activity : taskActivities) {
            if (activity == null) {
                continue;
            }
            ProjectHistoryItem item = new ProjectHistoryItem();
            item.setSource(ProjectHistoryItem.SOURCE_TASK_ACTIVITY);
            item.setActorId(activity.getUserId());
            item.setActorName(resolveUserName(activity.getUserId(), userProfileMap));
            item.setAvatarUrl(resolveAvatarUrl(activity.getUserId(), userProfileMap));
            item.setActionLabel(mapTaskAction(activity.getActionType()));
            item.setTaskTitle(taskTitleMap.getOrDefault(activity.getTaskId(), "Task #" + activity.getTaskId()));
            item.setDetail(buildTaskActivityDetail(activity.getActionType(), activity.getOldValue(), activity.getNewValue()));
            item.setCreatedAt(activity.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private List<ProjectHistoryItem> mapComments(
            List<Comment> comments,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap) {
        List<ProjectHistoryItem> result = new ArrayList<>();
        if (comments == null) {
            return result;
        }

        for (Comment comment : comments) {
            if (comment == null) {
                continue;
            }
            ProjectHistoryItem item = new ProjectHistoryItem();
            item.setSource(ProjectHistoryItem.SOURCE_COMMENT);
            item.setActorId(comment.getUserId());
            item.setActorName(resolveCommentUserName(comment, userProfileMap));
            item.setAvatarUrl(resolveCommentAvatarUrl(comment, userProfileMap));
            item.setActionLabel("da binh luan");
            long taskId = comment.getTaskId() != null ? comment.getTaskId() : -1;
            item.setTaskTitle(taskTitleMap.getOrDefault(taskId, taskId > 0 ? "Task #" + taskId : "Task"));
            item.setDetail("Noi dung binh luan");
            item.setCommentContent(comment.getContent());
            item.setCreatedAt(comment.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private String resolveCommentUserName(Comment comment, Map<String, UserProfile> userProfileMap) {
        if (comment != null && comment.getUser() != null) {
            if (comment.getUser().getDisplayName() != null && !comment.getUser().getDisplayName().trim().isEmpty()) {
                return comment.getUser().getDisplayName().trim();
            }
            if (comment.getUser().getEmail() != null && !comment.getUser().getEmail().trim().isEmpty()) {
                return comment.getUser().getEmail().trim();
            }
        }
        return resolveUserName(comment != null ? comment.getUserId() : null, userProfileMap);
    }

    private String resolveCommentAvatarUrl(Comment comment, Map<String, UserProfile> userProfileMap) {
        if (comment != null && comment.getUser() != null
                && comment.getUser().getAvatarUrl() != null
                && !comment.getUser().getAvatarUrl().trim().isEmpty()) {
            return comment.getUser().getAvatarUrl().trim();
        }
        return resolveAvatarUrl(comment != null ? comment.getUserId() : null, userProfileMap);
    }

    private String resolveUserName(String userId, Map<String, UserProfile> userProfileMap) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Unknown";
        }
        if (userProfileMap != null && userProfileMap.containsKey(userId) && userProfileMap.get(userId) != null) {
            String displayName = userProfileMap.get(userId).displayName;
            if (displayName != null && !displayName.trim().isEmpty()) {
                return displayName;
            }
        }
        return userId;
    }

    private String resolveAvatarUrl(String userId, Map<String, UserProfile> userProfileMap) {
        if (userId == null || userId.trim().isEmpty() || userProfileMap == null) {
            return null;
        }
        UserProfile profile = userProfileMap.get(userId);
        return profile != null ? profile.avatarUrl : null;
    }

    private String mapTaskAction(String actionTypeRaw) {
        if (actionTypeRaw == null || actionTypeRaw.trim().isEmpty()) {
            return "da cap nhat task";
        }

        String action = actionTypeRaw.trim().toUpperCase(Locale.US);
        if ("CREATE".equals(action)) {
            return "da tao task";
        }
        if ("UPDATE_STATUS".equals(action)) {
            return "da doi trang thai";
        }
        if ("DELETE".equals(action)) {
            return "da dua task vao thung rac";
        }
        if ("RESTORE".equals(action)) {
            return "da khoi phuc task";
        }
        if ("HARD_DELETE".equals(action)) {
            return "da xoa vinh vien task";
        }
        if (action.startsWith("UPDATE")) {
            return "da chinh sua task";
        }
        return "da cap nhat task";
    }

    private String buildTaskActivityDetail(String actionTypeRaw, String oldValue, String newValue) {
        String action = actionTypeRaw != null ? actionTypeRaw.trim().toUpperCase(Locale.US) : "";
        String oldText = oldValue != null && !oldValue.trim().isEmpty() ? oldValue.trim() : "-";
        String newText = newValue != null && !newValue.trim().isEmpty() ? newValue.trim() : "-";

        if ("UPDATE_STATUS".equals(action) || "DELETE".equals(action) || "RESTORE".equals(action)) {
            return oldText + " -> " + newText;
        }
        if ("CREATE".equals(action)) {
            return "Trang thai ban dau: " + newText;
        }
        if ("HARD_DELETE".equals(action)) {
            return "Task da bi xoa khoi he thong";
        }
        if (!"-".equals(oldText) || !"-".equals(newText)) {
            return oldText + " -> " + newText;
        }
        return "Cap nhat task";
    }

    private String joinTaskIds(Set<Long> taskIds) {
        List<Long> sortedIds = new ArrayList<>(taskIds);
        Collections.sort(sortedIds);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sortedIds.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(sortedIds.get(i));
        }
        return builder.toString();
    }

    private void sortFeedByTimeDesc(List<ProjectHistoryItem> feed) {
        if (feed == null) {
            return;
        }
        feed.sort(new Comparator<ProjectHistoryItem>() {
            @Override
            public int compare(ProjectHistoryItem left, ProjectHistoryItem right) {
                long rightTime = parseEpochMillis(right != null ? right.getCreatedAt() : null);
                long leftTime = parseEpochMillis(left != null ? left.getCreatedAt() : null);
                return Long.compare(rightTime, leftTime);
            }
        });
    }

    private long parseEpochMillis(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return 0L;
        }
        try {
            return java.time.OffsetDateTime.parse(rawTime).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static class UserProfile {
        String displayName;
        String avatarUrl;
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
                            logProjectActivity(
                                    createdProject.getId(),
                                    SessionManager.getUserId(),
                                    "CREATE",
                                    "PROJECT",
                                    createdProject.getId(),
                                    null,
                                    createdProject.getName());

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
                            logProjectActivity(
                                    project.getId(),
                                    SessionManager.getUserId(),
                                    "MEMBER_JOINED",
                                    "MEMBER",
                                    null,
                                    null,
                                    "OWNER");
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
                            Project updatedProject = response.body().get(0);
                            logProjectActivity(
                                    updatedProject.getId(),
                                    SessionManager.getUserId(),
                                    "UPDATE",
                                    "PROJECT",
                                    updatedProject.getId(),
                                    null,
                                    updatedProject.getName());
                            callback.onSuccess(updatedProject);
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
                            logProjectActivity(
                                    projectId,
                                    SessionManager.getUserId(),
                                    "DELETE",
                                    "PROJECT",
                                    projectId,
                                    null,
                                    null);
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

    /**
     * Get all members of a specific project (with nested User info)
     */
    public void getProjectMembers(long projectId, ProjectCallback<List<ProjectMember>> callback) {
        projectApi.getProjectMembers(
                "eq." + projectId,
                "*,users(*)").enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ProjectMember> filtered = new ArrayList<>();
                            for (ProjectMember member : response.body()) {
                                if (member != null && !member.isRemoved()) {
                                    filtered.add(member);
                                }
                            }
                            callback.onSuccess(filtered);
                        } else {
                            callback.onError("Failed to load members: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }
}
