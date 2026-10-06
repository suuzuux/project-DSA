-- ============================================================
-- WePlaNet 데모 데이터 4 - 아티스트·커뮤니티 추가, 공지글, 미디어, 일정
-- ------------------------------------------------------------
-- 실행 순서
--   1) weplanet_schema_full_reset_v2.sql / weplanet_demo_seed.sql  ※ 이미 돌린 DB면 생략
--   2) weplanet_demo_seed_2_시연보강.sql / weplanet_demo_seed_3_계정게시글추가.sql  ※ 이미 돌린 DB면 생략
--      (2·3 을 안 돌렸어도 이 파일은 돌아감 - 그 파일들의 계정·커뮤니티가 필요한 행만 자동으로 빠진다)
--   3) 이 파일 - 여러 번 돌려도 된다 (맨 앞에서 이 파일이 넣은 행을 지우고 다시 넣음)
--   4) docs/demo/demo_images 의 새 이미지 42개(demo_vela_*, demo_siwoo_*, demo_pastel_*, demo_nebula_media*, demo_harin_media*)를
--      서버 실행 폴더의 uploads/ 에 복사
--
-- 추가 계정 (비밀번호 공통 Test1234)
--   vela_official   그룹 VELA (보이그룹 4인, 블루웨이브뮤직) -> 멤버 준서·이안·도하·시현
--   siwoo_official  솔로 윤시우 (문빔컴퍼니)
--   pastel_official 그룹 PASTEL (걸그룹 듀오, 스타라이트엔터테인먼트) -> 멤버 지유·아린
--
-- 넣는 것
--   [1] 새 커뮤니티 3개 (아티스트 계정·그룹 정보·멤버·포털 프로필)
--   [2] 새 커뮤니티 가입·팔로우(기존 팬들) + 멤버십 (demo_fan15 는 3곳 모두 가입 + 멤버십)
--   [3] 새 커뮤니티 게시글 18개 + 댓글·아티스트 답글·좋아요 (자동 생성)
--   [4] 커뮤니티 공지 21개 - 새 커뮤니티 5곳(NEBULA·서하린 포함) 3개씩 + 기존 6곳 1개씩
--   [5] 홈페이지 공지 5개
--   [6] 미디어 20개(사진 30장) - 새 커뮤니티 5곳 4개씩 (멤버십 전용 1개씩) + 좋아요
--   [7] 아티스트 일정 15개 - 새 커뮤니티 5곳 3개씩
--   [8] 배지 - 새 커뮤니티 활동에 맞춰 지급
--
-- ※ MySQL Workbench 는 자동 커밋이 꺼져 있을 수 있어 맨 끝에 COMMIT 을 넣어 두었다
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
SET @old_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ------------------------------------------------------------
-- [0] 재실행 대비: 이 파일이 넣는 행을 먼저 지운다
--     users 1109~1111(아티스트), 1223~1228(멤버) / post 14001~14199 / comment 24001~24999
--     board_media 3101~3199 / board_media_files 3201~3299 / portal_notice 101~199 / site_notice 101~120 / artist_schedule 101~199
-- ------------------------------------------------------------
DELETE FROM `board_media_like` WHERE `board_id` BETWEEN 3101 AND 3199;
DELETE FROM `board_media_files` WHERE `id` BETWEEN 3201 AND 3299 OR `board_id` BETWEEN 3101 AND 3199;
DELETE FROM `board_media` WHERE `id` BETWEEN 3101 AND 3199;
DELETE FROM `portal_notice` WHERE `id` BETWEEN 101 AND 199;
DELETE FROM `site_notice` WHERE `id` BETWEEN 101 AND 120;
DELETE FROM `artist_schedule` WHERE `id` BETWEEN 101 AND 199;
DELETE FROM `comment_report` WHERE `comment_id` IN (SELECT `id` FROM `comment` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111)));
DELETE FROM `report` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111)) x);
DELETE FROM `comment` WHERE (`post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111))) AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111));
DELETE FROM `comment` WHERE `id` BETWEEN 24001 AND 24999 AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `id` BETWEEN 24001 AND 24999;
DELETE FROM `post_like` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111));
DELETE FROM `post_bookmark` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111));
DELETE FROM `post_attachment` WHERE `post_id` BETWEEN 14001 AND 14199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1109, 1110, 1111));
DELETE FROM `post` WHERE `id` BETWEEN 14001 AND 14199 OR `artist_id` IN (1109, 1110, 1111);
DELETE FROM `fan_badge_ownership` WHERE `artist_id` IN (1109, 1110, 1111);
DELETE FROM `membership_period` WHERE `artist_id` IN (1109, 1110, 1111);
DELETE FROM `membership` WHERE `artist_id` IN (1109, 1110, 1111);
DELETE FROM `user_follows` WHERE `following_id` IN (1109, 1110, 1111) OR `community_id` IN (1109, 1110, 1111);
DELETE FROM `community_members` WHERE `artist_id` IN (1109, 1110, 1111);
DELETE FROM `group_members` WHERE `group_id` IN (1109, 1110, 1111);
DELETE FROM `artist_profile` WHERE `artist_id` IN (1109, 1110, 1111, 1223, 1224, 1225, 1226, 1227, 1228);
DELETE FROM `artist_profiles` WHERE `user_id` IN (1109, 1110, 1111, 1223, 1224, 1225, 1226, 1227, 1228);
DELETE FROM `artist_groups` WHERE `id` IN (1109, 1110, 1111);
DELETE FROM `users` WHERE `id` BETWEEN 1223 AND 1228;
DELETE FROM `users` WHERE `id` IN (1109, 1110, 1111);

-- ------------------------------------------------------------
-- [1] 새 커뮤니티 3개 - VELA (보이그룹 4인) / 윤시우 (솔로) / PASTEL (걸그룹 듀오)
-- ------------------------------------------------------------
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`) VALUES
  (1109, 'vela_official',   '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST', 'ACTIVE', 102, 'VELA',   'VELA',   'vela_official@weplanet.test',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1110, 'siwoo_official',  '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST', 'ACTIVE', 103, '윤시우', '윤시우', 'siwoo_official@weplanet.test',  DATE_SUB(NOW(6), INTERVAL 150 DAY), DATE_SUB(NOW(6), INTERVAL 150 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1111, 'pastel_official', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST', 'ACTIVE', 101, 'PASTEL', 'PASTEL', 'pastel_official@weplanet.test', DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY));
-- 그룹 멤버 계정: 그룹 계정으로 로그인 -> 프로필 선택 -> 개인 비밀번호(Test1234)
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`) VALUES
  (1223, 'member_1109_vela1',   '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 102, '준서', '준서', 'member_1109_vela1@member.weplanet.local',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1224, 'member_1109_vela2',   '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 102, '이안', '이안', 'member_1109_vela2@member.weplanet.local',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1225, 'member_1109_vela3',   '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 102, '도하', '도하', 'member_1109_vela3@member.weplanet.local',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1226, 'member_1109_vela4',   '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 102, '시현', '시현', 'member_1109_vela4@member.weplanet.local',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1227, 'member_1111_pastel1', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 101, '지유', '지유', 'member_1111_pastel1@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1228, 'member_1111_pastel2', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 101, '아린', '아린', 'member_1111_pastel2@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY));
INSERT INTO `artist_profiles` (`user_id`, `agency_id`, `stage_name`, `debut_date`, `position`, `bio`, `profile_img`) VALUES
  (1109, 102, 'VELA', '2024-08-08', 'GROUP', '같은 방향으로 돛을 올린 네 소년, VELA 공식 커뮤니티 ⛵ 세일러 여러분 환영해요!', '/uploads/demo_vela_logo.png'),
  (1223, 102, '준서', '2024-08-08', 'LEADER', 'VELA의 준서입니다!', '/uploads/demo_vela_member1.png'),
  (1224, 102, '이안', '2024-08-08', 'VOCAL', 'VELA의 이안입니다!', '/uploads/demo_vela_member2.png'),
  (1225, 102, '도하', '2024-08-08', 'RAP', 'VELA의 도하입니다!', '/uploads/demo_vela_member3.png'),
  (1226, 102, '시현', '2024-08-08', 'DANCE', 'VELA의 시현입니다!', '/uploads/demo_vela_member4.png'),
  (1110, 103, '윤시우', '2021-12-03', 'SOLO', '담담하게, 오래 노래하고 싶은 윤시우입니다 🎸 시우랑 여러분 반가워요', '/uploads/demo_siwoo_logo.png'),
  (1111, 101, 'PASTEL', '2025-09-05', 'GROUP', '부드러운 색으로 하루를 물들이는 듀오 PASTEL 🎨 파스텔톤 모여라!', '/uploads/demo_pastel_logo.png'),
  (1227, 101, '지유', '2025-09-05', 'LEADER', 'PASTEL의 지유입니다!', '/uploads/demo_pastel_member1.png'),
  (1228, 101, '아린', '2025-09-05', 'VOCAL', 'PASTEL의 아린입니다!', '/uploads/demo_pastel_member2.png');
INSERT INTO `artist_groups` (`id`, `agency_id`, `name`, `name_en`, `fandom_name`, `debut_date`, `status`, `gender`, `member_count`, `nationality`, `category`, `created_at`, `updated_at`) VALUES
  (1109, 102, 'VELA',   'VELA',       '세일러',   '2024-08-08', 'ACTIVE', 'MALE',   4, 'KR', '아이돌',   DATE_SUB(NOW(6), INTERVAL 160 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1110, 103, '윤시우', 'YOON SIWOO', '시우랑',   '2021-12-03', 'ACTIVE', 'MALE',   1, 'KR', '솔로가수', DATE_SUB(NOW(6), INTERVAL 150 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (1111, 101, 'PASTEL', 'PASTEL',     '파스텔톤', '2025-09-05', 'ACTIVE', 'FEMALE', 2, 'KR', '아이돌',   DATE_SUB(NOW(6), INTERVAL 140 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY));
INSERT INTO `group_members` (`group_id`, `artist_id`, `is_leader`, `joined_at`) VALUES
  (1109, 1223, 1, '2024-08-08'),
  (1109, 1224, 0, '2024-08-08'),
  (1109, 1225, 0, '2024-08-08'),
  (1109, 1226, 0, '2024-08-08'),
  (1111, 1227, 1, '2025-09-05'),
  (1111, 1228, 0, '2025-09-05');
INSERT INTO `artist_profile` (`artist_id`, `intro`, `header_image_url`, `logo_image_url`, `created_at`, `updated_at`) VALUES
  (1109, '같은 방향으로 돛을 올린 네 소년, VELA 공식 커뮤니티 ⛵ 세일러 여러분 환영해요!', 'demo_vela_header.jpg', 'demo_vela_logo.png', NOW(), NOW()),
  (1223, 'VELA 준서 ⛵', NULL, 'demo_vela_member1.png', NOW(), NOW()),
  (1224, 'VELA 이안 ⛵', NULL, 'demo_vela_member2.png', NOW(), NOW()),
  (1225, 'VELA 도하 ⛵', NULL, 'demo_vela_member3.png', NOW(), NOW()),
  (1226, 'VELA 시현 ⛵', NULL, 'demo_vela_member4.png', NOW(), NOW()),
  (1110, '담담하게, 오래 노래하고 싶은 윤시우입니다 🎸 시우랑 여러분 반가워요', 'demo_siwoo_header.jpg', 'demo_siwoo_logo.png', NOW(), NOW()),
  (1111, '부드러운 색으로 하루를 물들이는 듀오 PASTEL 🎨 파스텔톤 모여라!', 'demo_pastel_header.jpg', 'demo_pastel_logo.png', NOW(), NOW()),
  (1227, 'PASTEL 지유 🎨', NULL, 'demo_pastel_member1.png', NOW(), NOW()),
  (1228, 'PASTEL 아린 🎨', NULL, 'demo_pastel_member2.png', NOW(), NOW());

-- ------------------------------------------------------------
-- [2] 새 커뮤니티 가입·팔로우 (days_ago 일 전, 단 회원가입 10분 뒤보다 이르지 않게) + 멤버십
--     없는 팬 계정(데모 2·3 을 안 돌린 DB)은 자동으로 빠진다
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_joins`;
CREATE TEMPORARY TABLE `tmp_joins` (`fan_id` bigint, `artist_id` bigint, `days_ago` int, `bio` varchar(30), PRIMARY KEY (`fan_id`, `artist_id`));
INSERT INTO `tmp_joins` VALUES
  -- VELA
  (1302, 1109, 95, '돛 올려 VELA ⛵'), (1306, 1109, 88, NULL), (1308, 1109, 80, '도하 랩 최고'), (1311, 1109, 66, NULL),
  (1315, 1109, 45, '모든 커뮤니티 가입 완료'), (1402, 1109, 20, NULL), (1404, 1109, 18, '준서 리더 최고'), (1411, 1109, 12, NULL),
  (1501, 1109, 70, NULL), (1504, 1109, 60, '이안 목소리 짱'), (1507, 1109, 50, NULL), (1510, 1109, 40, '포카 교환해요'),
  -- 윤시우
  (1301, 1110, 92, '시우 노래로 출근'), (1305, 1110, 85, NULL), (1309, 1110, 77, '기타 치는 시우 최고'), (1313, 1110, 63, NULL),
  (1315, 1110, 45, '모든 커뮤니티 가입 완료'), (1406, 1110, 19, NULL), (1413, 1110, 9, NULL),
  (1502, 1110, 75, '하린도 시우도'), (1505, 1110, 58, '새벽 감성엔 시우'), (1508, 1110, 47, NULL), (1511, 1110, 33, NULL),
  -- PASTEL
  (1303, 1111, 90, '파스텔 데뷔 축하'), (1307, 1111, 82, NULL), (1310, 1111, 74, '지유 최애'), (1314, 1111, 61, '콘서트 가자'),
  (1315, 1111, 45, '모든 커뮤니티 가입 완료'), (1409, 1111, 14, NULL), (1415, 1111, 7, 'パステル大好き'),
  (1503, 1111, 68, '아린 목소리 반함'), (1506, 1111, 55, NULL), (1509, 1111, 41, NULL), (1512, 1111, 28, '파스텔톤 신입');

INSERT INTO `community_members` (`fan_id`, `artist_id`, `joined_at`, `nickname`, `bio`, `updated_at`)
SELECT j.fan_id, j.artist_id,
       GREATEST(DATE_SUB(NOW(6), INTERVAL j.days_ago DAY), DATE_ADD(u.created_at, INTERVAL 10 MINUTE)),
       u.nickname, j.bio,
       GREATEST(DATE_SUB(NOW(6), INTERVAL j.days_ago DAY), DATE_ADD(u.created_at, INTERVAL 10 MINUTE))
FROM `tmp_joins` j
JOIN `users` u ON u.id = j.fan_id AND u.role = 'FAN';

INSERT IGNORE INTO `user_follows` (`follower_id`, `following_id`, `community_id`, `created_at`)
SELECT cm.fan_id, cm.artist_id, cm.artist_id, cm.joined_at
FROM `community_members` cm
WHERE cm.artist_id IN (1109, 1110, 1111);

DROP TEMPORARY TABLE IF EXISTS `tmp_joins`;

INSERT INTO `membership` (`created_at`, `expires_at`, `artist_id`, `fan_id`)
SELECT DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_ADD(DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), INTERVAL 1 YEAR), x.artist_id, x.fan_id
FROM (
            SELECT 1315 AS fan_id, 1109 AS artist_id, 44 AS days_ago
  UNION ALL SELECT 1315, 1110, 44
  UNION ALL SELECT 1315, 1111, 44
  UNION ALL SELECT 1308, 1109, 70
  UNION ALL SELECT 1309, 1110, 60
  UNION ALL SELECT 1310, 1111, 50
) x
JOIN `community_members` cm ON cm.fan_id = x.fan_id AND cm.artist_id = x.artist_id;
INSERT INTO `membership_period` (`fan_id`, `artist_id`, `started_at`, `expires_at`, `streak_count`, `created_at`)
SELECT m.fan_id, m.artist_id, m.created_at, m.expires_at, 1, m.created_at
FROM `membership` m
WHERE m.artist_id IN (1109, 1110, 1111);

-- ------------------------------------------------------------
-- [3] 새 커뮤니티 게시글 18개 (mins_ago 분 전) + 댓글·답글·좋아요 자동 생성
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_posts`;
CREATE TEMPORARY TABLE `tmp_posts` (
  `id` bigint PRIMARY KEY, `board_type` varchar(10), `artist_id` bigint, `author_id` bigint, `mins_ago` int,
  `title` varchar(200), `content` text
);
INSERT INTO `tmp_posts` VALUES
  -- VELA
  (14001, 'ARTIST', 1109, 1223, 26000, '세일러 여러분 안녕하세요 ⛵', 'VELA 리더 준서입니다! 공식 커뮤니티 오픈했어요. 여기서 자주 만나요!'),
  (14002, 'ARTIST', 1109, 1225, 11000, '작업실에서 랩 가사 쓰는 중', '이번 앨범 제 파트 가사 직접 썼어요. 세일러 생각하면서 썼으니까 기대해 주세요 ✍️'),
  (14003, 'ARTIST', 1109, 1226, 1500, '오늘 안무 영상 찍었어요 🕺', '챌린지 영상 곧 올라가요. 세일러도 같이 춰 줄 거죠?'),
  (14011, 'FAN', 1109, 1302, 18000, 'VELA 입덕 영업합니다', '라이브 실력이 진짜 미쳤어요. 음방 라이브 클립부터 보세요!'),
  (14012, 'FAN', 1109, 1501, 7200, '이안 고음 클립 모음', '이안 고음 파트만 모아 봤어요. 소름 주의 ⚠️'),
  (14013, 'FAN', 1109, 1411, 3000, '세일러 신입 인사', 'ECLIPSE 팬인데 VELA도 입덕했어요. 잘 부탁드려요!'),
  -- 윤시우
  (14021, 'ARTIST', 1110, 1110, 24000, '시우랑, 오랜만이에요', '긴 휴식 끝에 돌아왔어요. 기다려 줘서 정말 고마워요 🎸'),
  (14022, 'ARTIST', 1110, 1110, 9000, '새 앨범 트랙리스트 공개', '이번 앨범은 여섯 곡이에요. 그중 3번 트랙을 제일 아껴요'),
  (14023, 'ARTIST', 1110, 1110, 2200, '오늘 밤 라디오 출연해요 📻', '밤 10시 라디오에서 라이브 두 곡 불러요. 같이 들어요!'),
  (14031, 'FAN', 1110, 1301, 16000, '시우 컴백 환영해요', '1년 반 기다렸어요… 돌아와 줘서 고마워요'),
  (14032, 'FAN', 1110, 1505, 6500, '새벽에 듣는 시우 노래', '새벽 감성에는 역시 시우 어쿠스틱이죠'),
  (14033, 'FAN', 1110, 1413, 2000, '기타 코드 따 봤어요 🎸', '시우 타이틀곡 기타 코드 정리했어요. 같이 연습해요'),
  -- PASTEL
  (14041, 'ARTIST', 1111, 1227, 22000, '파스텔톤 첫 인사 🎨', '안녕하세요 PASTEL 지유예요! 데뷔하고 첫 커뮤니티라 너무 설레요'),
  (14042, 'ARTIST', 1111, 1228, 8500, '아린의 연습실 일기', '오늘은 하모니 연습만 4시간! 지유랑 목소리 합 맞추는 게 제일 재밌어요'),
  (14043, 'ARTIST', 1111, 1227, 1200, '이번 주 팬사인회 기다려요', '파스텔톤 만날 생각에 벌써 떨려요. 다들 조심히 와요 💗'),
  (14051, 'FAN', 1111, 1303, 15000, '파스텔 데뷔 무대 후기', '두 명인데 무대가 꽉 차요. 하모니가 진짜 예술이에요'),
  (14052, 'FAN', 1111, 1503, 5600, '아린 음색 무슨 일', '아린 목소리 듣고 바로 입덕했어요. 음색 장인'),
  (14053, 'FAN', 1111, 1409, 5000, '파스텔 팬사인회 응모했어요', '당첨되면 꼭 후기 올릴게요! 다들 응모하셨나요?');

INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`)
SELECT tp.id, tp.board_type, tp.artist_id, tp.author_id, tp.title, tp.content, 0, 0, DATE_SUB(NOW(6), INTERVAL tp.mins_ago MINUTE)
FROM `tmp_posts` tp
JOIN `users` u ON u.id = tp.author_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_posts`;

INSERT INTO `comment` (`id`, `content`, `created_at`, `author_id`, `post_id`, `parent_id`, `deleted_at`)
SELECT 24000 + ROW_NUMBER() OVER (ORDER BY p.id, cm.fan_id),
       ELT(1 + MOD(p.id * 3 + cm.fan_id * 7, 18),
           '와 진짜 최고예요 😍', '오늘도 덕분에 행복해요', '이거 보려고 하루 버텼어요', '저도 같은 생각이에요ㅋㅋ',
           '사진 너무 예뻐요 📸', '응원합니다!! 화이팅 🔥', '정보 감사합니다 🙏', '다음에도 꼭 올려주세요!',
           '눈물 나요ㅠㅠ', '완전 공감해요!! 👍', '저장했어요 💾', '같이 가요!!',
           '이 조합 미쳤다', '매일 보러 올게요', '오늘 하루 힘이 나요', '댓글 달려고 로그인했어요',
           '최고의 하루 보내세요 ☀️', '계속 응원할게요 💜'),
       LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(p.created_at, INTERVAL 5 + MOD(p.id * 11 + cm.fan_id * 17, 600) MINUTE)),
       cm.fan_id, p.id, NULL, NULL
FROM `post` p
JOIN `community_members` cm ON cm.artist_id = p.artist_id AND cm.joined_at < p.created_at
JOIN `users` u ON u.id = cm.fan_id AND u.role = 'FAN' AND u.status = 'ACTIVE'
WHERE p.id BETWEEN 14001 AND 14199
  AND cm.fan_id <> p.author_id
  AND (cm.fan_id BETWEEN 1301 AND 1315 OR cm.fan_id BETWEEN 1401 AND 1419 OR cm.fan_id BETWEEN 1501 AND 1512)
  AND MOD(p.id * 5 + cm.fan_id * 7, 3) <> 0;

INSERT INTO `comment` (`id`, `content`, `created_at`, `author_id`, `post_id`, `parent_id`, `deleted_at`)
SELECT 24500 + ROW_NUMBER() OVER (ORDER BY p.id),
       ELT(1 + MOD(p.id, 5), '고마워요 💕', '항상 응원해 줘서 고마워요!', '오늘도 좋은 하루 보내요 ☀️', '댓글 보고 힘 났어요!!', '우리 또 만나요 🫶'),
       LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(c.created_at, INTERVAL 30 MINUTE)),
       p.author_id, p.id, c.id, NULL
FROM `post` p
JOIN `comment` c ON c.id = (SELECT MIN(c2.id) FROM `comment` c2 WHERE c2.post_id = p.id AND c2.id BETWEEN 24001 AND 24499)
WHERE p.id BETWEEN 14001 AND 14199
  AND p.board_type = 'ARTIST';

INSERT INTO `post_like` (`created_at`, `post_id`, `user_id`)
SELECT LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(p.created_at, INTERVAL 3 + MOD(p.id * 7 + cm.fan_id * 13, 900) MINUTE)), p.id, cm.fan_id
FROM `post` p
JOIN `community_members` cm ON cm.artist_id = p.artist_id AND cm.joined_at < p.created_at
JOIN `users` u ON u.id = cm.fan_id AND u.role = 'FAN' AND u.status = 'ACTIVE'
WHERE p.id BETWEEN 14001 AND 14199
  AND cm.fan_id <> p.author_id
  AND (cm.fan_id BETWEEN 1301 AND 1315 OR cm.fan_id BETWEEN 1401 AND 1419 OR cm.fan_id BETWEEN 1501 AND 1512)
  AND MOD(p.id * 7 + cm.fan_id * 5, 3) <> 0;

UPDATE `post` p
SET p.like_count = (SELECT COUNT(*) FROM `post_like` l WHERE l.post_id = p.id)
WHERE p.id BETWEEN 14001 AND 14199;

-- ------------------------------------------------------------
-- [4] 커뮤니티 공지 (포털에서 소속사·아티스트가 쓰는 공지)
--     새 커뮤니티 5곳: 이용 안내(상단 고정) + 일정 + 멤버십 / 기존 6곳: 이벤트·일정 공지 1개씩 (고정 안 함)
-- ------------------------------------------------------------
INSERT INTO `portal_notice` (`id`, `artist_id`, `title`, `content`, `published`, `pinned`, `pin_order`, `created_at`, `updated_at`)
SELECT x.id, x.artist_id, x.title, x.content, 1, x.pinned, x.pin_order,
       DATE_SUB(NOW(), INTERVAL x.mins_ago MINUTE), DATE_SUB(NOW(), INTERVAL x.mins_ago MINUTE)
FROM (
  SELECT 101 AS id, 1107 AS artist_id, '[공지] NEBULA 커뮤니티 이용 안내' AS title,
         '스타더스트 여러분 안녕하세요!\n\nNEBULA 공식 커뮤니티에 오신 것을 환영합니다 🌌\n\n· 멤버와 팬 모두를 존중하는 글을 써 주세요.\n· 사생활 침해, 비방, 광고 글은 예고 없이 삭제될 수 있어요.\n· 불편한 글은 신고 버튼으로 알려 주세요.' AS content,
         1 AS pinned, 1 AS pin_order, 40000 AS mins_ago
  UNION ALL SELECT 102, 1107, '[일정] NEBULA 첫 단독 팬미팅 안내',
         '데뷔 6개월을 맞아 첫 단독 팬미팅을 엽니다!\n\n· 일시: 다음 달 둘째 주 토요일 오후 5시\n· 장소: 블루스퀘어 SOL트래블홀\n· 예매: 멤버십 선예매 후 일반 예매\n\n자세한 내용은 추후 공지로 안내드릴게요.', 0, NULL, 6000
  UNION ALL SELECT 103, 1107, '[멤버십] 스타더스트 멤버십 혜택 안내',
         '멤버십 회원에게는 아래 혜택을 드려요.\n\n· 미디어 탭 멤버십 전용 사진 공개\n· 멤버별 1:1 DM\n· 팬미팅 선예매', 0, NULL, 30000
  UNION ALL SELECT 104, 1108, '[공지] 하린별 커뮤니티 이용 안내',
         '하린별 여러분 반가워요!\n\n서하린 공식 커뮤니티는 서로를 따뜻하게 응원하는 공간이에요.\n· 비방, 사생활 침해 글은 삭제될 수 있어요.\n· 노래 커버, 후기 글은 언제나 환영해요 🎧', 1, 1, 38000
  UNION ALL SELECT 105, 1108, '[일정] 소극장 콘서트 〈새벽의 노래〉 안내',
         '서하린 소극장 콘서트가 열립니다!\n\n· 일시: 이번 달 마지막 주 금·토 오후 7시 30분\n· 장소: 홍대 웨스트브릿지\n· 예매: 멤버십 선예매 후 일반 예매', 0, NULL, 4500
  UNION ALL SELECT 106, 1108, '[이벤트] 신곡 가사 한 줄 이벤트',
         '신곡에 들어갈 가사 한 줄을 하린별이 직접 지어 주세요!\n\n댓글로 남겨 주신 문장 중 하나를 골라 실제 가사에 넣을 예정이에요 ✍️', 0, NULL, 1300
  UNION ALL SELECT 107, 1109, '[공지] VELA 커뮤니티 이용 안내',
         '세일러 여러분 안녕하세요!\n\nVELA 공식 커뮤니티에 오신 걸 환영해요 ⛵\n· 멤버와 팬을 존중하는 글을 써 주세요.\n· 불법 굿즈, 티켓 양도 글은 삭제됩니다.', 1, 1, 30000
  UNION ALL SELECT 108, 1109, '[일정] VELA 미니 2집 컴백 쇼케이스',
         'VELA 미니 2집 〈Tailwind〉 컴백 쇼케이스를 엽니다!\n\n· 일시: 다음 주 화요일 오후 8시\n· 장소: 예스24 라이브홀\n· 응모: 멤버십 회원 대상 추첨', 0, NULL, 3000
  UNION ALL SELECT 109, 1109, '[멤버십] 세일러 멤버십 혜택 안내',
         '세일러 멤버십 혜택\n\n· 멤버십 전용 미디어\n· 멤버별 1:1 DM\n· 쇼케이스 응모 자격', 0, NULL, 25000
  UNION ALL SELECT 110, 1110, '[공지] 시우랑 커뮤니티 이용 안내',
         '시우랑 여러분, 오랜만이에요.\n\n윤시우 공식 커뮤니티가 새로 열렸어요 🎸\n· 서로 존중하는 대화를 부탁드려요.\n· 커버 영상, 공연 후기 글을 기다릴게요.', 1, 1, 26000
  UNION ALL SELECT 111, 1110, '[일정] 라디오 · 페스티벌 출연 일정',
         '이번 주 윤시우 출연 일정이에요.\n\n· 목요일 밤 10시 라디오 라이브\n· 토요일 오후 4시 가을 음악 페스티벌', 0, NULL, 2400
  UNION ALL SELECT 112, 1110, '[멤버십] 시우랑 멤버십 혜택 안내',
         '시우랑 멤버십 혜택\n\n· 미공개 작업실 사진\n· 1:1 DM\n· 공연 선예매', 0, NULL, 20000
  UNION ALL SELECT 113, 1111, '[공지] PASTEL 커뮤니티 이용 안내',
         '파스텔톤 여러분 환영해요 🎨\n\nPASTEL 공식 커뮤니티는 서로를 다정하게 응원하는 공간이에요.\n· 비방, 사생활 침해 글은 삭제될 수 있어요.', 1, 1, 24000
  UNION ALL SELECT 114, 1111, '[이벤트] 데뷔 기념 팬사인회 응모 안내',
         'PASTEL 데뷔 기념 팬사인회를 엽니다!\n\n· 일시: 이번 주 일요일 오후 2시\n· 장소: 영등포 타임스퀘어\n· 응모: 공식 앨범 구매자 대상 추첨 (100명)', 0, NULL, 7000
  UNION ALL SELECT 115, 1111, '[멤버십] 파스텔톤 멤버십 혜택 안내',
         '파스텔톤 멤버십 혜택\n\n· 미공개 셀카\n· 멤버별 1:1 DM\n· 팬사인회 추가 응모권', 0, NULL, 18000
  -- 기존 커뮤니티
  UNION ALL SELECT 116, 1101, '[이벤트] #NOVA컴백 해시태그 총공 참여 안내',
         '스텔라 여러분! 컴백 기념 해시태그 총공이 진행 중이에요 🚀\n\n팬 게시판에 #NOVA컴백 을 넣어 글을 써 주시면 참여로 집계됩니다.\n· 1인 1일 3건까지 인정\n· 참여율로 순위가 정해져요!', 0, NULL, 2800
  UNION ALL SELECT 117, 1102, '[이벤트] 데뷔 3주년 팬레터 이벤트',
         '루미너스 여러분, LUMI 데뷔 3주년을 맞아 팬레터를 받아요 💌\n\n팬 게시판에 [팬레터] 말머리로 글을 남겨 주시면 멤버들이 직접 읽을 예정이에요.', 0, NULL, 5200
  UNION ALL SELECT 118, 1103, '[공지] 단독 콘서트 MD 사전 예약 안내',
         'ECLIPSE 단독 콘서트 MD 사전 예약이 열렸어요.\n\n· 기간: 오늘부터 7일간\n· 수령: 공연 당일 MD 부스에서 현장 수령\n· 굿즈샵 탭에서 주문할 수 있어요.', 0, NULL, 8600
  UNION ALL SELECT 119, 1104, '[일정] 데뷔 기념 라이브 방송 안내',
         'PRISM 데뷔 기념 라이브 방송을 진행합니다 🌈\n\n· 일시: 이번 주 금요일 밤 9시\n· 라이브 탭에서 바로 볼 수 있어요.', 0, NULL, 4000
  UNION ALL SELECT 120, 1105, '[공지] 소극장 콘서트 티켓 오픈',
         '한유리 소극장 콘서트 티켓이 오픈됩니다 🎹\n\n· 멤버십 선예매: 내일 오후 8시\n· 일반 예매: 모레 오후 8시', 0, NULL, 6600
  UNION ALL SELECT 121, 1106, '[공지] 도쿄 팬미팅 사진 공개',
         'カイトモ 여러분, 도쿄 팬미팅 현장 사진을 미디어 탭에 올렸어요 📸\n\n함께해 주셔서 감사합니다. またね!', 0, NULL, 3600
) x
JOIN `users` u ON u.id = x.artist_id;

-- ------------------------------------------------------------
-- [5] 홈페이지 공지 (최고관리자 작성)
-- ------------------------------------------------------------
INSERT INTO `site_notice` (`id`, `author_id`, `title`, `category`, `content`, `published`, `publish_at`, `pinned`, `pin_order`, `created_at`, `updated_at`) VALUES
  (101, 1001, '신규 아티스트 입점 안내 - NEBULA · 서하린 · VELA · 윤시우 · PASTEL', 'GENERAL',
   'WePlaNet 에 다섯 아티스트의 공식 커뮤니티가 새로 열렸습니다 🎉\n\n· NEBULA (문빔컴퍼니)\n· 서하린 (스타라이트엔터테인먼트)\n· VELA (블루웨이브뮤직)\n· 윤시우 (문빔컴퍼니)\n· PASTEL (스타라이트엔터테인먼트)\n\n지금 커뮤니티 둘러보기에서 가입해 보세요!',
   1, NULL, 1, 2, DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (102, 1001, '해시태그 총공 이벤트가 시작됐어요 🔥', 'EVENT',
   '응원하는 아티스트의 해시태그를 넣어 팬 게시판에 글을 써 주세요.\n\n· 참여율(참여한 가입자 ÷ 전체 가입자)로 순위를 정해요.\n· 1인 1일 3건까지 집계돼요.\n· 실시간 순위는 홈 배너에서 확인할 수 있어요.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 3 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (103, 1002, '개인정보 처리방침 개정 안내', 'UPDATE',
   '개인정보 처리방침이 일부 개정됩니다.\n\n· 정산 계좌 정보 암호화 보관 항목 추가\n· 소셜 로그인(카카오·구글·라인) 수집 항목 정리\n\n개정된 방침은 공지일로부터 7일 뒤 적용됩니다.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 6 DAY), DATE_SUB(NOW(6), INTERVAL 6 DAY)),
  (104, 1003, '추석 연휴 굿즈 배송 안내', 'GENERAL',
   '추석 연휴 기간에는 택배사 휴무로 굿즈 배송이 늦어질 수 있습니다.\n\n연휴 전 마지막 출고는 연휴 시작 이틀 전 오후 2시 주문 건까지이며, 이후 주문은 연휴가 끝난 뒤 차례대로 출고됩니다.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 12 DAY), DATE_SUB(NOW(6), INTERVAL 12 DAY)),
  (105, 1004, '모바일 화면 개선 업데이트', 'UPDATE',
   '휴대폰에서도 편하게 쓸 수 있도록 화면을 개선했어요.\n\n· 헤더와 커뮤니티 레이아웃 정리\n· 굿즈샵 목록 한 줄 두 개 보기\n· 글쓰기 에디터 다크 모드 글자색 수정',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_SUB(NOW(6), INTERVAL 1 DAY));

-- ------------------------------------------------------------
-- [6] 미디어 (소속사 담당자가 업로드) - 새 커뮤니티 5곳 × 4개, 4번째는 멤버십 전용
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_media_comm`;
CREATE TEMPORARY TABLE `tmp_media_comm` (`base_id` bigint, `artist_id` bigint, `uploader_id` bigint, `name` varchar(50), `day_shift` int);
INSERT INTO `tmp_media_comm` VALUES
  (3101, 1107, 1013, 'NEBULA', 0),
  (3105, 1108, 1011, '서하린', 1),
  (3109, 1109, 1012, 'VELA',   2),
  (3113, 1110, 1013, '윤시우', 3),
  (3117, 1111, 1011, 'PASTEL', 4);

