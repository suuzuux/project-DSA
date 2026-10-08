-- WePlaNet 데모 데이터 3 - 팬·아티스트 계정, 게시글, 댓글, 좋아요 추가 (재실행 가능, 새 이미지는 uploads/ 에 복사).

USE `weplanet`;
SET NAMES utf8mb4;
SET @old_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- [0] 재실행 대비: 이 파일이 넣는 행을 먼저 지운다.
DELETE FROM `comment_report` WHERE `comment_id` IN (SELECT `id` FROM `comment` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108)));
DELETE FROM `report` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108)) x);
DELETE FROM `comment` WHERE (`post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108))) AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108));
DELETE FROM `comment` WHERE `id` BETWEEN 23001 AND 23999 AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `id` BETWEEN 23001 AND 23999;
DELETE FROM `post_like` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108));
DELETE FROM `post_bookmark` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108));
DELETE FROM `post_attachment` WHERE `post_id` BETWEEN 13001 AND 13199 OR `post_id` IN (SELECT `id` FROM `post` WHERE `artist_id` IN (1107, 1108));
DELETE FROM `hashtag_event_entry` WHERE `post_id` BETWEEN 13001 AND 13199;
DELETE FROM `post` WHERE `id` BETWEEN 13001 AND 13199 OR `artist_id` IN (1107, 1108);
DELETE FROM `fan_badge_ownership` WHERE `fan_id` BETWEEN 1501 AND 1512 OR `artist_id` IN (1107, 1108);
DELETE FROM `membership_period` WHERE `fan_id` BETWEEN 1501 AND 1512 OR `artist_id` IN (1107, 1108);
DELETE FROM `membership` WHERE `fan_id` BETWEEN 1501 AND 1512 OR `artist_id` IN (1107, 1108);
DELETE FROM `user_follows` WHERE `follower_id` BETWEEN 1501 AND 1512 OR `following_id` IN (1107, 1108) OR `community_id` IN (1107, 1108);
DELETE FROM `community_members` WHERE `fan_id` BETWEEN 1501 AND 1512 OR `artist_id` IN (1107, 1108);
DELETE FROM `group_members` WHERE `group_id` IN (1107, 1108);
DELETE FROM `artist_profile` WHERE `artist_id` IN (1107, 1108, 1219, 1220, 1221, 1222);
DELETE FROM `artist_profiles` WHERE `user_id` IN (1107, 1108, 1219, 1220, 1221, 1222);
DELETE FROM `artist_groups` WHERE `id` IN (1107, 1108);
DELETE FROM `users` WHERE `id` IN (1219, 1220, 1221, 1222);
DELETE FROM `users` WHERE `id` IN (1107, 1108) OR `id` BETWEEN 1501 AND 1512;

-- [1] 새 커뮤니티 2개 - NEBULA (걸그룹 4인) / 서하린 (솔로)
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`) VALUES
  (1107, 'nebula_official', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST', 'ACTIVE', 103, 'NEBULA', 'NEBULA', 'nebula_official@weplanet.test', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (1108, 'harin_official', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST', 'ACTIVE', 101, '서하린', '서하린', 'harin_official@weplanet.test', DATE_SUB(NOW(6), INTERVAL 170 DAY), DATE_SUB(NOW(6), INTERVAL 170 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY));
