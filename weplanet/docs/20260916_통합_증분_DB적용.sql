-- ============================================================
-- WePlaNet 통합 DB 스키마 [파일 2] ADMIN-2 + AUTH-10 + SETTINGS-01 증분 적용
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
--   6) AUTH-10 users.provider 의미 변경 및 password nullable 전환
--   7) SETTINGS-01 users 알림 수신 설정 컬럼
--   8) 테스트 계정 비밀번호 Test1234 통일
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

    -- --------------------------------------------------------
    -- 6. AUTH-10 소셜 연동 구조 변경
    --    provider: 가입 경로가 아니라 현재 연동된 소셜 provider
    --    password: 소셜 전용 가입자는 비밀번호가 없을 수 있음
    -- --------------------------------------------------------
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND CONSTRAINT_NAME = 'ck_users_provider'
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE `users`
            DROP CHECK `ck_users_provider`;
    END IF;

    -- 기존 LOCAL 값은 이제 "소셜 연동 없음"을 뜻하는 NULL로 통일한다.
    UPDATE `users`
    SET `provider` = NULL
    WHERE `provider` = 'LOCAL';

    ALTER TABLE `users`
        MODIFY `provider`
            varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL
            COMMENT '연동된 소셜 provider: GOOGLE/KAKAO/LINE (연동 없으면 NULL)',
        MODIFY `password`
            varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL
            COMMENT '비밀번호(BCrypt 해시, 소셜 전용 가입자는 NULL)';

    ALTER TABLE `users`
        ADD CONSTRAINT `ck_users_provider`
            CHECK (`provider` IS NULL
                OR `provider` IN ('GOOGLE', 'KAKAO', 'LINE'));

    -- --------------------------------------------------------
    -- 7. SETTINGS-01 이벤트·혜택 및 커뮤니티 활동 알림 설정
    -- --------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'marketing_consent'
    ) THEN
        ALTER TABLE `users`
            ADD COLUMN `marketing_consent` tinyint(1) NOT NULL DEFAULT '0'
                COMMENT '광고성 정보 수신 동의 (회원가입 체크박스와 공유)'
                AFTER `provider_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'community_activity_email_enabled'
    ) THEN
        ALTER TABLE `users`
            ADD COLUMN `community_activity_email_enabled` tinyint(1) NOT NULL DEFAULT '0'
                COMMENT '가입한 아티스트 활동(게시글/공지/라이브) 이메일 수신 여부'
                AFTER `marketing_consent`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'night_notification_allowed'
    ) THEN
        ALTER TABLE `users`
            ADD COLUMN `night_notification_allowed` tinyint(1) NOT NULL DEFAULT '0'
                COMMENT '오후 9시~오전 8시(KST) 알림 수신 여부'
                AFTER `community_activity_email_enabled`;
    END IF;
END$$

DELIMITER ;

CALL `wp_apply_admin2_schema`();
DROP PROCEDURE `wp_apply_admin2_schema`;

-- ------------------------------------------------------------
-- 8. 기존 테스트 계정의 비밀번호를 Test1234로 통일
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
      (TABLE_NAME = 'users'
          AND COLUMN_NAME IN (
              'agency_id',
              'provider',
              'password',
              'marketing_consent',
              'community_activity_email_enabled',
              'night_notification_allowed'
          ))
      OR (TABLE_NAME IN ('report', 'comment_report')
          AND COLUMN_NAME IN ('status', 'resolved_at'))
      OR (TABLE_NAME = 'site_notice'
          AND COLUMN_NAME IN ('category', 'publish_at', 'pinned', 'pin_order'))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME IN (
      'ck_email_verification_purpose',
      'ck_users_provider'
  )
ORDER BY CONSTRAINT_NAME;

SELECT 'ADMIN-2 + AUTH-10 + SETTINGS-01 통합 증분 DB 적용 완료' AS result;



-- ============================================================
-- WePlaNet BADGE-2 : 배지 카탈로그 25종 시드 (단독 실행용)
-- ------------------------------------------------------------
-- 대상: 이미 weplanet DB를 사용 중인 팀원
-- 실행: 이 파일 하나만 MySQL Workbench에서 전체 선택 후 실행
--
-- weplanet_schema_full_reset.sql 의 [2] 배지 카탈로그 부분만 떼어낸 파일.
-- DROP TABLE / DELETE 가 없어서 다른 테이블과 기존 데이터는 건드리지 않는다.
--
--   - fan_badge 테이블이 없으면 만든다 (있으면 그대로 둔다)
--   - 배지가 없으면 INSERT, 이미 있으면(badge_code 기준) 이름/아이콘/이미지/설명/순서만 최신값으로 UPDATE
--   - fan_badge_ownership(보유 기록)은 badge_code 로만 연결돼 있어서 영향 없음
--
-- 여러 번 실행해도 안전합니다.
-- ============================================================

USE `weplanet`;

-- fan_badge: 배지 카탈로그(마스터, 전 아티스트 공통)
CREATE TABLE IF NOT EXISTS `fan_badge` (
                                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '배지 PK',
                                           `badge_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 코드(전체 고유)',
    `badge_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 표시명',
    `badge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 유형: BASIC/SPECIAL',
    `icon` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '🏅' COMMENT '표시용 이모지',
    `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배지 이미지 경로. 있으면 이미지, 없으면 icon 이모지로 표시',
    `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '획득 조건 안내 문구',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '유형 내 표시 순서(작을수록 앞)',
    `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fan_badge_code` (`badge_code`),
    KEY `idx_fan_badge_list` (`badge_type`, `sort_order`),
    CONSTRAINT `ck_fan_badge_master_type` CHECK (`badge_type` IN (_utf8mb4'BASIC', _utf8mb4'SPECIAL'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='배지 카탈로그(전 아티스트 공통)';

-- 배지 카탈로그 25종 (일반 15 + 스페셜 10)
-- badge_code 가 UNIQUE 라서, 이미 있는 배지는 ON DUPLICATE KEY UPDATE 로 값만 갱신된다.
INSERT INTO `fan_badge`
(`badge_code`, `badge_name`, `badge_type`, `icon`, `image_url`, `description`, `sort_order`, `created_at`)
VALUES
    ('BASIC_FIRST_JOIN',    '커뮤니티 첫 가입',      'BASIC', '🎉', 'community-first-join.svg',  '커뮤니티에 처음 가입하면 획득',              1,  NOW(6)),
    ('BASIC_FIRST_POST',    '첫 게시글 작성',        'BASIC', '✍️', 'first-post.svg',            '팬 게시판에 첫 글을 쓰면 획득',           2,  NOW(6)),
    ('BASIC_COMMENT_5',     '댓글 5개 작성',         'BASIC', '💬', 'five-comments.svg',         '이 커뮤니티에 댓글 5개를 쓰면 획득',      3,  NOW(6)),
    ('BASIC_MEDIA_VIEW',    '미디어 시청',           'BASIC', '🎬', 'media-view.svg',            'Media 탭 콘텐츠를 보면 획득',             4,  NOW(6)),
    ('BASIC_DAY_100',       '가입 후 100일',         'BASIC', '💯', 'member-100-days.svg',       '가입 후 100일이 지나면 획득',        5,  NOW(6)),
    ('BASIC_DAY_200',       '가입 후 200일',         'BASIC', '📅', 'member-200-days.svg',       '가입 후 200일이 지나면 획득',        6,  NOW(6)),
    ('BASIC_DAY_300',       '가입 후 300일',         'BASIC', '🗓️', 'member-300-days.svg',       '가입 후 300일이 지나면 획득',        7,  NOW(6)),
    ('BASIC_LIKE_10',       '좋아요 10개',           'BASIC', '👍', 'ten-likes.svg',             '게시글에 좋아요를 10번 누르면 획득',          8,  NOW(6)),
    ('BASIC_LIKED_5',       '받은 좋아요 5개',       'BASIC', '❤️', 'five-likes-received.svg',   '내 게시글이 좋아요 5개를 받으면 획득',        9,  NOW(6)),
    ('BASIC_FOLLOW_ARTIST', '아티스트 프로필 팔로우', 'BASIC', '⭐', 'artist-profile-follow.svg', '아티스트 프로필을 팔로우하면 획득',          10, NOW(6)),
    ('BASIC_SHOP_PURCHASE', '샵 구매',               'BASIC', '🛍️', 'shop-purchase.svg',         'Shop에서 상품을 구매하면 획득',              11, NOW(6)),
    ('BASIC_LIVE_VIEW',     '라이브 시청',           'BASIC', '📡', 'live-view.svg',             'Live 방송을 보면 획득',                  12, NOW(6)),
    ('BASIC_YEAR_1',        '커뮤니티 가입 후 1년',  'BASIC', '🥇', 'community-1-year.svg',      '가입 후 1년이 지나면 획득',         13, NOW(6)),
    ('BASIC_YEAR_2',        '커뮤니티 가입 후 2년',  'BASIC', '🥈', 'community-2-years.svg',     '가입 후 2년이 지나면 획득',         14, NOW(6)),
    ('BASIC_YEAR_3',        '커뮤니티 가입 후 3년',  'BASIC', '🥉', 'community-3-years.svg',     '가입 후 3년이 지나면 획득',         15, NOW(6)),
    ('SPECIAL_DEBUT_1',       '아티스트 데뷔 1주년',  'SPECIAL', '🎂', 'artist-debut-1-year.svg',  '데뷔 1주년을 함께하면 획득',        1,  NOW(6)),
    ('SPECIAL_DEBUT_2',       '아티스트 데뷔 2주년',  'SPECIAL', '🎊', 'artist-debut-2-years.svg', '데뷔 2주년을 함께하면 획득',        2,  NOW(6)),
    ('SPECIAL_DEBUT_3',       '아티스트 데뷔 3주년',  'SPECIAL', '🏆', 'artist-debut-3-years.svg', '데뷔 3주년을 함께하면 획득',        3,  NOW(6)),
    ('SPECIAL_FOLLOWER_10',   '팔로워 10명 달성',     'SPECIAL', '👥', 'ten-followers.svg',        '내 팔로워가 10명이 되면 획득',               4,  NOW(6)),
    ('SPECIAL_MEMBERSHIP_1',  '첫 멤버십 가입',       'SPECIAL', '💎', 'first-membership.svg',     '멤버십에 처음 가입하면 획득',      5,  NOW(6)),
    ('SPECIAL_MEMBERSHIP_2',  '멤버십 연속 2년',      'SPECIAL', '💠', 'membership-2-years.svg',   '멤버십을 2년 연속 유지하면 획득',            6,  NOW(6)),
    ('SPECIAL_MEMBERSHIP_3',  '멤버십 연속 3년',      'SPECIAL', '🔷', 'membership-3-years.svg',   '멤버십을 3년 연속 유지하면 획득',            7,  NOW(6)),
    ('SPECIAL_MEMBERSHIP_4',  '멤버십 연속 4년',      'SPECIAL', '🔶', 'membership-4-years.svg',   '멤버십을 4년 연속 유지하면 획득',            8,  NOW(6)),
    ('SPECIAL_MEMBERSHIP_5',  '멤버십 연속 5년',      'SPECIAL', '👑', 'membership-5-years.svg',   '멤버십을 5년 연속 유지하면 획득',            9,  NOW(6)),
    ('SPECIAL_PROJECT_CREATE','프로젝트 참여',        'SPECIAL', '🚀', 'project-registered.svg',   '팬 프로젝트에 참여(결제 완료)하면 획득',    10, NOW(6))
    ON DUPLICATE KEY UPDATE
                         `badge_name`  = VALUES(`badge_name`),
                         `badge_type`  = VALUES(`badge_type`),
                         `icon`        = VALUES(`icon`),
                         `image_url`   = VALUES(`image_url`),
                         `description` = VALUES(`description`),
                         `sort_order`  = VALUES(`sort_order`);

-- [확인] total 25, with_image 25 가 나오면 정상입니다.
SELECT COUNT(*) AS total,
       SUM(`image_url` IS NOT NULL) AS with_image
FROM `fan_badge`;


-- ============================================================
-- WePlaNet BADGE-2 : 멤버십 가입/갱신 이력 테이블 (증분)
-- ------------------------------------------------------------
-- 대상: 이미 weplanet DB를 사용 중인 팀원
-- 실행: 이 파일 하나만 MySQL Workbench에서 전체 선택 후 실행
--
-- 왜 필요한가
--   membership 테이블은 (fan_id, artist_id) 당 한 줄이고 만료일을 덮어쓰기 때문에
--   "몇 년째 유지 중인지"를 알 수 없다. 가입/갱신 때마다 한 줄씩 쌓아 이력을 남긴다.
--
-- streak_count : 이 기간이 연속 몇 번째인지. 새 시작일이 직전 만료일 + 7일 안이면 +1, 넘으면 1.
--                넣을 때 계산해서 저장하므로 배지 판정은 마지막 한 줄만 보면 된다.
--
-- 기존 데이터는 건드리지 않으며 여러 번 실행해도 안전합니다.
-- ============================================================

USE `weplanet`;

CREATE TABLE IF NOT EXISTS `membership_period` (
                                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '멤버십 기간 PK',
                                                   `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
                                                   `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
                                                   `started_at` datetime(6) NOT NULL COMMENT '이 기간의 가입/갱신 시각',
    `expires_at` datetime(6) NOT NULL COMMENT '이 기간의 만료 시각',
    `streak_count` int NOT NULL DEFAULT 1 COMMENT '연속 몇 번째 기간인지(1=첫 가입)',
    `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
    PRIMARY KEY (`id`),
    KEY `idx_membership_period_latest` (`fan_id`, `artist_id`, `started_at`),
    KEY `fk_membership_period_artist` (`artist_id`),
    CONSTRAINT `fk_membership_period_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_membership_period_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
    CONSTRAINT `ck_membership_period` CHECK (`expires_at` > `started_at`),
    CONSTRAINT `ck_membership_streak` CHECK (`streak_count` >= 1)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='멤버십 가입/갱신 이력';

-- 이미 멤버십에 가입돼 있는 사람들의 "현재 기간"을 이력으로 옮겨둔다.
-- (이력이 하나도 없으면 다음 갱신이 1번째로 잡혀서 연속 계산이 어긋난다)
INSERT INTO `membership_period` (`fan_id`, `artist_id`, `started_at`, `expires_at`, `streak_count`, `created_at`)
SELECT m.`fan_id`, m.`artist_id`, m.`created_at`, m.`expires_at`, 1, NOW(6)
FROM `membership` m
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_period` p
    WHERE p.`fan_id` = m.`fan_id` AND p.`artist_id` = m.`artist_id`
);

-- [확인] membership 줄 수 이상이 나오면 정상입니다.
SELECT COUNT(*) AS periods FROM `membership_period`;