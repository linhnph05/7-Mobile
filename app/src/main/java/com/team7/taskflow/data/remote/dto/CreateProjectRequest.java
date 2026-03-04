package com.team7.taskflow.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO (Data Transfer Object) để tạo project mới
 * Không bao gồm project_id vì nó được tự động sinh bởi database
 */
public class CreateProjectRequest {

    @SerializedName("owner_id")
    private String ownerId;

    @SerializedName("project_name")
    private String projectName;

    @SerializedName("description")
    private String description;

    @SerializedName("project_key")
    private String projectKey;

    @SerializedName("background_color")
    private String backgroundColor;

    @SerializedName("is_private")
    private boolean isPrivate;

    public CreateProjectRequest() {}

    // Builder pattern
    public CreateProjectRequest setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public CreateProjectRequest setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public CreateProjectRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public CreateProjectRequest setProjectKey(String projectKey) {
        this.projectKey = projectKey;
        return this;
    }

    public CreateProjectRequest setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public CreateProjectRequest setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
        return this;
    }

    // Getters
    public String getOwnerId() { return ownerId; }
    public String getProjectName() { return projectName; }
    public String getDescription() { return description; }
    public String getProjectKey() { return projectKey; }
    public String getBackgroundColor() { return backgroundColor; }
    public boolean isPrivate() { return isPrivate; }
}

