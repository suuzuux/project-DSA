-- ============================================================
-- WePlaNet 통합 DB 스키마 [파일 2] ADMIN-2 증분 적용 (2026-09-09)
-- ------------------------------------------------------------
-- 대상: 이미 weplanet DB를 사용 중인 팀원
-- 실행: 이 파일 하나만 MySQL Workbench에서 전체 선택 후 실행
--
-- 포함 변경사항
--   1) users.agency_id 및 조회 인덱스/외래키
--   2) admin_action_logs 테이블
--   3) report/comment_report 처리 상태와 처리 시각
--   4) site_notice 분류/예약발행/상단고정
--   5) email_verification 관리자 로그인 인증 목적
--   6) 테스트 계정 비밀번호 Test1234 통일
--
-- 기존 데이터는 삭제하지 않으며 여러 번 실행해도 안전합니다.
-- 빈 DB를 처음 구성하는 경우에는 weplanet_schema_full_reset.sql을 사용하세요.
-- ============================================================

USE `weplanet`;

DROP PROCEDURE IF EXISTS `wp_apply_admin2_schema`;

DELIMITER $$

CREATE PROCEDURE `wp_apply_admin2_schema`()
BEGIN
    -- 기본 스키마가 없는 DB에서 잘못 실행하는 것을 방지한다.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'users 테이블이 없습니다. 빈 DB는 weplanet_schema_full_reset.sql을 먼저 실행하세요.';
    END IF;

    -- --------------------------------------------------------
    -- 1. users.agency_id
    -- --------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'agency_id'
    ) THEN
        ALTER TABLE `users`
            ADD COLUMN `agency_id` bigint DEFAULT NULL
                COMMENT '소속사(agencies.id). ARTIST/AGENCY만 사용, FAN/ADMIN은 NULL'
                AFTER `status`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND INDEX_NAME = 'idx_users_agency_role'
    ) THEN
        ALTER TABLE `users`
            ADD KEY `idx_users_agency_role` (`agency_id`, `role`);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND CONSTRAINT_NAME = 'fk_users_agency'
    ) THEN
        ALTER TABLE `users`
            ADD CONSTRAINT `fk_users_agency`
                FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`);
    END IF;

    -- --------------------------------------------------------
    -- 2. 관리자 조치 감사 로그
    -- --------------------------------------------------------
    CREATE TABLE IF NOT EXISTS `admin_action_logs` (
        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '로그 PK',
        `actor_id` bigint NOT NULL COMMENT '조치 수행 운영자(users.id)',
        `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조치 유형',
        `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 종류',
        `target_id` bigint NOT NULL COMMENT '대상 ID',
        `reason` text COLLATE utf8mb4_unicode_ci COMMENT '조치 사유',
        `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '요청 IP',
        `created_at` datetime(6) NOT NULL COMMENT '기록 시각',
        PRIMARY KEY (`id`),
        KEY `idx_aal_actor` (`actor_id`, `created_at`),
        KEY `idx_aal_target` (`target_type`, `target_id`),
        CONSTRAINT `fk_aal_actor`
            FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='운영자 조치 감사 로그';

    -- --------------------------------------------------------
    -- 3. 게시글/댓글 신고 처리 상태
    -- --------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'report'
          AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE `report`
            ADD COLUMN `status`
                enum('PENDING', 'DISMISSED', 'RESOLVED')
                CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                NOT NULL DEFAULT 'PENDING'
                COMMENT '처리 상태 (대기/기각/처리완료)'
                AFTER `reason`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'report'
          AND COLUMN_NAME = 'resolved_at'
    ) THEN
        ALTER TABLE `report`
            ADD COLUMN `resolved_at` datetime(6) DEFAULT NULL
                COMMENT '신고 처리 시각 (대기중이면 NULL)'
                AFTER `status`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'comment_report'
          AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE `comment_report`
            ADD COLUMN `status`
                enum('PENDING', 'DISMISSED', 'RESOLVED')
                CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                NOT NULL DEFAULT 'PENDING'
                COMMENT '처리 상태 (대기/기각/처리완료)'
                AFTER `reason`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'comment_report'
          AND COLUMN_NAME = 'resolved_at'
    ) THEN
        ALTER TABLE `comment_report`
            ADD COLUMN `resolved_at` datetime(6) DEFAULT NULL
                COMMENT '신고 처리 시각 (대기중이면 NULL)'
                AFTER `status`;
    END IF;

    -- --------------------------------------------------------
    -- 4. 전체 공지 분류/예약발행/상단고정
    -- --------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'site_notice'
          AND COLUMN_NAME = 'category'
    ) THEN
        ALTER TABLE `site_notice`
            ADD COLUMN `category`
                enum('GENERAL', 'EVENT', 'MAINTENANCE', 'UPDATE')
                CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                NOT NULL DEFAULT 'GENERAL'
                COMMENT '공지 분류 (일반/이벤트/점검/업데이트)'
                AFTER `title`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'site_notice'
          AND COLUMN_NAME = 'publish_at'
    ) THEN
        ALTER TABLE `site_notice`
            ADD COLUMN `publish_at` datetime(6) DEFAULT NULL
                COMMENT '예약 발행 시각 (NULL이면 예약 없음)'
                AFTER `published`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'site_notice'
          AND COLUMN_NAME = 'pinned'
    ) THEN
        ALTER TABLE `site_notice`
            ADD COLUMN `pinned` bit(1) NOT NULL DEFAULT b'0'
                COMMENT '목록 상단 노출 여부'
                AFTER `publish_at`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'site_notice'
          AND COLUMN_NAME = 'pin_order'
    ) THEN
        ALTER TABLE `site_notice`
            ADD COLUMN `pin_order` int DEFAULT NULL
                COMMENT '상단 노출 순서(1부터, 작을수록 위, 최대 5개)'
                AFTER `pinned`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'site_notice'
          AND INDEX_NAME = 'idx_site_notice_pinned'
    ) THEN
        ALTER TABLE `site_notice`
            ADD KEY `idx_site_notice_pinned` (`pinned`, `pin_order`);
    END IF;

    -- --------------------------------------------------------
    -- 5. 관리자 로그인 이메일 인증 목적
    -- --------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.CHECK_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_NAME = 'ck_email_verification_purpose'
          AND CHECK_CLAUSE NOT LIKE '%ADMIN_LOGIN%'
    ) THEN
        ALTER TABLE `email_verification`
            DROP CHECK `ck_email_verification_purpose`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.CHECK_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_NAME = 'ck_email_verification_purpose'
    ) THEN
        ALTER TABLE `email_verification`
            ADD CONSTRAINT `ck_email_verification_purpose`
                CHECK (`purpose` IN (
                    'SIGNUP',
                    'FAN_PROJECT_CREATE',
                    'ADMIN_LOGIN'
                ));
    END IF;
