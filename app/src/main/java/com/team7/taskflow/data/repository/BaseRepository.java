package com.team7.taskflow.data.repository;

import java.io.IOException;

import retrofit2.Response;

/**
 * Base class cho tất cả repository.
 *
 * Gom 3 helper dùng chung để tránh DRY:
 *  - getErrorMessage   : null-safe lấy message từ Throwable
 *  - buildApiError     : tạo chuỗi lỗi chi tiết từ Retrofit Response
 *  - formatErrorBody   : đọc error body dạng string
 */
public abstract class BaseRepository {

    /** Lấy error message an toàn — không bao giờ trả về null. */
    protected static String getErrorMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    /** Tạo chuỗi lỗi có HTTP code + body từ Retrofit Response. */
    protected static String buildApiError(String operation, Response<?> response) {
        if (response == null) {
            return operation + " failed: empty response";
        }
        String body = formatErrorBody(response).trim();
        if (body.isEmpty()) {
            return operation + " failed with HTTP " + response.code();
        }
        return operation + " failed with HTTP " + response.code() + ": " + body;
    }

    /** Đọc error body thành String; trả về "" nếu không có hoặc lỗi IO. */
    protected static String formatErrorBody(Response<?> response) {
        if (response == null || response.errorBody() == null) return "";
        try {
            String body = response.errorBody().string();
            return body != null ? body.trim() : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
