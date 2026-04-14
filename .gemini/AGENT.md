# 🤖 AGENT.md — TaskFlow (7-Mobile) Project Rules

> **File này được AI đọc tự động mỗi khi bạn gõ prompt.**  
> Mọi quy tắc ghi ở đây sẽ ảnh hưởng đến cách AI sinh code cho toàn dự án.

---

## 1. Tổng quan dự án

- **Tên**: TaskFlow — Ứng dụng quản lý tác vụ và dự án dành cho nhóm.
- **Platform**: Android (Java, minSdk 26, targetSdk 35).
- **Backend**: Supabase (PostgreSQL REST API). Credentials nằm trong `BuildConfig`, KHÔNG BAO GIỜ viết cứng URL/Key trong code.
- **Architecture**: Clean Architecture — `data/ → domain/ → ui/`. Mỗi feature là một package riêng trong `ui/`.

---

## 2. Nguyên tắc code — Sạch, dễ sửa, dễ test

### 2.1 Repository Pattern (Bắt buộc)
- **Mọi** thao tác với Supabase REST API phải đi qua một class `Repository` trong `data/repository/`.
- Activity / Fragment **KHÔNG ĐƯỢC** gọi trực tiếp HTTP. Luôn đi qua Repository.
- Repository chỉ lo lấy/gửi dữ liệu. KHÔNG chứa logic UI hay quản lý session.

### 2.2 Single Responsibility — Mỗi class/method chỉ có 1 nhiệm vụ
- Activity → hiển thị UI, lắng nghe sự kiện.
- Repository → giao tiếp API.
- Model (`domain/model/`) → chứa dữ liệu, không có logic phụ thuộc Android.
- Tránh để 1 hàm dài hơn 40 dòng. Nếu dài hơn, tách thành nhiều hàm con rõ ràng.

### 2.3 Xử lý bất đồng bộ
- Mọi cuộc gọi mạng phải chạy ngoài Main Thread (dùng `ExecutorService`, `CompletableFuture`, hoặc `Thread`).
- Khi cập nhật UI xong, **phải** dùng `runOnUiThread()` hoặc `Handler(Looper.getMainLooper())`.
- Luôn xử lý lỗi (`onError`) — hiện `Toast` hoặc `Snackbar` thân thiện, KHÔNG để app crash.

### 2.4 Kiểu dữ liệu phải khớp DB
- `project_id`, `task_id` → dùng kiểu `long` (bigint trong DB).
- `user_id` → dùng kiểu `String` (UUID trong DB).
- KHÔNG ĐƯỢC nhầm lẫn kiểu dữ liệu giữa client và DB.

### 2.5 Dễ test
- Khi viết Repository: thiết kế hàm có thể inject callback (`onSuccess`, `onError`) để mock dễ dàng.
- Khi viết logic: tách logic thuần (nhận đầu vào, trả đầu ra) ra khỏi Android framework để viết unit test được.
- KHÔNG đặt business logic phức tạp trong Activity.

---

## 3. Giao diện (UI) — Chuyên nghiệp, Tối giản

### 3.1 Màu sắc — KHÔNG lòe loẹt
- **TUYỆT ĐỐI KHÔNG** hardcode hex color trong XML layout (ví dụ: `android:textColor="#FF0000"`).
- **BẮT BUỘC** dùng các tên màu ngữ nghĩa (semantic) đã định nghĩa trong `colors.xml`:
  - Nền: `@color/theme_background`, `@color/theme_surface`, `@color/theme_surface_variant`
  - Chữ: `@color/theme_text_primary`, `@color/theme_text_secondary`, `@color/theme_text_hint`
  - Viền: `@color/theme_border`, `@color/theme_divider`
  - Brand: `@color/primary`, `@color/primary_dark`
  - Trạng thái: `@color/success`, `@color/warning`, `@color/danger`
- Phong cách chung: trung tính, tối giản, dùng **viền nhẹ** và **nền trắng/xám nhạt** thay vì tô nền rực rỡ.
- Tránh dùng emoji (🚩 🏁 🏳️) làm icon — dùng vector drawable (`ic_flag.xml`, `ic_label.xml`...).
- Khi highlight trạng thái đã chọn: **chỉ đổi `textColor`** sang đậm hơn, KHÔNG tô nền lòe loẹt.