-- 그룹 멤버 계정 (그룹 로그인 후 프로필 선택, 개인 비밀번호 Test1234)
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`) VALUES
  (1219, 'member_1107_nebula1', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 103, '유나', '유나', 'member_1107_nebula1@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (1220, 'member_1107_nebula2', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 103, '리아', '리아', 'member_1107_nebula2@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (1221, 'member_1107_nebula3', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 103, '세린', '세린', 'member_1107_nebula3@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (1222, 'member_1107_nebula4', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'ARTIST_MEMBER', 'ACTIVE', 103, '하은', '하은', 'member_1107_nebula4@member.weplanet.local', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY));
INSERT INTO `artist_profiles` (`user_id`, `agency_id`, `stage_name`, `debut_date`, `position`, `bio`, `profile_img`) VALUES
  (1107, 103, 'NEBULA', '2025-04-10', 'GROUP', '우주를 떠도는 네 개의 빛, NEBULA 공식 커뮤니티입니다 🌌 스타더스트 환영해요!', '/uploads/demo_nebula_logo.png'),
  (1219, 103, '유나', '2025-04-10', 'LEADER', 'NEBULA의 유나입니다!', '/uploads/demo_nebula_member1.png'),
  (1220, 103, '리아', '2025-04-10', 'VOCAL', 'NEBULA의 리아입니다!', '/uploads/demo_nebula_member2.png'),
  (1221, 103, '세린', '2025-04-10', 'DANCE', 'NEBULA의 세린입니다!', '/uploads/demo_nebula_member3.png'),
  (1222, 103, '하은', '2025-04-10', 'RAP', 'NEBULA의 하은입니다!', '/uploads/demo_nebula_member4.png'),
  (1108, 101, '서하린', '2023-10-21', 'SOLO', '노래로 하루를 위로하는 싱어송라이터 서하린입니다 🎧', '/uploads/demo_harin_logo.png');
INSERT INTO `artist_groups` (`id`, `agency_id`, `name`, `name_en`, `fandom_name`, `debut_date`, `status`, `gender`, `member_count`, `nationality`, `category`, `created_at`, `updated_at`) VALUES
  (1107, 103, 'NEBULA', 'NEBULA', '스타더스트', '2025-04-10', 'ACTIVE', 'FEMALE', 4, 'KR', '아이돌', DATE_SUB(NOW(6), INTERVAL 180 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (1108, 101, '서하린', 'SEO HARIN', '하린별', '2023-10-21', 'ACTIVE', 'FEMALE', 1, 'KR', '솔로가수', DATE_SUB(NOW(6), INTERVAL 170 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY));
INSERT INTO `group_members` (`group_id`, `artist_id`, `is_leader`, `joined_at`) VALUES
  (1107, 1219, 1, '2025-04-10'),
  (1107, 1220, 0, '2025-04-10'),
  (1107, 1221, 0, '2025-04-10'),
  (1107, 1222, 0, '2025-04-10');
INSERT INTO `artist_profile` (`artist_id`, `intro`, `header_image_url`, `logo_image_url`, `created_at`, `updated_at`) VALUES
  (1107, '우주를 떠도는 네 개의 빛, NEBULA 공식 커뮤니티입니다 🌌 스타더스트 환영해요!', 'demo_nebula_header.jpg', 'demo_nebula_logo.png', NOW(), NOW()),
  (1219, 'NEBULA 유나 🌌', NULL, 'demo_nebula_member1.png', NOW(), NOW()),
  (1220, 'NEBULA 리아 🌌', NULL, 'demo_nebula_member2.png', NOW(), NOW()),
  (1221, 'NEBULA 세린 🌌', NULL, 'demo_nebula_member3.png', NOW(), NOW()),
  (1222, 'NEBULA 하은 🌌', NULL, 'demo_nebula_member4.png', NOW(), NOW()),
  (1108, '노래로 하루를 위로하는 싱어송라이터 서하린입니다 🎧', 'demo_harin_header.jpg', 'demo_harin_logo.png', NOW(), NOW());

-- [2] 새 팬 12명 (1501~1512) + 커뮤니티 가입·팔로우 + 멤버십
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `last_login_at`, `created_at`, `updated_at`)
SELECT x.id, x.username, '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, x.real_name, x.nickname,
       CONCAT(x.username, '@weplanet.test'),
       DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_SUB(NOW(6), INTERVAL x.login_mins MINUTE),
       DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_SUB(NOW(6), INTERVAL x.login_mins MINUTE)
FROM (
            SELECT 1501 AS id, 'demo_fan16' AS username, '김우주' AS real_name, '우주먼지' AS nickname, 118 AS days_ago, 90 AS login_mins
  UNION ALL SELECT 1502, 'demo_fan17', '이하린', '하린바라기', 110, 300
  UNION ALL SELECT 1503, 'demo_fan18', '박은하', '은하수산책', 102, 45
  UNION ALL SELECT 1504, 'demo_fan19', '최별', '별똥별', 95, 1500
  UNION ALL SELECT 1505, 'demo_fan20', '정새벽', '새벽감성', 88, 700
  UNION ALL SELECT 1506, 'demo_fan21', '한무대', '무대장인', 80, 2600
  UNION ALL SELECT 1507, 'demo_fan22', '오일기', '덕질일기장', 72, 4200
  UNION ALL SELECT 1508, 'demo_fan23', '윤단장', '응원단장', 65, 130
  UNION ALL SELECT 1509, 'demo_fan24', '서노래', '노래방요정', 58, 820
  UNION ALL SELECT 1510, 'demo_fan25', '강포카', '포토카드부자', 50, 60
  UNION ALL SELECT 1511, 'demo_fan26', '문콘서', '콘서트러버', 44, 1900
  UNION ALL SELECT 1512, 'demo_fan27', '임밤', '밤하늘지기', 38, 240
) x;

-- 커뮤니티 가입 (days_ago 일 전, 회원가입 10분 뒤보다 이르지 않게)
DROP TEMPORARY TABLE IF EXISTS `tmp_joins`;
CREATE TEMPORARY TABLE `tmp_joins` (`fan_id` bigint, `artist_id` bigint, `days_ago` int, `bio` varchar(30), PRIMARY KEY (`fan_id`, `artist_id`));
INSERT INTO `tmp_joins` VALUES
  -- NEBULA
  (1501, 1107, 110, '네뷸라 데뷔 때부터 🌌'), (1503, 1107, 95, '은하수처럼 반짝'), (1504, 1107, 88, NULL), (1506, 1107, 72, '세린 춤선 최고'),
  (1508, 1107, 58, '응원은 내가 책임진다'), (1510, 1107, 44, '포카 교환 환영'), (1512, 1107, 30, NULL),
  (1301, 1107, 62, '늘 응원해요!'), (1304, 1107, 58, NULL), (1310, 1107, 51, '유나 최애'), (1315, 1107, 45, '모든 커뮤니티 가입 완료'),
  -- 서하린
  (1502, 1108, 105, '하린 노래로 버티는 중'), (1505, 1108, 80, '새벽에 듣는 하린'), (1507, 1108, 65, NULL), (1509, 1108, 50, '노래방 18번은 하린'),
  (1511, 1108, 38, '콘서트 다 갈 거예요'), (1512, 1108, 30, NULL),
  (1303, 1108, 64, '하린별 입문'), (1307, 1108, 57, '피아노로 하린 노래 쳐요'), (1312, 1108, 49, NULL), (1315, 1108, 45, '모든 커뮤니티 가입 완료'),
  -- 새 팬의 기존 커뮤니티 가입
  (1501, 1101, 100, '노바도 좋아요'), (1502, 1105, 90, NULL), (1503, 1102, 85, '루미 사랑해'), (1504, 1103, 80, NULL),
  (1505, 1104, 70, NULL), (1506, 1106, 60, 'KAITO 応援!'), (1508, 1101, 50, '스텔라 출석'), (1509, 1102, 45, NULL);

INSERT INTO `community_members` (`fan_id`, `artist_id`, `joined_at`, `nickname`, `bio`, `updated_at`)
SELECT j.fan_id, j.artist_id,
       GREATEST(DATE_SUB(NOW(6), INTERVAL j.days_ago DAY), DATE_ADD(u.created_at, INTERVAL 10 MINUTE)),
       u.nickname, j.bio,
       GREATEST(DATE_SUB(NOW(6), INTERVAL j.days_ago DAY), DATE_ADD(u.created_at, INTERVAL 10 MINUTE))
FROM `tmp_joins` j
JOIN `users` u ON u.id = j.fan_id
WHERE NOT EXISTS (SELECT 1 FROM `community_members` cm WHERE cm.fan_id = j.fan_id AND cm.artist_id = j.artist_id);

-- 가입한 커뮤니티의 아티스트 프로필 팔로우
INSERT IGNORE INTO `user_follows` (`follower_id`, `following_id`, `community_id`, `created_at`)
SELECT j.fan_id, j.artist_id, j.artist_id, cm.joined_at
FROM `tmp_joins` j
JOIN `community_members` cm ON cm.fan_id = j.fan_id AND cm.artist_id = j.artist_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_joins`;