INSERT INTO `board_media` (`id`, `group_id`, `uploader_id`, `title`, `content`, `created_at`, `updated_at`, `like_count`, `membership_only`)
SELECT c.base_id + t.idx, c.artist_id, c.uploader_id,
       CONCAT(IF(t.membership_only = 1, '[MEMBERSHIP] ', ''), c.name, ' ', t.title), t.content,
       DATE_SUB(NOW(6), INTERVAL (t.days_ago + c.day_shift) * 1440 + 37 * t.idx MINUTE),
       DATE_SUB(NOW(6), INTERVAL (t.days_ago + c.day_shift) * 1440 + 37 * t.idx MINUTE),
       0, t.membership_only
FROM `tmp_media_comm` c
JOIN `users` u ON u.id = c.artist_id
CROSS JOIN (
            SELECT 0 AS idx, '뮤직비디오 비하인드 컷' AS title, '촬영 현장에서 찍은 비하인드 사진을 공개합니다 🎬' AS content, 2 AS days_ago, 0 AS membership_only
  UNION ALL SELECT 1, '음악방송 출근길', '오늘도 출근길 응원 와 주셔서 감사합니다!', 6, 0
  UNION ALL SELECT 2, '화보 촬영 현장', '매거진 화보 촬영 현장 스케치 📷', 11, 0
  UNION ALL SELECT 3, '미공개 셀카', '멤버십 회원에게만 공개하는 미공개 셀카 💌', 16, 1
) t;

