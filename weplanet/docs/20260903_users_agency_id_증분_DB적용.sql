-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-03, 3번째)
-- ------------------------------------------------------------
-- 대상: users 테이블에 agency_id(소속사) 컬럼 추가
--
-- [왜 필요한가]
--   지금은 에이전시(AGENCY) 계정이 "어떤 아티스트를 관리하는지"가 코드에 없어서,
--   PortalController.resolveManagedArtist()가 그냥 전체 아티스트 중 첫 번째를
--   골라서 관리하게 되어 있다. 소속 관계를 users.agency_id 하나로 표현해서
--   "내 소속사 아티스트만" 관리하도록 바꾸기 위한 컬럼.
--
--   ARTIST 계정  : 내가 속한 소속사
--   AGENCY 계정  : 내가 일하는 소속사
--   FAN/ADMIN    : NULL (소속 없음)
--
-- [이 파일이 하는 일]
--   1) users.agency_id 컬럼 추가 (NULL 허용, agencies.id 참조)
--   2) 기존 테스트 계정(artist_hwiwon / artist_jungsik / agency_wp)을
--      1번 소속사(WePlaNet Agency)에 연결 - 로컬에서 바로 테스트 가능하도록
--
-- [안전성] 여러 번 실행해도 안전(멱등). 기존 데이터는 지우지 않는다.
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [1] users.agency_id 컬럼
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'agency_id'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN agency_id bigint DEFAULT NULL COMMENT ''소속사(agencies.id) - ARTIST/AGENCY만 사용, FAN/ADMIN은 NULL'' AFTER role',
    'SELECT ''SKIP: users.agency_id already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] 인덱스 (소속사별 아티스트 조회용)
-- ------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_agency_role'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE users ADD KEY idx_users_agency_role (agency_id, role)',
    'SELECT ''SKIP: idx_users_agency_role already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [3] 외래키 (agencies.id 참조)
-- ------------------------------------------------------------
SET @fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND CONSTRAINT_NAME = 'fk_users_agency'
);
SET @ddl := IF(
    @fk_exists = 0,
    'ALTER TABLE users ADD CONSTRAINT fk_users_agency FOREIGN KEY (agency_id) REFERENCES agencies (id)',
    'SELECT ''SKIP: fk_users_agency already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [4] 테스트 계정을 1번 소속사에 연결
--     (agencies id=1 = 'WePlaNet Agency' - MediaGroupDataInitializer가 자동 생성)
--     이미 연결돼 있거나 소속사가 없으면 아무 일도 안 함
-- ------------------------------------------------------------
UPDATE users
SET agency_id = 1
WHERE username IN ('artist_hwiwon', 'artist_jungsik', 'agency_wp')
  AND agency_id IS NULL
  AND EXISTS (SELECT 1 FROM (SELECT id FROM agencies WHERE id = 1) AS a);

-- ============================================================
-- [확인]
-- ============================================================
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'agency_id';

SELECT id, username, role, agency_id
FROM users
WHERE role IN ('ARTIST', 'AGENCY')
ORDER BY role, id;

SELECT '증분 적용 완료.' AS done;
