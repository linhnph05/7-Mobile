# 📋 NGHĨA - DANH SÁCH CÔNG VIỆC CHI TIẾT

> **Cập nhật:** 26/02/2026  
> **Package:** `com.team7.taskflow`

---

## 📊 TỔNG QUAN

| Nhóm | Chức năng | Độ ưu tiên | Trạng thái |
|------|-----------|------------|------------|
| 3 | Quản lý Dự án/Nhóm việc | 🔴 Cao | 🟡 Đang làm |
| 10 | Thông báo | 🟠 Trung bình | 🔲 Chưa làm |
| 16 | AI - Smart Date Parsing | 🟢 Thấp | 🔲 Chưa làm |

---

## 📁 NHÓM 3: QUẢN LÝ DỰ ÁN/NHÓM VIỆC

### ✅ 3.1 Tạo mới Dự án (Project) và Bảng công việc (Board)
**File:** `ui/project/CreateProjectActivity.java`, `activity_create_project.xml`

| Task | Chi tiết | Status |
|------|----------|--------|
| 3.1.1 | Giao diện tạo project (tên, mô tả, key, màu, template) | ✅ Done |
| 3.1.2 | Validate input (tên không trống, key unique) | 🔲 Todo |
| 3.1.3 | Gọi API tạo project vào Supabase | 🔲 Todo |
| 3.1.4 | Tự động thêm user vào project_members với role OWNER | 🔲 Todo |
| 3.1.5 | Tạo Board mặc định (Kanban/Scrum/Table) | 🔲 Todo |
| 3.1.6 | Hiển thị loading và thông báo thành công/lỗi | 🔲 Todo |

**API cần gọi:**
```
POST /projects
POST /project_members (owner)
```

---

### ✅ 3.2 Chỉnh sửa thông tin dự án (Settings)
**File:** `ui/project/ProjectSettingActivity.java`, `activity_project_setting.xml`

| Task | Chi tiết | Status |
|------|----------|--------|
| 3.2.1 | Giao diện settings (tên, mô tả, project key) | ✅ Done |
| 3.2.2 | Load thông tin project từ API | 🔲 Todo |
| 3.2.3 | Cho phép sửa thông tin | 🔲 Todo |
| 3.2.4 | Validate project key unique | 🔲 Todo |
| 3.2.5 | Gọi API cập nhật project | 🔲 Todo |
| 3.2.6 | Chỉ cho phép OWNER/ADMIN sửa | 🔲 Todo |

**API cần gọi:**
```
GET /projects?project_id=eq.{id}
PATCH /projects?project_id=eq.{id}
```

---

### ✅ 3.3 Xóa hoặc khôi phục dự án
**File:** `ui/project/ProjectSettingActivity.java`

| Task | Chi tiết | Status |
|------|----------|--------|
| 3.3.1 | Nút xóa project (soft delete) | 🔲 Todo |
| 3.3.2 | Dialog xác nhận xóa | 🔲 Todo |
| 3.3.3 | Gọi API soft delete (is_deleted = true) | 🔲 Todo |
| 3.3.4 | Màn hình Thùng rác (Trash) | 🔲 Todo |
| 3.3.5 | Khôi phục project từ thùng rác | 🔲 Todo |
| 3.3.6 | Hard delete sau 30 ngày (optional) | 🔲 Todo |

**API cần gọi:**
```
PATCH /projects?project_id=eq.{id} {is_deleted: true, deleted_at: now()}
PATCH /projects?project_id=eq.{id} {is_deleted: false, deleted_at: null}
DELETE /projects?project_id=eq.{id} (hard delete)
```

---

### ✅ 3.4 Hiển thị danh sách dự án (List/Grid)
**File:** `ui/dashboard/DashboardActivity.java`, `ProjectAdapter.java`

| Task | Chi tiết | Status |
|------|----------|--------|
| 3.4.1 | Giao diện Dashboard với RecyclerView | ✅ Done |
| 3.4.2 | Load projects từ API (owner + member) | ✅ Done |
| 3.4.3 | Hiển thị dạng Grid (2 cột) | ✅ Done |
| 3.4.4 | Thêm toggle chuyển Grid ↔ List | 🔲 Todo |
| 3.4.5 | Hiển thị dạng List (1 cột) | 🔲 Todo |
| 3.4.6 | Lưu preference người dùng (Grid/List) | 🔲 Todo |
| 3.4.7 | Pull-to-refresh | 🔲 Todo |
| 3.4.8 | Hiển thị tiến độ (x/y tasks completed) | ✅ Done |
| 3.4.9 | Hiển thị màu sắc project | ✅ Done |

