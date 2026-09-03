-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-03)
-- ------------------------------------------------------------
-- 대상: site_notice(전체 공지) 테이블에 상단 고정 기능 추가
--        (portal_notice에 이미 있는 pinned/pin_order와 완전히 동일한 패턴)
--
-- [이 파일이 하는 일]
--   · site_notice에 pinned(bit), pin_order(int, nullable) 컬럼 추가
--   · 정렬용 인덱스 추가 (idx_site_notice_pinned)
--   · 이미 적용돼 있으면 건너뜀 (재실행해도 안전)
--
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [1] site_notice.pinned
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'site_notice'
      AND COLUMN_NAME = 'pinned'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE site_notice ADD COLUMN pinned bit(1) NOT NULL DEFAULT b''0'' COMMENT ''목록 상단 노출 여부'' AFTER published',
    'SELECT ''SKIP: site_notice.pinned already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] site_notice.pin_order
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'site_notice'
      AND COLUMN_NAME = 'pin_order'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE site_notice ADD COLUMN pin_order int DEFAULT NULL COMMENT ''상단 노출 순서(1부터, 작을수록 위, 최대 5개)'' AFTER pinned',
    'SELECT ''SKIP: site_notice.pin_order already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [3] 정렬용 인덱스
-- ------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'site_notice'
      AND INDEX_NAME = 'idx_site_notice_pinned'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE site_notice ADD KEY idx_site_notice_pinned (pinned, pin_order)',
    'SELECT ''SKIP: idx_site_notice_pinned already exists'' AS migration_status'
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
  AND COLUMN_NAME IN ('pinned', 'pin_order');

SELECT '증분 적용 완료.' AS done;
