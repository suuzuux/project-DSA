-- ============================================================
-- WePlaNet 아티스트·소속사 신청 워크플로우 증분 스키마
-- 기존 데이터는 삭제하지 않고 신청 테이블만 추가한다.
-- ============================================================

USE `weplanet`;

CREATE TABLE IF NOT EXISTS `partnership_applications` (
                                                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '입점 신청 PK',
                                                          `applicant_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신청 유형: ARTIST/AGENCY',
    `applicant_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '아티스트명 또는 소속사명',
    `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '담당자명',
    `email` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '회신 이메일',
    `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '연락처',
    `message` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신청 내용',
    `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '처리 상태',
    `reviewed_by` bigint DEFAULT NULL COMMENT '검토 관리자(users.id)',
    `reviewed_at` datetime(6) DEFAULT NULL COMMENT '검토 시각',
    `rejection_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '반려 사유',
    `created_at` datetime(6) NOT NULL COMMENT '신청 시각',
    `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (`id`),
    KEY `idx_pa_status_created` (`status`, `created_at`),
    KEY `idx_pa_type_status` (`applicant_type`, `status`),
    KEY `idx_pa_email` (`email`),
    KEY `idx_pa_reviewer` (`reviewed_by`, `reviewed_at`),
    CONSTRAINT `fk_pa_reviewer`
    FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`),
    CONSTRAINT `ck_pa_applicant_type`
    CHECK (`applicant_type` IN (_utf8mb4'ARTIST', _utf8mb4'AGENCY')),
    CONSTRAINT `ck_pa_status`
    CHECK (`status` IN (
           _utf8mb4'PENDING_APPROVAL',
           _utf8mb4'APPROVED',
           _utf8mb4'REJECTED'
                       )),
    CONSTRAINT `ck_pa_review_state`
    CHECK (
(
              `status` = _utf8mb4'PENDING_APPROVAL'
              AND `reviewed_by` IS NULL
              AND `reviewed_at` IS NULL
              AND `rejection_reason` IS NULL
)
    OR (
           `status` = _utf8mb4'APPROVED'
           AND `reviewed_by` IS NOT NULL
           AND `reviewed_at` IS NOT NULL
           AND `rejection_reason` IS NULL
       )
    OR (
           `status` = _utf8mb4'REJECTED'
           AND `reviewed_by` IS NOT NULL
           AND `reviewed_at` IS NOT NULL
           AND `rejection_reason` IS NOT NULL
       )
    )
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci
    COMMENT='아티스트·소속사 등록 신청';

SELECT 'partnership_applications 테이블 생성 완료' AS result;