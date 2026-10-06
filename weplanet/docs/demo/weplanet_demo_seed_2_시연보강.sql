-- ============================================================
-- WePlaNet 데모 데이터 2 - 시연 보강 (발표 시연 화면을 채우는 추가 데이터)
-- ------------------------------------------------------------
-- 실행 순서
--   1) weplanet_schema_full_reset_v2.sql  (전체 초기화 + 기본 시드)   ※ 이미 돌린 DB면 생략
--   2) weplanet_demo_seed.sql             (데모 데이터)               ※ 이미 돌린 DB면 생략
--   3) 이 파일                             - 여러 번 돌려도 된다 (맨 앞에서 이 파일이 넣은 행을 지우고 다시 넣음)
--   이미지는 새로 쓰지 않는다 (demo_images 에 있는 파일만 재사용)
--
-- 채우는 화면
--   [1] 관리자 대시보드 "최근 30일 가입 추이"  - 최근 28일 동안 가입한 팬 20명 (1401~1420, 1420 은 정지 계정)
--   [2] 해시태그 총공 - 지금 진행 중인 이벤트에 해시태그 글/집계 기록 추가 (제외 사례 3종 포함)
--                       진행 중인 이벤트가 없으면 "10월 컴백 응원 총공"(4팀, 그저께~4일 뒤)을 새로 만든다
--                     - 지난 이벤트 "9월 가을맞이 총공" (집계 확정 완료, 결과 공지 버튼 시연용)
--   [3] 통합 신고·제재  - 게시글 신고 5건(대기 4 + 기각 1), 댓글 신고 3건(대기)
--   [4] 입점 신청       - 대기 4건(한/일/영) + 반려 1건. 승인 메일은 admin4.wp 메일함(+별칭)으로 온다
--   [5] 팬 프로젝트     - 심사 대기 2 / 반려 1 / 모금 마감(정산 대기) 1 / 정산 완료 1
--                         정산 계좌는 화면 표시용 더미 값 (앱이 복호화하지 않으므로 확인·정산 완료 버튼까지 동작)
--   [6] 배지            - 실제 활동 데이터(가입·글·댓글·좋아요·멤버십·후원)에 맞춰 지급
--                         demo_fan15 는 NOVA 에서 "일반 3 / 스페셜 1" 그대로 둔다 (등록 자격 부족 시연용)
--                         demo_fan09(응원봉장인) 는 NOVA 프로젝트 등록 자격 충족
--   [7] 관리자 로그     - 위 데이터와 맞는 조치 이력
--   [8] 북마크          - demo_fan15
--
-- 시연 팁
--   총공 "참여율 오르는 장면"은 NOVA 가입자 중 아직 참여하지 않은 demo_fan04(새벽세시) 로 #NOVA컴백 글을 쓰면 된다
--   새 팬 계정 비밀번호도 Test1234 (1403/1408/1415 는 소셜 가입이라 아이디 로그인 불가)
--
-- ※ MySQL Workbench 는 자동 커밋이 꺼져 있을 수 있어 맨 끝에 COMMIT 을 넣어 두었다
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
SET @old_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ------------------------------------------------------------
-- [0] 재실행 대비: 이 파일이 넣는 ID 범위를 먼저 지운다
--     users 1401~1420 / post 12001~12299 / comment 22001~22099 / fan_project 503~507
--     hashtag_event 901(지난 이벤트), 902(이 파일이 만든 진행 이벤트) / partnership 801~805 / admin_action_logs 7001~7099
--     report 7001~7099 / comment_report 7101~7199 / post_bookmark 9001~9099
-- ------------------------------------------------------------
DELETE FROM `comment_report` WHERE `id` BETWEEN 7101 AND 7199 OR `comment_id` BETWEEN 22001 AND 22099;
DELETE FROM `report` WHERE `id` BETWEEN 7001 AND 7099 OR `post_id` BETWEEN 12001 AND 12299;
DELETE FROM `post_bookmark` WHERE `id` BETWEEN 9001 AND 9099 OR `post_id` BETWEEN 12001 AND 12299;
DELETE FROM `hashtag_event_entry` WHERE `post_id` BETWEEN 12001 AND 12299;
DELETE FROM `hashtag_event` WHERE `id` IN (901, 902);
DELETE FROM `comment` WHERE `parent_id` BETWEEN 22001 AND 22099;   -- 시연 중 이 댓글에 달린 답글
DELETE FROM `comment` WHERE `id` BETWEEN 22001 AND 22099;
DELETE FROM `post_like` WHERE `post_id` BETWEEN 12001 AND 12299;
DELETE FROM `comment` WHERE `post_id` BETWEEN 12001 AND 12299 AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `post_id` BETWEEN 12001 AND 12299;
DELETE FROM `post` WHERE `id` BETWEEN 12001 AND 12299;
DELETE FROM `fan_project_contribution` WHERE `project_id` BETWEEN 503 AND 507;
DELETE FROM `fan_project` WHERE `id` BETWEEN 503 AND 507;   -- 커버 이미지·정산 계좌는 ON DELETE CASCADE
DELETE FROM `partnership_applications` WHERE `id` BETWEEN 801 AND 805;
DELETE FROM `admin_action_logs` WHERE `id` BETWEEN 7001 AND 7099;
DELETE FROM `fan_badge_ownership` WHERE `fan_id` BETWEEN 1401 AND 1420;
DELETE FROM `community_members` WHERE `fan_id` BETWEEN 1401 AND 1420;
DELETE FROM `users` WHERE `id` BETWEEN 1401 AND 1420;

