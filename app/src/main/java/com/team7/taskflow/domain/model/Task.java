package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class Task {

    @SerializedName("task_id")
    private long id;

    @SerializedName("project_id")
    private long projectId;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("status")
    private String status;

    @SerializedName("priority")
    private String priority;

    @SerializedName("position")
    private double position;

    @SerializedName("due_date")
    private String dueDate;

    @SerializedName("start_date")
    private String startDate;

    @SerializedName("assignee_id")
    private String assigneeId;

    @SerializedName("parent_task_id")
    private Long parentTaskId;

    @SerializedName("tag")
    private String tag;

    @SerializedName("created_at")
    private String createdAt;

    public Task() {
        this.status = "TODO";
        this.priority = "MEDIUM";
    }

    public Task(long projectId, String title) {
        this.projectId = projectId;
        this.title = title;
        this.status = "TODO";
        this.priority = "MEDIUM";
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getProjectId() { return projectId; }
    public void setProjectId(long projectId) { this.projectId = projectId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public double getPosition() { return position; }
    public void setPosition(double position) { this.position = position; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public Long getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Long parentTaskId) { this.parentTaskId = parentTaskId; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