---

## 🔔 NHÓM 10: THÔNG BÁO

### ✅ 10.1 Push Notification khi được assign Task
**File:** `service/NotificationService.java`, `data/remote/api/NotificationApi.java`

| Task | Chi tiết | Status |
|------|----------|--------|
| 10.1.1 | Tạo NotificationService | 🔲 Todo |
| 10.1.2 | Đăng ký FCM (Firebase Cloud Messaging) | 🔲 Todo |
| 10.1.3 | Lưu FCM token vào Supabase (bảng users hoặc devices) | 🔲 Todo |
| 10.1.4 | Tạo Supabase Edge Function gửi notification | 🔲 Todo |
| 10.1.5 | Trigger khi INSERT vào tasks với assignee_id | 🔲 Todo |
| 10.1.6 | Hiển thị notification với title, body, icon | 🔲 Todo |
| 10.1.7 | Click notification → mở TaskDetailActivity | 🔲 Todo |

**Cần thêm:**
- Firebase SDK
- FCM token table trong Supabase
- Edge Function để gửi push

---

### ✅ 10.2 Thông báo nhắc nhở Deadline (Local Notification)
**File:** `receiver/DeadlineReminderReceiver.java`, `utils/AlarmHelper.java`

| Task | Chi tiết | Status |
|------|----------|--------|
| 10.2.1 | Tạo BroadcastReceiver cho alarm | 🔲 Todo |
| 10.2.2 | Tạo AlarmHelper để schedule/cancel alarm | 🔲 Todo |
| 10.2.3 | Schedule alarm khi tạo/sửa task có deadline | 🔲 Todo |
| 10.2.4 | Hiển thị local notification | 🔲 Todo |
| 10.2.5 | Cài đặt thời gian nhắc (15p, 1h, 1 ngày trước) | 🔲 Todo |
| 10.2.6 | Cancel alarm khi task hoàn thành/xóa | 🔲 Todo |
| 10.2.7 | Reschedule alarms khi app restart | 🔲 Todo |

