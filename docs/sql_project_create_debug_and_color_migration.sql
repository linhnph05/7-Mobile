-- =============================================================
-- Project create 400 debug + migrate existing project colors
-- Target: Supabase Postgres
-- =============================================================

-- 1) Xem toàn bộ constraints của bảng projects
SELECT
  c.conname,
  c.contype,
  pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
JOIN pg_class t ON t.oid = c.conrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'public'
  AND t.relname = 'projects'
ORDER BY c.conname;

-- 2) Xem trigger trên bảng projects
SELECT
  tg.tgname,
  pg_get_triggerdef(tg.oid, true) AS definition
FROM pg_trigger tg
JOIN pg_class t ON t.oid = tg.tgrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'public'
  AND t.relname = 'projects'
  AND NOT tg.tgisinternal
ORDER BY tg.tgname;

-- 3) Check trùng tên project (không phân biệt hoa thường, bỏ khoảng trắng thừa)
SELECT
  lower(regexp_replace(trim(project_name), '\\s+', ' ', 'g')) AS normalized_project_name,
  count(*) AS total
FROM public.projects
WHERE coalesce(is_deleted, false) = false
GROUP BY lower(regexp_replace(trim(project_name), '\\s+', ' ', 'g'))
HAVING count(*) > 1
ORDER BY total DESC;

-- 4) Check trùng project_key
SELECT
  upper(trim(project_key)) AS normalized_project_key,
  count(*) AS total
FROM public.projects
WHERE coalesce(is_deleted, false) = false
GROUP BY upper(trim(project_key))
HAVING count(*) > 1
ORDER BY total DESC;

-- 5) Gợi ý đọc lỗi chuẩn từ PostgREST (nếu có log table hoặc edge log thì tra theo thời gian)
-- Bạn cũng có thể copy error body từ app (response.errorBody) để map ngược về constraint.


-- =============================================================
-- MIGRATION MÀU: random token Set A cho các project hiện có
-- Set A tokens:
--   ocean_blue, sunset_orange, jade_green, violet_mist, rose_pink
-- =============================================================

-- 6) [QUAN TRỌNG] Nới kiểu dữ liệu để lưu token tên màu.
-- Lỗi "value too long for type character varying(7)" xuất phát từ đây.
-- Khuyến nghị: đổi sang varchar(32) hoặc text.
ALTER TABLE public.projects
  ALTER COLUMN background_color TYPE character varying(32);

-- Nếu DB có check constraint chỉ cho phép HEX (ví dụ '^#[0-9A-Fa-f]{6}$'),
-- bạn cần drop/update constraint đó để cho phép token tên màu.
-- Dùng câu query ở mục (1) để xác định đúng tên constraint trước khi chỉnh.

-- 7) Preview trước khi update
SELECT project_id, project_name, background_color
FROM public.projects
ORDER BY project_id;

-- 8) Update random token cho tất cả project chưa bị xóa
WITH randomized AS (
  SELECT
    p.project_id,
    (ARRAY['ocean_blue','sunset_orange','jade_green','violet_mist','rose_pink'])[
      1 + floor(random() * 5)::int
    ] AS new_color
  FROM public.projects p
  WHERE coalesce(p.is_deleted, false) = false
)
UPDATE public.projects p
SET background_color = r.new_color
FROM randomized r
WHERE p.project_id = r.project_id;

-- 9) Kiểm tra kết quả sau update
SELECT background_color, count(*)
FROM public.projects
WHERE coalesce(is_deleted, false) = false
GROUP BY background_color
ORDER BY background_color;

-- 10) Nếu muốn chỉ update các bản ghi đang là HEX cũ, dùng câu này thay cho mục 8:
-- WITH randomized AS (
--   SELECT
--     p.project_id,
--     (ARRAY['ocean_blue','sunset_orange','jade_green','violet_mist','rose_pink'])[
--       1 + floor(random() * 5)::int
--     ] AS new_color
--   FROM public.projects p
--   WHERE coalesce(p.is_deleted, false) = false
--     AND p.background_color ~ '^#[0-9A-Fa-f]{6}$'
-- )
-- UPDATE public.projects p
-- SET background_color = r.new_color
-- FROM randomized r
-- WHERE p.project_id = r.project_id;


-- =============================================================
-- FALLBACK (KHÔNG ĐỔI SCHEMA): random HEX Set A để tương thích varchar(7)
-- Dùng đoạn này nếu bạn CHƯA muốn nới cột background_color.
-- =============================================================
-- WITH randomized_hex AS (
--   SELECT
--     p.project_id,
--     (ARRAY['#4C6FFF','#E68A57','#42A986','#8A6CCF','#C95A78'])[
--       1 + floor(random() * 5)::int
--     ] AS new_color
--   FROM public.projects p
--   WHERE coalesce(p.is_deleted, false) = false
-- )
-- UPDATE public.projects p
-- SET background_color = r.new_color
-- FROM randomized_hex r
-- WHERE p.project_id = r.project_id;
