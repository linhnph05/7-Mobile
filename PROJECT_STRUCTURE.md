# 📱 TaskFlow - Task Management App

## 🏗️ Kiến trúc: Clean Architecture + Feature-based

```
com.team7.taskflow/
bạnbạn├── data/                        # 📦 Lớp dữ liệu
│   ├── remote/                  # Supabase API, Config
│   │   └── api/                 # API interfaces
│   └── repository/              # Repository implementations
│
├── domain/                      # 💼 Business logic
│   └── model/                   # Data models (Project, Task, User)
│
├── ui/                          # 🎨 Giao diện (MỖI NGƯỜI 1 PACKAGE)
│   ├── dashboard/               # ✅ [Nghĩa] - Trang chủ
│   ├── project/                 # ✅ [Nghĩa] - Tạo/Sửa project
│   ├── notification/            # ✅ [Nghĩa] - Thông báo
│   ├── auth/                    # 🔲 [Đồng đội 1] - Login/Register
│   ├── task/                    # 🔲 [Đồng đội 2] - Task list/detail
│   └── common/                  # 🔲 [Chung] - Shared components
│
└── utils/                       # 🔧 Utilities (MockAuth, AppConfig)
```

## 👥 Phân công công việc

| Package | Owner | Màn hình | Status |
|---------|-------|----------|--------|
| `ui/dashboard` | Nghĩa | DashboardActivity, ProjectAdapter | ✅ Done |
| `ui/project` | Nghĩa | CreateProjectActivity, ProjectSettingActivity | ✅ Done |
| `ui/notification` | Nghĩa | NotificationsActivity | ✅ Done |
| `ui/auth` | Đồng đội 1 | LoginActivity, RegisterActivity | 🔲 Todo |
| `ui/task` | Đồng đội 2 | TaskListActivity, TaskDetailActivity, CreateTaskActivity | 🔲 Todo |
| `data/remote` | - | SupabaseConfig, SupabaseClient, API interfaces | ✅ Done |
| `data/repository` | - | ProjectRepository, TaskRepository | ✅ Done |

## 🔐 Mock Authentication (Để Test)

Khi cần test app mà chưa có tính năng Auth:

```java
// Trong AppConfig.java - set true để bật mock
public static final boolean USE_MOCK_AUTH = true;

// Tài khoản test
Email: admin1@gmail.com
Password: 123
```

⚠️ **TRƯỚC KHI COMMIT**: Set `USE_MOCK_AUTH = false` trong `AppConfig.java`

## 📁 Cấu trúc Layout (res/layout)

```
res/layout/
├── activity_dashboard.xml          # ✅ Trang chủ
├── activity_create_project.xml     # ✅ Tạo project
├── activity_project_setting.xml    # ✅ Cài đặt project
├── activity_notifications.xml      # ✅ Thông báo
├── activity_login.xml              # [TODO] Đăng nhập
├── item_project.xml                # ✅ Item trong danh sách project
├── item_task.xml                   # [TODO] Item task
└── activity_task_list.xml          # [TODO] Danh sách task
```

## 🚀 Hướng dẫn làm việc

### 1. Quy tắc đặt tên
- **Activity**: `[Feature]Activity.java` (ví dụ: `LoginActivity.java`)
- **Layout**: `activity_[feature].xml` hoặc `item_[name].xml`
- **Drawable**: `bg_[name].xml`, `ic_[name].xml`

### 2. Khi thêm Activity mới
1. Tạo file trong package tương ứng (ví dụ: `ui/auth/LoginActivity.java`)
2. Tạo layout trong `res/layout/`
3. Đăng ký trong `AndroidManifest.xml`

### 3. Dùng chung Model
- Tất cả dùng chung model trong `domain/model/`
- Không tạo model riêng trong package UI

### 4. Git workflow
- Mỗi người làm trong package của mình → ít conflict
- Tạo branch theo feature: `feature/auth`, `feature/task-list`

## 📝 Models có sẵn

