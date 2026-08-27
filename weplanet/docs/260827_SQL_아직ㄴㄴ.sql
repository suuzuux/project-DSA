-- ============================================================
-- WePlaNet 배지 기능(BADGE-1) 추가분 - 260827 정휘원
-- ------------------------------------------------------------
-- 260826_SQL.sql 로 DB를 이미 만들어둔 사람이, DB를 갈아엎지 않고
-- 배지 기능만 따라잡기 위한 스크립트다.
--
--   * DB를 새로 말 사람은 이 파일 말고 260827_SQL_수정2.sql 한 개만 실행하면 된다.
--   * 여러 번 실행해도 안전하다(멱등). 이미 반영돼 있으면 건너뛴다.
--   * 기존 데이터는 지우지 않는다. DROP TABLE 없음.
--
-- 실행법 (한글/이모지 때문에 charset 옵션 필수)
--   mysql -uroot -proot --default-character-set=utf8mb4 weplanet < docs/260827_SQL_아직ㄴㄴ.sql
--
-- 바뀌는 것 3가지
--   1) [신설]   fan_badge  - 배지 카탈로그(마스터). 배지 달성률의 분모가 된다.
--                            모든 아티스트 공통이라 artist_id 를 두지 않는다.
--                            표시는 image_url(이미지) 우선, 없으면 icon(이모지) 대체.
--                            지금은 전부 이모지고, 이미지가 준비되는 대로 채우면 된다.
--   2) [수정]   fan_badge_ownership.group_id -> artist_id
--                            FK 대상도 artist_groups -> users 로 바뀐다.
--                            팀 커뮤니티가 users.id(ARTIST) 기준으로 동작하고
--                            post/membership/chat_message 도 모두 users 를 보므로 통일.
--   3) [데이터] 배지 25종 등록 (일반 15 + 스페셜 10)
-- ============================================================

USE `weplanet`;


-- ------------------------------------------------------------
-- 1) fan_badge 신설
--    이미 있으면 건너뛴다.
-- ------------------------------------------------------------
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


-- ------------------------------------------------------------
-- 1-2) fan_badge.image_url 보강
--     위 CREATE 는 테이블이 이미 있으면 통째로 건너뛰기 때문에,
--     image_url 이 추가되기 전 버전으로 fan_badge 를 만들어둔 사람은
--     컬럼이 없는 상태로 남는다. 그래서 따로 확인해서 채워준다.
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS wp_badge_add_image_url;

DELIMITER $$

CREATE PROCEDURE wp_badge_add_image_url()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'fan_badge'
                     AND COLUMN_NAME = 'image_url') THEN

        ALTER TABLE `fan_badge`
            ADD COLUMN `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
                COMMENT '배지 이미지 경로. 있으면 이미지, 없으면 icon 이모지로 표시'
                AFTER `icon`;

        SELECT 'fan_badge.image_url : 추가 완료' AS result;
    ELSE
        SELECT 'fan_badge.image_url : 이미 있음 (건너뜀)' AS result;
    END IF;
END$$

DELIMITER ;

CALL wp_badge_add_image_url();

DROP PROCEDURE wp_badge_add_image_url;


-- ------------------------------------------------------------
-- 2) fan_badge_ownership : group_id -> artist_id
--    MySQL 은 ALTER 에 IF EXISTS 가 없어서 프로시저로 감싸 멱등화했다.
--    이미 artist_id 인 DB에서는 아무것도 하지 않고 메시지만 출력한다.
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS wp_badge_ownership_to_artist_id;

DELIMITER $$

CREATE PROCEDURE wp_badge_ownership_to_artist_id()
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'fan_badge_ownership'
                 AND COLUMN_NAME = 'group_id') THEN

        ALTER TABLE `fan_badge_ownership`
            DROP FOREIGN KEY `fk_fan_badge_group`,
            DROP INDEX `fk_fan_badge_group`,
            DROP INDEX `idx_fan_badge_count`,
            DROP INDEX `uk_fan_badge_ownership`,
            CHANGE COLUMN `group_id` `artist_id` BIGINT NOT NULL COMMENT '아티스트(users.id) - 커뮤니티 단위',
            ADD UNIQUE KEY `uk_fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`),
            ADD KEY `idx_fan_badge_count` (`fan_id`, `artist_id`, `badge_type`, `revoked_at`),
            ADD KEY `fk_fan_badge_artist` (`artist_id`),
            ADD CONSTRAINT `fk_fan_badge_artist`
                FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`);

        SELECT 'fan_badge_ownership : group_id -> artist_id 적용 완료' AS result;
    ELSE
        SELECT 'fan_badge_ownership : 이미 적용됨 (건너뜀)' AS result;
    END IF;
END$$

DELIMITER ;

CALL wp_badge_ownership_to_artist_id();

DROP PROCEDURE wp_badge_ownership_to_artist_id;