DROP TEMPORARY TABLE IF EXISTS `tmp_media_comm`;

INSERT INTO `board_media_files` (`id`, `board_id`, `original_name`, `stored_name`, `content_type`, `media_type`, `file_size`, `sort_order`, `created_at`)
SELECT x.id, x.board_id, x.original_name, x.stored_name, 'image/jpeg', 'IMAGE', x.file_size, x.sort_order, m.created_at
FROM (
            SELECT 3201 AS id, 3101 AS board_id, 'nebula_1_1.jpg' AS original_name, 'demo_nebula_media3101_1.jpg' AS stored_name, 58010 AS file_size, 0 AS sort_order
  UNION ALL SELECT 3202, 3101, 'nebula_1_2.jpg', 'demo_nebula_media3101_2.jpg', 57666, 1
  UNION ALL SELECT 3203, 3102, 'nebula_2_1.jpg', 'demo_nebula_media3102_1.jpg', 57125, 0
  UNION ALL SELECT 3204, 3103, 'nebula_3_1.jpg', 'demo_nebula_media3103_1.jpg', 56204, 0
  UNION ALL SELECT 3205, 3103, 'nebula_3_2.jpg', 'demo_nebula_media3103_2.jpg', 56235, 1
  UNION ALL SELECT 3206, 3104, 'nebula_4_1.jpg', 'demo_nebula_media3104_1.jpg', 61581, 0
  UNION ALL SELECT 3207, 3105, 'harin_1_1.jpg',  'demo_harin_media3105_1.jpg',  54492, 0
  UNION ALL SELECT 3208, 3105, 'harin_1_2.jpg',  'demo_harin_media3105_2.jpg',  54409, 1
  UNION ALL SELECT 3209, 3106, 'harin_2_1.jpg',  'demo_harin_media3106_1.jpg',  53144, 0
  UNION ALL SELECT 3210, 3107, 'harin_3_1.jpg',  'demo_harin_media3107_1.jpg',  52814, 0
  UNION ALL SELECT 3211, 3107, 'harin_3_2.jpg',  'demo_harin_media3107_2.jpg',  52567, 1
  UNION ALL SELECT 3212, 3108, 'harin_4_1.jpg',  'demo_harin_media3108_1.jpg',  57058, 0
  UNION ALL SELECT 3213, 3109, 'vela_1_1.jpg',   'demo_vela_media3109_1.jpg',   56944, 0
  UNION ALL SELECT 3214, 3109, 'vela_1_2.jpg',   'demo_vela_media3109_2.jpg',   56557, 1
  UNION ALL SELECT 3215, 3110, 'vela_2_1.jpg',   'demo_vela_media3110_1.jpg',   56287, 0
  UNION ALL SELECT 3216, 3111, 'vela_3_1.jpg',   'demo_vela_media3111_1.jpg',   54745, 0
  UNION ALL SELECT 3217, 3111, 'vela_3_2.jpg',   'demo_vela_media3111_2.jpg',   54674, 1
  UNION ALL SELECT 3218, 3112, 'vela_4_1.jpg',   'demo_vela_media3112_1.jpg',   60813, 0
  UNION ALL SELECT 3219, 3113, 'siwoo_1_1.jpg',  'demo_siwoo_media3113_1.jpg',  51924, 0
  UNION ALL SELECT 3220, 3113, 'siwoo_1_2.jpg',  'demo_siwoo_media3113_2.jpg',  52171, 1
  UNION ALL SELECT 3221, 3114, 'siwoo_2_1.jpg',  'demo_siwoo_media3114_1.jpg',  51794, 0
  UNION ALL SELECT 3222, 3115, 'siwoo_3_1.jpg',  'demo_siwoo_media3115_1.jpg',  50793, 0
  UNION ALL SELECT 3223, 3115, 'siwoo_3_2.jpg',  'demo_siwoo_media3115_2.jpg',  50522, 1
  UNION ALL SELECT 3224, 3116, 'siwoo_4_1.jpg',  'demo_siwoo_media3116_1.jpg',  55687, 0
  UNION ALL SELECT 3225, 3117, 'pastel_1_1.jpg', 'demo_pastel_media3117_1.jpg', 42533, 0
  UNION ALL SELECT 3226, 3117, 'pastel_1_2.jpg', 'demo_pastel_media3117_2.jpg', 42535, 1
  UNION ALL SELECT 3227, 3118, 'pastel_2_1.jpg', 'demo_pastel_media3118_1.jpg', 42006, 0
  UNION ALL SELECT 3228, 3119, 'pastel_3_1.jpg', 'demo_pastel_media3119_1.jpg', 40894, 0
  UNION ALL SELECT 3229, 3119, 'pastel_3_2.jpg', 'demo_pastel_media3119_2.jpg', 41077, 1
  UNION ALL SELECT 3230, 3120, 'pastel_4_1.jpg', 'demo_pastel_media3120_1.jpg', 45064, 0
) x
JOIN `board_media` m ON m.id = x.board_id;