-- ------------------------------------------------------------
-- [1] 최근 28일 동안 가입한 팬 20명 (비밀번호 공통 Test1234)
--     1403(카카오) / 1408(구글) / 1415(라인) 은 소셜 가입, 1420 은 광고글로 정지된 계정
-- ------------------------------------------------------------
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `provider`, `provider_id`, `last_login_at`, `created_at`, `updated_at`) VALUES
  (1401, 'demo_new01', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '김별하', '노바별빛', 'demo_new01@weplanet.test', DATE_SUB(NOW(6), INTERVAL 38820 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 300 MINUTE), DATE_SUB(NOW(6), INTERVAL 38820 MINUTE), DATE_SUB(NOW(6), INTERVAL 300 MINUTE)),
  (1402, 'demo_new02', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '이시은', '시온의하루', 'demo_new02@weplanet.test', DATE_SUB(NOW(6), INTERVAL 36100 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 1200 MINUTE), DATE_SUB(NOW(6), INTERVAL 36100 MINUTE), DATE_SUB(NOW(6), INTERVAL 1200 MINUTE)),
  (1403, 'kakao900001403', NULL, 'FAN', 'ACTIVE', NULL, '박컴백', '컴백기다림', 'demo_new03@weplanet.test', DATE_SUB(NOW(6), INTERVAL 34500 MINUTE), 'KAKAO', '900001403', DATE_SUB(NOW(6), INTERVAL 100 MINUTE), DATE_SUB(NOW(6), INTERVAL 34500 MINUTE), DATE_SUB(NOW(6), INTERVAL 100 MINUTE)),
  (1404, 'demo_new04', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '최하람', '하람덕후', 'demo_new04@weplanet.test', DATE_SUB(NOW(6), INTERVAL 31900 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 4300 MINUTE), DATE_SUB(NOW(6), INTERVAL 31900 MINUTE), DATE_SUB(NOW(6), INTERVAL 4300 MINUTE)),
  (1405, 'demo_new05', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '정새봄', '새내기노바', 'demo_new05@weplanet.test', DATE_SUB(NOW(6), INTERVAL 30400 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 2900 MINUTE), DATE_SUB(NOW(6), INTERVAL 30400 MINUTE), DATE_SUB(NOW(6), INTERVAL 2900 MINUTE)),
  (1406, 'demo_new06', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '한태리', '태오사랑', 'demo_new06@weplanet.test', DATE_SUB(NOW(6), INTERVAL 28700 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 8000 MINUTE), DATE_SUB(NOW(6), INTERVAL 28700 MINUTE), DATE_SUB(NOW(6), INTERVAL 8000 MINUTE)),
  (1407, 'demo_new07', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '윤루아', '루미너스온', 'demo_new07@weplanet.test', DATE_SUB(NOW(6), INTERVAL 26200 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 60 MINUTE), DATE_SUB(NOW(6), INTERVAL 26200 MINUTE), DATE_SUB(NOW(6), INTERVAL 60 MINUTE)),
  (1408, 'google900001408', NULL, 'FAN', 'ACTIVE', NULL, 'Seoa Kang', '서아바라기', 'demo_new08@weplanet.test', DATE_SUB(NOW(6), INTERVAL 24800 MINUTE), 'GOOGLE', '900001408', DATE_SUB(NOW(6), INTERVAL 200 MINUTE), DATE_SUB(NOW(6), INTERVAL 24800 MINUTE), DATE_SUB(NOW(6), INTERVAL 200 MINUTE)),
  (1409, 'demo_new09', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '오다은', '루미새싹', 'demo_new09@weplanet.test', DATE_SUB(NOW(6), INTERVAL 21700 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 1500 MINUTE), DATE_SUB(NOW(6), INTERVAL 21700 MINUTE), DATE_SUB(NOW(6), INTERVAL 1500 MINUTE)),
  (1410, 'demo_new10', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '서도경', '이클립스러', 'demo_new10@weplanet.test', DATE_SUB(NOW(6), INTERVAL 20300 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 120 MINUTE), DATE_SUB(NOW(6), INTERVAL 20300 MINUTE), DATE_SUB(NOW(6), INTERVAL 120 MINUTE)),
  (1411, 'demo_new11', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '문지안', '도윤최고', 'demo_new11@weplanet.test', DATE_SUB(NOW(6), INTERVAL 19900 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 2600 MINUTE), DATE_SUB(NOW(6), INTERVAL 19900 MINUTE), DATE_SUB(NOW(6), INTERVAL 2600 MINUTE)),
  (1412, 'demo_new12', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '강유나', '일식관측', 'demo_new12@weplanet.test', DATE_SUB(NOW(6), INTERVAL 17100 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 5100 MINUTE), DATE_SUB(NOW(6), INTERVAL 17100 MINUTE), DATE_SUB(NOW(6), INTERVAL 5100 MINUTE)),
  (1413, 'demo_new13', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '임카이', '카이의봄', 'demo_new13@weplanet.test', DATE_SUB(NOW(6), INTERVAL 14500 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 3300 MINUTE), DATE_SUB(NOW(6), INTERVAL 14500 MINUTE), DATE_SUB(NOW(6), INTERVAL 3300 MINUTE)),
  (1414, 'demo_new14', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '신하나', '프리즘빛', 'demo_new14@weplanet.test', DATE_SUB(NOW(6), INTERVAL 12900 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 400 MINUTE), DATE_SUB(NOW(6), INTERVAL 12900 MINUTE), DATE_SUB(NOW(6), INTERVAL 400 MINUTE)),
  (1415, 'line900001415', NULL, 'FAN', 'ACTIVE', NULL, '佐藤 ひな', 'ひなた', 'demo_new15@weplanet.test', DATE_SUB(NOW(6), INTERVAL 11300 MINUTE), 'LINE', '900001415', DATE_SUB(NOW(6), INTERVAL 900 MINUTE), DATE_SUB(NOW(6), INTERVAL 11300 MINUTE), DATE_SUB(NOW(6), INTERVAL 900 MINUTE)),
  (1416, 'demo_new16', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '조무진', '무지개조각', 'demo_new16@weplanet.test', DATE_SUB(NOW(6), INTERVAL 8600 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 1800 MINUTE), DATE_SUB(NOW(6), INTERVAL 8600 MINUTE), DATE_SUB(NOW(6), INTERVAL 1800 MINUTE)),
  (1417, 'demo_new17', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '배유리', '유리구슬', 'demo_new17@weplanet.test', DATE_SUB(NOW(6), INTERVAL 7100 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 700 MINUTE), DATE_SUB(NOW(6), INTERVAL 7100 MINUTE), DATE_SUB(NOW(6), INTERVAL 700 MINUTE)),
  (1418, 'demo_new18', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '황토모', '카이토모', 'demo_new18@weplanet.test', DATE_SUB(NOW(6), INTERVAL 4400 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 500 MINUTE), DATE_SUB(NOW(6), INTERVAL 4400 MINUTE), DATE_SUB(NOW(6), INTERVAL 500 MINUTE)),
  (1419, 'demo_new19', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'ACTIVE', NULL, '노은밤', '노래하는밤', 'demo_new19@weplanet.test', DATE_SUB(NOW(6), INTERVAL 2700 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 240 MINUTE), DATE_SUB(NOW(6), INTERVAL 2700 MINUTE), DATE_SUB(NOW(6), INTERVAL 240 MINUTE)),
  (1420, 'demo_new20', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'FAN', 'SUSPENDED', NULL, '광고계정', '굿즈공구왕', 'demo_new20@weplanet.test', DATE_SUB(NOW(6), INTERVAL 6200 MINUTE), NULL, NULL, DATE_SUB(NOW(6), INTERVAL 3000 MINUTE), DATE_SUB(NOW(6), INTERVAL 6200 MINUTE), DATE_SUB(NOW(6), INTERVAL 2880 MINUTE));

-- 커뮤니티 가입: 가입 10분 뒤 첫 커뮤니티, 1419 는 다음 날 두 번째 커뮤니티
INSERT INTO `community_members` (`fan_id`, `artist_id`, `joined_at`, `nickname`, `bio`, `updated_at`)
SELECT x.fan_id, x.artist_id, DATE_ADD(u.created_at, INTERVAL x.after_min MINUTE), u.nickname, x.bio, DATE_ADD(u.created_at, INTERVAL x.after_min MINUTE)
FROM (
            SELECT 1401 AS fan_id, 1101 AS artist_id, 10 AS after_min, '컴백만 기다렸어요' AS bio
  UNION ALL SELECT 1402, 1101, 10, '시온이 최고 🌟'
  UNION ALL SELECT 1403, 1101, 10, NULL
  UNION ALL SELECT 1404, 1101, 10, '하람 생일 카운트다운'
  UNION ALL SELECT 1405, 1101, 10, NULL
  UNION ALL SELECT 1406, 1101, 10, '태오 직캠 수집 중'
  UNION ALL SELECT 1407, 1102, 10, '루미 입덕 1개월 차'
  UNION ALL SELECT 1408, 1102, 10, 'Seoa forever 💜'
  UNION ALL SELECT 1409, 1102, 10, NULL
  UNION ALL SELECT 1410, 1103, 10, '이클립스 영원히'
  UNION ALL SELECT 1411, 1103, 10, '도윤 보러 왔어요'
  UNION ALL SELECT 1412, 1103, 10, NULL
  UNION ALL SELECT 1413, 1103, 10, NULL
  UNION ALL SELECT 1414, 1104, 10, '프리즘 응원해요'
  UNION ALL SELECT 1415, 1104, 10, '日本から応援してます'
  UNION ALL SELECT 1416, 1104, 10, NULL
  UNION ALL SELECT 1417, 1105, 10, '유리 언니 사랑해요'
  UNION ALL SELECT 1418, 1106, 10, 'KAITO 最高!'
  UNION ALL SELECT 1419, 1101, 10, '노래가 좋아서 입덕'
  UNION ALL SELECT 1419, 1105, 1440, NULL
  UNION ALL SELECT 1420, 1101, 10, NULL
) x
JOIN `users` u ON u.id = x.fan_id;

