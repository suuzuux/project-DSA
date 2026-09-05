-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-05, 2번째)
-- ------------------------------------------------------------
-- 대상: site_notice(전체 공지) 테이블에 publish_at(예약 발행 시각) 컬럼 추가
--
-- [이 파일이 하는 일]
--   · site_notice에 publish_at 컬럼 추가 (datetime, NULL 허용)
--     - NULL이면 예약 없이 published 값 그대로 즉시 반영 (기존 동작 그대로)
--     - 값이 있으면, 그 시각이 지나야 공개 목록/상세에 노출됨
--   · 이미 적용돼 있으면 건너뜀 (재실행해도 안전)
-- ============================================================

USE weplanet;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'site_notice'
      AND COLUMN_NAME = 'publish_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE site_notice ADD COLUMN publish_at datetime(6) NULL COMMENT ''예약 발행 시각 (NULL이면 예약 없음)'' AFTER published',
    'SELECT ''SKIP: site_notice.publish_at already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인]
-- ============================================================
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'site_notice'
  AND COLUMN_NAME = 'publish_at';

SELECT '증분 적용 완료.' AS done;