-- ------------------------------------------------------------
-- 3) 배지 카탈로그 25종 (일반 15 + 스페셜 10) - 전 아티스트 공통
--    badge_code 가 유니크라, INSERT IGNORE 로 여러 번 실행해도 중복되지 않는다.
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge`
  (`badge_code`, `badge_name`, `badge_type`, `icon`, `description`, `sort_order`, `created_at`)
VALUES
  -- 일반 배지 15종
  ('BASIC_FIRST_JOIN',    '커뮤니티 첫 가입',      'BASIC', '🎉', '커뮤니티에 처음 가입하면 획득',          1,  NOW(6)),
  ('BASIC_FIRST_POST',    '첫 게시글 작성',        'BASIC', '✍️', '팬 게시판에 첫 글을 쓰면 획득',       2,  NOW(6)),
  ('BASIC_COMMENT_5',     '댓글 5개 작성',         'BASIC', '💬', '이 커뮤니티에 댓글 5개를 쓰면 획득',  3,  NOW(6)),
  ('BASIC_MEDIA_VIEW',    '미디어 시청',           'BASIC', '🎬', 'Media 탭 콘텐츠를 보면 획득',         4,  NOW(6)),
  ('BASIC_DAY_100',       '가입 후 100일',         'BASIC', '💯', '가입 후 100일이 지나면 획득',    5,  NOW(6)),
  ('BASIC_DAY_200',       '가입 후 200일',         'BASIC', '📅', '가입 후 200일이 지나면 획득',    6,  NOW(6)),
  ('BASIC_DAY_300',       '가입 후 300일',         'BASIC', '🗓️', '가입 후 300일이 지나면 획득',    7,  NOW(6)),
  ('BASIC_LIKE_10',       '좋아요 10개',           'BASIC', '👍', '게시글에 좋아요를 10번 누르면 획득',      8,  NOW(6)),
  ('BASIC_LIKED_5',       '받은 좋아요 5개',       'BASIC', '❤️', '내 게시글이 좋아요 5개를 받으면 획득',    9,  NOW(6)),
  ('BASIC_FOLLOW_ARTIST', '아티스트 프로필 팔로우', 'BASIC', '⭐', '아티스트 프로필을 팔로우하면 획득',      10, NOW(6)),
  ('BASIC_SHOP_PURCHASE', '샵 구매',               'BASIC', '🛍️', 'Shop에서 상품을 구매하면 획득',          11, NOW(6)),
  ('BASIC_LIVE_VIEW',     '라이브 시청',           'BASIC', '📡', 'Live 방송을 보면 획득',              12, NOW(6)),
  ('BASIC_YEAR_1',        '커뮤니티 가입 후 1년',  'BASIC', '🥉', '가입 후 1년이 지나면 획득',     13, NOW(6)),
  ('BASIC_YEAR_2',        '커뮤니티 가입 후 2년',  'BASIC', '🥈', '가입 후 2년이 지나면 획득',     14, NOW(6)),
  ('BASIC_YEAR_3',        '커뮤니티 가입 후 3년',  'BASIC', '🥇', '가입 후 3년이 지나면 획득',     15, NOW(6)),

  -- 스페셜 배지 10종
  ('SPECIAL_DEBUT_1',       '아티스트 데뷔 1주년', 'SPECIAL', '🎂', '데뷔 1주년을 함께하면 획득',    1,  NOW(6)),
  ('SPECIAL_DEBUT_2',       '아티스트 데뷔 2주년', 'SPECIAL', '🎊', '데뷔 2주년을 함께하면 획득',    2,  NOW(6)),
  ('SPECIAL_DEBUT_3',       '아티스트 데뷔 3주년', 'SPECIAL', '🏆', '데뷔 3주년을 함께하면 획득',    3,  NOW(6)),
  ('SPECIAL_FOLLOWER_10',   '팔로워 10명 달성',    'SPECIAL', '👥', '내 팔로워가 10명이 되면 획득',           4,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_1',  '첫 멤버십 가입',      'SPECIAL', '💎', '멤버십에 처음 가입하면 획득',  5,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_2',  '멤버십 연속 2년',     'SPECIAL', '💠', '멤버십을 2년 연속 유지하면 획득',        6,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_3',  '멤버십 연속 3년',     'SPECIAL', '🔷', '멤버십을 3년 연속 유지하면 획득',        7,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_4',  '멤버십 연속 4년',     'SPECIAL', '🔶', '멤버십을 4년 연속 유지하면 획득',        8,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_5',  '멤버십 연속 5년',     'SPECIAL', '👑', '멤버십을 5년 연속 유지하면 획득',        9,  NOW(6)),
  ('SPECIAL_PROJECT_CREATE','프로젝트 등록 달성',  'SPECIAL', '🚀', '팬 프로젝트를 등록하면 획득',           10, NOW(6));


-- 확인용
SELECT badge_type AS 유형, COUNT(*) AS 개수 FROM `fan_badge` GROUP BY badge_type;