**AndroidManifest cần thêm:**
```xml
<receiver android:name=".receiver.DeadlineReminderReceiver" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

### ✅ 10.3 Thông báo khi có bình luận
**File:** Tương tự 10.1 - dùng FCM

| Task | Chi tiết | Status |
|------|----------|--------|
| 10.3.1 | Trigger khi INSERT vào comments | 🔲 Todo |
| 10.3.2 | Lấy danh sách user liên quan (assignee, owner, watchers) | 🔲 Todo |
| 10.3.3 | Gửi push notification đến các user đó | 🔲 Todo |
| 10.3.4 | Không gửi cho chính người comment | 🔲 Todo |

---

### ✅ 10.4 Màn hình Notification Center
**File:** `ui/notification/NotificationsActivity.java`, `activity_notifications.xml`

| Task | Chi tiết | Status |
|------|----------|--------|
| 10.4.1 | Giao diện Notification Center | ✅ Done |
| 10.4.2 | Tạo bảng notifications trong Supabase | 🔲 Todo |
| 10.4.3 | API lấy danh sách notifications | 🔲 Todo |
| 10.4.4 | RecyclerView hiển thị notifications | 🔲 Todo |
| 10.4.5 | Item: avatar, tên, hành động, thời gian | 🔲 Todo |
| 10.4.6 | Filter: All types, @me, Comment, Join request | ✅ Done |
| 10.4.7 | Filter: Unread only | ✅ Done |
| 10.4.8 | Mark as read (single/all) | 🔲 Todo |
| 10.4.9 | Click notification → navigate đến task/project | 🔲 Todo |
| 10.4.10 | Nút Settings (cài đặt thông báo) | 🔲 Todo |

**Database schema cần thêm:**
```sql
CREATE TABLE notifications (
    notification_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id uuid REFERENCES users(user_id),
    type text NOT NULL, -- 'ASSIGN', 'COMMENT', 'DEADLINE', 'JOIN_REQUEST'
    title text,
    body text,
    data jsonb, -- {task_id, project_id, comment_id, etc.}
    is_read boolean DEFAULT false,
    created_at timestamptz DEFAULT now()
);
```

---

## 🤖 NHÓM 16: AI - SMART DATE PARSING

### ✅ 16.1 Smart Date Parsing (NLP)
**File:** `ui/task/QuickAddTaskBottomSheet.java`, `utils/SmartDateParser.java`

| Task | Chi tiết | Status |
|------|----------|--------|
| 16.1.1 | Giao diện Bottom Sheet Quick Add | ✅ Done |
| 16.1.2 | Tạo SmartDateParser class | 🔲 Todo |
| 16.1.3 | Parse text tiếng Việt (hôm nay, ngày mai, thứ 6, tuần sau) | 🔲 Todo |
| 16.1.4 | Parse text tiếng Anh (today, tomorrow, next week) | 🔲 Todo |
| 16.1.5 | Parse giờ (9h sáng, 2pm, 14:00) | 🔲 Todo |
| 16.1.6 | Hiển thị chip deadline được parse | 🔲 Todo |
| 16.1.7 | Cho phép user sửa nếu parse sai | 🔲 Todo |
| 16.1.8 | Parse hashtag → project (#work, #personal) | 🔲 Todo |
| 16.1.9 | Parse mention → assignee (@john, @nghia) | 🔲 Todo |
| 16.1.10 | Parse priority (!high, !urgent) | 🔲 Todo |

**Ví dụ input/output:**
```
Input:  "Nộp báo cáo vào thứ 6 tuần sau lúc 9h sáng #work @john"
Output: {
    title: "Nộp báo cáo",
    deadline: "2026-03-06T09:00:00",
    project: "work",
    assignee: "john",
    priority: null
}
```

**Thư viện có thể dùng:**
- Regex patterns cho tiếng Việt
- Google ML Kit (nếu cần NLP nâng cao)
- Hoặc gọi API AI (ChatGPT, Gemini)

---

## 📁 CẤU TRÚC FILE CẦN TẠO MỚI

```
app/src/main/java/com/team7/taskflow/
├── receiver/
│   └── DeadlineReminderReceiver.java      # [10.2] Local notification
│
├── service/
│   └── FCMService.java                     # [10.1] Firebase messaging
│
├── utils/
│   ├── AlarmHelper.java                    # [10.2] Schedule alarms
│   ├── NotificationHelper.java             # [10.x] Create notifications
│   └── SmartDateParser.java                # [16.1] NLP date parsing
│
├── ui/
│   ├── task/
│   │   └── QuickAddTaskBottomSheet.java    # [16.1] Bottom sheet
│   └── notification/
│       ├── NotificationsActivity.java      # ✅ Done
│       └── NotificationAdapter.java        # [10.4] RecyclerView adapter
│
└── data/
    └── remote/api/
        └── NotificationApi.java            # [10.4] API interface
```

---

## 🗓️ ĐỀ XUẤT THỨ TỰ THỰC HIỆN

### Phase 1: Hoàn thiện Quản lý Dự án (Tuần 1)
1. ✅ Dashboard hiển thị projects - DONE
2. 🔲 Tạo project mới (API call)
3. 🔲 Sửa project settings
4. 🔲 Xóa/khôi phục project
5. 🔲 Toggle Grid/List view

### Phase 2: Notification Center (Tuần 2)
1. 🔲 Tạo bảng notifications trong Supabase
2. 🔲 API lấy notifications
3. 🔲 RecyclerView + Adapter
4. 🔲 Mark as read

### Phase 3: Local Notifications (Tuần 3)
1. 🔲 DeadlineReminderReceiver
2. 🔲 AlarmHelper
3. 🔲 Schedule/Cancel alarms

### Phase 4: Push Notifications (Tuần 4)
1. 🔲 Setup Firebase
2. 🔲 FCMService
3. 🔲 Supabase Edge Functions

### Phase 5: Smart Date Parsing (Tuần 5)
1. 🔲 SmartDateParser class
2. 🔲 QuickAddTaskBottomSheet
3. 🔲 Parse tiếng Việt/Anh

---

## 📝 GHI CHÚ

### Dependencies cần thêm:
```kotlin
// Firebase (cho Push Notification)
implementation("com.google.firebase:firebase-messaging:23.4.0")

// WorkManager (cho background tasks)
implementation("androidx.work:work-runtime:2.9.0")
```

### Permissions cần thêm (AndroidManifest.xml):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

> **Tổng cộng:** ~50 tasks  
> **Ưu tiên:** Nhóm 3 > Nhóm 10 > Nhóm 16

