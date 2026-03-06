package com.team7.taskflow.utils;

/**
 * ⚠️ CẤU HÌNH DEBUG/TEST - DỄ DÀNG BẬT/TẮT
 * ==========================================
 *
 * File này chứa các cấu hình để test app mà không cần backend thật.
 *
 * TRƯỚC KHI COMMIT LÊN GITHUB:
 * 1. Set DEBUG_MODE = false
 * 2. Set USE_MOCK_AUTH = false
 * 3. Set USE_MOCK_DATA = false
 *
 * Hoặc thêm file này vào .gitignore nếu muốn giữ config local.
 */
public class AppConfig {

    // ============================================
    // 🔐 CHẾ ĐỘ XÁC THỰC
    // ============================================

    /**
     * Ứng dụng chỉ dùng Supabase Auth cho đăng nhập/đăng ký.
     */
    public static final boolean USE_SUPABASE_AUTH_ONLY = true;

    // ============================================
    // ⚠️ CÁC FLAG NÀY CẦN SET FALSE KHI RELEASE ⚠️
    // ============================================

    /**
     * Bật chế độ debug (hiển thị log, thông tin debug)
     */
    public static final boolean DEBUG_MODE = true;

    /**
     * Sử dụng mock authentication (đã bỏ - luôn dùng auth thật)
     * 
     * @deprecated MockAuth đã bị xóa, flag này không còn sử dụng
     */
    @Deprecated
    public static final boolean USE_MOCK_AUTH = false;

    /**
     * ⚠️ XÓA SESSION KHI MỞ APP (CHỈ DÙNG KHI DEV/TEST)
     * - true: Mỗi lần mở app sẽ xóa session → buộc đăng nhập lại (dùng để test)
     * - false: Giữ session bình thường
     *
     * ĐỔI THÀNH false KHI KHÔNG CẦN TEST NỮA
     */
    public static final boolean CLEAR_SESSION_ON_START = false;

    /**
     * Sử dụng mock data (dữ liệu giả thay vì gọi API)
     * - true: Hiển thị dữ liệu mẫu
     * - false: Gọi API Supabase để lấy dữ liệu thật
     */
    public static final boolean USE_MOCK_DATA = false;

    // ============================================
    // THÔNG TIN TEST ACCOUNT
    // ============================================

    /**
     * Email tài khoản test
     */
    public static final String TEST_EMAIL = "admin1@gmail.com";

    /**
     * Password tài khoản test (chỉ dùng cho mock)
     */
    public static final String TEST_PASSWORD = "123";

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Kiểm tra có đang ở chế độ test không
     */
    @SuppressWarnings("deprecation")
    public static boolean isTestMode() {
        return DEBUG_MODE && (USE_MOCK_AUTH || USE_MOCK_DATA);
    }

    /**
     * Log message nếu đang ở chế độ debug
     */
    public static void log(String tag, String message) {
        if (DEBUG_MODE) {
            android.util.Log.d(tag, message);
        }
    }
}