-- ------------------------------------------------------------
-- [2-1] 해시태그 총공 - 지난 이벤트 "9월 가을맞이 총공" (30일 전 시작, 7일간, 종료 2일 뒤 집계 확정)
--       겹치는 이벤트가 이미 있으면 만들지 않는다 (그러면 이 섹션의 글·집계도 건너뜀)
-- ------------------------------------------------------------
SET @past_start := TIMESTAMP(CURDATE() - INTERVAL 30 DAY);
SET @past_end   := TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '23:59:59');

INSERT INTO `hashtag_event` (`id`, `title`, `start_at`, `end_at`, `finalized_at`, `created_by`, `created_at`, `updated_at`)
SELECT 901, '9월 가을맞이 총공', @past_start, @past_end, DATE_ADD(@past_end, INTERVAL 2 DAY), 1001,
       DATE_SUB(@past_start, INTERVAL 5 DAY), DATE_ADD(@past_end, INTERVAL 2 DAY)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `hashtag_event` h WHERE h.start_at <= @past_end AND h.end_at >= @past_start);

INSERT INTO `hashtag_event_target` (`event_id`, `artist_id`, `hashtag`)
SELECT 901, x.artist_id, x.hashtag
FROM (
            SELECT 1103 AS artist_id, '#ECLIPSE_가을' AS hashtag
  UNION ALL SELECT 1104, '#PRISM_가을'
  UNION ALL SELECT 1105, '#유리와가을'
  UNION ALL SELECT 1106, '#KAITO가을'
) x
WHERE EXISTS (SELECT 1 FROM `hashtag_event` WHERE `id` = 901);

-- 지난 이벤트 글: 시작 시각 + hours 시간에 작성
DROP TEMPORARY TABLE IF EXISTS `tmp_past_posts`;
CREATE TEMPORARY TABLE `tmp_past_posts` (
  `post_id` bigint PRIMARY KEY, `artist_id` bigint, `author_id` bigint, `hours` int,
  `entry_status` varchar(30), `title` varchar(200), `body` text
);
INSERT INTO `tmp_past_posts` VALUES
  (12101, 1103, 1302, 10,  'COUNTED',    '가을 총공 시작!',               '오늘부터 일주일! 이클립스 가을 총공 다 같이 달려요 🍂'),
  (12102, 1103, 1302, 60,  'COUNTED',    '총공 이틀째 인증',              '출근길에 이클립스 노래 들으면서 한 줄 남기고 가요'),
  (12103, 1103, 1304, 20,  'COUNTED',    '가을엔 이클립스',               '선선한 날씨에 딱 맞는 노래들 추천합니다'),
  (12104, 1103, 1308, 30,  'COUNTED',    '도쿄에서 응원해요',             '여행 중에도 총공은 빠질 수 없죠!'),
  (12105, 1103, 1308, 90,  'COUNTED',    '총공 중간 점검',                '참여율 2위래요, 조금만 더 힘내요!!'),
  (12106, 1103, 1309, 40,  'COUNTED',    '응원봉 들고 인증',              '가을 버전 응원봉 꾸미기 완료 🎃'),
  (12107, 1103, 1310, 50,  'COUNTED',    '포카 정리하다가',               '가을 컨셉 포카가 제일 예쁜 듯'),
  (12108, 1103, 1310, 120, 'COUNTED',    '총공 막바지',                   '마지막까지 같이 가요 이클립스!'),
  (12109, 1103, 1310, 150, 'COUNTED',    '총공 마지막 날',                '일주일 동안 다들 고생 많았어요'),
  (12111, 1104, 1303, 15,  'COUNTED',    '프리즘 가을 총공 참여',          '가을 하늘처럼 맑은 프리즘 노래 🌈'),
  (12112, 1104, 1306, 70,  'COUNTED',    '총공 같이 해요',                '프리즘 팬분들 다 같이 참여해요!'),
  (12113, 1104, 1311, 100, 'COUNTED',    '퇴근길 총공',                   '오늘 하루도 프리즘 덕분에 버텼다'),
  (12121, 1105, 1301, 25,  'COUNTED',    '유리 언니 가을 총공 🍁',         '유리 언니 목소리는 가을이랑 너무 잘 어울려요'),
  (12122, 1105, 1307, 45,  'COUNTED',    '피아노 커버 올려요',            '유리 언니 노래 피아노로 쳐봤어요 🎹'),
  (12123, 1105, 1310, 80,  'COUNTED',    '총공 참여합니다',               '작은 팬덤이지만 참여율은 1등 해봐요!'),
  (12124, 1105, 1312, 110, 'COUNTED',    '직캠 정주행 중',                '가을밤에 유리 직캠 정주행'),
  (12125, 1105, 1314, 130, 'COUNTED',    '콘서트 가고 싶다',              '가을 콘서트 소식 기다려요'),
  (12131, 1106, 1305, 35,  'COUNTED',    'KAITO 가을 총공',               'カイト 가을 노래 최고예요'),
  (12132, 1106, 1313, 95,  'COUNTED',    '덕질 일기',                     '오늘도 카이토 노래로 하루 마무리'),
  (12133, 1106, 1313, 140, 'COUNTED',    '총공 마지막 인증',              '다음 총공도 꼭 참여할게요'),
  (12134, 1106, 1302, 55,  'NOT_MEMBER', '지나가다 응원해요',             '가입은 안 했지만 응원합니다');

INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`)
SELECT tp.post_id, 'FAN', tp.artist_id, tp.author_id, tp.title, CONCAT(tp.body, '\n\n', t.hashtag), 0, 0,
       DATE_ADD(e.start_at, INTERVAL tp.hours HOUR)
FROM `tmp_past_posts` tp
JOIN `hashtag_event_target` t ON t.event_id = 901 AND t.artist_id = tp.artist_id
JOIN `hashtag_event` e ON e.id = t.event_id;

INSERT INTO `hashtag_event_entry` (`target_id`, `post_id`, `fan_id`, `status`, `created_at`)
SELECT t.id, p.id, p.author_id, tp.entry_status, p.created_at
FROM `tmp_past_posts` tp
JOIN `post` p ON p.id = tp.post_id
JOIN `hashtag_event_target` t ON t.event_id = 901 AND t.artist_id = tp.artist_id;

-- 집계 확정 스냅샷 (관리자가 "집계 확정"을 눌렀을 때와 같은 값: 종료 시점 가입자 수 기준 참여율 순위)
UPDATE `hashtag_event_target` t
JOIN `hashtag_event` e ON e.id = t.event_id
SET t.final_member_count      = (SELECT COUNT(*) FROM `community_members` cm WHERE cm.artist_id = t.artist_id AND cm.joined_at <= e.end_at),
    t.final_participant_count = (SELECT COUNT(DISTINCT en.fan_id) FROM `hashtag_event_entry` en WHERE en.target_id = t.id AND en.status = 'COUNTED'),
    t.final_post_count        = (SELECT COUNT(*) FROM `hashtag_event_entry` en WHERE en.target_id = t.id AND en.status = 'COUNTED')
WHERE t.event_id = 901;

UPDATE `hashtag_event_target` t
JOIN (
  SELECT r.id,
         ROW_NUMBER() OVER (ORDER BY r.final_participant_count / NULLIF(r.final_member_count, 0) DESC,
                                     r.final_participant_count DESC, r.final_post_count DESC, r.id) AS rk
  FROM `hashtag_event_target` r
  WHERE r.event_id = 901
) x ON x.id = t.id
SET t.final_rank = x.rk;

-- ------------------------------------------------------------
-- [2-2] 해시태그 총공 - 지금 진행 중인 이벤트
--       진행 중인 이벤트가 있으면 그 이벤트의 참여 아티스트에만 글을 넣고 (해시태그도 그 이벤트 것을 씀),
--       없으면 902 "10월 컴백 응원 총공"(NOVA·LUMI·ECLIPSE·PRISM, 그저께 00:00 ~ 4일 뒤 23:59:59)을 만든다
-- ------------------------------------------------------------
SET @cur := (SELECT `id` FROM `hashtag_event` WHERE `start_at` <= NOW(6) AND `end_at` >= NOW(6) ORDER BY `start_at` DESC LIMIT 1);
SET @new_start := TIMESTAMP(CURDATE() - INTERVAL 2 DAY);
SET @new_end   := TIMESTAMP(CURDATE() + INTERVAL 4 DAY, '23:59:59');

INSERT INTO `hashtag_event` (`id`, `title`, `start_at`, `end_at`, `finalized_at`, `created_by`, `created_at`, `updated_at`)
SELECT 902, '10월 컴백 응원 총공', @new_start, @new_end, NULL, 1001, DATE_SUB(NOW(6), INTERVAL 5 DAY), DATE_SUB(NOW(6), INTERVAL 5 DAY)
FROM DUAL
WHERE @cur IS NULL
  AND NOT EXISTS (SELECT 1 FROM `hashtag_event` h WHERE h.start_at <= @new_end AND h.end_at >= @new_start);

INSERT INTO `hashtag_event_target` (`event_id`, `artist_id`, `hashtag`)
SELECT 902, x.artist_id, x.hashtag
FROM (
            SELECT 1101 AS artist_id, '#NOVA컴백' AS hashtag
  UNION ALL SELECT 1102, '#LUMI와함께'
  UNION ALL SELECT 1103, '#ECLIPSE_RISE'
  UNION ALL SELECT 1104, '#PRISM빛나라'
) x
WHERE EXISTS (SELECT 1 FROM `hashtag_event` WHERE `id` = 902);

SET @cur := COALESCE(@cur, (SELECT `id` FROM `hashtag_event` WHERE `id` = 902));

-- 진행 중 이벤트 글: mins_ago 분 전에 작성 (이벤트 시작 전으로 넘어가면 시작 30분 뒤로 맞춤)
-- 제외 사례: 12005 = 1인 1일 3건 초과, 12010 = 아티스트에게 숨김, 12014 = 미가입자
DROP TEMPORARY TABLE IF EXISTS `tmp_now_posts`;
CREATE TEMPORARY TABLE `tmp_now_posts` (
  `post_id` bigint PRIMARY KEY, `artist_id` bigint, `author_id` bigint, `mins_ago` int, `hidden` tinyint,
  `entry_status` varchar(30), `title` varchar(200), `body` text
);
INSERT INTO `tmp_now_posts` VALUES
  (12001, 1101, 1301, 1500, 0, 'COUNTED',            '컴백 무대 보고 왔어요',          '정규 2집 타이틀 너무 좋아요!! 다 같이 달려요 🚀'),
  (12002, 1101, 1301, 45,   0, 'COUNTED',            '총공 1일 3글 도전',             '오늘 첫 번째 인증! 출근길 스밍 중'),
  (12003, 1101, 1301, 35,   0, 'COUNTED',            '점심시간 인증',                 '두 번째 글! 점심 먹으면서 뮤비 보는 중'),
  (12004, 1101, 1301, 25,   0, 'COUNTED',            '오늘 마지막 인증',              '세 번째 글! 내일 또 올게요'),
  (12005, 1101, 1301, 15,   0, 'DAILY_LIMIT',        '하나만 더…',                    '네 번째 글인데 이건 집계 안 되겠죠? ㅎㅎ'),
  (12006, 1101, 1302, 2800, 0, 'COUNTED',            '하람 파트 레전드',              '하람 고음 파트 무한 반복 중입니다'),
  (12007, 1101, 1306, 2000, 0, 'COUNTED',            '응원 문구 정리',                '컴백 응원 문구 정리해 왔어요! 다 같이 써요'),
  (12008, 1101, 1309, 900,  0, 'COUNTED',            '응원봉 컴백 에디션',            '컴백 기념 응원봉 꾸미기 완료 ✨'),
  (12009, 1101, 1311, 600,  0, 'COUNTED',            '달토끼도 참여',                 '밤에도 총공은 계속된다 🌙'),
  (12010, 1101, 1311, 300,  1, 'HIDDEN_FROM_ARTIST', '(팬끼리) 총공 순위 걱정',       '루미 쪽 참여율이 더 높아요… 우리끼리 더 힘내요'),
  (12011, 1101, 1401, 3200, 0, 'COUNTED',            '입덕하자마자 총공',             '가입하자마자 컴백이라니 운명인가 봐요'),
  (12012, 1101, 1402, 1200, 0, 'COUNTED',            '시온 티저 미쳤다',              '시온 티저 사진 보고 심장 멈출 뻔'),
  (12013, 1101, 1403, 100,  0, 'COUNTED',            '컴백 축하해요',                 '기다린 보람이 있어요 NOVA!!'),
  (12014, 1101, 1305, 700,  0, 'NOT_MEMBER',         '지나가다 축하',                 '루미 팬이지만 노바 컴백도 축하해요'),
  (12015, 1101, 1401, 400,  0, 'COUNTED',            '음방 1위 가자',                 '이번 주 음방 1위 꼭 해요!!'),
  (12021, 1102, 1301, 2600, 0, 'COUNTED',            '루미 컴백 축하 🎉',             '루미 컴백 무대 의상 너무 예뻐요'),
  (12022, 1102, 1303, 2100, 0, 'COUNTED',            '총공 같이 해요',                '루미루미 출석! 오늘도 참여합니다'),
  (12023, 1102, 1305, 1700, 0, 'COUNTED',            '초코우유 마시며 응원',          '오늘의 덕질 메뉴는 루미 뮤비'),
  (12024, 1102, 1307, 1100, 0, 'COUNTED',            '피아노 커버 2탄',               '루미 타이틀곡 피아노 커버 올려요 🎹'),
  (12025, 1102, 1310, 800,  0, 'COUNTED',            '포카 교환 겸 인증',             '총공 인증하고 포카 교환도 구해요'),
  (12026, 1102, 1407, 500,  0, 'COUNTED',            '입덕 한 달 차의 총공',           '처음 참여하는 총공 너무 설레요'),
  (12027, 1102, 1408, 200,  0, 'COUNTED',            'Seoa love 💜',                  '서아 파트 진짜 최고예요'),
  (12028, 1102, 1303, 90,   0, 'COUNTED',            '오늘도 인증',                   '참여율 1위 지켜요 루미!'),
  (12029, 1102, 1407, 60,   0, 'COUNTED',            '퇴근길 루미',                   '퇴근길 지하철에서 한 줄 남겨요'),
  (12031, 1103, 1302, 3000, 0, 'COUNTED',            'ECLIPSE 컴백 응원',             '이클립스도 이번에 같이 컴백했죠! 응원합니다'),
  (12032, 1103, 1308, 1900, 0, 'COUNTED',            '도쿄에서 총공',                 '해외에서도 참여해요 🇯🇵'),
  (12033, 1103, 1310, 1000, 0, 'COUNTED',            '이번 앨범 수록곡 추천',         '3번 트랙 꼭 들어보세요'),
  (12034, 1103, 1410, 350,  0, 'COUNTED',            '이클립스러 출석',               '가입하고 첫 총공 참여!'),
  (12035, 1103, 1410, 120,  0, 'COUNTED',            '총공 두 번째 글',               '내일도 올게요 이클립스'),
  (12041, 1104, 1303, 2500, 0, 'COUNTED',            '프리즘 컴백 축하',              '프리즘 이번 컨셉 진짜 찰떡'),
  (12042, 1104, 1306, 1300, 0, 'COUNTED',            '무지개 응원',                   '무지개처럼 빛나는 프리즘 🌈'),
  (12043, 1104, 1414, 450,  0, 'COUNTED',            '프리즘빛 첫 인증',              '프리즘 총공 처음 참여해요!');

INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`)
SELECT tp.post_id, 'FAN', tp.artist_id, tp.author_id, tp.title, CONCAT(tp.body, '\n\n', t.hashtag), 0, tp.hidden,
       LEAST(NOW(6), GREATEST(DATE_ADD(e.start_at, INTERVAL 30 MINUTE), DATE_SUB(NOW(6), INTERVAL tp.mins_ago MINUTE)))
