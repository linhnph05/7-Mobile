package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class Attachment {
    @SerializedName("attachment_id")
    private transient Long id;

    @SerializedName("task_id")
    private long taskId;

    @SerializedName("uploader_id")
    private String uploaderId;

    @SerializedName("file_url")
    private String fileUrl;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("file_type")
    private String fileType;

    public Attachment(long taskId, String uploaderId, String fileUrl, String fileName, String fileType) {
        this.taskId = taskId;
        this.uploaderId = uploaderId;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }

    public String getUploaderId() { return uploaderId; }
    public void setUploaderId(String uploaderId) { this.uploaderId = uploaderId; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}
