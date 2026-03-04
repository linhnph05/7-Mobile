# Cấu hình Google Sign-In (OAuth) cho TaskFlow

## Tại sao cần làm bước này?

Khi user nhấn nút Google trong app, app sẽ tự động:
1. Hiện danh sách tài khoản Google trên máy
2. User chọn email → Google trả về ID token
3. App gửi ID token cho Supabase → Supabase xác thực → trả về session

Nhưng Google cần biết **app nào** đang yêu cầu đăng nhập → bạn phải đăng ký app 1 lần trên Google Cloud Console.

## Thông tin dự án

- **Package name**: `com.team7.taskflow`
- **SHA-1 (debug)**: `D1:53:B5:AE:39:C4:4A:26:E8:DF:6E:C9:F9:E6:0F:D0:66:CD:8E:7B`

---

## Bước 1: Tạo Google Cloud Project

1. Vào https://console.cloud.google.com/
2. Click **Select a project** → **New Project**
3. Đặt tên: `TaskFlow` → **Create**
4. Chọn project vừa tạo

## Bước 2: Bật API

1. Vào **APIs & Services** → **Library**
2. Tìm **Google Identity** hoặc **Google Sign-In**
3. Nếu thấy thì **Enable** (nếu không thấy thì bỏ qua, mặc định đã bật)

## Bước 3: Cấu hình OAuth Consent Screen

1. Vào **APIs & Services** → **OAuth consent screen**
2. Chọn **External** → **Create**
3. Điền:
   - App name: `TaskFlow`
   - User support email: email của bạn
   - Developer contact: email của bạn
4. **Save and Continue** qua các bước còn lại (không cần thêm scope)
5. **Publish App** (chuyển từ Testing sang Production, hoặc thêm email test)

## Bước 4: Tạo OAuth 2.0 Credentials

### 4a. Tạo Web Client ID (BẮT BUỘC - dùng cho Supabase)

1. Vào **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **OAuth client ID**
3. Application type: **Web application**
4. Name: `TaskFlow Web Client`
5. **Create** → **Copy Client ID** (dạng `xxx.apps.googleusercontent.com`)

### 4b. Tạo Android Client ID (BẮT BUỘC - để Google hiện account picker)

1. Click **+ CREATE CREDENTIALS** → **OAuth client ID**
2. Application type: **Android**
3. Name: `TaskFlow Android Debug`
4. Package name: `com.team7.taskflow`
5. SHA-1: `D1:53:B5:AE:39:C4:4A:26:E8:DF:6E:C9:F9:E6:0F:D0:66:CD:8E:7B`
6. **Create**

## Bước 5: Cấu hình Supabase

1. Vào https://supabase.com/dashboard → chọn project
2. **Authentication** → **Providers** → **Google**
3. Bật **Enable Google provider**
4. Paste **Web Client ID** và **Web Client Secret** (lấy từ bước 4a)
5. **Save**

## Bước 6: Thêm vào file .env

Mở file `.env` ở thư mục gốc dự án, thêm dòng:

```
GOOGLE_WEB_CLIENT_ID=paste-web-client-id-ở-đây.apps.googleusercontent.com
```

## Bước 7: Build lại app

```
.\gradlew.bat assembleDebug
```

Done! Nhấn nút Google trong app sẽ hiện danh sách email để chọn.