FROM `tmp_now_posts` tp
JOIN `hashtag_event_target` t ON t.event_id = @cur AND t.artist_id = tp.artist_id
JOIN `hashtag_event` e ON e.id = t.event_id;

INSERT INTO `hashtag_event_entry` (`target_id`, `post_id`, `fan_id`, `status`, `created_at`)
SELECT t.id, p.id, p.author_id, tp.entry_status, p.created_at
FROM `tmp_now_posts` tp
JOIN `post` p ON p.id = tp.post_id
JOIN `hashtag_event_target` t ON t.event_id = @cur AND t.artist_id = tp.artist_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_past_posts`;
DROP TEMPORARY TABLE IF EXISTS `tmp_now_posts`;

-- ------------------------------------------------------------
-- [3] 신고 - 신고당할 글·댓글과 신고 내역 (대기 위주 + 오늘 기각 1건)
--     "처리(삭제)" 사례는 글이 지워진 상태라 신고 행 없이 관리자 로그로만 남긴다 ([7] 참고)
-- ------------------------------------------------------------
INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`) VALUES
  (12201, 'FAN', 1101, 1405, '🔥포카 무료 나눔 이벤트🔥', '선착순 100명! 프로필 링크 들어가서 이름이랑 전화번호 입력하면 NOVA 미공개 포카 무료로 보내드려요 ㅎㅎ 빨리빨리~', 0, 0, DATE_SUB(NOW(6), INTERVAL 260 MINUTE)),
  (12202, 'FAN', 1102, 1409, '솔직히 타팬들 수준', '다른 그룹 팬들은 왜 이렇게 수준이 낮은지 모르겠음 ㅋㅋ 우리랑은 비교도 안 됨', 0, 0, DATE_SUB(NOW(6), INTERVAL 520 MINUTE)),
  (12203, 'FAN', 1103, 1413, '콘서트 티켓 양도합니다', '막콘 VIP 2연석 양도해요. 정가 3배, 입금 먼저 해주시면 바로 보내드립니다. 오픈채팅으로 문의 주세요', 0, 0, DATE_SUB(NOW(6), INTERVAL 1300 MINUTE)),
  (12204, 'FAN', 1104, 1416, '레오 진짜 싫다', '레오 때문에 무대 다 망쳤음. 그냥 탈퇴했으면 좋겠다', 0, 0, DATE_SUB(NOW(6), INTERVAL 2100 MINUTE)),
  (12206, 'FAN', 1103, 1411, '이번 무대 의상 아쉬워요', '곡은 정말 좋은데 의상이 컨셉이랑 조금 안 맞았던 것 같아요. 다음엔 더 잘 어울리는 걸로 보고 싶어요!', 0, 0, DATE_SUB(NOW(6), INTERVAL 3000 MINUTE));

INSERT INTO `comment` (`id`, `content`, `created_at`, `author_id`, `post_id`, `parent_id`, `deleted_at`) VALUES
  (22001, '이 글 쓴 사람 다른 커뮤에서 봤는데 완전 이상한 사람임 ㅋㅋ 다들 조심하세요', DATE_SUB(NOW(6), INTERVAL 400 MINUTE), 1405, 10001, NULL, NULL),
  (22002, '굿즈 여기보다 훨씬 싸게 팔아요 → 오픈채팅 "루미굿즈싸게" 검색', DATE_SUB(NOW(6), INTERVAL 900 MINUTE), 1409, 10019, NULL, NULL),
  (22003, '멤버 숙소 앞에서 찍은 사진 있는데 필요하신 분 DM 주세요', DATE_SUB(NOW(6), INTERVAL 1600 MINUTE), 1412, 10040, NULL, NULL);

INSERT INTO `report` (`id`, `created_at`, `reason`, `status`, `resolved_at`, `post_id`, `reporter_id`) VALUES
  (7001, DATE_SUB(NOW(6), INTERVAL 240 MINUTE), 'SPAM',  'PENDING',   NULL, 12201, 1302),
  (7002, DATE_SUB(NOW(6), INTERVAL 200 MINUTE), 'SPAM',  'PENDING',   NULL, 12201, 1306),
  (7003, DATE_SUB(NOW(6), INTERVAL 150 MINUTE), 'ETC',   'PENDING',   NULL, 12201, 1309),
  (7004, DATE_SUB(NOW(6), INTERVAL 500 MINUTE), 'ABUSE', 'PENDING',   NULL, 12202, 1303),
  (7005, DATE_SUB(NOW(6), INTERVAL 430 MINUTE), 'ABUSE', 'PENDING',   NULL, 12202, 1307),
  (7006, DATE_SUB(NOW(6), INTERVAL 1250 MINUTE),'SPAM',  'PENDING',   NULL, 12203, 1304),
  (7007, DATE_SUB(NOW(6), INTERVAL 1100 MINUTE),'ETC',   'PENDING',   NULL, 12203, 1308),
  (7008, DATE_SUB(NOW(6), INTERVAL 2000 MINUTE),'ABUSE', 'PENDING',   NULL, 12204, 1311),
  (7009, DATE_SUB(NOW(6), INTERVAL 2900 MINUTE),'ETC',   'DISMISSED', GREATEST(DATE_SUB(NOW(6), INTERVAL 90 MINUTE), TIMESTAMP(CURDATE())), 12206, 1312);

INSERT INTO `comment_report` (`id`, `created_at`, `reason`, `status`, `resolved_at`, `comment_id`, `reporter_id`) VALUES
  (7101, DATE_SUB(NOW(6), INTERVAL 380 MINUTE),  'ABUSE', 'PENDING', NULL, 22001, 1313),
  (7102, DATE_SUB(NOW(6), INTERVAL 330 MINUTE),  'ABUSE', 'PENDING', NULL, 22001, 1311),
  (7103, DATE_SUB(NOW(6), INTERVAL 850 MINUTE),  'SPAM',  'PENDING', NULL, 22002, 1307),
  (7104, DATE_SUB(NOW(6), INTERVAL 1500 MINUTE), 'ETC',   'PENDING', NULL, 22003, 1309),
  (7105, DATE_SUB(NOW(6), INTERVAL 1400 MINUTE), 'ETC',   'PENDING', NULL, 22003, 1310);

