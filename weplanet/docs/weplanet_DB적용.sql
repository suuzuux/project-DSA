-- ============================================================
-- WePlaNet 로컬 DB 적용 - SETTINGS-03(다국어) 변경분 (데이터 보존)
-- ------------------------------------------------------------
-- 대상: 직전 main 기준으로 DB를 이미 맞춰 둔 팀원
-- 실행: MySQL Workbench에서 이 파일 전체 선택 후 실행 (Ctrl+Shift+Enter)
--
-- 변경 내용 (권형준 / SETTINGS-03)
--   partnership_applications.applicant_language 컬럼 추가
--   - 등록 신청할 때의 화면 언어(KO/JA/EN). 승인/반려 메일을 이 언어로 보낸다
--   - 기존 신청은 한국어(KO)로 채워진다
--   - 허용값 CHECK (KO/JA/EN) 추가
--
-- - 기존 데이터는 삭제하지 않으며, 여러 번 실행해도 안전합니다 (이미 있으면 건너뜀).
-- - 빈 DB를 새로 만들 때는 weplanet_schema_full_reset.sql을 사용하세요 (이 변경도 포함되어 있음).
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `wp_settings03_apply`;

DELIMITER $$

CREATE PROCEDURE `wp_settings03_apply`()
BEGIN
    -- 1) 컬럼: 없을 때만 추가 (기존 행은 DEFAULT 'KO'로 채워짐)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'partnership_applications'
          AND COLUMN_NAME = 'applicant_language'
    ) THEN
        ALTER TABLE `partnership_applications`
            ADD COLUMN `applicant_language` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KO'
                COMMENT '신청할 때 화면 언어 (KO/JA/EN) - 승인/반려 메일을 이 언어로 보낸다'
                AFTER `message`;
    END IF;

    -- 2) CHECK 제약: 없을 때만 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'partnership_applications'
          AND CONSTRAINT_NAME = 'ck_pa_applicant_language'
    ) THEN
        ALTER TABLE `partnership_applications`
            ADD CONSTRAINT `ck_pa_applicant_language`
                CHECK (`applicant_language` IN (_utf8mb4'KO', _utf8mb4'JA', _utf8mb4'EN'));
    END IF;
END$$

DELIMITER ;

CALL `wp_settings03_apply`();
DROP PROCEDURE IF EXISTS `wp_settings03_apply`;

-- [검증] 1행(varchar / KO)이 나오면 적용 완료
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'partnership_applications'
  AND COLUMN_NAME = 'applicant_language';
