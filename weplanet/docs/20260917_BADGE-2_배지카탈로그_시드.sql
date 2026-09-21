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
