package com.team7.taskflow.data.repository;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.StorageApi;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.domain.model.User;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {
    private static final String TAG = "DEBUG_UPLOAD";
    
    // Tên Bucket trong Supabase Storage (BẠN PHẢI TẠO BUCKET NÀY TRÊN DASHBOARD)
    private static final String STORAGE_BUCKET = "avatars"; 
    
    private final UserApi userApi;
    private final StorageApi storageApi;

    public UserRepository() {
        this.userApi = SupabaseClient.getInstance().getService(UserApi.class);
        this.storageApi = SupabaseClient.getInstance().getStorageService(StorageApi.class);
    }

    public void getUserById(String userId, UserCallback callback) {
        userApi.getUserById("eq." + userId, "*").enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError("Profile not found");
                }
            }
            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateUserProfile(String userId, String email, String displayName, String bio, String avatarUrl, UpdateCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("user_id", userId);
        updates.put("email", email);
        if (displayName != null) updates.put("display_name", displayName);
        if (bio != null) updates.put("bio", bio);
        
        // Cập nhật link ảnh vào cột avatar_url trong database
        if (avatarUrl != null) updates.put("avatar_url", avatarUrl);
        
        updates.put("password_hash", "auth_managed"); 

        userApi.upsertUser(updates, "resolution=merge-duplicates").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Database update failed: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void uploadAvatar(String userId, Uri imageUri, ContentResolver contentResolver, UploadCallback callback) {
        Log.d(TAG, "Bắt đầu upload lên bucket: " + STORAGE_BUCKET);
        try {
            InputStream inputStream = contentResolver.openInputStream(imageUri);
            if (inputStream == null) {
                callback.onError("Could not open image");
                return;
            }

            byte[] bytes = getBytes(inputStream);
            // Tên file lưu trên storage
            String fileName = "profile_" + System.currentTimeMillis() + ".jpg";
            String path = userId + "/" + fileName;

            RequestBody requestBody = RequestBody.create(bytes, MediaType.parse("image/jpeg"));

            // Gọi API upload
            storageApi.uploadFile(STORAGE_BUCKET, path, requestBody).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        // Tạo Public URL để lưu vào database
                        String publicUrl = SupabaseConfig.SUPABASE_URL + "/storage/v1/object/public/" + STORAGE_BUCKET + "/" + path;
                        Log.d(TAG, "Upload THÀNH CÔNG. Public URL: " + publicUrl);
                        callback.onSuccess(publicUrl);
                    } else {
                        try {
                            String error = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                            Log.e(TAG, "LỖI SERVER: " + error);
                            callback.onError("Lỗi server: " + response.code() + ". Hãy đảm bảo đã tạo bucket '" + STORAGE_BUCKET + "'");
                        } catch (Exception e) {
                            callback.onError("Upload failed: " + response.code());
                        }
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Lỗi kết nối: " + t.getMessage());
                    callback.onError("Network error: " + t.getMessage());
                }
            });

        } catch (IOException e) {
            callback.onError(e.getMessage());
        }
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String message);
    }
    public interface UpdateCallback {
        void onSuccess();
        void onError(String message);
    }
    public interface UploadCallback {
        void onSuccess(String publicUrl);
        void onError(String message);
    }
}