-- 미디어 좋아요: 공개 미디어는 가입자 중 일부, 멤버십 전용은 멤버십 회원만
INSERT INTO `board_media_like` (`board_id`, `user_id`, `created_at`)
SELECT m.id, cm.fan_id, LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(m.created_at, INTERVAL 10 + MOD(m.id * 7 + cm.fan_id * 13, 700) MINUTE))
FROM `board_media` m
JOIN `community_members` cm ON cm.artist_id = m.group_id AND cm.joined_at < m.created_at
JOIN `users` u ON u.id = cm.fan_id AND u.role = 'FAN' AND u.status = 'ACTIVE'
WHERE m.id BETWEEN 3101 AND 3199
  AND (cm.fan_id BETWEEN 1301 AND 1315 OR cm.fan_id BETWEEN 1401 AND 1419 OR cm.fan_id BETWEEN 1501 AND 1512)
  AND (
        (m.membership_only = 0 AND MOD(m.id * 3 + cm.fan_id * 7, 3) <> 0)
     OR (m.membership_only = 1 AND EXISTS (SELECT 1 FROM `membership` ms WHERE ms.fan_id = cm.fan_id AND ms.artist_id = m.group_id))
  );

UPDATE `board_media` m
SET m.like_count = (SELECT COUNT(*) FROM `board_media_like` l WHERE l.board_id = m.id)
WHERE m.id BETWEEN 3101 AND 3199;