-- 멤버십 (demo_fan15 는 새 커뮤니티 2곳 모두)
INSERT INTO `membership` (`created_at`, `expires_at`, `artist_id`, `fan_id`)
SELECT DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_ADD(DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), INTERVAL 1 YEAR), x.artist_id, x.fan_id
FROM (
            SELECT 1315 AS fan_id, 1107 AS artist_id, 44 AS days_ago
  UNION ALL SELECT 1315, 1108, 44
  UNION ALL SELECT 1503, 1107, 90
  UNION ALL SELECT 1502, 1108, 100
) x;
INSERT INTO `membership_period` (`fan_id`, `artist_id`, `started_at`, `expires_at`, `streak_count`, `created_at`)
SELECT m.fan_id, m.artist_id, m.created_at, m.expires_at, 1, m.created_at
FROM `membership` m
WHERE m.artist_id IN (1107, 1108);

-- [3] 게시글 42개 (mins_ago 분 전 작성, 그룹 아티스트 게시판은 멤버가 작성)
DROP TEMPORARY TABLE IF EXISTS `tmp_posts`;
CREATE TEMPORARY TABLE `tmp_posts` (
  `id` bigint PRIMARY KEY, `board_type` varchar(10), `artist_id` bigint, `author_id` bigint, `mins_ago` int,
  `title` varchar(200), `content` text
);
INSERT INTO `tmp_posts` VALUES
  -- NEBULA 아티스트 게시판
  (13001, 'ARTIST', 1107, 1219, 38000, '안녕하세요 NEBULA 유나예요 🌌', '스타더스트 여러분! 드디어 우리 공식 커뮤니티가 생겼어요. 여기서 자주 이야기 나눠요 💜'),
  (13002, 'ARTIST', 1107, 1220, 21000, '오늘 녹음 끝! 🎙️', '신곡 녹음 무사히 끝냈어요. 이번 노래 진짜 좋아요… 빨리 들려드리고 싶다!'),
  (13003, 'ARTIST', 1107, 1221, 9800, '안무 연습 비하인드 💃', '오늘 연습실에서 8시간 동안 안무 맞췄어요. 다리는 아프지만 무대 생각하면 행복해요'),
  (13004, 'ARTIST', 1107, 1222, 4300, '스타더스트 이름 정해진 거 알아요?', '팬덤 이름 스타더스트! 우주 먼지처럼 늘 우리 곁에 있어 달라는 뜻이에요 ✨'),
  (13005, 'ARTIST', 1107, 1219, 900, '데뷔 6개월 감사 인사', '벌써 데뷔 6개월이에요. 함께해 준 스타더스트 덕분에 여기까지 왔어요. 고마워요!!'),
  -- 서하린 아티스트 게시판
  (13011, 'ARTIST', 1108, 1108, 36000, '하린별 여러분 안녕하세요 🎧', '서하린입니다. 이곳에서 노래 이야기, 일상 이야기 많이 나눠요. 반가워요!'),
  (13012, 'ARTIST', 1108, 1108, 15000, '이번 주 라이브 예고 📡', '이번 주 금요일 밤 10시에 라이브 할게요. 듣고 싶은 노래 댓글로 남겨 주세요!'),
  (13013, 'ARTIST', 1108, 1108, 6200, '비 오는 날 듣기 좋은 노래', '오늘처럼 비 오는 날엔 어쿠스틱 버전이 최고예요. 제 플레이리스트 공유합니다 ☔'),
  (13014, 'ARTIST', 1108, 1108, 1300, '신곡 작업 중이에요 ✍️', '요즘 새벽마다 가사 쓰고 있어요. 하린별에게 보내는 편지 같은 노래가 될 거예요'),
  -- 기존 커뮤니티 아티스트 게시판
  (13021, 'ARTIST', 1101, 1203, 2500, '스텔라 오늘 하루 어땠어요?', '컴백 준비로 바쁘지만 스텔라 댓글 보면서 힘내고 있어요. 오늘 하루도 고생 많았어요 🌙'),
  (13022, 'ARTIST', 1102, 1208, 5100, '채린의 주말 일상 ☕', '오랜만에 쉬는 날이라 카페 투어 다녀왔어요. 루미너스도 좋은 주말 보내요!'),
  (13023, 'ARTIST', 1103, 1212, 7400, '민호의 플레이리스트 공유', '요즘 연습 끝나고 듣는 노래들 공유해요. 코로나 추천곡도 알려 주세요 🎧'),
  (13024, 'ARTIST', 1104, 1217, 3600, '소라가 추천하는 가을 노래 🍂', '선선해진 날씨에 듣기 좋은 노래 다섯 곡 골라 봤어요. 스펙트럼 취향은 어때요?'),
  (13025, 'ARTIST', 1105, 1105, 8800, '유리알 여러분 고마워요', '지난 공연에 와 준 유리알 여러분 정말 고마워요. 덕분에 행복한 밤이었어요 🎹'),
  (13026, 'ARTIST', 1106, 1106, 4700, '한국어 공부 일기 📖', '오늘 배운 단어는 "덕분에"예요. 카이토모 덕분에 매일 행복해요! いつもありがとう'),
  -- NEBULA 팬 게시판
  (13031, 'FAN', 1107, 1501, 30000, '네뷸라 입덕 후기', '데뷔 무대 보고 바로 입덕했어요. 네 명 합이 진짜 좋아요. 다들 입덕 계기가 뭐예요?'),
  (13032, 'FAN', 1107, 1503, 19000, '유나 직캠 레전드', '어제 음방 유나 직캠 보셨어요? 표정 연기 미쳤어요 ㅠㅠ'),
  (13033, 'FAN', 1107, 1504, 12500, '데뷔곡 무한 반복 중', '출근길, 퇴근길 내내 데뷔곡만 듣고 있어요. 질리지가 않아요'),
  (13034, 'FAN', 1107, 1301, 8000, '스타더스트 모여라 ✨', '노바 팬이지만 네뷸라도 응원해요! 같이 응원할 분 손!'),
  (13035, 'FAN', 1107, 1506, 5200, '팬아트 그려봤어요 🎨', '네뷸라 4인 4색 컨셉으로 그려 봤어요. 마음에 드셨으면 좋겠어요'),
  (13036, 'FAN', 1107, 1510, 2600, '이번 주 음방 시간표 정리', '화 6시, 수 5시 30분, 금 5시 15분! 실시간 응원 같이 해요'),
  (13037, 'FAN', 1107, 1310, 700, '포카 교환 구해요 🃏', '세린 포카 구해요. 리아 포카 두 장 있어요. 댓글 주세요!'),
  -- 서하린 팬 게시판
  (13041, 'FAN', 1108, 1502, 27000, '하린 라이브 다시보기 필수', '지난 라이브에서 부른 어쿠스틱 버전 꼭 들어 보세요. 눈물 납니다'),
  (13042, 'FAN', 1108, 1505, 16000, '가사 해석해봤어요', '"괜찮지 않아도 괜찮아" 이 가사 듣고 펑펑 울었어요. 제 해석 공유해요'),
  (13043, 'FAN', 1108, 1307, 9100, '피아노로 쳐 본 하린 노래 🎹', '하린 데뷔곡 피아노 커버 연습 중이에요. 다음에 영상 올릴게요!'),
  (13044, 'FAN', 1108, 1509, 4100, '첫 콘서트 후기', '소극장 콘서트 다녀왔어요. 라이브가 음원보다 더 좋아요!!'),
  (13045, 'FAN', 1108, 1511, 1600, '하린별 가입 인사드려요', '콘서트 보고 바로 가입했어요. 잘 부탁드려요 🙇'),
  (13046, 'FAN', 1108, 1312, 400, '하린 무대 직캠 모음', '이번 페스티벌 무대 직캠 모아 봤어요. 고음 파트 꼭 보세요'),
  -- 기존 커뮤니티 팬 게시판
  (13051, 'FAN', 1101, 1501, 14000, '노바 입덕 3일 차 질문', '입문용 무대 추천해 주실 분 계신가요? 다 보고 싶어서 고민이에요'),
  (13052, 'FAN', 1101, 1508, 6600, '스텔라 가입 인사', '응원단장입니다! 응원법 열심히 외워서 콘서트 갈게요 📣'),
  (13053, 'FAN', 1102, 1503, 11000, '루미 굿즈 후기', '이번 시즌 그리팅 도착했어요. 포토북 퀄리티 대박이에요'),
  (13054, 'FAN', 1102, 1509, 3300, '서아 생일카페 위치 아시는 분?', '이번 주말에 가 보려는데 위치랑 운영 시간 아시는 분 알려 주세요!'),
  (13055, 'FAN', 1103, 1504, 9500, '이클립스 콘서트 MD 줄 정보', '오늘 MD 줄 아침 7시에 벌써 200명이었어요. 내일 가시는 분 참고하세요'),
  (13056, 'FAN', 1104, 1505, 5800, '프리즘 무대 의상 모음', '이번 활동 무대 의상 정리해 봤어요. 3주차 의상이 최고였어요 🌈'),
  (13057, 'FAN', 1105, 1502, 7800, '유리 언니 신곡 가사 너무 좋아요', '하린도 좋지만 유리 언니 감성은 또 다르네요. 가사 한 줄 한 줄이 위로예요'),
  (13058, 'FAN', 1106, 1506, 2200, 'カイト 일본 팬미팅 다녀왔어요', '도쿄 팬미팅 다녀왔어요! 한국어로 인사해 줘서 감동이었어요'),
  (13059, 'FAN', 1101, 1401, 1100, '컴백 쇼케이스 후기', '쇼케이스 다녀왔어요. 신곡 무대 라이브로 들으니까 소름 돋았어요'),
  (13060, 'FAN', 1102, 1407, 1900, '루미 입덕 한 달 기념', '입덕 한 달 됐어요! 그동안 모은 포카 자랑합니다 💗'),
  (13061, 'FAN', 1103, 1410, 2900, '이클립스 응원봉 꾸미기', '응원봉 스티커 꾸미기 완성! 다들 어떻게 꾸미셨어요?'),
  (13062, 'FAN', 1104, 1414, 3900, '프리즘 혼성 케미 최고', '혼성그룹이라 무대 구성이 다양해서 좋아요. 유닛 무대 또 보고 싶어요'),
  (13063, 'FAN', 1105, 1417, 4900, '유리알 신입 인사', '유리구슬입니다. 유리 언니 피아노 연주에 반해서 가입했어요'),
  (13064, 'FAN', 1106, 1418, 5900, 'KAITO 한국어 늘었어요', '최근 라이브 보니까 한국어 진짜 많이 늘었어요. 노력하는 모습 멋져요');

INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`)
SELECT tp.id, tp.board_type, tp.artist_id, tp.author_id, tp.title, tp.content, 0, 0, DATE_SUB(NOW(6), INTERVAL tp.mins_ago MINUTE)
FROM `tmp_posts` tp
JOIN `users` u ON u.id = tp.author_id;   -- seed_2 를 안 돌린 DB면 1401~1418 글은 자동으로 빠진다.

DROP TEMPORARY TABLE IF EXISTS `tmp_posts`;

-- 이미지 첨부 (uploads/ 로 복사한 파일)
INSERT INTO `post_attachment` (`content_type`, `created_at`, `file_size`, `original_name`, `stored_name`, `post_id`)
SELECT 'image/jpeg', p.created_at, x.file_size, x.original_name, x.stored_name, p.id
FROM (
            SELECT 13001 AS post_id, 44279 AS file_size, 'hello_stardust.jpg' AS original_name, 'demo_nebula_post13001.jpg' AS stored_name
  UNION ALL SELECT 13011, 37851, 'hello_harinbyul.jpg', 'demo_harin_post13011.jpg'
  UNION ALL SELECT 13035, 39806, 'nebula_fanart.jpg', 'demo_nebula_post13035.jpg'
) x
JOIN `post` p ON p.id = x.post_id;

-- [4] 댓글·좋아요 - 글 작성 전에 가입한 팬 중 일부가 자동으로 단다.
INSERT INTO `comment` (`id`, `content`, `created_at`, `author_id`, `post_id`, `parent_id`, `deleted_at`)
SELECT 23000 + ROW_NUMBER() OVER (ORDER BY p.id, cm.fan_id),
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
WHERE p.id BETWEEN 13001 AND 13199
  AND cm.fan_id <> p.author_id
  AND (cm.fan_id BETWEEN 1301 AND 1315 OR cm.fan_id BETWEEN 1401 AND 1419 OR cm.fan_id BETWEEN 1501 AND 1512)
  AND MOD(p.id * 5 + cm.fan_id * 7, 3) = 0;

-- 아티스트 게시글에는 작성 아티스트가 첫 댓글에 답글을 단다.
INSERT INTO `comment` (`id`, `content`, `created_at`, `author_id`, `post_id`, `parent_id`, `deleted_at`)
SELECT 23500 + ROW_NUMBER() OVER (ORDER BY p.id),
       ELT(1 + MOD(p.id, 5), '고마워요 💕', '항상 응원해 줘서 고마워요!', '오늘도 좋은 하루 보내요 ☀️', '댓글 보고 힘 났어요!!', '우리 또 만나요 🫶'),
       LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(c.created_at, INTERVAL 30 MINUTE)),
       p.author_id, p.id, c.id, NULL
FROM `post` p
JOIN `comment` c ON c.id = (SELECT MIN(c2.id) FROM `comment` c2 WHERE c2.post_id = p.id AND c2.id BETWEEN 23001 AND 23499)
WHERE p.id BETWEEN 13001 AND 13199
  AND p.board_type = 'ARTIST';

-- 좋아요: 가입자 중 절반 정도
INSERT INTO `post_like` (`created_at`, `post_id`, `user_id`)
SELECT LEAST(DATE_SUB(NOW(6), INTERVAL 1 MINUTE), DATE_ADD(p.created_at, INTERVAL 3 + MOD(p.id * 7 + cm.fan_id * 13, 900) MINUTE)), p.id, cm.fan_id
FROM `post` p
JOIN `community_members` cm ON cm.artist_id = p.artist_id AND cm.joined_at < p.created_at
JOIN `users` u ON u.id = cm.fan_id AND u.role = 'FAN' AND u.status = 'ACTIVE'
WHERE p.id BETWEEN 13001 AND 13199
  AND cm.fan_id <> p.author_id
  AND (cm.fan_id BETWEEN 1301 AND 1315 OR cm.fan_id BETWEEN 1401 AND 1419 OR cm.fan_id BETWEEN 1501 AND 1512)
  AND MOD(p.id * 7 + cm.fan_id * 5, 2) = 0;

UPDATE `post` p
SET p.like_count = (SELECT COUNT(*) FROM `post_like` l WHERE l.post_id = p.id)
WHERE p.id BETWEEN 13001 AND 13199;

-- [5] 배지 - 새 팬은 모든 커뮤니티, 기존 팬은 새 커뮤니티에서만
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT cm.fan_id, cm.artist_id, b.badge_code, b.badge_name, b.badge_type, cm.joined_at, cm.joined_at
FROM `community_members` cm
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_JOIN'
WHERE cm.fan_id BETWEEN 1501 AND 1512
   OR (cm.artist_id IN (1107, 1108) AND cm.fan_id BETWEEN 1301 AND 1315);

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT cm.fan_id, cm.artist_id, b.badge_code, b.badge_name, b.badge_type, DATE_ADD(cm.joined_at, INTERVAL 100 DAY), DATE_ADD(cm.joined_at, INTERVAL 100 DAY)
FROM `community_members` cm
JOIN `fan_badge` b ON b.badge_code = 'BASIC_DAY_100'
WHERE (cm.fan_id BETWEEN 1501 AND 1512 OR (cm.artist_id IN (1107, 1108) AND cm.fan_id BETWEEN 1301 AND 1315))
  AND cm.joined_at <= DATE_SUB(NOW(6), INTERVAL 100 DAY);

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(p.created_at), MIN(p.created_at)
FROM `post` p
JOIN `community_members` cm ON cm.fan_id = p.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_POST'
WHERE p.board_type = 'FAN'
  AND (p.author_id BETWEEN 1501 AND 1512 OR (p.artist_id IN (1107, 1108) AND p.author_id BETWEEN 1301 AND 1315))
GROUP BY p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MAX(c.created_at), MAX(c.created_at)
FROM `comment` c
JOIN `post` p ON p.id = c.post_id
JOIN `community_members` cm ON cm.fan_id = c.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_COMMENT_5'
WHERE (c.author_id BETWEEN 1501 AND 1512 OR (p.artist_id IN (1107, 1108) AND c.author_id BETWEEN 1301 AND 1315))
  AND c.deleted_at IS NULL
GROUP BY c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type
HAVING COUNT(*) >= 5;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT l.user_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MAX(l.created_at), MAX(l.created_at)
FROM `post_like` l
JOIN `post` p ON p.id = l.post_id
JOIN `community_members` cm ON cm.fan_id = l.user_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_LIKE_10'
WHERE l.user_id BETWEEN 1501 AND 1512 OR (p.artist_id IN (1107, 1108) AND l.user_id BETWEEN 1301 AND 1315)
GROUP BY l.user_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type
HAVING COUNT(*) >= 10;

INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT m.fan_id, m.artist_id, b.badge_code, b.badge_name, b.badge_type, m.created_at, m.created_at
FROM `membership` m
JOIN `fan_badge` b ON b.badge_code = 'SPECIAL_MEMBERSHIP_1'
WHERE m.artist_id IN (1107, 1108);

COMMIT;
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- [확인] 넣은 데이터 개수
SELECT '새 아티스트 커뮤니티' AS 항목, COUNT(*) AS 개수 FROM `users` WHERE `id` IN (1107, 1108)
UNION ALL SELECT '새 팬 계정', COUNT(*) FROM `users` WHERE `id` BETWEEN 1501 AND 1512
UNION ALL SELECT 'NEBULA 가입자', COUNT(*) FROM `community_members` WHERE `artist_id` = 1107
UNION ALL SELECT '서하린 가입자', COUNT(*) FROM `community_members` WHERE `artist_id` = 1108
UNION ALL SELECT '추가 게시글', COUNT(*) FROM `post` WHERE `id` BETWEEN 13001 AND 13199
UNION ALL SELECT '추가 댓글(답글 포함)', COUNT(*) FROM `comment` WHERE `id` BETWEEN 23001 AND 23999
UNION ALL SELECT '추가 좋아요', COUNT(*) FROM `post_like` WHERE `post_id` BETWEEN 13001 AND 13199;
