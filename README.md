Lấy project url và anon key từ Supabase và tạo file .env với nội dung sau ở thư mục gốc của dự án để có thể sign up:
```.env
SUPABASE_URL=<project url>
SUPABASE_ANON_KEY=<anon key>
```

Trong gitignore, thêm dòng sau để tránh commit file .env:
.env