-- ------------------------------------------------------------
-- [7] 아티스트 일정 - 새 커뮤니티 5곳 × 3개 (지난 일정 1 + 다가오는 일정 2)
-- ------------------------------------------------------------
INSERT INTO `artist_schedule` (`id`, `artist_id`, `category`, `title`, `description`, `location`, `ticket_url`, `schedule_at`, `created_at`, `updated_at`)
SELECT x.id, x.artist_id, x.category, x.title, x.description, x.location, NULL,
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.day_offset * 24 + x.hour_at HOUR), NOW(), NOW()
FROM (
            SELECT 101 AS id, 1107 AS artist_id, 'TV_BROADCAST' AS category, 'NEBULA 쇼! 음악중심 출연' AS title, '스타더스트 실시간 응원 부탁해요!' AS description, 'MBC 상암 공개홀' AS location, -3 AS day_offset, 15 AS hour_at
  UNION ALL SELECT 102, 1107, 'YOUTUBE', 'NEBULA 자체 예능 〈네뷸라 로그〉 공개', '매주 공개되는 네뷸라 브이로그', NULL, 1, 18
  UNION ALL SELECT 103, 1107, 'CONCERT', 'NEBULA 첫 단독 팬미팅', '멤버십 선예매 진행 중', '블루스퀘어 SOL트래블홀', 12, 17
  UNION ALL SELECT 104, 1108, 'RADIO', '서하린 라디오 게스트 출연', '라이브 두 곡을 들려드려요', 'KBS 쿨FM', -2, 22
  UNION ALL SELECT 105, 1108, 'OTHER', '서하린 공식 라이브 방송', '듣고 싶은 노래 신청 받아요', NULL, 2, 22
  UNION ALL SELECT 106, 1108, 'CONCERT', '서하린 소극장 콘서트 〈새벽의 노래〉', '이틀간 진행', '홍대 웨스트브릿지', 20, 19
  UNION ALL SELECT 107, 1109, 'PHOTO_MAGAZINE', 'VELA 매거진 화보 공개', '가을호 커버 화보', NULL, -5, 10
  UNION ALL SELECT 108, 1109, 'CONCERT', 'VELA 미니 2집 컴백 쇼케이스', '멤버십 회원 추첨', '예스24 라이브홀', 6, 20
  UNION ALL SELECT 109, 1109, 'TV_BROADCAST', 'VELA 엠카운트다운 컴백 무대', '첫 컴백 무대!', 'CJ ENM 센터', 8, 18
  UNION ALL SELECT 110, 1110, 'RADIO', '윤시우 라디오 라이브', '밤 10시 라이브 두 곡', 'MBC FM4U', 0, 22
  UNION ALL SELECT 111, 1110, 'CONCERT', '가을 음악 페스티벌 출연', '야외 스테이지 오후 4시', '난지 한강공원', 3, 16
  UNION ALL SELECT 112, 1110, 'BIRTHDAY', '윤시우 생일', '시우랑과 함께하는 생일 🎂', NULL, 25, 0
  UNION ALL SELECT 113, 1111, 'AWARDS', '올해의 신인상 후보 발표', '파스텔톤 투표 부탁해요!', NULL, -4, 12
  UNION ALL SELECT 114, 1111, 'OTHER', 'PASTEL 데뷔 기념 팬사인회', '앨범 구매자 추첨 100명', '영등포 타임스퀘어', 4, 14
  UNION ALL SELECT 115, 1111, 'YOUTUBE', 'PASTEL 하모니 커버 영상 공개', '듀오 하모니 커버', NULL, 9, 18
) x
JOIN `users` u ON u.id = x.artist_id;