### 3.2 Material Design 3
- Ưu tiên dùng component Material3: `MaterialButton`, `ShapeableImageView`, `BottomSheetDialog`.
- Bo góc dùng `app:cornerRadius` trên Material component thay vì tạo hàng chục file `shape.xml` riêng lẻ.
- Dialog chọn (Priority, Assignee, Tag, Date) → dùng `BottomSheetDialog` kéo từ dưới lên, KHÔNG dùng `PopupMenu` mặc định xấu của Android.

### 3.3 Tools preview
- Luôn dùng `tools:text`, `tools:visibility`, `tools:listitem` trong XML layout để preview được trên Android Studio.

### 3.4 Kích thước
- Dùng `dp` cho padding/margin/size, `sp` cho text.
- Ưu tiên `wrap_content` / `match_parent` + constraints, tránh hardcode chiều cao cố định trừ khi bắt buộc.
- Tham khảo `dimens.xml` trước khi đặt giá trị mới:
  - `@dimen/spacing_sm` (4dp), `@dimen/spacing_md` (8dp), `@dimen/spacing_lg` (16dp), `@dimen/spacing_xl` (24dp)

---

## 4. Chuỗi văn bản — Chuẩn bị cho đa ngôn ngữ (i18n)

### 4.1 KHÔNG BAO GIỜ viết cứng text trong XML hoặc Java
```xml
<!-- ❌ SAI — hardcode tiếng Việt thẳng vào layout -->
<TextView android:text="Tạo tác vụ" />
<Button android:text="Lưu" />
Toast.makeText(this, "Đã lưu thành công!", ...);

<!-- ✅ ĐÚNG — dùng string resource -->
<TextView android:text="@string/task_create_title" />
<Button android:text="@string/save" />
Toast.makeText(this, getString(R.string.task_saved_success), ...);
```

### 4.2 Cấu trúc strings.xml
- File `strings.xml` được chia theo **feature section** (COMMON, DASHBOARD, AUTH, TASK, ...).
- Khi thêm chuỗi mới: đặt vào đúng section tương ứng.
- Đặt tên key theo format: `[feature]_[element]_[purpose]`
  - Ví dụ: `task_title_hint`, `project_create_button`, `auth_login_title`

### 4.3 Chuẩn bị cho chuyển đổi Anh ↔ Việt
- Hiện tại `strings.xml` mặc định đang là **tiếng Anh**.
- Khi cần hỗ trợ tiếng Việt: tạo file `res/values-vi/strings.xml` với cùng key nhưng value tiếng Việt.
- **Quy tắc**: Mọi text người dùng sẽ đọc đều phải nằm trong `strings.xml`. Bao gồm:
  - Label, Hint, Button text
  - Toast messages, Dialog title/content
  - Error messages
- **Ngoại lệ**: Log messages và debug text có thể viết thẳng tiếng Anh trong Java.

---

## 5. Kiến trúc Supabase — Lean Client

### 5.1 Fire-and-Forget cho các side-effect
- **KHÔNG viết code Android** để tự tay insert vào các bảng:
  - `notifications` → DB trigger tự tạo khi task thay đổi.
  - `task_activities` → DB trigger tự log khi thay đổi trạng thái/thông tin task.
  - `project_members` (cho owner) → DB trigger tự gán owner khi tạo project.
- Client chỉ cần INSERT/UPDATE vào bảng chính (`tasks`, `projects`), phần còn lại DB lo.

### 5.2 Constraints — DB là gatekeeper cuối cùng
- Client nên kiểm tra cơ bản cho UX (ví dụ: title không rỗng, start_date < due_date).
- Nhưng không cần replicate toàn bộ logic constraint — DB sẽ reject nếu sai.

### 5.3 Security
- Credentials từ `BuildConfig` → `SupabaseConfig`.
- JWT token lưu trong `SharedPreferences` qua `SessionManager`.
- KHÔNG log hoặc hiển thị token ra giao diện.

---

## 6. Quy tắc đặt tên

| Loại | Format | Ví dụ |
|------|--------|-------|
| Activity | `[Feature]Activity.java` | `AiCreateActivity.java` |
| Layout - Activity | `activity_[feature].xml` | `activity_ai_create.xml` |
| Layout - Item | `item_[name].xml` | `item_project.xml` |
| Layout - Dialog | `dialog_[name].xml` | `dialog_priority_picker.xml` |
| Drawable - Background | `bg_[name].xml` | `bg_chip_neutral.xml` |
| Drawable - Icon | `ic_[name].xml` | `ic_attach_file.xml` |
| String key | `[feature]_[element]_[purpose]` | `task_title_hint` |
| Color name | `theme_[purpose]` (semantic) | `theme_text_primary` |
| Repository | `[Feature]Repository.java` | `TaskRepository.java` |
| Model | `[Name].java` (trong `domain/model/`) | `Task.java`, `Project.java` |

