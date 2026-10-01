-- ============================================================
-- WePlaNet 로컬 DB 적용 - SETTINGS-03(다국어) 변경분
-- ------------------------------------------------------------
-- 대상: main 최신을 pull 받고 DB도 최신으로 맞춰 둔 팀원
-- 실행: MySQL Workbench에서 전체 선택 후 실행 (한 번만 실행)
--
-- 변경 내용 (권형준 / SETTINGS-03)
--   partnership_applications.applicant_language 컬럼 추가
--   - 등록 신청할 때의 화면 언어(KO/JA/EN). 승인/반려 메일을 이 언어로 보낸다
--   - 기존 신청은 한국어(KO)로 채워진다
--   - 허용값 CHECK (KO/JA/EN) 추가
--
-- - 이미 적용한 DB에서 다시 실행하면 "Duplicate column" 오류가 나는데, 무시해도 됩니다.
-- - 빈 DB를 새로 만들 때는 weplanet_schema_full_reset.sql을 사용하세요 (이 변경도 포함되어 있음).
-- ============================================================

USE `weplanet`;

ALTER TABLE `partnership_applications`
    ADD COLUMN `applicant_language` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KO'
    COMMENT '신청할 때 화면 언어 (KO/JA/EN) - 승인/반려 메일을 이 언어로 보낸다'
        AFTER `message`;

ALTER TABLE `partnership_applications`
    ADD CONSTRAINT `ck_pa_applicant_language`
        CHECK (`applicant_language` IN ('KO', 'JA', 'EN'));