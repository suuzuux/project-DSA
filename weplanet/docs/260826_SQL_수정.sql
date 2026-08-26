-- ============================================================
-- WePlaNet Fan Project Schema
-- 팬 프로젝트 관련 7개 테이블만 삭제 후 재생성한다.
-- users 테이블이 먼저 생성되어 있어야 한다.
-- ============================================================

USE weplanet;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS fan_project_fraud_check;
DROP TABLE IF EXISTS fan_project_settlement_account;
DROP TABLE IF EXISTS fan_project_contribution;
DROP TABLE IF EXISTS fan_project_cover_image;
DROP TABLE IF EXISTS fan_project;
DROP TABLE IF EXISTS fan_badge_ownership;
DROP TABLE IF EXISTS email_verification;

-- ============================================================
-- email_verification: 이메일 인증 기록
-- ============================================================

CREATE TABLE email_verification (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '이메일 인증 PK',
    user_id bigint DEFAULT NULL COMMENT '프로젝트 인증 회원(users.id), 회원가입 인증은 NULL',
    email varchar(255) NOT NULL COMMENT '인증 대상 이메일',
    purpose varchar(30) NOT NULL COMMENT '인증 목적: SIGNUP/FAN_PROJECT_CREATE',
    verification_key varchar(36) NOT NULL COMMENT '인증 요청 식별 UUID',
    code_hash varchar(255) NOT NULL COMMENT '인증번호 BCrypt 해시',
    attempt_count int NOT NULL DEFAULT 0 COMMENT '인증번호 실패 횟수',
    expires_at datetime(6) NOT NULL COMMENT '인증 만료 시각',
    verified_at datetime(6) DEFAULT NULL COMMENT '인증 완료 시각',
    consumed_at datetime(6) DEFAULT NULL COMMENT '회원가입/프로젝트 등록에 사용된 시각',
    created_at datetime(6) NOT NULL COMMENT '인증 요청 생성 시각',
    updated_at datetime(6) NOT NULL COMMENT '인증 정보 수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_key (verification_key),
    KEY idx_email_verification_email (email, purpose, created_at),
    KEY idx_email_verification_user (user_id, purpose, created_at),

    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_email_verification_purpose
        CHECK (purpose IN ('SIGNUP', 'FAN_PROJECT_CREATE')),

    CONSTRAINT ck_email_verification_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 5),

    CONSTRAINT ck_email_verification_consumed
        CHECK (consumed_at IS NULL OR verified_at IS NOT NULL),

    CONSTRAINT ck_email_verification_expiration
        CHECK (expires_at > created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='회원가입 및 팬 프로젝트 이메일 인증';


-- fan_badge_ownership: 팬 활동 배지 (프로젝트 자격 등)
CREATE TABLE `fan_badge_ownership` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '배지 보유 PK',
  `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
  `artist_id` bigint NOT NULL COMMENT '아티스트 커뮤니티(users.id)',
  `badge_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 코드',
  `badge_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 표시명',
  `badge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 유형: BASIC/SPECIAL',
  `awarded_at` datetime(6) NOT NULL COMMENT '수여 시각',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '회수 시각(NULL=유효)',
  `awarded_by` bigint DEFAULT NULL COMMENT '수여자(users.id)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`),
  KEY `idx_fan_badge_count` (`fan_id`, `artist_id`, `badge_type`, `revoked_at`),
  KEY `idx_fan_badge_awarded_by` (`awarded_by`),
  KEY `fk_fan_badge_artist` (`artist_id`),
  CONSTRAINT `fk_fan_badge_awarded_by` FOREIGN KEY (`awarded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_badge_period` CHECK ((`revoked_at` IS NULL) OR (`revoked_at` >= `awarded_at`)),
  CONSTRAINT `ck_fan_badge_type` CHECK (`badge_type` IN (_utf8mb4'BASIC', _utf8mb4'SPECIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 배지 보유/회수';

-- fan_project: 팬 프로젝트 개설·모금 (prjPROJECT)
CREATE TABLE `fan_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '프로젝트 PK',
  `artist_id` bigint NOT NULL COMMENT '대상 아티스트 커뮤니티(users.id)',
  `creator_id` bigint NOT NULL COMMENT '개설 신청자(users.id)',
  `title` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '프로젝트 제목',
  `event_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이벤트 유형: BIRTHDAY_CAFE/BILLBOARD/CONCERT/ETC',
  `goal_amount` bigint NOT NULL COMMENT '목표 모금액(원, 1만~300만)',
  `funding_start_at` datetime(6) NOT NULL COMMENT '모금 시작 시각',
  `funding_end_at` datetime(6) NOT NULL COMMENT '모금 종료 시각',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '프로젝트 소개',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '상태: 승인대기/승인/반려/모금중 등',
  `special_badge_count_at_apply` int NOT NULL COMMENT '신청 시점 SPECIAL 배지 수(자격 스냅샷)',
  `basic_badge_count_at_apply` int NOT NULL COMMENT '신청 시점 BASIC 배지 수(자격 스냅샷)',
  `eligibility_rule_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SPECIAL_1_AND_BASIC_5' COMMENT '개설 자격 규칙 코드',
  `identity_verification_method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EMAIL' COMMENT '프로젝트 등록 인증 방식(EMAIL)',
  `identity_verified_at` datetime(6) NOT NULL COMMENT '본인인증 완료 시각',
  `reviewed_by` bigint DEFAULT NULL COMMENT '검토자(users.id)',
  `reviewed_at` datetime(6) DEFAULT NULL COMMENT '검토 시각',
  `rejection_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '반려 사유(REJECTED 시)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '삭제(soft delete) 시각',
  PRIMARY KEY (`id`),
  KEY `idx_fan_project_artist_status` (`artist_id`, `status`, `funding_start_at`),
  KEY `idx_fan_project_creator` (`creator_id`, `created_at`),
  KEY `idx_fan_project_funding_end` (`status`, `funding_end_at`),
  KEY `idx_fan_project_reviewer` (`reviewed_by`, `reviewed_at`),
  CONSTRAINT `fk_fan_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_project_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_project_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_badge_counts` CHECK ((`special_badge_count_at_apply` >= 0) AND (`basic_badge_count_at_apply` >= 0)),
  CONSTRAINT `ck_fan_project_creation_eligibility` CHECK ((`special_badge_count_at_apply` >= 1) AND (`basic_badge_count_at_apply` >= 5)),
  CONSTRAINT `ck_fan_project_event_type` CHECK (`event_type` IN (_utf8mb4'BIRTHDAY_CAFE', _utf8mb4'BILLBOARD', _utf8mb4'CONCERT', _utf8mb4'ETC')),
  CONSTRAINT `ck_fan_project_funding_period` CHECK (`funding_end_at` > `funding_start_at`),
  CONSTRAINT `ck_fan_project_goal_amount` CHECK (`goal_amount` BETWEEN 10000 AND 3000000),
  CONSTRAINT `ck_fan_project_identity_method` CHECK (`identity_verification_method` = _utf8mb4'EMAIL'),
  CONSTRAINT `ck_fan_project_rejection_reason` CHECK ((`status` <> _utf8mb4'REJECTED') OR (`rejection_reason` IS NOT NULL)),
  CONSTRAINT `ck_fan_project_review` CHECK (
    ((`status` = _utf8mb4'PENDING_APPROVAL') AND (`reviewed_by` IS NULL) AND (`reviewed_at` IS NULL))
    OR (`status` NOT IN (_utf8mb4'PENDING_APPROVAL', _utf8mb4'APPROVED', _utf8mb4'REJECTED'))
    OR ((`status` IN (_utf8mb4'APPROVED', _utf8mb4'REJECTED')) AND (`reviewed_by` IS NOT NULL) AND (`reviewed_at` IS NOT NULL))
  ),
  CONSTRAINT `ck_fan_project_status` CHECK (`status` IN (
    _utf8mb4'PENDING_APPROVAL', _utf8mb4'APPROVED', _utf8mb4'REJECTED',
    _utf8mb4'FUNDING', _utf8mb4'FUNDING_CLOSED', _utf8mb4'COMPLETED', _utf8mb4'CANCELLED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트(개설·승인·모금)';

-- fan_project_cover_image: 프로젝트 대표 이미지
CREATE TABLE `fan_project_cover_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '커버 이미지 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id) 1:1',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '원본 파일명',
  `stored_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME(image/*)',
  `file_size` bigint NOT NULL COMMENT '파일 크기(byte)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_cover_project` (`project_id`),
  UNIQUE KEY `uk_fan_project_cover_stored` (`stored_name`),
  CONSTRAINT `fk_fan_project_cover_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_fan_project_cover_size` CHECK (`file_size` > 0),
  CONSTRAINT `ck_fan_project_cover_type` CHECK (`content_type` LIKE _utf8mb4'image/%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 커버 이미지';

-- fan_project_contribution: 후원/결제
CREATE TABLE `fan_project_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '후원/결제 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id)',
  `contributor_id` bigint NOT NULL COMMENT '후원자(users.id)',
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문번호',
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '결제 멱등 키(중복요청 방지)',
  `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT '결제 제공자',
  `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '제공자 거래 ID',
  `amount` bigint NOT NULL COMMENT '결제 금액(원)',
  `refund_amount` bigint NOT NULL DEFAULT '0' COMMENT '환불 금액(원)',
  `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY' COMMENT '결제 상태: READY/PAID/FAILED 등',
  `paid_at` datetime(6) DEFAULT NULL COMMENT '결제 완료 시각',
  `cancelled_at` datetime(6) DEFAULT NULL COMMENT '취소 시각',
  `refunded_at` datetime(6) DEFAULT NULL COMMENT '환불 시각',
  `refund_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '환불 사유',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_contribution_order` (`order_no`),
  UNIQUE KEY `uk_fan_project_contribution_idempotency` (`idempotency_key`),
  KEY `idx_fan_project_contribution_total` (`project_id`, `payment_status`),
  KEY `idx_fan_project_contributor_history` (`contributor_id`, `created_at`),
  CONSTRAINT `fk_fan_project_contribution_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`),
  CONSTRAINT `fk_fan_project_contribution_user` FOREIGN KEY (`contributor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_contribution_amount` CHECK (`amount` > 0),
  CONSTRAINT `ck_fan_project_contribution_refund` CHECK ((`refund_amount` >= 0) AND (`refund_amount` <= `amount`)),
  CONSTRAINT `ck_fan_project_contribution_status` CHECK (`payment_status` IN (
    _utf8mb4'READY', _utf8mb4'PAID', _utf8mb4'FAILED',
    _utf8mb4'CANCELLED', _utf8mb4'REFUND_REQUESTED', _utf8mb4'REFUNDED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 후원/결제';

-- fan_project_settlement_account: 정산 계좌
CREATE TABLE `fan_project_settlement_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '정산 계좌 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id) 1:1',
  `bank_code` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '은행코드(3자리)',
  `account_number_enc` varbinary(512) NOT NULL COMMENT '계좌번호(암호화)',
  `account_number_hmac` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '계좌번호 HMAC(검색용)',
  `account_number_last4` varchar(4) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '계좌 끝 4자리(표시용)',
  `verification_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNVERIFIED' COMMENT '계좌 검증: UNVERIFIED/VERIFIED/FAILED',
  `verified_at` datetime(6) DEFAULT NULL COMMENT '검증 완료 시각',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_settlement_project` (`project_id`),
  KEY `idx_fan_project_settlement_hmac` (`account_number_hmac`),
  CONSTRAINT `fk_fan_project_settlement_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_fan_project_account_last4` CHECK (regexp_like(`account_number_last4`, _utf8mb4'^[0-9]{4}$')),
  CONSTRAINT `ck_fan_project_account_verification` CHECK (`verification_status` IN (_utf8mb4'UNVERIFIED', _utf8mb4'VERIFIED', _utf8mb4'FAILED')),
  CONSTRAINT `ck_fan_project_bank_code` CHECK (`bank_code` IN (
    _utf8mb4'004', _utf8mb4'088', _utf8mb4'011', _utf8mb4'090', _utf8mb4'020',
    _utf8mb4'081', _utf8mb4'092', _utf8mb4'032', _utf8mb4'031'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 정산 계좌';

-- fan_project_fraud_check: 사기조회 결과
CREATE TABLE `fan_project_fraud_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '사기조회 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id)',
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조회 대상: ACCOUNT',
  `target_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 식별 해시',
  `bank_code` char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '은행코드(계좌 조회 시)',
  `provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'THECHEAT' COMMENT '조회 제공자',
  `provider_result_code` int DEFAULT NULL COMMENT '제공자 결과 코드',
  `result_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '결과: PENDING/CLEAR/CAUTION/ERROR',
  `caution_yn` char(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주의 여부 Y/N',
  `search_window_start_at` datetime(6) DEFAULT NULL COMMENT '조회 기간 시작',
  `search_window_end_at` datetime(6) DEFAULT NULL COMMENT '조회 기간 종료',
  `checked_at` datetime(6) DEFAULT NULL COMMENT '조회 수행 시각',
  `requested_by` bigint DEFAULT NULL COMMENT '요청자(users.id)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_fan_project_fraud_latest` (`project_id`, `target_type`, `checked_at`),
  KEY `idx_fan_project_fraud_target` (`target_fingerprint`, `checked_at`),
  KEY `idx_fan_project_fraud_requester` (`requested_by`),
  CONSTRAINT `fk_fan_project_fraud_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_fan_project_fraud_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_fraud_bank_code` CHECK ((`bank_code` IS NULL) OR regexp_like(`bank_code`, _utf8mb4'^[0-9]{3}$')),
  CONSTRAINT `ck_fan_project_fraud_caution` CHECK ((`caution_yn` IS NULL) OR (`caution_yn` IN (_utf8mb4'Y', _utf8mb4'N'))),
  CONSTRAINT `ck_fan_project_fraud_status` CHECK (`result_status` IN (_utf8mb4'PENDING', _utf8mb4'CLEAR', _utf8mb4'CAUTION', _utf8mb4'ERROR')),
  CONSTRAINT `ck_fan_project_fraud_target` CHECK (`target_type` = _utf8mb4'ACCOUNT'),
  CONSTRAINT `ck_fan_project_fraud_window` CHECK (
    (`search_window_start_at` IS NULL) OR (`search_window_end_at` IS NULL)
    OR (`search_window_end_at` >= `search_window_start_at`)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 사기조회';

SET FOREIGN_KEY_CHECKS = 1;