-- ------------------------------------------------------------
-- [8] 배지 - 새 커뮤니티(VELA·윤시우·PASTEL) 활동 기준
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT cm.fan_id, cm.artist_id, b.badge_code, b.badge_name, b.badge_type, cm.joined_at, cm.joined_at
FROM `community_members` cm
JOIN `users` u ON u.id = cm.fan_id AND u.role = 'FAN'
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_JOIN'
WHERE cm.artist_id IN (1109, 1110, 1111);

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(p.created_at), MIN(p.created_at)
FROM `post` p
JOIN `community_members` cm ON cm.fan_id = p.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_POST'
WHERE p.board_type = 'FAN' AND p.artist_id IN (1109, 1110, 1111)
GROUP BY p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MAX(c.created_at), MAX(c.created_at)
FROM `comment` c
JOIN `post` p ON p.id = c.post_id
JOIN `community_members` cm ON cm.fan_id = c.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_COMMENT_5'
WHERE p.artist_id IN (1109, 1110, 1111) AND c.deleted_at IS NULL
GROUP BY c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type
HAVING COUNT(*) >= 5;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT l.user_id, m.group_id, b.badge_code, b.badge_name, b.badge_type, MIN(l.created_at), MIN(l.created_at)
FROM `board_media_like` l
JOIN `board_media` m ON m.id = l.board_id
JOIN `community_members` cm ON cm.fan_id = l.user_id AND cm.artist_id = m.group_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_MEDIA_VIEW'
WHERE m.id BETWEEN 3101 AND 3199
GROUP BY l.user_id, m.group_id, b.badge_code, b.badge_name, b.badge_type;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT m.fan_id, m.artist_id, b.badge_code, b.badge_name, b.badge_type, m.created_at, m.created_at
FROM `membership` m
JOIN `fan_badge` b ON b.badge_code = 'SPECIAL_MEMBERSHIP_1'
WHERE m.artist_id IN (1109, 1110, 1111);