-- ------------------------------------------------------------
-- [4] 입점 신청 - 대기 4건(한·일·영) + 반려 1건
--     승인하면 활성화 메일이 신청 이메일로 가므로 admin4.wp 메일함(+별칭)으로 받게 해 두었다
-- ------------------------------------------------------------
INSERT INTO `partnership_applications` (`id`, `applicant_type`, `applicant_name`, `contact_name`, `email`, `phone`, `message`, `applicant_language`, `status`, `reviewed_by`, `reviewed_at`, `rejection_reason`, `created_at`, `updated_at`) VALUES
  (801, 'AGENCY', '오로라스튜디오', '김민서', 'admin4.wp+aurora@gmail.com', '010-2481-3579',
   '안녕하세요, 신인 보이그룹 2팀을 매니지먼트하는 오로라스튜디오입니다. 내년 상반기 데뷔를 앞두고 팬 커뮤니티를 WePlaNet 에서 운영하고 싶어 입점을 신청드립니다. 공지·일정·굿즈 기능을 주로 사용할 예정입니다.',
   'KO', 'PENDING_APPROVAL', NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 180 MINUTE), DATE_SUB(NOW(6), INTERVAL 180 MINUTE)),
  (802, 'ARTIST', '하늘 (HANEUL)', '이하늘', 'admin4.wp+haneul@gmail.com', '010-7720-1145',
   '싱어송라이터 하늘입니다. 1인 소속사로 활동 중이며, 팬분들과 라이브 방송과 DM 으로 더 가깝게 소통하고 싶어 신청합니다.',
   'KO', 'PENDING_APPROVAL', NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 1450 MINUTE), DATE_SUB(NOW(6), INTERVAL 1450 MINUTE)),
  (803, 'AGENCY', 'サクラ・エンターテインメント', '田中 美咲', 'admin4.wp+sakura@gmail.com', '+81-3-5555-0123',
   'はじめまして。東京のサクラ・エンターテインメントです。所属ガールズグループの韓国ファン向けコミュニティを開設したく、申請いたします。日本語での対応も可能です。',
   'JA', 'PENDING_APPROVAL', NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 2900 MINUTE), DATE_SUB(NOW(6), INTERVAL 2900 MINUTE)),
  (804, 'AGENCY', 'Northwind Records', 'Emily Carter', 'admin4.wp+northwind@gmail.com', '+1-213-555-0198',
   'Hello, we are Northwind Records, an independent label based in LA. We would like to open an official community for our K-pop crossover project and use membership and goods shop features.',
   'EN', 'PENDING_APPROVAL', NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 4300 MINUTE), DATE_SUB(NOW(6), INTERVAL 4300 MINUTE)),
  (805, 'AGENCY', '빛나는기획', '박준호', 'admin4.wp+bitna@gmail.com', '010-3300-9988',
   '신생 기획사 빛나는기획입니다. 소속 아티스트 커뮤니티 개설을 희망합니다.',
   'KO', 'REJECTED', 1002, DATE_SUB(NOW(6), INTERVAL 5000 MINUTE), '사업자 정보가 확인되지 않아 반려합니다. 사업자등록번호와 소속 아티스트 정보를 보완해 다시 신청해 주세요.',
   DATE_SUB(NOW(6), INTERVAL 8600 MINUTE), DATE_SUB(NOW(6), INTERVAL 5000 MINUTE));

-- ------------------------------------------------------------
-- [5] 팬 프로젝트 - 503·504 심사 대기 / 505 반려 / 506 모금 마감(정산 대기) / 507 정산 완료
--     정산 계좌의 암호문·HMAC 은 무작위 더미 값 (화면은 은행·끝 4자리·검증 상태만 사용)
-- ------------------------------------------------------------
INSERT INTO `fan_project` (`id`, `artist_id`, `creator_id`, `title`, `event_type`, `goal_amount`, `funding_start_at`, `funding_end_at`, `description`, `status`,
                           `special_badge_count_at_apply`, `basic_badge_count_at_apply`, `identity_verified_at`, `reviewed_by`, `reviewed_at`, `rejection_reason`, `created_at`, `updated_at`) VALUES
  (503, 1101, 1309, '시온 생일카페 프로젝트', 'BIRTHDAY_CAFE', 600000,
   TIMESTAMP(CURDATE() + INTERVAL 3 DAY), TIMESTAMP(CURDATE() + INTERVAL 24 DAY, '23:59:59'),
   '시온 생일을 맞아 홍대 카페 한 곳을 하루 동안 꾸며 생일카페를 엽니다. 모금액은 대관비, 컵홀더·포토카드 제작비, 현수막 제작에 사용하며 남은 금액은 아티스트 이름으로 기부합니다.',
   'PENDING_APPROVAL', 2, 7, DATE_SUB(NOW(6), INTERVAL 1500 MINUTE), NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 1440 MINUTE), DATE_SUB(NOW(6), INTERVAL 1440 MINUTE)),
  (504, 1103, 1302, 'ECLIPSE 콘서트 응원 화환', 'CONCERT', 400000,
   TIMESTAMP(CURDATE() + INTERVAL 2 DAY), TIMESTAMP(CURDATE() + INTERVAL 16 DAY, '23:59:59'),
   '다음 달 단독 콘서트 공연장 앞에 쌀 화환을 보냅니다. 화환 쌀은 공연 후 지역 푸드뱅크에 기부할 예정이며, 정산 내역은 커뮤니티에 공개합니다.',
   'PENDING_APPROVAL', 1, 6, DATE_SUB(NOW(6), INTERVAL 400 MINUTE), NULL, NULL, NULL, DATE_SUB(NOW(6), INTERVAL 360 MINUTE), DATE_SUB(NOW(6), INTERVAL 360 MINUTE)),
  (505, 1104, 1306, 'PRISM 데뷔 기념 전광판', 'BILLBOARD', 2500000,
   TIMESTAMP(CURDATE() + INTERVAL 5 DAY), TIMESTAMP(CURDATE() + INTERVAL 35 DAY, '23:59:59'),
   '데뷔 기념일에 강남역 전광판 광고를 진행하고 싶어요. 많이 모이면 기간을 늘릴게요.',
   'REJECTED', 1, 5, DATE_SUB(NOW(6), INTERVAL 3000 MINUTE), 1003, DATE_SUB(NOW(6), INTERVAL 1300 MINUTE),
   '목표 금액 대비 사용 계획이 구체적이지 않아 반려합니다. 광고 기간·단가 견적과 초과 모금 시 처리 방법을 보완해 다시 신청해 주세요.',
   DATE_SUB(NOW(6), INTERVAL 2900 MINUTE), DATE_SUB(NOW(6), INTERVAL 1300 MINUTE)),
  (506, 1102, 1305, 'LUMI 3주년 팬 서포트', 'ETC', 500000,
   DATE_SUB(NOW(6), INTERVAL 40 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY),
   'LUMI 데뷔 3주년을 맞아 멤버 4명에게 커피차와 손편지 앨범을 보냅니다. 커피차 1회 + 편지 앨범 제작비로 사용합니다.',
   'FUNDING_CLOSED', 2, 6, DATE_SUB(NOW(6), INTERVAL 44 DAY), 1001, DATE_SUB(NOW(6), INTERVAL 42 DAY), NULL, DATE_SUB(NOW(6), INTERVAL 44 DAY), DATE_SUB(NOW(6), INTERVAL 3 DAY)),
  (507, 1105, 1301, '유리 생일 지하철 광고', 'BILLBOARD', 700000,
   DATE_SUB(NOW(6), INTERVAL 60 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY),
   '한유리 생일 주간에 합정역 디지털 광고판 2면을 2주 동안 진행했습니다. 광고 사진은 커뮤니티에 올려 두었어요.',
   'COMPLETED', 1, 8, DATE_SUB(NOW(6), INTERVAL 65 DAY), 1002, DATE_SUB(NOW(6), INTERVAL 63 DAY), NULL, DATE_SUB(NOW(6), INTERVAL 65 DAY), DATE_SUB(NOW(6), INTERVAL 21 DAY));

