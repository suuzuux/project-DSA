-- ============================================================
-- WePlaNet 팬 프로젝트 테스트용 유틸 SQL
-- ------------------------------------------------------------
-- ADMIN 페이지 미개발 구간을 손으로 처리하거나, 모금 테스트를 위해
-- 날짜를 조정할 때 쓰는 스크립트 모음입니다. 필요한 섹션만 골라서
-- @project_id 등 변수만 바꿔 실행하면 됩니다.
--
-- 구성
--   [A] 프로젝트 승인 처리 (PENDING_APPROVAL -> APPROVED)
--   [B] 모금 기간을 지금 진행 중으로 당기기 (테스트용)
--
-- ------------------------------------------------------------
-- 주의: users.username 컬럼은 utf8mb4_unicode_ci로 정의되어 있는데,
-- mysql 세션의 기본 콜레이션(보통 utf8mb4_0900_ai_ci)으로 사용자 변수를
-- SET @var = '문자열'; 로 그냥 선언하면, 그 변수는 세션 기본 콜레이션을
-- 갖게 되어 컬럼과 비교할 때 아래 에러가 납니다.
--   ERROR 1267: Illegal mix of collations
--     (utf8mb4_unicode_ci,IMPLICIT) and (utf8mb4_0900_ai_ci,IMPLICIT)
-- 그래서 문자열 변수는 항상 COLLATE utf8mb4_unicode_ci를 명시해서
-- 선언합니다. (아래 두 섹션 모두 이미 적용되어 있음)
-- ============================================================

USE weplanet;


-- ============================================================
-- [A] 프로젝트 승인 처리
-- ------------------------------------------------------------
-- @project_id, @admin_username 만 바꿔서 실행하세요.
-- ============================================================

SET @project_id = 1;
SET @admin_username = _utf8mb4'admin_test' COLLATE utf8mb4_unicode_ci;

START TRANSACTION;

UPDATE fan_project AS fp
JOIN users AS admin
  ON admin.username = @admin_username
 AND admin.role = 'ADMIN'
SET fp.status = 'APPROVED',
    fp.reviewed_by = admin.id,
    fp.reviewed_at = NOW(6),
    fp.rejection_reason = NULL,
    fp.updated_at = NOW(6)
WHERE fp.id = @project_id
  AND fp.status = 'PENDING_APPROVAL'
  AND fp.deleted_at IS NULL;

SET @affected_rows = ROW_COUNT();

COMMIT;

-- affected_rows가 1이 아니면(0 또는 에러) 승인 대상이 없거나 이미 처리된 것입니다.
SELECT @affected_rows AS affected_rows;

SELECT id, title, status, reviewed_by, reviewed_at, funding_start_at, funding_end_at
FROM fan_project
WHERE id = @project_id;


-- ============================================================
-- [B] 모금 기간을 지금 진행 중으로 당기기 (테스트 전용)
-- ------------------------------------------------------------
-- 프로젝트 등록 시 모금 시작일을 당일로 넣어도, 자정 기준 비교 때문에
-- 모금 시작 전 상태로 막혀서 바로 참여 테스트가 안 됩니다.
-- 참여 버튼을 바로 눌러보고 싶을 때만 아래로 시작일을 1시간 당기세요.
-- (운영 데이터에는 절대 사용하지 마세요 - 테스트 DB 전용입니다)
-- ============================================================

SET @project_id = 1;

UPDATE fan_project
SET funding_start_at = NOW(6) - INTERVAL 1 HOUR
WHERE id = @project_id;

SELECT id, status, funding_start_at, funding_end_at
FROM fan_project
WHERE id = @project_id;