### Project.java
```java
- id, name, description, color
- totalTasks, completedTasks
- createdAt, updatedAt
- notificationEnabled
```

### Task.java
```java
- id, projectId, title, description
- status (TODO, IN_PROGRESS, DONE)
- priority (LOW, MEDIUM, HIGH)
- dueDate, createdAt, updatedAt
```

## 🎨 Theme Colors
- Background: `#121629`
- Card: `#1A1F36`
- Input: `#1E2235`
- Primary: `#2945FF`
- Text: `#FFFFFF`
- Text Secondary: `#A0A0A0`
- Danger: `#EF4444`

---

## 📂 Cấu trúc thư mục RES

```
res/
├── drawable/                    # Icons & Backgrounds
│   ├── bg_*.xml                # Backgrounds (bg_search_bar, bg_input, bg_color_circle)
│   ├── ic_*.xml                # Icons (ic_back, ic_notification, ic_nav_*)
│   └── shape_*.xml             # Shapes
│
├── layout/                      # Layouts (QUY TẮC ĐẶT TÊN QUAN TRỌNG!)
│   ├── activity_*.xml          # Activity layouts
│   ├── fragment_*.xml          # Fragment layouts
│   ├── item_*.xml              # RecyclerView items
│   ├── dialog_*.xml            # Dialog layouts
│   └── view_*.xml              # Custom view layouts
│
├── menu/                        # Menu resources
│   └── bottom_nav_menu.xml     # Bottom navigation menu
│
└── values/                      # Value resources
    ├── colors.xml              # ✅ Màu sắc (đã tổ chức theo category)
    ├── dimens.xml              # ✅ Kích thước (spacing, radius, text size)
    ├── strings.xml             # ✅ Text (đã tổ chức theo feature)
    ├── styles.xml              # ✅ Styles cho components
    └── themes.xml              # App themes
```

### 📏 Quy tắc đặt tên trong RES

| Loại | Prefix | Ví dụ |
|------|--------|-------|
| Activity layout | `activity_` | `activity_dashboard.xml` |
| Fragment layout | `fragment_` | `fragment_task_list.xml` |
| Item layout | `item_` | `item_project.xml` |
| Dialog layout | `dialog_` | `dialog_create_task.xml` |
| Background | `bg_` | `bg_search_bar.xml` |
| Icon | `ic_` | `ic_notification.xml` |
| Shape | `shape_` | `shape_rounded_rect.xml` |
| Selector | `selector_` | `selector_button.xml` |

### 🎨 Sử dụng Design System

**Thay vì hardcode:**
```xml
<!-- ❌ KHÔNG NÊN -->
<Button
    android:backgroundTint="#2945FF"
    android:textColor="#FFFFFF"
    android:layout_height="50dp" />
```

**Dùng resources:**
```xml
<!-- ✅ NÊN -->
<Button
    style="@style/Button.Primary" />
```

### 📝 Sử dụng Strings

**Thay vì hardcode:**
```xml
<!-- ❌ KHÔNG NÊN -->
<TextView android:text="Create Project" />
```

**Dùng strings:**
```xml
<!-- ✅ NÊN -->
<TextView android:text="@string/project_create_title" />
```

### 📐 Sử dụng Dimens

**Thay vì hardcode:**
```xml
<!-- ❌ KHÔNG NÊN -->
<View android:padding="16dp" />
```

**Dùng dimens:**
```xml
<!-- ✅ NÊN -->
<View android:padding="@dimen/spacing_lg" />
```

---

## 📋 Checklist khi thêm màn hình mới

- [ ] Tạo Activity/Fragment trong đúng package (`ui/[feature]/`)
- [ ] Tạo layout file với prefix đúng (`activity_`, `fragment_`)
- [ ] Thêm strings vào `strings.xml` (theo section của feature)
- [ ] Dùng colors từ `colors.xml`
- [ ] Dùng dimens từ `dimens.xml`
- [ ] Dùng styles từ `styles.xml` nếu có
- [ ] Đăng ký Activity trong `AndroidManifest.xml`