END$$

DELIMITER ;

CALL `wp_apply_admin2_schema`();
DROP PROCEDURE `wp_apply_admin2_schema`;

-- ------------------------------------------------------------
-- 6. 기존 테스트 계정의 비밀번호를 Test1234로 통일
--    실제 회원이나 팀원이 따로 만든 계정은 변경하지 않는다.
-- ------------------------------------------------------------
UPDATE `users`
SET `password` = '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu',
    `updated_at` = NOW(6)
WHERE `username` IN (
    'admin_test',
    'agency_wp',
    'agency_hs',
    'artist_hwiwon',
    'artist_jungsik',
    'artist_hyeseon',
    'asd123',
    'qatest99',
    'aifan_bot',
    'aifan_mina',
    'aifan_hayul',
    'aifan_haerin',
    'aifan_jun',
    'aifan_yuna'
);

-- 기존 대표 테스트 계정의 소속사를 이름 기준으로 연결한다.
UPDATE `users` u
JOIN `agencies` a ON a.`name` = '휘원공주정식왕자'
SET u.`agency_id` = a.`id`
WHERE u.`username` IN ('artist_hwiwon', 'artist_jungsik', 'agency_wp')
  AND u.`agency_id` IS NULL;

UPDATE `users` u
JOIN `agencies` a ON a.`name` = '혜선우주최강'
SET u.`agency_id` = a.`id`
WHERE u.`username` IN ('artist_hyeseon', 'agency_hs')
  AND u.`agency_id` IS NULL;

-- 적용 결과 확인
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
      (TABLE_NAME = 'users' AND COLUMN_NAME = 'agency_id')
      OR (TABLE_NAME IN ('report', 'comment_report')
          AND COLUMN_NAME IN ('status', 'resolved_at'))
      OR (TABLE_NAME = 'site_notice'
          AND COLUMN_NAME IN ('category', 'publish_at', 'pinned', 'pin_order'))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'ck_email_verification_purpose';

SELECT 'ADMIN-2 통합 증분 DB 적용 완료' AS result;
