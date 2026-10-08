-- WePlaNet 로컬 DB 적용 - partnership_applications.applicant_language 컬럼 추가 (한 번만 실행).

USE `weplanet`;

ALTER TABLE `partnership_applications`
    ADD COLUMN `applicant_language` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KO'
    COMMENT '신청할 때 화면 언어 (KO/JA/EN) - 승인/반려 메일을 이 언어로 보낸다'
        AFTER `message`;

ALTER TABLE `partnership_applications`
    ADD CONSTRAINT `ck_pa_applicant_language`
        CHECK (`applicant_language` IN ('KO', 'JA', 'EN'));