INSERT INTO `fan_project_cover_image` (`project_id`, `original_name`, `stored_name`, `content_type`, `file_size`, `created_at`) VALUES
  (503, 'cover_503.jpg', 'demo_nova_media3003_1.jpg',    'image/jpeg', 178369, DATE_SUB(NOW(6), INTERVAL 1440 MINUTE)),
  (504, 'cover_504.jpg', 'demo_eclipse_media3013_1.jpg', 'image/jpeg', 182332, DATE_SUB(NOW(6), INTERVAL 360 MINUTE)),
  (505, 'cover_505.jpg', 'demo_prism_media3017_1.jpg',   'image/jpeg', 31736,  DATE_SUB(NOW(6), INTERVAL 2900 MINUTE)),
  (506, 'cover_506.jpg', 'demo_lumi_media3008_1.jpg',    'image/jpeg', 201355, DATE_SUB(NOW(6), INTERVAL 44 DAY)),
  (507, 'cover_507.jpg', 'demo_yuri_media3022_1.jpg',    'image/jpeg', 30935,  DATE_SUB(NOW(6), INTERVAL 65 DAY));

INSERT INTO `fan_project_settlement_account` (`project_id`, `bank_code`, `account_number_enc`, `account_number_hmac`, `account_number_last4`, `verification_status`, `verified_at`, `created_at`, `updated_at`) VALUES
  (506, '090', RANDOM_BYTES(48), SHA2(CONCAT('demo2-settlement-506-', UUID()), 256), '4821', 'UNVERIFIED', NULL, DATE_SUB(NOW(6), INTERVAL 44 DAY), DATE_SUB(NOW(6), INTERVAL 44 DAY)),
  (507, '004', RANDOM_BYTES(48), SHA2(CONCAT('demo2-settlement-507-', UUID()), 256), '1357', 'VERIFIED', DATE_SUB(NOW(6), INTERVAL 22 DAY), DATE_SUB(NOW(6), INTERVAL 65 DAY), DATE_SUB(NOW(6), INTERVAL 22 DAY));

-- 후원 내역 (모의 결제 MOCK, 전부 결제 완료) - 506 은 목표 대비 112%, 507 은 107%
INSERT INTO `fan_project_contribution` (`project_id`, `contributor_id`, `order_no`, `idempotency_key`, `payment_provider`, `amount`, `is_anonymous`, `refund_policy_agreed_at`, `payment_status`, `paid_at`, `created_at`, `updated_at`)
SELECT x.project_id, x.contributor_id, CONCAT('DEMO2-', x.project_id, '-', LPAD(x.seq, 3, '0')), CONCAT('demo2-', x.project_id, '-', LPAD(x.seq, 3, '0')), 'MOCK',
       x.amount, x.anon, DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), 'PAID',
       DATE_ADD(DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_ADD(DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), INTERVAL 30 MINUTE)
FROM (
            SELECT 506 AS project_id, 1 AS seq, 1301 AS contributor_id, 50000 AS amount, 0 AS anon, 39 AS days_ago
  UNION ALL SELECT 506, 2, 1303, 100000, 0, 35
  UNION ALL SELECT 506, 3, 1307, 100000, 1, 30
  UNION ALL SELECT 506, 4, 1309, 30000,  0, 22
  UNION ALL SELECT 506, 5, 1310, 80000,  0, 15
  UNION ALL SELECT 506, 6, 1314, 100000, 0, 10
  UNION ALL SELECT 506, 7, 1407, 50000,  0, 8
  UNION ALL SELECT 506, 8, 1408, 50000,  1, 5
  UNION ALL SELECT 507, 1, 1304, 200000, 0, 58
  UNION ALL SELECT 507, 2, 1307, 150000, 0, 50
  UNION ALL SELECT 507, 3, 1310, 100000, 1, 44
  UNION ALL SELECT 507, 4, 1312, 150000, 0, 37
  UNION ALL SELECT 507, 5, 1314, 100000, 0, 31
  UNION ALL SELECT 507, 6, 1301, 50000,  0, 27
) x;

-- ------------------------------------------------------------
-- [6] 배지 - 실제 활동 데이터에 맞춰 지급 (이미 있는 배지는 건너뜀)
--     대상: demo_fan01~14(1301~1314) + 새 팬(1401~1419). demo_fan15(1315)는 일부러 제외
-- ------------------------------------------------------------
-- 커뮤니티 첫 가입
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT cm.fan_id, cm.artist_id, b.badge_code, b.badge_name, b.badge_type, cm.joined_at, cm.joined_at
FROM `community_members` cm
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_JOIN'
WHERE cm.fan_id BETWEEN 1301 AND 1314 OR cm.fan_id BETWEEN 1401 AND 1419;

-- 가입 후 100일
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT cm.fan_id, cm.artist_id, b.badge_code, b.badge_name, b.badge_type, DATE_ADD(cm.joined_at, INTERVAL 100 DAY), DATE_ADD(cm.joined_at, INTERVAL 100 DAY)
FROM `community_members` cm
JOIN `fan_badge` b ON b.badge_code = 'BASIC_DAY_100'
WHERE (cm.fan_id BETWEEN 1301 AND 1314 OR cm.fan_id BETWEEN 1401 AND 1419)
  AND cm.joined_at <= DATE_SUB(NOW(6), INTERVAL 100 DAY);

-- 첫 게시글 (팬 게시판)
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(p.created_at), MIN(p.created_at)
FROM `post` p
JOIN `community_members` cm ON cm.fan_id = p.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_FIRST_POST'
WHERE p.board_type = 'FAN'
  AND (p.author_id BETWEEN 1301 AND 1314 OR p.author_id BETWEEN 1401 AND 1419)
GROUP BY p.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type;

-- 댓글 5개
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MAX(c.created_at), MAX(c.created_at)
FROM `comment` c
JOIN `post` p ON p.id = c.post_id
JOIN `community_members` cm ON cm.fan_id = c.author_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_COMMENT_5'
WHERE (c.author_id BETWEEN 1301 AND 1314 OR c.author_id BETWEEN 1401 AND 1419)
  AND c.deleted_at IS NULL
GROUP BY c.author_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type
HAVING COUNT(*) >= 5;

-- 좋아요 10개
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT l.user_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type, MAX(l.created_at), MAX(l.created_at)
FROM `post_like` l
JOIN `post` p ON p.id = l.post_id
JOIN `community_members` cm ON cm.fan_id = l.user_id AND cm.artist_id = p.artist_id
JOIN `fan_badge` b ON b.badge_code = 'BASIC_LIKE_10'
WHERE l.user_id BETWEEN 1301 AND 1314 OR l.user_id BETWEEN 1401 AND 1419
GROUP BY l.user_id, p.artist_id, b.badge_code, b.badge_name, b.badge_type
HAVING COUNT(*) >= 10;