---

## 7. Git Workflow

- Mỗi người làm trong package riêng → ít conflict.
- Branch theo feature: `feature/auth`, `feature/task-list`, `feature/ai-create`.
- **Trước khi commit**: set `USE_MOCK_AUTH = false` trong `AppConfig.java`.
- Không commit file build (`*.apk`, `build/`, `.gradle/`).

---

## 8. Checklist khi thêm màn hình mới

- [ ] Tạo Activity trong đúng package `ui/[feature]/`
- [ ] Tạo layout XML với prefix đúng (`activity_`, `dialog_`, `item_`)
- [ ] Mọi text hiển thị → thêm vào `strings.xml` (đúng section)
- [ ] Mọi màu dùng → lấy từ `colors.xml` (tên semantic `theme_*`)
- [ ] Mọi khoảng cách → tham khảo `dimens.xml`
- [ ] Style component → tham khảo `styles.xml` trước khi viết mới
- [ ] Đăng ký Activity trong `AndroidManifest.xml`
- [ ] Nếu cần gọi API → tạo/dùng Repository trong `data/repository/`
- [ ] Nếu cần model mới → tạo trong `domain/model/`
- [ ] Preview layout bằng `tools:text`, `tools:visibility`

---

## 10. Debugging & Error Tracking — "Logcat First"

### 10.1 Quy trình khi gặp lỗi (Crash / Bug)
1. **Mở Logcat**: Filter theo package `com.team7.taskflow` và mức độ `Error`.
2. **Tìm "Caused by"**: Đây là dòng chứa nguyên nhân gốc rễ.
3. **Click link màu xanh**: Android Studio sẽ đưa bạn đến đúng dòng code bị lỗi.
4. **Báo cáo cho AI**: Khi hỏi, hãy copy đoạn stack trace từ dòng `FATAL EXCEPTION` đến hết khối `Caused by` đầu tiên.

### 10.2 Tiêu chuẩn Logging
- Dùng `Log.e(TAG, "Message", e)` để log lỗi kèm theo Exception (giúp hiện đủ stack trace).
- Định nghĩa `private static final String TAG = "FeatureNameActivity";` ở đầu mỗi class.
- KHÔNG để lại `System.out.println()` trong code.

---

## 11. Lưu ý quan trọng cho AI assistant

1. **Trả lời bằng tiếng Việt** trừ khi user hỏi bằng tiếng Anh.
2. **Ưu tiên sửa ít file nhất** có thể — tránh refactor cả dự án khi chỉ cần thay đổi nhỏ.
3. Khi sửa giao diện: **KHÔNG tô màu lòe loẹt**. Dùng tone trung tính (xám, trắng, viền nhẹ). Chỉ dùng màu nhấn (primary, danger, success) cho trạng thái quan trọng.
4. Khi thêm text mới: **PHẢI thêm vào `strings.xml`** thay vì viết cứng trong XML/Java. Đây là chuẩn bị cho tính năng chuyển đổi Anh-Việt trong tương lai.
5. Khi build: dùng `.\gradlew.bat assembleDebug` trên Windows để verify.
6. **KHÔNG tạo file thừa** — trước khi tạo drawable/style mới, kiểm tra xem đã có file tương tự chưa.
7. Tôn trọng kiến trúc hiện tại: `data/` → `domain/` → `ui/`. KHÔNG đặt logic API trong Activity.
8. **Hướng dẫn User**: Khi gặp lỗi Crash, hãy hướng dẫn User cách dùng Logcat để lấy stack trace thay vì tự đoán mò.
9. **Sự nhất quán giao diện (UI Consistency)**: Khi chỉnh sửa màn hình Profile hoặc Cài đặt, PHẢI dùng phong cách: CardView bao bọc các hàng (Rows), Icon bo góc có màu nền nhẹ, và mũi tên chỉ hướng (chevron). Tránh dùng các nút bấm (Button) thô cứng cho điều hướng.
10. **Thông tin thực tế (Metadata)**: Ưu tiên hiển thị thông tin từ DB (như "Ngày tham gia") thay vì chỉ hiện số phiên bản (Version).
11. **Đa ngôn ngữ & Chế độ tối**: Mọi UI mới phải check kỹ ở cả Light/Dark Mode và Anh-Việt.
