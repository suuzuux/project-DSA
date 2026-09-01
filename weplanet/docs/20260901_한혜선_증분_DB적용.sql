-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-01, 한혜선)
-- ------------------------------------------------------------
-- 브랜치: NOTICE-01 / SCHEDULE-01 계열 작업분
-- 대상: 이미 weplanet DB가 있는 로컬/공유 개발 환경
--
-- [이 파일이 하는 일]
--   1) portal_notice — 공지 상단 노출(pinned, pin_order) 컬럼·인덱스 추가
--
-- [이 파일이 하지 않는 일]
--   · artist_schedule 생일(BIRTHDAY) — 테이블 변경 없음. 기존 category 컬럼에
--     앱에서 'BIRTHDAY' 값만 저장합니다. (weplanet_schema.sql 주석만 갱신됨)
--   · weplanet_schema.sql 전체 재실행 — 하지 마세요. 기존 데이터가 지워집니다.
--
-- [적용 후]
--   · Spring Boot(server.port=9999) 재시작
--   · Schema validation 통과 확인
--   · 이 파일은 팀원 각자 PC에서 1회 실행 후 삭제해도 됩니다.
--
-- ============================================================
-- 실행 방법 (아래 중 편한 것 하나만 선택)
-- ============================================================
--
-- ■ 방법 A — MySQL Workbench (GUI, 추천)
--   1) MySQL Workbench 실행 → Local instance(MySQL 8.x) 더블클릭으로 접속
--   2) 메뉴 File → Open SQL Script…
--   3) 이 파일(20260901_한혜선_증분_DB적용.sql) 선택
--   4) 상단 ⚡ Execute (번개 아이콘) 클릭 — 또는 Ctrl+Shift+Enter
--   5) 하단 Output 탭에 에러 없이 완료됐는지 확인
--   6) (선택) 파일 하단 [확인] SELECT 결과에 pinned / pin_order / 인덱스가 보이면 OK
--
-- ■ 방법 B — IntelliJ IDEA / Cursor Database 도구
--   1) 우측 Database 패널 → + → Data Source → MySQL
--   2) Host: localhost, Port: 3306, Database: weplanet, User/Password 입력 → Test Connection → OK
--   3) weplanet 데이터소스 우클릭 → New → Query Console
--   4) 이 파일 내용 전체 붙여넣기 (또는 파일 열기)
--   5) 녹색 ▶ Run 버튼 (Execute) 클릭 — 또는 Ctrl+Enter
--   6) Services/Console에 에러 없는지 확인
--
-- ■ 방법 C — 명령줄 (mysql 클라이언트)
--   프로젝트 weplanet 폴더에서:
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < docs/20260901_한혜선_증분_DB적용.sql
--   (비밀번호 입력 후 Enter)
--
-- ■ 방법 D — DBeaver
--   1) weplanet 연결 더블클릭
--   2) SQL Editor → Open SQL script → 이 파일 선택
--   3) Execute SQL Script (Alt+X) 실행
--
-- [주의]
--   · Duplicate column name / Duplicate key name 메시지가 나오면 이미 적용된 것입니다.
--     아래 스크립트는 가능한 한 중복 실행을 막지만, 수동으로 ALTER를 이미 돌렸다면
--     [확인] 쿼리만 실행해도 됩니다.
--   · ddl-auto=validate 이므로 이 증분 적용 없이 앱을 켜면 portal_notice 관련
--     Schema validation 오류가 날 수 있습니다.
--
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [1] portal_notice.pinned
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'portal_notice'
      AND COLUMN_NAME = 'pinned'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE portal_notice ADD COLUMN pinned bit(1) NOT NULL DEFAULT b''0'' COMMENT ''목록 상단 노출 여부'' AFTER published',
    'SELECT ''SKIP: portal_notice.pinned already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] portal_notice.pin_order
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'portal_notice'
      AND COLUMN_NAME = 'pin_order'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE portal_notice ADD COLUMN pin_order int DEFAULT NULL COMMENT ''상단 노출 순서(1부터, 작을수록 위, 최대 5개)'' AFTER pinned',
    'SELECT ''SKIP: portal_notice.pin_order already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [3] portal_notice 인덱스
-- ------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'portal_notice'
      AND INDEX_NAME = 'idx_portal_notice_artist_pinned'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE portal_notice ADD KEY idx_portal_notice_artist_pinned (artist_id, pinned, pin_order)',
    'SELECT ''SKIP: idx_portal_notice_artist_pinned already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인] 아래 3개 결과를 보고 적용 여부를 판단하세요.
-- ============================================================

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'portal_notice'
  AND COLUMN_NAME IN ('pinned', 'pin_order')
ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'portal_notice'
  AND INDEX_NAME = 'idx_portal_notice_artist_pinned'
GROUP BY INDEX_NAME;

SELECT '증분 적용 완료. 이 파일은 1회 실행 후 삭제해도 됩니다.' AS done;