-- 첫 멤버십 가입 (스페셜)
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT m.fan_id, m.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(m.created_at), MIN(m.created_at)
FROM `membership` m
JOIN `fan_badge` b ON b.badge_code = 'SPECIAL_MEMBERSHIP_1'
WHERE m.fan_id BETWEEN 1301 AND 1314 OR m.fan_id BETWEEN 1401 AND 1419
GROUP BY m.fan_id, m.artist_id, b.badge_code, b.badge_name, b.badge_type;

-- 프로젝트 참여 (스페셜) - 결제 완료한 후원 기준, 프로젝트의 커뮤니티에 지급
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT c.contributor_id, fp.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(c.paid_at), MIN(c.paid_at)
FROM `fan_project_contribution` c
JOIN `fan_project` fp ON fp.id = c.project_id
JOIN `fan_badge` b ON b.badge_code = 'SPECIAL_PROJECT_CREATE'
WHERE c.payment_status = 'PAID'
  AND (c.contributor_id BETWEEN 1301 AND 1314 OR c.contributor_id BETWEEN 1401 AND 1419)
GROUP BY c.contributor_id, fp.artist_id, b.badge_code, b.badge_name, b.badge_type;

-- demo_fan09(응원봉장인) NOVA - 프로젝트 등록 자격(일반 5 + 스페셜 1)을 확실히 넘기도록 활동 배지 추가
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT 1309, 1101, b.badge_code, b.badge_name, b.badge_type, DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_SUB(NOW(6), INTERVAL x.days_ago DAY)
FROM (
            SELECT 'BASIC_FOLLOW_ARTIST' AS badge_code, 90 AS days_ago
  UNION ALL SELECT 'BASIC_MEDIA_VIEW', 60
  UNION ALL SELECT 'BASIC_LIVE_VIEW', 20
) x
JOIN `fan_badge` b ON b.badge_code = x.badge_code;

-- ------------------------------------------------------------
-- [7] 관리자 로그 - 위 데이터와 맞는 조치 이력 (관리자 4명이 나눠서 처리)
-- ------------------------------------------------------------
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`) VALUES
  (7001, 1002, 'PROJECT_APPROVE',               'PROJECT',                 507,   '유리 생일 지하철 광고 승인',                                  '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 63 DAY)),
  (7002, 1001, 'PROJECT_APPROVE',               'PROJECT',                 506,   'LUMI 3주년 팬 서포트 승인',                                   '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 42 DAY)),
  (7003, 1004, 'SETTLEMENT_ACCOUNT_VERIFY',     'SETTLEMENT',              507,   '유리 생일 지하철 광고정산 계좌 확인 완료',                       '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 22 DAY)),
  (7004, 1004, 'SETTLEMENT_COMPLETE',           'SETTLEMENT',              507,   '유리 생일 지하철 광고정산 완료',                                '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 21 DAY)),
  (7005, 1001, 'PROJECT_APPROVE',               'PROJECT',                 501,   'NOVA 데뷔 4주년 지하철광고 승인',                              '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 9 DAY)),
  (7006, 1001, 'PROJECT_APPROVE',               'PROJECT',                 502,   '서아 생일카페 프로젝트 승인',                                  '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 9 DAY)),
  (7007, 1002, 'PARTNERSHIP_APPLICATION_REJECT','PARTNERSHIP_APPLICATION', 805,   '사업자 정보가 확인되지 않아 반려합니다. 사업자등록번호와 소속 아티스트 정보를 보완해 다시 신청해 주세요.', '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 5000 MINUTE)),
  (7008, 1003, 'REPORT_RESOLVE',                'REPORT',                  12299, '게시글 신고 처리 : 콘텐츠 삭제 (오늘만 특가 굿즈 공구 모집)',      '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 2900 MINUTE)),
  (7009, 1003, 'USER_SUSPEND',                  'USER',                    1420,  '광고성 게시글 반복 작성 (신고 2건 처리)',                         '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 2880 MINUTE)),
  (7010, 1003, 'PROJECT_REJECT',                'PROJECT',                 505,   '목표 금액 대비 사용 계획이 구체적이지 않아 반려합니다. 광고 기간·단가 견적과 초과 모금 시 처리 방법을 보완해 다시 신청해 주세요.', '127.0.0.1', DATE_SUB(NOW(6), INTERVAL 1300 MINUTE)),
  (7011, 1002, 'REPORT_DISMISS',                'REPORT',                  12206, '게시글 신고 1건 기각',                                         '127.0.0.1', GREATEST(DATE_SUB(NOW(6), INTERVAL 90 MINUTE), TIMESTAMP(CURDATE())));

-- 총공 이벤트 로그는 이벤트가 실제로 만들어졌을 때만
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`)
SELECT 7012, 1001, 'HASHTAG_EVENT_CREATE', 'HASHTAG_EVENT', 901, '9월 가을맞이 총공 등록 (4팀)', '127.0.0.1', `created_at` FROM `hashtag_event` WHERE `id` = 901;
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`)
SELECT 7013, 1001, 'HASHTAG_EVENT_FINALIZE', 'HASHTAG_EVENT', 901, '9월 가을맞이 총공 집계 확정', '127.0.0.1', `finalized_at` FROM `hashtag_event` WHERE `id` = 901;
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`)
SELECT 7014, 1001, 'HASHTAG_EVENT_CREATE', 'HASHTAG_EVENT', 902, '10월 컴백 응원 총공 등록 (4팀)', '127.0.0.1', `created_at` FROM `hashtag_event` WHERE `id` = 902;

-- ------------------------------------------------------------
-- [8] 북마크 - demo_fan15
-- ------------------------------------------------------------
INSERT INTO `post_bookmark` (`id`, `created_at`, `post_id`, `user_id`) VALUES
  (9001, DATE_SUB(NOW(6), INTERVAL 7 DAY), 10011, 1315),
  (9002, DATE_SUB(NOW(6), INTERVAL 5 DAY), 10019, 1315),
  (9003, DATE_SUB(NOW(6), INTERVAL 3 DAY), 10040, 1315),
  (9004, DATE_SUB(NOW(6), INTERVAL 1 DAY), 10003, 1315);

COMMIT;
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- ------------------------------------------------------------
-- [확인] 넣은 데이터 개수
-- ------------------------------------------------------------
SELECT '최근 30일 가입 팬' AS 항목, COUNT(*) AS 개수 FROM `users` WHERE `role` = 'FAN' AND `created_at` >= DATE_SUB(NOW(6), INTERVAL 30 DAY)
UNION ALL SELECT '진행 중 총공 집계 글', COUNT(*) FROM `hashtag_event_entry` en JOIN `hashtag_event_target` t ON t.id = en.target_id WHERE t.event_id = @cur
UNION ALL SELECT '지난 총공(901) 집계 글', COUNT(*) FROM `hashtag_event_entry` en JOIN `hashtag_event_target` t ON t.id = en.target_id WHERE t.event_id = 901
UNION ALL SELECT '처리 대기 신고(글+댓글)', (SELECT COUNT(*) FROM `report` WHERE `status` = 'PENDING') + (SELECT COUNT(*) FROM `comment_report` WHERE `status` = 'PENDING')
UNION ALL SELECT '입점 신청 대기', COUNT(*) FROM `partnership_applications` WHERE `status` = 'PENDING_APPROVAL'
UNION ALL SELECT '프로젝트 심사 대기', COUNT(*) FROM `fan_project` WHERE `status` = 'PENDING_APPROVAL'
UNION ALL SELECT '정산 대기(모금 마감)', COUNT(*) FROM `fan_project` WHERE `status` = 'FUNDING_CLOSED'
UNION ALL SELECT '배지 보유 행', COUNT(*) FROM `fan_badge_ownership`
UNION ALL SELECT '관리자 로그', COUNT(*) FROM `admin_action_logs`;
