Tạo file .env với nội dung sau ở thư mục gốc của dự án để có thể sign up:
SUPABASE_URL=https://<project ref>.supabase.co
SUPABASE_ANON_KEY=<anon key>

Trong gitignore, thêm dòng sau để tránh commit file .env:
.env
