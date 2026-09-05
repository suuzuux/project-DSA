-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-03, 2번째)
-- ------------------------------------------------------------
-- 대상: site_notice(전체 공지) 테이블에 category(분류) 컬럼 추가
--
-- [이 파일이 하는 일]
--   · site_notice에 category enum 컬럼 추가 (GENERAL/EVENT/MAINTENANCE/UPDATE)
--     - DEFAULT 'GENERAL'로 넣기 때문에 기존에 쌓여있던 공지도
--       전부 자동으로 "일반 공지"로 분류됨 - 별도 백필 필요 없음
--   · 이미 적용돼 있으면 건너뜀 (재실행해도 안전)
--
-- ============================================================

USE weplanet;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'site_notice'
      AND COLUMN_NAME = 'category'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE site_notice ADD COLUMN category enum(''GENERAL'',''EVENT'',''MAINTENANCE'',''UPDATE'') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''GENERAL'' COMMENT ''공지 분류 (일반/이벤트/점검/업데이트)'' AFTER title',
    'SELECT ''SKIP: site_notice.category already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인]
-- ============================================================
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'site_notice'
  AND COLUMN_NAME = 'category';

SELECT category, COUNT(*) AS cnt FROM site_notice GROUP BY category;

SELECT '증분 적용 완료. 기존 공지는 전부 GENERAL로 채워져 있어야 정상입니다.' AS done;
