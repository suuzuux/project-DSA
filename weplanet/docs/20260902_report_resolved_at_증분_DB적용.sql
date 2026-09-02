-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-02, 2번째)
-- ------------------------------------------------------------
-- 브랜치: REPORT-01
-- 대상: report / comment_report 두 테이블에 resolved_at(처리 시각) 컬럼 추가
--
-- [먼저 확인] 이 파일은 20260902_report_status_증분_DB적용.sql "다음"에 실행하세요.
--            (그 파일이 만드는 status 컬럼이 이미 있다는 전제로 동작합니다)
--
-- [이 파일이 하는 일]
--   · report, comment_report에 resolved_at (datetime, NULL 허용) 컬럼 추가
--     - 아직 처리 안 된(PENDING) 신고는 NULL
--     - 기각(DISMISSED)/처리완료(RESOLVED)로 바뀌는 순간 애플리케이션에서
--       현재 시각을 채워 넣음 (이 컬럼 자체엔 DEFAULT를 안 둠 - "처리된 시각"이라는
--       의미상 실제로 처리될 때만 값이 들어가야 하기 때문)
--   · 이미 존재하면 아무 것도 하지 않고 건너뜀 (재실행해도 안전)
--
-- [이 파일이 하지 않는 일]
--   · docs/weplanet_schema.sql은 건드리지 않음
--   · 기존 데이터를 지우거나 값을 채워 넣지 않음 (전부 NULL로 시작 - 정상)
--
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [1] report.resolved_at
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'report'
      AND COLUMN_NAME = 'resolved_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE report ADD COLUMN resolved_at datetime(6) NULL COMMENT ''신고가 기각/처리완료된 시각 (대기중이면 NULL)'' AFTER status',
    'SELECT ''SKIP: report.resolved_at already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] comment_report.resolved_at - 위와 동일
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'comment_report'
      AND COLUMN_NAME = 'resolved_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE comment_report ADD COLUMN resolved_at datetime(6) NULL COMMENT ''신고가 기각/처리완료된 시각 (대기중이면 NULL)'' AFTER status',
    'SELECT ''SKIP: comment_report.resolved_at already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인]
-- ============================================================

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('report', 'comment_report')
  AND COLUMN_NAME = 'resolved_at';

SELECT '증분 적용 완료. 기존 데이터는 전부 resolved_at = NULL이어야 정상입니다.' AS done;