COMMIT;
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- ------------------------------------------------------------
-- [확인] 넣은 데이터 개수
-- ------------------------------------------------------------
SELECT '새 아티스트 커뮤니티' AS 항목, COUNT(*) AS 개수 FROM `users` WHERE `id` IN (1109, 1110, 1111)
UNION ALL SELECT '새 커뮤니티 가입자', COUNT(*) FROM `community_members` WHERE `artist_id` IN (1109, 1110, 1111)
UNION ALL SELECT '추가 게시글', COUNT(*) FROM `post` WHERE `id` BETWEEN 14001 AND 14199
UNION ALL SELECT '추가 댓글(답글 포함)', COUNT(*) FROM `comment` WHERE `id` BETWEEN 24001 AND 24999
UNION ALL SELECT '커뮤니티 공지', COUNT(*) FROM `portal_notice` WHERE `id` BETWEEN 101 AND 199
UNION ALL SELECT '홈페이지 공지', COUNT(*) FROM `site_notice` WHERE `id` BETWEEN 101 AND 120
UNION ALL SELECT '미디어 게시물', COUNT(*) FROM `board_media` WHERE `id` BETWEEN 3101 AND 3199
UNION ALL SELECT '미디어 사진', COUNT(*) FROM `board_media_files` WHERE `id` BETWEEN 3201 AND 3299
UNION ALL SELECT '아티스트 일정', COUNT(*) FROM `artist_schedule` WHERE `id` BETWEEN 101 AND 199;
