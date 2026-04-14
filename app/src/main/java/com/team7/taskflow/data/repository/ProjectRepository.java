package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.data.remote.dto.CreateProjectRequest;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.CommentReaction;
import com.team7.taskflow.domain.model.ProjectActivity;
import com.team7.taskflow.domain.model.ProjectHistoryItem;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.utils.SessionManager;

import java.time.LocalDate;
import java.time.Instant;
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

    private ProjectRepository() {
        projectApi = SupabaseClient.getInstance().getService(ProjectApi.class);
        taskApi = SupabaseClient.getInstance().getService(TaskApi.class);
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
        projectApi.getMemberProjects(
                "eq." + userId,
                "*,projects(*)").enqueue(new Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                            @NonNull Response<List<ProjectMember>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Project> projects = new ArrayList<>();
                            for (ProjectMember member : response.body()) {
                                if (member == null || member.isRemoved()) {
                                    continue;
                                }
                                Project project = member != null ? member.getProject() : null;
                                if (project == null || project.isDeleted()) {
                                    continue;
                                }
                                project.setUserRole(member.getRole());
                                projects.add(project);
                            }
                            projects.sort((left, right) -> Long.compare(right.getId(), left.getId()));
                            enrichDashboardData(projects, callback);
                        } else {
                            callback.onError("Failed to load member projects: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        callback.onError("Network error (member projects): " + t.getMessage());
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
                                if (!isActivityWithinLast24Hours(activity.getCreatedAt())) {
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

    private boolean isActivityWithinLast24Hours(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return false;
        }
        try {
            Instant createdInstant = OffsetDateTime.parse(createdAt).toInstant();
            Instant now = Instant.now();
            return !createdInstant.isAfter(now)
                    && createdInstant.isAfter(now.minusSeconds(24 * 60 * 60));
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
                                if (member == null || member.isRemoved()) {
                                    continue;
                                }
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
        String currentUserId = SessionManager.getUserId();
        String currentUserEmail = SessionManager.getUserEmail();

        projectApi.getProjectTasks("eq." + projectId, "task_id,title,assignee_id")
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        Map<Long, String> taskTitleMap = new HashMap<>();
                        Map<Long, String> taskAssigneeMap = new HashMap<>();
                        if (response.isSuccessful() && response.body() != null) {
                            for (Task task : response.body()) {
                                if (task == null) {
                                    continue;
                                }
                                long taskId = task.getId();
                                String title = task.getTitle() != null && !task.getTitle().trim().isEmpty()
                                        ? task.getTitle().trim()
                                        : "Task #" + taskId;
                                taskTitleMap.put(taskId, title);
                                if (task.getAssigneeId() != null && !task.getAssigneeId().trim().isEmpty()) {
                                    taskAssigneeMap.put(taskId, task.getAssigneeId().trim());
                                }
                            }
                        }

                        fetchMemberProfileMap(projectId, new ProjectCallback<Map<String, UserProfile>>() {
                            @Override
                            public void onSuccess(Map<String, UserProfile> userProfileMap) {
                                loadProjectActivityFeed(projectId, taskTitleMap, taskAssigneeMap, userProfileMap,
                                        currentUserId, currentUserEmail, callback);
                            }

                            @Override
                            public void onError(String error) {
                                loadProjectActivityFeed(projectId, taskTitleMap, taskAssigneeMap, new HashMap<>(),
                                        currentUserId, currentUserEmail, callback);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        fetchMemberProfileMap(projectId, new ProjectCallback<Map<String, UserProfile>>() {
                            @Override
                            public void onSuccess(Map<String, UserProfile> userProfileMap) {
                                loadProjectActivityFeed(projectId, new HashMap<>(), new HashMap<>(), userProfileMap,
                                        currentUserId, currentUserEmail, callback);
                            }

                            @Override
                            public void onError(String error) {
                                loadProjectActivityFeed(projectId, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                                        currentUserId, currentUserEmail, callback);
                            }
                        });
                    }
                });
    }

    private void loadProjectActivityFeed(long projectId,
            Map<Long, String> taskTitleMap,
            Map<Long, String> taskAssigneeMap,
            Map<String, UserProfile> userProfileMap,
            String currentUserId,
            String currentUserEmail,
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

                        List<ProjectActivity> activities = response.body() != null ? response.body()
                                : new ArrayList<>();
                        List<ProjectHistoryItem> feed = mapProjectActivities(
                                activities,
                                taskTitleMap,
                                taskAssigneeMap,
                                userProfileMap,
                                currentUserId,
                                currentUserEmail);

                        loadCommentReactionFeed(taskTitleMap, userProfileMap, currentUserId,
                                new ProjectCallback<List<ProjectHistoryItem>>() {
                                    @Override
                                    public void onSuccess(List<ProjectHistoryItem> extraFeed) {
                                        if (extraFeed != null && !extraFeed.isEmpty()) {
                                            feed.addAll(extraFeed);
                                        }
                                        sortFeedByTimeDesc(feed);
                                        callback.onSuccess(feed);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        sortFeedByTimeDesc(feed);
                                        callback.onSuccess(feed);
                                    }
                                });
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
            Map<Long, String> taskAssigneeMap,
            Map<String, UserProfile> userProfileMap,
            String currentUserId,
            String currentUserEmail) {
        List<ProjectHistoryItem> result = new ArrayList<>();
        if (activities == null) {
            return result;
        }

        for (ProjectActivity activity : activities) {
            if (activity == null) {
                continue;
            }

            if (!isProjectHistoryRelevant(activity, taskAssigneeMap, currentUserId, currentUserEmail)) {
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

    private boolean isProjectHistoryRelevant(ProjectActivity activity,
            Map<Long, String> taskAssigneeMap,
            String currentUserId,
            String currentUserEmail) {
        if (activity == null) {
            return false;
        }

        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return true;
        }

        String actorId = activity.getUserId();
        if (currentUserId.equals(actorId)) {
            return true;
        }

        String action = normalize(activity.getActionType());
        String entity = normalize(activity.getEntityType());
        Long entityId = activity.getEntityId();

        // Always show member-join/member-add signals in project history.
        if (action.contains("member_joined") || action.contains("owner_joined") || action.contains("member_added")) {
            return true;
        }

        if (action.contains("INVITATION_SENT")) {
            String invitationValue = trimOrDash(activity.getNewValue());
            if (currentUserEmail != null && !currentUserEmail.trim().isEmpty()) {
                String emailPrefix = invitationValue.contains("|")
                        ? invitationValue.substring(0, invitationValue.indexOf('|')).trim()
                        : invitationValue.trim();
                if (currentUserEmail.equalsIgnoreCase(emailPrefix)) {
                    return true;
                }
            }
        }

        if (entity.contains("task") && entityId != null && taskAssigneeMap != null) {
            String assigneeId = taskAssigneeMap.get(entityId);
            if (currentUserId.equals(assigneeId)) {
                return true;
            }
        }

        if (action.contains("COMMENT")) {
            if (entityId != null && taskAssigneeMap != null) {
                String assigneeId = taskAssigneeMap.get(entityId);
                return currentUserId.equals(assigneeId);
            }
        }

        return false;
    }

    private void loadCommentReactionFeed(Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap,
            String currentUserId,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        List<ProjectHistoryItem> result = Collections.synchronizedList(new ArrayList<>());

        if (currentUserId == null || currentUserId.trim().isEmpty() || taskTitleMap == null || taskTitleMap.isEmpty()) {
            callback.onSuccess(result);
            return;
        }

        List<Long> taskIds = new ArrayList<>(taskTitleMap.keySet());
        AtomicInteger pendingTasks = new AtomicInteger(taskIds.size());

        if (pendingTasks.get() == 0) {
            callback.onSuccess(result);
            return;
        }

        for (Long taskId : taskIds) {
            taskApi.getCommentsByTask("eq." + taskId,
                    "eq.false",
                    "comment_id,task_id,user_id,content,created_at,is_deleted",
                    "created_at.desc").enqueue(new Callback<List<Comment>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Comment>> call,
                                @NonNull Response<List<Comment>> response) {
                            List<Comment> comments = response.isSuccessful() && response.body() != null
                                    ? response.body()
                                    : new ArrayList<>();

                            List<Comment> ownedComments = filterOwnedComments(comments, currentUserId);

                            if (ownedComments.isEmpty()) {
                                completeCommentReactionLoad(pendingTasks, result, callback);
                                return;
                            }

                            AtomicInteger pendingReactions = new AtomicInteger(ownedComments.size());
                            for (Comment ownedComment : ownedComments) {
                                Long commentId = ownedComment.getId();
                                if (commentId == null) {
                                    if (pendingReactions.decrementAndGet() == 0) {
                                        completeCommentReactionLoad(pendingTasks, result, callback);
                                    }
                                    continue;
                                }

                                taskApi.getCommentReactions("eq." + commentId, null, null)
                                        .enqueue(new Callback<List<CommentReaction>>() {
                                            @Override
                                            public void onResponse(@NonNull Call<List<CommentReaction>> call,
                                                    @NonNull Response<List<CommentReaction>> response) {
                                                List<CommentReaction> reactions = response.isSuccessful()
                                                        && response.body() != null
                                                                ? response.body()
                                                                : new ArrayList<>();
                                                for (CommentReaction reaction : reactions) {
                                                    if (!shouldIncludeCommentReaction(reaction, currentUserId)) {
                                                        continue;
                                                    }
                                                    result.add(buildCommentReactionHistoryItem(
                                                            taskId,
                                                            ownedComment,
                                                            reaction,
                                                            taskTitleMap,
                                                            userProfileMap));
                                                }

                                                if (pendingReactions.decrementAndGet() == 0) {
                                                    completeCommentReactionLoad(pendingTasks, result, callback);
                                                }
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<List<CommentReaction>> call,
                                                    @NonNull Throwable t) {
                                                if (pendingReactions.decrementAndGet() == 0) {
                                                    completeCommentReactionLoad(pendingTasks, result, callback);
                                                }
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                            completeCommentReactionLoad(pendingTasks, result, callback);
                        }
                    });
        }
    }

    private void completeCommentReactionLoad(AtomicInteger pendingTasks,
            List<ProjectHistoryItem> result,
            ProjectCallback<List<ProjectHistoryItem>> callback) {
        if (pendingTasks.decrementAndGet() <= 0) {
            callback.onSuccess(result);
        }
    }

    private List<Comment> filterOwnedComments(List<Comment> comments, String currentUserId) {
        List<Comment> ownedComments = new ArrayList<>();
        if (comments == null || currentUserId == null || currentUserId.trim().isEmpty()) {
            return ownedComments;
        }

        for (Comment comment : comments) {
            if (comment != null && currentUserId.equals(comment.getUserId())) {
                ownedComments.add(comment);
            }
        }
        return ownedComments;
    }

    private boolean shouldIncludeCommentReaction(CommentReaction reaction, String currentUserId) {
        return reaction != null
                && reaction.getUserId() != null
                && reaction.getReactionType() != null
                && !"DELETED".equalsIgnoreCase(reaction.getReactionType().trim())
                && (currentUserId == null || !currentUserId.equals(reaction.getUserId()));
    }

    private ProjectHistoryItem buildCommentReactionHistoryItem(Long taskId,
            Comment ownedComment,
            CommentReaction reaction,
            Map<Long, String> taskTitleMap,
            Map<String, UserProfile> userProfileMap) {
        ProjectHistoryItem item = new ProjectHistoryItem();
        item.setSource(ProjectHistoryItem.SOURCE_COMMENT);
        item.setActorId(reaction.getUserId());
        item.setActorName(resolveUserName(reaction.getUserId(), userProfileMap));
        item.setAvatarUrl(resolveAvatarUrl(reaction.getUserId(), userProfileMap));
        item.setActionLabel(buildCommentReactionActionLabel(reaction.getReactionType()));
        item.setTaskTitle(taskTitleMap.getOrDefault(taskId, "Task #" + taskId));
        item.setCommentContent(ownedComment != null ? ownedComment.getContent() : null);
        item.setDetail(buildCommentReactionDetail(reaction.getReactionType()));
        item.setCreatedAt(reaction.getCreatedAt());
        return item;
    }

    private String buildCommentReactionActionLabel(String reactionTypeRaw) {
        String reactionType = reactionTypeRaw != null ? reactionTypeRaw.trim().toUpperCase(Locale.US) : "";
        if (reactionType.contains("LIKE")) {
            return "đã thích bình luận của bạn";
        }
        if (reactionType.contains("LOVE")) {
            return "đã thả tim vào bình luận của bạn";
        }
        if (reactionType.contains("CELEBRATE")) {
            return "đã chúc mừng bình luận của bạn";
        }
        return "đã phản ứng với bình luận của bạn";
    }

    private String buildCommentReactionDetail(String reactionTypeRaw) {
        String reactionType = reactionTypeRaw != null ? reactionTypeRaw.trim().toUpperCase(Locale.US) : "";
        if (reactionType.contains("LIKE")) {
            return "👍";
        }
        if (reactionType.contains("LOVE")) {
            return "❤️";
        }
        if (reactionType.contains("CELEBRATE")) {
            return "🎉";
        }
        return "";
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

        // Comment activities
        if (action.contains("COMMENT_DELETE"))
            return "đã xoá bình luận";
        if (action.contains("COMMENT_UPDATE"))
            return "đã chỉnh sửa bình luận";
        if (action.contains("COMMENT"))
            return "đã bình luận";

        // Reaction activities
        if (action.contains("ADD_REACTION"))
            return "đã thêm phản ứng";
        if (action.contains("REMOVE_REACTION"))
            return "đã bỏ phản ứng";

        // Member activities
        if (action.contains("MEMBER_JOINED") || action.contains("MEMBER_ADDED") || action.contains("OWNER_JOINED")) {
            return "đã thêm thành viên";
        }
        if (action.contains("MEMBER_REMOVED") || action.contains("MEMBER_LEFT")) {
            return "đã xoá thành viên";
        }

        // Invitation activities
        if (action.contains("INVITATION_SENT"))
            return "đã gửi lời mời";

        // Create activities
        if ("CREATE".equals(action)) {
            if (entity.contains("project"))
                return "đã tạo dự án";
            if (entity.contains("task"))
                return "đã tạo công việc";
            return "đã tạo mới";
        }

        // Update status activity
        if ("UPDATE_STATUS".equals(action))
            return "đã thay đổi trạng thái";

        // Update activities
        if (action.startsWith("UPDATE")) {
            if (entity.contains("project"))
                return "đã cập nhật dự án";
            if (entity.contains("task"))
                return "đã cập nhật công việc";
            if (entity.contains("member"))
                return "đã cập nhật thành viên";
            return "đã cập nhật";
        }

        // Delete activities
        if (action.contains("DELETE") || action.contains("TRASH")) {
            if (entity.contains("project"))
                return "đã xoá dự án";
            if (entity.contains("task"))
                return "đã xoá công việc";
            return "đã xoá";
        }

        // Restore activities
        if (action.contains("RESTORE")) {
            if (entity.contains("task"))
                return "đã khôi phục công việc";
            if (entity.contains("project"))
                return "đã khôi phục dự án";
            return "đã khôi phục";
        }

        return "đã cập nhật";
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

    private String resolveUserName(String userId, Map<String, UserProfile> userProfileMap) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Unknown";
        }
        if (userProfileMap != null) {
            UserProfile profile = userProfileMap.get(userId);
            if (profile != null && profile.displayName != null && !profile.displayName.trim().isEmpty()) {
                return profile.displayName.trim();
            }
        }
        return "Unknown";
    }

    private String resolveAvatarUrl(String userId, Map<String, UserProfile> userProfileMap) {
        if (userId == null || userId.trim().isEmpty() || userProfileMap == null) {
            return null;
        }
        UserProfile profile = userProfileMap.get(userId);
        return profile != null ? profile.avatarUrl : null;
    }

    private String trimOrDash(String raw) {
        return raw != null && !raw.trim().isEmpty() ? raw.trim() : "-";
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
                            if (member == null || member.isRemoved()) {
                                continue;
                            }
                            if (member == null || member.getUserId() == null || member.getUserId().trim().isEmpty()) {
                                continue;
                            }
                            String userId = member.getUserId();
                            UserProfile profile = new UserProfile();
                            profile.displayName = "Unknown";
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
     * Create a new project.
     * Owner membership/activity side-effects are handled by DB triggers.
     */
    public void createProject(Project project, ProjectCallback<Project> callback) {
        String sessionUserId = SessionManager.getUserId();
        String accessToken = SessionManager.getAccessToken();

        if (sessionUserId == null || sessionUserId.trim().isEmpty()) {
            callback.onError("Session user không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("Thiếu access token. Vui lòng đăng nhập lại để tiếp tục.");
            return;
        }

        // Always use current authenticated user as owner to satisfy RLS
        // projects_insert_owner.
        String effectiveOwnerId = sessionUserId.trim();

        // Tạo DTO request (không bao gồm project_id)
        CreateProjectRequest request = new CreateProjectRequest()
                .setOwnerId(effectiveOwnerId)
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
                            createdProject.setUserRole("OWNER");
                            callback.onSuccess(createdProject);
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
     * Update an existing project
     */
    public void updateProject(long projectId, Project project, ProjectCallback<Project> callback) {
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        if (project.getName() != null)
            updates.put("project_name", project.getName());
        if (project.getDescription() != null)
            updates.put("description", project.getDescription());
        if (project.getColor() != null)
            updates.put("background_color", project.getColor());

        // Include is_private if needed. Since it's often passed in a new Project
        // object,
        // we check if it was explicitly set or just use it.
        // In current pattern, a new Project is created just for privacy update in
        // activity.
        if (project.isPrivate() || !updates.isEmpty()) {
            // If other fields are being updated, we might want to include privacy too
            // but to be safe for JUST privacy updates:
            // Let's check if the intent was only privacy.
            // However, Supabase PATCH only updates fields in the map.
        }

        // Actually, let's always put it if we are sure it's valid.
        // But better: only put it if we want to change it.
        // For now, let's just make sure it's available in the map when called from
        // Privacy update.
        // We'll add a check: if name/desc/color are null, but we have a boolean
        // value...
        // Wait, a better way is to check the called context.
        // Let's just add it if the object has it set.
        updates.put("is_private", project.isPrivate());

        projectApi.updateProject(
                "eq." + projectId,
                updates,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful()) {
                            // If it's successful but body is empty, it's still a success for Supabase PATCH
                            // usually
                            if (response.body() != null && !response.body().isEmpty()) {
                                callback.onSuccess(response.body().get(0));
                            } else {
                                // Return the input object as a fallback success result
                                callback.onSuccess(project);
                            }
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

    public void getDeletedOwnedProjects(String ownerId, ProjectCallback<List<Project>> callback) {
        projectApi.getDeletedOwnedProjects(
                "eq." + ownerId,
                "eq.true",
                "deleted_at.desc").enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError("Failed to load deleted projects: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    public void restoreProject(long projectId, ProjectCallback<Project> callback) {
        projectApi.restoreProject(
                "eq." + projectId,
                new ProjectApi.RestoreBody(),
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Project>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Project>> call,
                            @NonNull Response<List<Project>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError("Failed to restore project: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
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
