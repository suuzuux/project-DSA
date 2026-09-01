-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-01, 권형준)
-- ------------------------------------------------------------
-- 브랜치: AUTH-02 (소셜 로그인 - Google/Kakao/LINE) 작업분
-- 대상: 이미 weplanet DB가 있는 로컬/공유 개발 환경
--
-- [이 파일이 하는 일]
--   1) users — 소셜 로그인 식별용 provider, provider_id 컬럼 추가
--   2) users — (provider, provider_id) 유니크 키 추가
--   3) users — provider 값 검증용 CHECK 제약 추가
--
-- [이 파일이 하지 않는 일]
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
--   3) 이 파일(20260901_권형준_증분_DB적용.sql) 선택
--   4) 상단 ⚡ Execute (번개 아이콘) 클릭 — 또는 Ctrl+Shift+Enter
--   5) 하단 Output 탭에 에러 없이 완료됐는지 확인
--   6) (선택) 파일 하단 [확인] SELECT 결과에 provider / provider_id / 유니크 키가 보이면 OK
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
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < docs/20260901_권형준_증분_DB적용.sql
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
--   · ddl-auto=validate 이므로 이 증분 적용 없이 앱을 켜면 users 관련
--     Schema validation 오류가 날 수 있습니다.
--   · 기존 회원 row는 모두 provider='LOCAL', provider_id=NULL 로 자동 채워집니다
--     (기존 아이디/비밀번호 회원가입 계정이므로 정상입니다).
--
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [1] users.provider
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'provider'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN provider varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''LOCAL'' COMMENT ''가입 경로: LOCAL/GOOGLE/KAKAO/LINE'' AFTER deleted_at',
    'SELECT ''SKIP: users.provider already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] users.provider_id
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'provider_id'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN provider_id varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''소셜 플랫폼 고유 ID (LOCAL 가입자는 NULL)'' AFTER provider',
    'SELECT ''SKIP: users.provider_id already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [3] users 유니크 키 (provider, provider_id)
-- ------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'uk_users_provider_provider_id'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE users ADD UNIQUE KEY uk_users_provider_provider_id (provider, provider_id)',
    'SELECT ''SKIP: uk_users_provider_provider_id already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [4] users.provider CHECK 제약
-- ------------------------------------------------------------
SET @chk_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND CONSTRAINT_NAME = 'ck_users_provider'
      AND CONSTRAINT_TYPE = 'CHECK'
);
SET @ddl := IF(
    @chk_exists = 0,
    'ALTER TABLE users ADD CONSTRAINT ck_users_provider CHECK (provider IN (''LOCAL'', ''GOOGLE'', ''KAKAO'', ''LINE''))',
    'SELECT ''SKIP: ck_users_provider already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인] 아래 결과를 보고 적용 여부를 판단하세요.
-- ============================================================

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('provider', 'provider_id')
ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND INDEX_NAME = 'uk_users_provider_provider_id'
GROUP BY INDEX_NAME;

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND CONSTRAINT_NAME = 'ck_users_provider';

SELECT '증분 적용 완료. 이 파일은 1회 실행 후 삭제해도 됩니다.' AS done;
