Tạo file .env với các key sau ở thư mục gốc của dự án để có thể hoạt động:
```.env
SUPABASE_URL=<project url>
SUPABASE_ANON_KEY=<anon key>
GOOGLE_WEB_CLIENT_ID=<google web client id>
GITHUB_REDIRECT_URI=taskai://auth-callback
GEMINI_API_KEY=<gemini api key>
```

Trong .gitignore, thêm dòng sau để tránh commit file .env:
.env
