-- ========================================================================================
-- SCRIPT ĐỒNG BỘ AUTH.USERS VÀ PUBLIC.USERS
-- Lưu ý quan trọng: Nếu bạn chuyển Primary Key sang UUID, hệ thống sẽ DÙNG CHUỖI KÝ TỰ NGẪU NHIÊN 
-- (VD: 550e8400-e29b-41d4-a716-446655440000), bạn SẼ KHÔNG THỂ có các ID kiểu đếm số (1, 2, 3...) nối tiếp như cũ.
-- ========================================================================================

-- 1. XOÁ DỮ LIỆU CŨ BỊ LỖI VÀ GẮN RÀNG BUỘC CHO CHÍNH XÁC (Trỏ foreign key về auth.users)
-- (Lỗi bạn vừa gặp là do trong bảng public.users đang có những user ảo (ví dụ ID 0000...01) KHÔNG HỀ tồn tại ở bảng auth.users
-- nên khi tạo Foreign Key nó dội lại lỗi. Lệnh dưới đây sẽ dọn dẹp các user rác đó trước khi gắn khóa).

-- Xóa những user cũ trong public.users mà ID của chúng không nằm trong auth.users
DELETE FROM public.users 
WHERE user_id NOT IN (SELECT id FROM auth.users);

ALTER TABLE public.users 
    DROP CONSTRAINT IF EXISTS users_auth_fkey;

ALTER TABLE public.users 
    ADD CONSTRAINT users_auth_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


-- 2. TẠO FUNCTION TRIGGER XỬ LÝ ĐỒNG BỘ
-- Function này sẽ tự động được gọi mỗi khi có 1 user mới đăng ký thành công bên tài khoản Auth.
CREATE OR REPLACE FUNCTION public.handle_new_user() 
RETURNS trigger 
LANGUAGE plpgsql 
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  -- Insert dữ liệu sang bảng public.users
  INSERT INTO public.users (user_id, email, password_hash, display_name, avatar_url)
  VALUES (
    NEW.id, -- Lấy UUID từ bảng hệ thống Auth
    NEW.email,
    'Google_OAuth', -- Bypass password hash cho người dùng đăng nhập bằng Google
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.email), -- Nếu không có tên thì lấy tạm email
    NEW.raw_user_meta_data->>'avatar_url'
  )
  ON CONFLICT (email) DO UPDATE 
  SET 
    user_id = EXCLUDED.user_id, 
    display_name = COALESCE(public.users.display_name, EXCLUDED.display_name),
    avatar_url = COALESCE(public.users.avatar_url, EXCLUDED.avatar_url);
    
  RETURN NEW;
END;
$$;


-- 3. GẮN TRIGGER VÀO BẢNG AUTH.USERS
-- Trước khi tạo thì xóa trigger cũ đi (nếu có) để tránh lặp trùng
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

-- Tạo Trigger chạy hàm AFTER INSERT (Sau khi tạo user thành công trên Auth)
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();


-- 4. COPY NỐT DỮ LIỆU NHỮNG ACC GOOGLE ĐÃ LỠ ĐĂNG NHẬP TRƯỚC KHI CÓ TRIGGER
-- Chạy lệnh này sẽ nhặt toàn bộ những user có trong auth mà chưa có trong public.users
INSERT INTO public.users (user_id, email, password_hash, display_name, avatar_url)
SELECT 
    id, 
    email, 
    'Google_OAuth',
    COALESCE(raw_user_meta_data->>'full_name', email),
    raw_user_meta_data->>'avatar_url'
FROM auth.users
WHERE email NOT IN (SELECT email FROM public.users)
ON CONFLICT (email) DO NOTHING;
