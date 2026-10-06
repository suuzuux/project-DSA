-- ============================================================
-- WePlaNet 데모 데이터 5 - 소속사, 굿즈, 일정, 전체공지, 팬 프로젝트, 지난 해시태그 총공 + 결과 공지, 관리자 로그
-- ------------------------------------------------------------
-- 실행 순서
--   1) weplanet_schema_full_reset_v2.sql / weplanet_demo_seed.sql  ※ 이미 돌린 DB면 생략
--   2) 데모 2·3·4 (시연보강 / 계정게시글추가 / 아티스트공지미디어)   ※ 이미 돌린 DB면 생략
--      (안 돌렸어도 이 파일은 돌아감 - 그 파일들의 계정·커뮤니티가 필요한 행만 자동으로 빠진다)
--   3) 이 파일 - 여러 번 돌려도 된다 (맨 앞에서 이 파일이 넣은 행을 지우고 다시 넣음)
--   4) docs/demo/demo_images 의 새 굿즈 이미지 20개(demo_nebula|harin|vela|siwoo|pastel_goods_*)를 서버 실행 폴더의 uploads/ 에 복사
--
-- 추가 계정 (비밀번호 공통 Test1234)
--   소속사  agency_noeul (노을엔터테인먼트, 권한 승인됨) / agency_pado (파도뮤직, 권한 승인됨)
--           agency_skygarden (하늘정원컴퍼니, 권한 승인 대기) / starlight_marketing (스타라이트 직원, 승인됨)
--           bluewave_ar (블루웨이브 직원, 승인 대기)
--
-- 넣는 것
--   [1] 소속사 3곳 + 담당자 계정 5개 (권한 승인 대기 2건 포함) + 이 소속사들의 입점 신청 승인 이력 2건
--   [2] 굿즈 27개 - 새 커뮤니티 5곳 4개씩(응원봉·포토카드·후드티·멤버십 시즌그리팅) + 기존 6곳 디지털 포토북 + 판매 준비 중(숨김) 1개
--   [3] 아티스트 일정 24개 - 11개 커뮤니티의 다가오는 일정
--   [4] 전체공지 6개 (예약 공지 1개 포함)
--   [5] 팬 프로젝트 8개 - 모금 중 3 / 승인(시작 전) 1 / 심사 대기 1 / 모금 마감(정산 대기) 1 / 정산 완료 1 / 취소 1
--   [6] 지난 해시태그 총공 2회 (8월 5팀, 7월 3팀) - 글·집계·확정 스냅샷
--   [7] 해시태그 총공 결과 공지 - 집계 확정된 지난 총공(9월·8월·7월)마다 1개씩, 앱의 "결과 공지 자동 작성"과 같은 형식
--   [8] 관리자 로그 - 위 데이터와 맞는 조치 이력 약 40건
--
-- ※ MySQL Workbench 는 자동 커밋이 꺼져 있을 수 있어 맨 끝에 COMMIT 을 넣어 두었다
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
SET @old_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;
SET SESSION group_concat_max_len = 20000;

-- ------------------------------------------------------------
-- [0] 재실행 대비: 이 파일이 넣는 행을 먼저 지운다
--     agencies 104~106 / users 1014~1018 / partnership 806~807 / shop_goods 4101~4199 / artist_schedule 201~299
--     site_notice 121~140 / fan_project 508~515 / hashtag_event 903·904 / post 12301~12399 / admin_action_logs 7101~7199
-- ------------------------------------------------------------
DELETE FROM `admin_action_logs` WHERE `id` BETWEEN 7101 AND 7199;
DELETE FROM `site_notice` WHERE `id` BETWEEN 121 AND 140;
DELETE FROM `artist_schedule` WHERE `id` BETWEEN 201 AND 299;
DELETE FROM `hashtag_event` WHERE `id` IN (903, 904);   -- 참여 아티스트·집계 기록은 ON DELETE CASCADE
DELETE FROM `comment` WHERE `post_id` BETWEEN 12301 AND 12399 AND `parent_id` IS NOT NULL;
DELETE FROM `comment` WHERE `post_id` BETWEEN 12301 AND 12399;
DELETE FROM `post_like` WHERE `post_id` BETWEEN 12301 AND 12399;
DELETE FROM `post_bookmark` WHERE `post_id` BETWEEN 12301 AND 12399;
DELETE FROM `report` WHERE `post_id` BETWEEN 12301 AND 12399;
DELETE FROM `post` WHERE `id` BETWEEN 12301 AND 12399;
DELETE FROM `fan_project_contribution` WHERE `project_id` BETWEEN 508 AND 515;
DELETE FROM `fan_project` WHERE `id` BETWEEN 508 AND 515;   -- 커버·정산 계좌는 ON DELETE CASCADE
DELETE FROM `main_banner` WHERE `goods_id` BETWEEN 4101 AND 4199;
DELETE FROM `shop_cart_item` WHERE CAST(SUBSTRING_INDEX(`product_id`, ':', 1) AS UNSIGNED) BETWEEN 4101 AND 4199;
DELETE FROM `shop_goods_variant` WHERE `goods_id` BETWEEN 4101 AND 4199;
DELETE FROM `shop_goods_category` WHERE `goods_id` BETWEEN 4101 AND 4199;
DELETE FROM `shop_goods_option` WHERE `goods_id` BETWEEN 4101 AND 4199;
DELETE FROM `shop_goods` WHERE `id` BETWEEN 4101 AND 4199;
DELETE FROM `partnership_applications` WHERE `id` BETWEEN 806 AND 807;
DELETE FROM `agency_profiles` WHERE `user_id` BETWEEN 1014 AND 1018;
DELETE FROM `users` WHERE `id` BETWEEN 1014 AND 1018;
DELETE FROM `agencies` WHERE `id` BETWEEN 104 AND 106;

-- ------------------------------------------------------------
-- [1] 소속사 3곳 + 담당자 계정 (비밀번호 공통 Test1234)
--     하늘정원컴퍼니 대표와 블루웨이브 A&R 직원은 관리자 "소속사 권한 승인" 대기 상태
-- ------------------------------------------------------------
INSERT INTO `agencies` (`id`, `name`, `business_no`, `ceo_name`, `status`, `created_at`, `updated_at`) VALUES
  (104, '노을엔터테인먼트', '104-81-10404', '서민재', 'ACTIVE', DATE_SUB(NOW(6), INTERVAL 21 DAY), DATE_SUB(NOW(6), INTERVAL 21 DAY)),
  (105, '파도뮤직',         '105-81-10505', '정하윤', 'ACTIVE', DATE_SUB(NOW(6), INTERVAL 13 DAY), DATE_SUB(NOW(6), INTERVAL 13 DAY)),
  (106, '하늘정원컴퍼니',   '106-81-10606', '김도윤', 'ACTIVE', DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2 DAY));
INSERT INTO `users` (`id`, `username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `last_login_at`, `created_at`, `updated_at`) VALUES
  (1014, 'agency_noeul',        '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'AGENCY', 'ACTIVE', 104, '서민재', '노을 매니저',       'agency_noeul@weplanet.test',        DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 300 MINUTE),  DATE_SUB(NOW(6), INTERVAL 21 DAY), DATE_SUB(NOW(6), INTERVAL 300 MINUTE)),
  (1015, 'agency_pado',         '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'AGENCY', 'ACTIVE', 105, '정하윤', '파도 매니저',       'agency_pado@weplanet.test',         DATE_SUB(NOW(6), INTERVAL 12 DAY), DATE_SUB(NOW(6), INTERVAL 1500 MINUTE), DATE_SUB(NOW(6), INTERVAL 13 DAY), DATE_SUB(NOW(6), INTERVAL 1500 MINUTE)),
  (1016, 'agency_skygarden',    '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'AGENCY', 'ACTIVE', 106, '김도윤', '하늘정원 매니저',   'agency_skygarden@weplanet.test',    DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2800 MINUTE), DATE_SUB(NOW(6), INTERVAL 2 DAY),  DATE_SUB(NOW(6), INTERVAL 2800 MINUTE)),
  (1017, 'starlight_marketing', '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'AGENCY', 'ACTIVE', 101, '최유나', '스타라이트 마케팅', 'starlight_marketing@weplanet.test', DATE_SUB(NOW(6), INTERVAL 7 DAY),  DATE_SUB(NOW(6), INTERVAL 120 MINUTE),  DATE_SUB(NOW(6), INTERVAL 7 DAY),  DATE_SUB(NOW(6), INTERVAL 120 MINUTE)),
  (1018, 'bluewave_ar',         '$2a$10$H2u7S71f8gdjfDEPzl3k/uUtgbG/rTwHDz8XUUe2X0yOAqE5f6muu', 'AGENCY', 'ACTIVE', 102, '한지훈', '블루웨이브 A&R',    'bluewave_ar@weplanet.test',         DATE_SUB(NOW(6), INTERVAL 1 DAY),  DATE_SUB(NOW(6), INTERVAL 900 MINUTE),  DATE_SUB(NOW(6), INTERVAL 1 DAY),  DATE_SUB(NOW(6), INTERVAL 900 MINUTE));
INSERT INTO `agency_profiles` (`user_id`, `agency_id`, `department`, `position`, `is_owner`, `approved_by`, `approved_at`) VALUES
  (1014, 104, '매니지먼트팀', '대표',   1, 1001, DATE_SUB(NOW(6), INTERVAL 20 DAY)),
  (1015, 105, '매니지먼트팀', '팀장',   1, 1002, DATE_SUB(NOW(6), INTERVAL 12 DAY)),
  (1016, 106, '경영지원팀',   '실장',   1, NULL, NULL),
  (1017, 101, '마케팅팀',     '매니저', 0, 1001, DATE_SUB(NOW(6), INTERVAL 6 DAY)),
  (1018, 102, 'A&R팀',        '대리',   0, NULL, NULL);

-- 노을엔터테인먼트·파도뮤직은 입점 신청 승인으로 들어온 소속사
INSERT INTO `partnership_applications` (`id`, `applicant_type`, `applicant_name`, `contact_name`, `email`, `phone`, `message`, `applicant_language`, `status`, `reviewed_by`, `reviewed_at`, `rejection_reason`, `created_at`, `updated_at`) VALUES
  (806, 'AGENCY', '노을엔터테인먼트', '서민재', 'agency_noeul@weplanet.test', '010-4100-2626',
   '발라드·인디 아티스트 중심의 소속사 노을엔터테인먼트입니다. 소속 아티스트의 공식 팬 커뮤니티를 WePlaNet 에서 운영하고 싶습니다.',
   'KO', 'APPROVED', 1001, DATE_SUB(NOW(6), INTERVAL 21 DAY), NULL, DATE_SUB(NOW(6), INTERVAL 24 DAY), DATE_SUB(NOW(6), INTERVAL 21 DAY)),
  (807, 'AGENCY', '파도뮤직', '정하윤', 'agency_pado@weplanet.test', '010-5200-7373',
   '밴드 음악 전문 레이블 파도뮤직입니다. 라이브 방송과 굿즈샵 기능을 중심으로 사용하려고 합니다.',
   'KO', 'APPROVED', 1002, DATE_SUB(NOW(6), INTERVAL 13 DAY), NULL, DATE_SUB(NOW(6), INTERVAL 15 DAY), DATE_SUB(NOW(6), INTERVAL 13 DAY));

-- ------------------------------------------------------------
-- [2] 굿즈
--     새 커뮤니티 5곳(NEBULA·서하린·VELA·윤시우·PASTEL) 4개씩: 응원봉 / 포토카드 / 후드티(S~XL) / 시즌그리팅(멤버십 전용)
--     기존 6곳: 디지털 포토북 1개씩 (미디어 사진을 썸네일로 사용) / NOVA: 판매 준비 중(숨김) 티셔츠 1개
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_goods_comm`;
CREATE TEMPORARY TABLE `tmp_goods_comm` (`base_id` bigint, `artist_id` bigint, `img_key` varchar(20), `name` varchar(50), `day_shift` int);
INSERT INTO `tmp_goods_comm` VALUES
  (4101, 1107, 'nebula', 'NEBULA', 0),
  (4105, 1108, 'harin',  '서하린', 1),
  (4109, 1109, 'vela',   'VELA',   2),
  (4113, 1110, 'siwoo',  '윤시우', 3),
  (4117, 1111, 'pastel', 'PASTEL', 4);

INSERT INTO `shop_goods` (`id`, `artist_id`, `name`, `description`, `price`, `thumbnail_url`, `official_url`, `status`, `sort_order`, `membership_only`, `shop_category`, `created_at`, `updated_at`)
SELECT c.base_id + t.idx, c.artist_id, CONCAT(c.name, ' ', t.name), t.description, t.price,
       CONCAT('demo_', c.img_key, '_goods_', t.img, '.jpg'), NULL, 'ON_SALE', t.idx, t.membership_only, t.shop_category,
       DATE_SUB(NOW(6), INTERVAL (t.days_ago + c.day_shift) DAY), DATE_SUB(NOW(6), INTERVAL (t.days_ago + c.day_shift) DAY)
FROM `tmp_goods_comm` c
JOIN `users` u ON u.id = c.artist_id
CROSS JOIN (
            SELECT 0 AS idx, '공식 응원봉' AS name, '## 공식 응원봉\n\n- 블루투스 연동으로 공연장 연출과 함께 빛나요\n- AAA 건전지 3개 (별도 구매)\n- 전용 스트랩 포함' AS description, 45000 AS price, 'lightstick' AS img, 0 AS membership_only, 'MD' AS shop_category, 40 AS days_ago
  UNION ALL SELECT 1, '포토카드 세트 (8종)', '## 포토카드 세트\n\n미공개 사진이 담긴 포토카드 8종 세트입니다.\n\n- 크기 55 × 85mm\n- 무광 코팅', 15000, 'photocard', 0, 'MD', 32
  UNION ALL SELECT 2, '로고 후드티', '## 로고 후드티\n\n도톰한 기모 원단으로 겨울까지 따뜻하게!\n\n- 소재: 면 80%, 폴리 20%\n- 오버핏', 69000, 'hoodie', 0, 'MD', 25
  UNION ALL SELECT 3, '2027 시즌그리팅 (멤버십 전용)', '## 2027 시즌그리팅\n\n멤버십 회원만 구매할 수 있어요.\n\n- 탁상 달력 + 다이어리 + 포스터 + 포토카드', 38000, 'season', 1, 'MEMBERSHIP', 10
) t;

DROP TEMPORARY TABLE IF EXISTS `tmp_goods_comm`;

INSERT INTO `shop_goods` (`id`, `artist_id`, `name`, `description`, `price`, `thumbnail_url`, `official_url`, `status`, `sort_order`, `membership_only`, `shop_category`, `created_at`, `updated_at`)
SELECT x.id, x.artist_id, x.name, x.description, x.price, x.thumb, NULL, x.status, x.sort_order, 0, x.shop_category,
       DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_SUB(NOW(6), INTERVAL x.days_ago DAY)
FROM (
            SELECT 4121 AS id, 1101 AS artist_id, 'NOVA 정규 2집 디지털 포토북' AS name, '## 디지털 포토북\n\n정규 2집 콘셉트 사진 120장을 고화질로 담았어요.\n\n- 결제 후 마이페이지에서 바로 열람' AS description, 9900 AS price, 'demo_nova_media3001_1.jpg' AS thumb, 'ON_SALE' AS status, 10 AS sort_order, 'DIGITAL' AS shop_category, 6 AS days_ago
  UNION ALL SELECT 4122, 1102, 'LUMI 3주년 디지털 포토북', '## 디지털 포토북\n\n데뷔 3주년 기념 미공개 사진 100장.\n\n- 결제 후 마이페이지에서 바로 열람', 9900, 'demo_lumi_media3006_1.jpg', 'ON_SALE', 10, 'DIGITAL', 8
  UNION ALL SELECT 4123, 1103, 'ECLIPSE 콘서트 디지털 포토북', '## 디지털 포토북\n\n단독 콘서트 무대·백스테이지 사진 150장.\n\n- 결제 후 마이페이지에서 바로 열람', 12900, 'demo_eclipse_media3011_1.jpg', 'ON_SALE', 10, 'DIGITAL', 4
  UNION ALL SELECT 4124, 1104, 'PRISM 데뷔 기념 디지털 포토북', '## 디지털 포토북\n\n데뷔 기념 멤버별 화보 80장.\n\n- 결제 후 마이페이지에서 바로 열람', 9900, 'demo_prism_media3016_1.jpg', 'ON_SALE', 10, 'DIGITAL', 9
  UNION ALL SELECT 4125, 1105, '한유리 소극장 콘서트 디지털 포토북', '## 디지털 포토북\n\n소극장 콘서트 현장 사진 60장과 손글씨 편지.\n\n- 결제 후 마이페이지에서 바로 열람', 8900, 'demo_yuri_media3021_1.jpg', 'ON_SALE', 10, 'DIGITAL', 5
  UNION ALL SELECT 4126, 1106, 'KAITO 도쿄 팬미팅 디지털 포토북', '## 디지털 포토북\n\n도쿄 팬미팅 현장 사진 90장.\n\n- 결제 후 마이페이지에서 바로 열람', 9900, 'demo_kaito_media3027_1.jpg', 'ON_SALE', 10, 'DIGITAL', 3
  UNION ALL SELECT 4127, 1101, 'NOVA 컴백 기념 티셔츠 (판매 준비 중)', '## 컴백 기념 티셔츠\n\n판매 준비 중인 상품입니다. 오픈 일정은 공지로 안내드려요.', 35000, 'demo_nova_goods_hoodie.jpg', 'HIDDEN', 11, 'MD', 1
) x
JOIN `users` u ON u.id = x.artist_id;

-- 상품 분류
INSERT INTO `shop_goods_category` (`goods_id`, `category`)
SELECT g.id, CASE WHEN g.name LIKE '%후드티%' OR g.name LIKE '%티셔츠%' THEN 'CLOTHING' ELSE 'OTHER' END
FROM `shop_goods` g
WHERE g.id BETWEEN 4101 AND 4199;

-- 재고 (옷은 사이즈별, 나머지는 단일)
INSERT INTO `shop_goods_variant` (`goods_id`, `option_key`, `option_value`, `stock_quantity`)
SELECT g.id, 'SIZE', s.size, s.stock
FROM `shop_goods` g
CROSS JOIN (SELECT 'S' AS size, 40 AS stock UNION ALL SELECT 'M', 60 UNION ALL SELECT 'L', 60 UNION ALL SELECT 'XL', 30) s
WHERE g.id BETWEEN 4101 AND 4199 AND (g.name LIKE '%후드티%' OR g.name LIKE '%티셔츠%');
INSERT INTO `shop_goods_variant` (`goods_id`, `option_key`, `option_value`, `stock_quantity`)
SELECT g.id, 'DEFAULT', '', CASE WHEN g.shop_category = 'DIGITAL' THEN 999 WHEN g.membership_only = 1 THEN 50 ELSE 100 END
FROM `shop_goods` g
WHERE g.id BETWEEN 4101 AND 4199 AND NOT (g.name LIKE '%후드티%' OR g.name LIKE '%티셔츠%');

-- ------------------------------------------------------------
-- [3] 아티스트 일정 - 오늘 기준 day_offset 일 뒤 hour_at 시 (음수면 지난 일정)
-- ------------------------------------------------------------
INSERT INTO `artist_schedule` (`id`, `artist_id`, `category`, `title`, `description`, `location`, `ticket_url`, `schedule_at`, `created_at`, `updated_at`)
SELECT x.id, x.artist_id, x.category, x.title, x.description, x.location, NULL,
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.day_offset * 24 + x.hour_at HOUR), NOW(), NOW()
FROM (
            SELECT 201 AS id, 1101 AS artist_id, 'TV_BROADCAST' AS category, 'NOVA 인기가요 컴백 무대' AS title, '컴백 첫 주 무대! 실시간 응원 부탁해요' AS description, 'SBS 등촌동 공개홀' AS location, 3 AS day_offset, 15 AS hour_at
  UNION ALL SELECT 202, 1101, 'RADIO', 'NOVA 컬투쇼 게스트', '시온·하람 출연', 'SBS 목동', 5, 14
  UNION ALL SELECT 203, 1101, 'CONCERT', 'NOVA 컴백 기념 팬사인회', '앨범 구매자 추첨 100명', '코엑스 라이브플라자', 9, 17
  UNION ALL SELECT 204, 1102, 'YOUTUBE', 'LUMI 3주년 기념 라이브', '루미너스와 함께하는 3주년 축하 라이브', NULL, 2, 20
  UNION ALL SELECT 205, 1102, 'BIRTHDAY', '나윤 생일', '나윤의 생일을 축하해 주세요 🎂', NULL, 11, 0
  UNION ALL SELECT 206, 1102, 'PHOTO_MAGAZINE', 'LUMI 패션 매거진 화보 공개', '겨울호 화보', NULL, 15, 10
  UNION ALL SELECT 207, 1103, 'CONCERT', 'ECLIPSE 단독 콘서트 〈ECLIPSE NIGHT〉 1일차', 'MD 사전 예약 진행 중', '올림픽공원 핸드볼경기장', 13, 18
  UNION ALL SELECT 208, 1103, 'CONCERT', 'ECLIPSE 단독 콘서트 〈ECLIPSE NIGHT〉 2일차', '막콘!', '올림픽공원 핸드볼경기장', 14, 17
  UNION ALL SELECT 209, 1104, 'OTHER', 'PRISM 데뷔 기념 라이브 방송', '라이브 탭에서 만나요', NULL, 4, 21
  UNION ALL SELECT 210, 1104, 'AWARDS', 'PRISM 연말 시상식 참석', '올해의 그룹상 후보', '고척스카이돔', 40, 18
  UNION ALL SELECT 211, 1105, 'CONCERT', '한유리 소극장 콘서트', '멤버십 선예매 진행', '대학로 아트원씨어터', 10, 19
  UNION ALL SELECT 212, 1105, 'RADIO', '한유리 라디오 라이브', '신곡 라이브 최초 공개', 'MBC FM4U', 1, 22
  UNION ALL SELECT 213, 1106, 'YOUTUBE', 'KAITO 한국어 브이로그 공개', '서울 카페 투어 편', NULL, 2, 18
  UNION ALL SELECT 214, 1106, 'CONCERT', 'KAITO 서울 팬미팅', '첫 서울 단독 팬미팅', '예스24 라이브홀', 18, 18
  UNION ALL SELECT 215, 1107, 'TV_BROADCAST', 'NEBULA 쇼챔피언 출연', '스타더스트 응원 부탁해요', 'MBC 일산 드림센터', 6, 17
  UNION ALL SELECT 216, 1107, 'BIRTHDAY', '유나 생일', '유나의 생일 🎂', NULL, 8, 0
  UNION ALL SELECT 217, 1108, 'YOUTUBE', '서하린 커버 영상 공개', '하린별 신청곡 커버', NULL, 4, 19
  UNION ALL SELECT 218, 1108, 'TV_BROADCAST', '서하린 불후의 명곡 출연', '첫 경연 무대', 'KBS 신관', 16, 18
  UNION ALL SELECT 219, 1109, 'RADIO', 'VELA 아이돌 라디오 출연', '준서·이안 출연', 'MBC 상암', 7, 21
  UNION ALL SELECT 220, 1109, 'OTHER', 'VELA 컴백 팬사인회', '앨범 구매자 추첨', '영풍문고 여의도', 12, 15
  UNION ALL SELECT 221, 1110, 'CONCERT', '윤시우 단독 콘서트 〈오래, 천천히〉', '3년 만의 단독 공연', '블루스퀘어 마스터카드홀', 22, 19
  UNION ALL SELECT 222, 1110, 'PHOTO_MAGAZINE', '윤시우 매거진 인터뷰 공개', '컴백 인터뷰', NULL, 5, 10
  UNION ALL SELECT 223, 1111, 'TV_BROADCAST', 'PASTEL 뮤직뱅크 출연', '데뷔 활동 마지막 주', 'KBS 신관 공개홀', 3, 17
  UNION ALL SELECT 224, 1111, 'YOUTUBE', 'PASTEL 듀엣 라이브 클립 공개', '하모니 라이브', NULL, 6, 18
) x
JOIN `users` u ON u.id = x.artist_id;

-- ------------------------------------------------------------
-- [4] 전체공지 (홈페이지 공지) - 128 은 이틀 뒤 공개되는 예약 공지
-- ------------------------------------------------------------
INSERT INTO `site_notice` (`id`, `author_id`, `title`, `category`, `content`, `published`, `publish_at`, `pinned`, `pin_order`, `created_at`, `updated_at`) VALUES
  (121, 1002, '[안내] 팬 프로젝트 등록 자격 안내', 'GENERAL',
   '팬 프로젝트는 팬들이 함께 모금해 아티스트를 응원하는 기능이에요.\n\n안전한 모금을 위해 아래 조건을 모두 만족해야 등록할 수 있어요.\n\n- 해당 커뮤니티에서 일반 배지 5개 + 스페셜 배지 1개 이상\n- 가입 이메일 본인 확인\n- 정산 계좌 등록 (계좌번호는 암호화해 보관)\n\n등록한 프로젝트는 관리자 심사 후 모금이 시작됩니다.',
   1, NULL, 1, 3, DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_SUB(NOW(6), INTERVAL 20 DAY)),
  (122, 1003, '[안내] 불법 티켓 거래 주의', 'GENERAL',
   '최근 커뮤니티에서 정가보다 비싸게 티켓을 파는 글이 늘고 있어요.\n\n- 입금을 먼저 요구하는 거래는 사기일 가능성이 높아요.\n- 불법 거래 글은 신고해 주시면 확인 후 삭제하고 작성자를 제재합니다.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 9 DAY), DATE_SUB(NOW(6), INTERVAL 9 DAY)),
  (123, 1001, '[업데이트] AI 번역·요약 기능 안내', 'UPDATE',
   '게시글 아래 [번역] 버튼으로 한국어·영어·일본어 사이를 바로 번역할 수 있어요.\n\n긴 글은 [AI 요약] 버튼으로 세 줄 요약도 볼 수 있습니다.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 15 DAY), DATE_SUB(NOW(6), INTERVAL 15 DAY)),
  (124, 1004, '[이벤트] 가을 굿즈 기획전 2탄', 'EVENT',
   '가을 굿즈 기획전 2탄이 시작됐어요 🍂\n\n- 기간: 오늘부터 2주간\n- 신규 커뮤니티(NEBULA·서하린·VELA·윤시우·PASTEL) 공식 굿즈 첫 오픈\n- 디지털 포토북 신규 입고',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 4 DAY), DATE_SUB(NOW(6), INTERVAL 4 DAY)),
  (125, 1002, '[안내] 라이브 방송 이용 안내', 'GENERAL',
   '아티스트 라이브 방송은 커뮤니티 가입자라면 누구나 볼 수 있어요.\n\n- 라이브 채팅에서 금칙어는 자동으로 걸러집니다.\n- 방송이 끝나면 다시보기로 저장돼요.\n- 라이브를 보면 "라이브 시청" 배지를 받을 수 있어요.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 25 DAY), DATE_SUB(NOW(6), INTERVAL 25 DAY)),
  (126, 1003, '[점검] 결제 시스템 점검 안내', 'MAINTENANCE',
   '결제 대행사 점검으로 아래 시간 동안 굿즈·멤버십·팬 프로젝트 결제가 잠시 중단됩니다.\n\n- 일시: 이번 주 수요일 새벽 1시 ~ 3시\n- 가상계좌 입금 확인은 점검이 끝난 뒤 순서대로 처리돼요.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)),
  (127, 1001, '[안내] 휴면 계정 전환 안내', 'GENERAL',
   '1년 동안 로그인하지 않은 계정은 개인정보 보호를 위해 휴면 계정으로 전환됩니다.\n\n휴면 전환 30일 전에 이메일로 미리 안내드리며, 휴면 계정은 로그인 후 이메일 인증으로 다시 사용할 수 있어요.',
   1, NULL, 0, NULL, DATE_SUB(NOW(6), INTERVAL 33 DAY), DATE_SUB(NOW(6), INTERVAL 33 DAY)),
  (128, 1001, '[점검] 11월 정기 서버 점검 안내', 'MAINTENANCE',
   '서비스 안정화를 위한 정기 점검이 진행됩니다.\n\n- 일시: 다음 주 화요일 새벽 2시 ~ 5시\n- 점검 중에는 모든 서비스를 이용할 수 없어요.',
   1, DATE_ADD(NOW(6), INTERVAL 2 DAY), 0, NULL, NOW(6), NOW(6));

-- ------------------------------------------------------------
-- [5] 팬 프로젝트 8개
--     508·509·510 모금 중 / 511 승인(이틀 뒤 시작) / 512 심사 대기 / 513 정산 완료 / 514 취소 / 515 모금 마감(정산 대기)
-- ------------------------------------------------------------
INSERT INTO `fan_project` (`id`, `artist_id`, `creator_id`, `title`, `event_type`, `goal_amount`, `funding_start_at`, `funding_end_at`, `description`, `status`,
                           `special_badge_count_at_apply`, `basic_badge_count_at_apply`, `identity_verified_at`, `reviewed_by`, `reviewed_at`, `rejection_reason`, `created_at`, `updated_at`)
SELECT x.id, x.artist_id, x.creator_id, x.title, x.event_type, x.goal_amount,
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.start_day DAY),
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.end_day * 24 * 60 + 1439 MINUTE),
       x.description, x.status, x.special_cnt, x.basic_cnt,
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.created_day DAY),
       x.reviewed_by, IF(x.reviewed_by IS NULL, NULL, DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.created_day + 1 DAY)),
       NULL,
       DATE_ADD(TIMESTAMP(CURDATE()), INTERVAL x.created_day DAY), NOW(6)
FROM (
            SELECT 508 AS id, 1101 AS artist_id, 1302 AS creator_id, '하람 생일 버스광고' AS title, 'BILLBOARD' AS event_type, 900000 AS goal_amount,
                   -10 AS start_day, 15 AS end_day, '하람 생일을 맞아 홍대 노선 버스 외부 광고를 2주 동안 진행합니다. 모금액은 광고비와 디자인 비용으로 사용해요.' AS description,
                   'FUNDING' AS status, 1 AS special_cnt, 6 AS basic_cnt, -13 AS created_day, 1001 AS reviewed_by
  UNION ALL SELECT 509, 1103, 1304, 'ECLIPSE 4주년 서포트', 'ETC', 600000, -5, 20,
                   '데뷔 4주년 기념으로 멤버 5명에게 도시락 서포트와 롤링페이퍼 앨범을 보냅니다.', 'FUNDING', 1, 5, -8, 1002
  UNION ALL SELECT 510, 1107, 1501, '유나 생일카페 프로젝트', 'BIRTHDAY_CAFE', 500000, -3, 12,
                   '유나 생일 주간에 성수동 카페에서 생일카페를 엽니다. 컵홀더·포토카드·현수막 제작비로 사용해요.', 'FUNDING', 1, 5, -6, 1003
  UNION ALL SELECT 511, 1108, 1502, '하린 콘서트 응원 화환', 'CONCERT', 300000, 2, 14,
                   '소극장 콘서트 공연장 앞에 쌀 화환을 보냅니다. 화환 쌀은 공연 후 기부해요.', 'APPROVED', 1, 5, -2, 1002
  UNION ALL SELECT 512, 1109, 1302, 'VELA 컴백 지하철 광고', 'BILLBOARD', 1200000, 4, 30,
                   '미니 2집 컴백을 축하하며 합정역 디지털 광고판에 2주간 광고를 진행하려고 합니다. 광고 시안은 커뮤니티 투표로 정해요.', 'PENDING_APPROVAL', 1, 5, -1, NULL
  UNION ALL SELECT 513, 1104, 1309, '소라 생일 카페', 'BIRTHDAY_CAFE', 400000, -80, -50,
                   '소라 생일을 맞아 연남동 카페에서 생일카페를 열었습니다. 후기와 정산 내역은 커뮤니티에 올려 두었어요.', 'COMPLETED', 1, 7, -84, 1004
  UNION ALL SELECT 514, 1106, 1313, 'KAITO 팬미팅 응원', 'CONCERT', 500000, -30, -10,
                   '도쿄 팬미팅 응원 화환을 준비했지만 공연장 반입 불가 안내를 받아 프로젝트를 취소했습니다. 후원금은 전액 환불 처리됐어요.', 'CANCELLED', 1, 5, -33, 1001
  UNION ALL SELECT 515, 1102, 1314, '나윤 생일 전광판', 'BILLBOARD', 800000, -35, -1,
                   '나윤 생일 주간에 강남역 대형 전광판 광고를 진행합니다. 모금이 끝나 정산을 기다리고 있어요.', 'FUNDING_CLOSED', 2, 6, -38, 1003
) x
JOIN `users` a ON a.id = x.artist_id
JOIN `users` c ON c.id = x.creator_id;

INSERT INTO `fan_project_cover_image` (`project_id`, `original_name`, `stored_name`, `content_type`, `file_size`, `created_at`)
SELECT x.project_id, CONCAT('cover_', x.project_id, '.jpg'), x.stored_name, 'image/jpeg', x.file_size, p.created_at
FROM (
            SELECT 508 AS project_id, 'demo_nova_media3002_1.jpg' AS stored_name, 188785 AS file_size
  UNION ALL SELECT 509, 'demo_eclipse_media3012_1.jpg', 175023
  UNION ALL SELECT 510, 'demo_nebula_media3101_1.jpg',  58010
  UNION ALL SELECT 511, 'demo_harin_media3105_1.jpg',   54492
  UNION ALL SELECT 512, 'demo_vela_media3109_1.jpg',    56944
  UNION ALL SELECT 513, 'demo_prism_media3018_1.jpg',   191489
  UNION ALL SELECT 514, 'demo_kaito_media3026_1.jpg',   173848
  UNION ALL SELECT 515, 'demo_lumi_media3009_1.jpg',    204640
) x
JOIN `fan_project` p ON p.id = x.project_id;

INSERT INTO `fan_project_settlement_account` (`project_id`, `bank_code`, `account_number_enc`, `account_number_hmac`, `account_number_last4`, `verification_status`, `verified_at`, `created_at`, `updated_at`)
SELECT p.id, x.bank_code, RANDOM_BYTES(48), SHA2(CONCAT('demo5-settlement-', p.id, '-', UUID()), 256), x.last4, x.status,
       IF(x.status = 'VERIFIED', DATE_SUB(NOW(6), INTERVAL 45 DAY), NULL), p.created_at, NOW(6)
FROM (
            SELECT 513 AS project_id, '088' AS bank_code, '7742' AS last4, 'VERIFIED' AS status
  UNION ALL SELECT 515, '081', '3096', 'UNVERIFIED'
) x
JOIN `fan_project` p ON p.id = x.project_id;

-- 후원 (모의 결제 MOCK) - 514 는 취소된 프로젝트라 전액 환불 상태
INSERT INTO `fan_project_contribution` (`project_id`, `contributor_id`, `order_no`, `idempotency_key`, `payment_provider`, `amount`, `is_anonymous`, `refund_policy_agreed_at`,
                                        `refund_amount`, `payment_status`, `paid_at`, `cancelled_at`, `refunded_at`, `refund_reason`, `created_at`, `updated_at`)
SELECT x.project_id, x.contributor_id, CONCAT('DEMO5-', x.project_id, '-', LPAD(x.seq, 3, '0')), CONCAT('demo5-', x.project_id, '-', LPAD(x.seq, 3, '0')), 'MOCK',
       x.amount, x.anon, DATE_SUB(NOW(6), INTERVAL x.days_ago DAY),
       IF(x.refunded = 1, x.amount, 0), IF(x.refunded = 1, 'REFUNDED', 'PAID'),
       DATE_ADD(DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), INTERVAL 30 MINUTE),
       NULL,
       IF(x.refunded = 1, DATE_SUB(NOW(6), INTERVAL 11 DAY), NULL),
       IF(x.refunded = 1, '프로젝트 취소로 전액 환불', NULL),
       DATE_SUB(NOW(6), INTERVAL x.days_ago DAY), DATE_SUB(NOW(6), INTERVAL IF(x.refunded = 1, 11, x.days_ago) DAY)
FROM (
            SELECT 508 AS project_id, 1 AS seq, 1301 AS contributor_id, 50000 AS amount, 0 AS anon, 9 AS days_ago, 0 AS refunded
  UNION ALL SELECT 508, 2, 1304, 100000, 0, 8, 0
  UNION ALL SELECT 508, 3, 1306, 30000,  1, 6, 0
  UNION ALL SELECT 508, 4, 1309, 100000, 0, 5, 0
  UNION ALL SELECT 508, 5, 1311, 50000,  0, 3, 0
  UNION ALL SELECT 508, 6, 1401, 20000,  0, 2, 0
  UNION ALL SELECT 508, 7, 1501, 50000,  1, 1, 0
  UNION ALL SELECT 509, 1, 1302, 100000, 0, 4, 0
  UNION ALL SELECT 509, 2, 1308, 50000,  0, 3, 0
  UNION ALL SELECT 509, 3, 1310, 30000,  1, 2, 0
  UNION ALL SELECT 509, 4, 1504, 50000,  0, 1, 0
  UNION ALL SELECT 510, 1, 1503, 50000,  0, 2, 0
  UNION ALL SELECT 510, 2, 1310, 100000, 0, 2, 0
  UNION ALL SELECT 510, 3, 1506, 30000,  0, 1, 0
  UNION ALL SELECT 513, 1, 1303, 100000, 0, 78, 0
  UNION ALL SELECT 513, 2, 1306, 100000, 0, 70, 0
  UNION ALL SELECT 513, 3, 1311, 80000,  1, 65, 0
  UNION ALL SELECT 513, 4, 1313, 100000, 0, 58, 0
  UNION ALL SELECT 513, 5, 1314, 50000,  0, 53, 0
  UNION ALL SELECT 514, 1, 1305, 50000,  0, 28, 1
  UNION ALL SELECT 514, 2, 1308, 30000,  0, 25, 1
  UNION ALL SELECT 514, 3, 1311, 50000,  0, 20, 1
  UNION ALL SELECT 515, 1, 1301, 100000, 0, 34, 0
  UNION ALL SELECT 515, 2, 1303, 150000, 0, 30, 0
  UNION ALL SELECT 515, 3, 1307, 200000, 1, 22, 0
  UNION ALL SELECT 515, 4, 1310, 100000, 0, 15, 0
  UNION ALL SELECT 515, 5, 1503, 150000, 0, 9,  0
  UNION ALL SELECT 515, 6, 1509, 100000, 0, 4,  0
) x
JOIN `fan_project` p ON p.id = x.project_id
JOIN `users` u ON u.id = x.contributor_id;

-- 결제 완료한 후원자에게 "프로젝트 참여" 스페셜 배지 (demo_fan15 는 후원자가 아니라 NOVA 배지 수 그대로)
INSERT IGNORE INTO `fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT c.contributor_id, fp.artist_id, b.badge_code, b.badge_name, b.badge_type, MIN(c.paid_at), MIN(c.paid_at)
FROM `fan_project_contribution` c
JOIN `fan_project` fp ON fp.id = c.project_id
JOIN `community_members` cm ON cm.fan_id = c.contributor_id AND cm.artist_id = fp.artist_id
JOIN `fan_badge` b ON b.badge_code = 'SPECIAL_PROJECT_CREATE'
WHERE c.project_id BETWEEN 508 AND 515 AND c.payment_status = 'PAID'
GROUP BY c.contributor_id, fp.artist_id, b.badge_code, b.badge_name, b.badge_type;

-- ------------------------------------------------------------
-- [6] 지난 해시태그 총공 2회 - 겹치는 이벤트가 있으면 그 회차는 건너뛴다
--     903 "8월 여름 총공" (62일 전 시작, 7일간, 5팀) / 904 "7월 데뷔 기념 총공" (96일 전 시작, 7일간, 3팀)
-- ------------------------------------------------------------
INSERT INTO `hashtag_event` (`id`, `title`, `start_at`, `end_at`, `finalized_at`, `created_by`, `created_at`, `updated_at`)
SELECT x.id, x.title, x.start_at, x.end_at, DATE_ADD(x.end_at, INTERVAL 2 DAY), x.created_by,
       DATE_SUB(x.start_at, INTERVAL 6 DAY), DATE_ADD(x.end_at, INTERVAL 2 DAY)
FROM (
            SELECT 903 AS id, '8월 여름 총공' AS title, TIMESTAMP(CURDATE() - INTERVAL 62 DAY) AS start_at, TIMESTAMP(CURDATE() - INTERVAL 56 DAY, '23:59:59') AS end_at, 1002 AS created_by
  UNION ALL SELECT 904, '7월 데뷔 기념 총공', TIMESTAMP(CURDATE() - INTERVAL 96 DAY), TIMESTAMP(CURDATE() - INTERVAL 90 DAY, '23:59:59'), 1001
) x
WHERE NOT EXISTS (SELECT 1 FROM `hashtag_event` h WHERE h.start_at <= x.end_at AND h.end_at >= x.start_at);

INSERT INTO `hashtag_event_target` (`event_id`, `artist_id`, `hashtag`)
SELECT x.event_id, x.artist_id, x.hashtag
FROM (
            SELECT 903 AS event_id, 1101 AS artist_id, '#NOVA_여름' AS hashtag
  UNION ALL SELECT 903, 1102, '#LUMI_썸머'
  UNION ALL SELECT 903, 1103, '#ECLIPSE여름밤'
  UNION ALL SELECT 903, 1104, '#PRISM_SUMMER'
  UNION ALL SELECT 903, 1105, '#유리의여름'
  UNION ALL SELECT 904, 1102, '#LUMI_데뷔기념'
  UNION ALL SELECT 904, 1104, '#PRISM_7월'
  UNION ALL SELECT 904, 1106, '#KAITO_ONE'
) x
JOIN `hashtag_event` e ON e.id = x.event_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_ev_posts`;
CREATE TEMPORARY TABLE `tmp_ev_posts` (
  `post_id` bigint PRIMARY KEY, `event_id` bigint, `artist_id` bigint, `author_id` bigint, `hours` int,
  `entry_status` varchar(30), `title` varchar(200), `body` text
);
INSERT INTO `tmp_ev_posts` VALUES
  -- 903 8월 여름 총공
  (12301, 903, 1101, 1301, 8,   'COUNTED',    '여름 총공 시작!',          '스텔라 여름 총공 같이 달려요 ☀️'),
  (12302, 903, 1101, 1301, 50,  'COUNTED',    '여름엔 노바',              '수영장 갈 때도 노바 노래'),
  (12303, 903, 1101, 1302, 20,  'COUNTED',    '하람 여름 직캠',           '여름 페스티벌 하람 직캠 공유해요'),
  (12304, 903, 1101, 1306, 70,  'COUNTED',    '여름 팬레터',              '더운 여름 다들 건강 챙겨요'),
  (12305, 903, 1101, 1309, 30,  'COUNTED',    '응원봉 여름 에디션',       '여름 스티커로 꾸며 봤어요'),
  (12306, 903, 1101, 1309, 100, 'COUNTED',    '총공 막바지',              '마지막까지 힘내요 스텔라'),
  (12307, 903, 1101, 1302, 140, 'COUNTED',    '총공 마지막 날',           '일주일 동안 고생했어요!'),
  (12311, 903, 1102, 1303, 12,  'COUNTED',    '루미 썸머 총공',           '루미랑 여름 보내기 💗'),
  (12312, 903, 1102, 1305, 40,  'COUNTED',    '여름 음료 들고 인증',      '루미 노래 들으며 아이스커피'),
  (12313, 903, 1102, 1307, 60,  'COUNTED',    '여름 피아노 커버',         '루미 여름 노래 피아노 커버'),
  (12314, 903, 1102, 1310, 90,  'COUNTED',    '포카 여름 정리',           '여름 포카 정리 완료'),
  (12315, 903, 1102, 1314, 110, 'COUNTED',    '여름 콘서트 가자',         '루미 여름 콘서트 기다려요'),
  (12316, 903, 1102, 1303, 150, 'COUNTED',    '총공 끝까지',              '참여율 1등 가자 루미너스'),
  (12321, 903, 1103, 1304, 25,  'COUNTED',    '여름밤 이클립스',          '여름밤엔 이클립스 노래죠 🌙'),
  (12322, 903, 1103, 1308, 75,  'COUNTED',    '여름 여행 중 총공',        '바다 보면서 한 줄 남겨요'),
  (12323, 903, 1103, 1308, 130, 'COUNTED',    '총공 두 번째',             '여름밤 총공 화이팅'),
  (12331, 903, 1104, 1306, 15,  'COUNTED',    '프리즘 썸머',              '여름 무지개처럼 🌈'),
  (12332, 903, 1104, 1311, 55,  'COUNTED',    '여름 퇴근길 프리즘',        '더위엔 프리즘 노래'),
  (12333, 903, 1104, 1313, 95,  'COUNTED',    '여름 응원법',              '여름 응원법 정리'),
  (12334, 903, 1104, 1306, 125, 'COUNTED',    '총공 마지막',              '다음 총공도 같이 해요'),
  (12341, 903, 1105, 1307, 35,  'COUNTED',    '유리 언니 여름 노래',      '여름에 듣기 좋은 유리 언니 노래'),
  (12342, 903, 1105, 1312, 85,  'COUNTED',    '여름밤 직캠',              '여름밤 페스티벌 직캠'),
  (12343, 903, 1105, 1305, 65,  'NOT_MEMBER', '지나가다 응원',            '가입은 안 했지만 유리 언니 응원해요'),
  -- 904 7월 데뷔 기념 총공
  (12351, 904, 1102, 1301, 10,  'COUNTED',    '루미 데뷔 기념일 축하',    '데뷔 기념일 축하해요 루미!'),
  (12352, 904, 1102, 1305, 60,  'COUNTED',    '데뷔 무대 다시 보기',      '데뷔 무대 다시 봐도 레전드'),
  (12353, 904, 1102, 1301, 120, 'COUNTED',    '총공 마지막 날',           '데뷔 기념 총공 끝까지'),
  (12361, 904, 1104, 1303, 15,  'COUNTED',    '프리즘 7월 총공',          '7월은 프리즘의 달 🌈'),
  (12362, 904, 1104, 1306, 45,  'COUNTED',    '데뷔곡 무한 반복',          '데뷔곡 또 듣는 중'),
  (12363, 904, 1104, 1309, 75,  'COUNTED',    '응원봉 인증',              '프리즘 응원봉 들고 인증'),
  (12364, 904, 1104, 1311, 100, 'COUNTED',    '퇴근길 총공',              '퇴근길에 한 줄'),
  (12365, 904, 1104, 1303, 130, 'COUNTED',    '총공 막바지',              '참여율 1등 가자'),
  (12366, 904, 1104, 1306, 155, 'COUNTED',    '총공 끝',                  '다들 고생했어요'),
  (12371, 904, 1106, 1305, 20,  'COUNTED',    'KAITO ONE 총공',           'カイト 데뷔 기념 총공 참여!'),
  (12372, 904, 1106, 1308, 55,  'COUNTED',    '도쿄에서 응원',            '일본에서도 응원해요'),
  (12373, 904, 1106, 1311, 85,  'COUNTED',    '카이토 한국어 최고',       '한국어 늘어서 감동');

INSERT INTO `post` (`id`, `board_type`, `artist_id`, `author_id`, `title`, `content`, `like_count`, `hidden_from_artist`, `created_at`)
SELECT tp.post_id, 'FAN', tp.artist_id, tp.author_id, tp.title, CONCAT(tp.body, '\n\n', t.hashtag), 0, 0,
       DATE_ADD(e.start_at, INTERVAL tp.hours HOUR)
FROM `tmp_ev_posts` tp
JOIN `hashtag_event_target` t ON t.event_id = tp.event_id AND t.artist_id = tp.artist_id
JOIN `hashtag_event` e ON e.id = t.event_id;

INSERT INTO `hashtag_event_entry` (`target_id`, `post_id`, `fan_id`, `status`, `created_at`)
SELECT t.id, p.id, p.author_id, tp.entry_status, p.created_at
FROM `tmp_ev_posts` tp
JOIN `post` p ON p.id = tp.post_id
JOIN `hashtag_event_target` t ON t.event_id = tp.event_id AND t.artist_id = tp.artist_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_ev_posts`;

-- 집계 확정 스냅샷 (종료 시점 가입자 수 기준 참여율 → 참여 인원 → 글 수 순)
UPDATE `hashtag_event_target` t
JOIN `hashtag_event` e ON e.id = t.event_id
SET t.final_member_count      = (SELECT COUNT(*) FROM `community_members` cm WHERE cm.artist_id = t.artist_id AND cm.joined_at <= e.end_at),
    t.final_participant_count = (SELECT COUNT(DISTINCT en.fan_id) FROM `hashtag_event_entry` en WHERE en.target_id = t.id AND en.status = 'COUNTED'),
    t.final_post_count        = (SELECT COUNT(*) FROM `hashtag_event_entry` en WHERE en.target_id = t.id AND en.status = 'COUNTED')
WHERE t.event_id IN (903, 904);

UPDATE `hashtag_event_target` t
JOIN (
  SELECT r.id,
         ROW_NUMBER() OVER (PARTITION BY r.event_id
                            ORDER BY r.final_participant_count / NULLIF(r.final_member_count, 0) DESC,
                                     r.final_participant_count DESC, r.final_post_count DESC, r.id) AS rk
  FROM `hashtag_event_target` r
  WHERE r.event_id IN (903, 904)
) x ON x.id = t.id
SET t.final_rank = x.rk;

-- ------------------------------------------------------------
-- [7] 해시태그 총공 결과 공지 - 집계 확정된 지난 총공(901·903·904)마다 하나씩
--     관리자 모니터링의 [결과 공지 작성]이 채워 주는 초안(HashtagResultNoticeDraft)과 같은 마크다운 형식
-- ------------------------------------------------------------
INSERT INTO `site_notice` (`id`, `author_id`, `title`, `category`, `content`, `published`, `publish_at`, `pinned`, `pin_order`, `created_at`, `updated_at`)
SELECT 130 + ROW_NUMBER() OVER (ORDER BY e.start_at),
       1001,
       CONCAT('[이벤트] ', e.title, ' 결과 안내'),
       'EVENT',
       CONCAT(
         e.title, '에 참여해주신 모든 팬 여러분, 감사합니다! 💜\n\n',
         '- 기간: ', DATE_FORMAT(e.start_at, '%Y.%m.%d'), ' ~ ', DATE_FORMAT(e.end_at, '%Y.%m.%d'), '\n',
         '- 참여 커뮤니티: ', s.teams, '팀 · 인정된 해시태그 글 ', FORMAT(s.posts, 0), '건 · 참여 인원 ', FORMAT(s.parts, 0), '명\n\n',
         '## 🏆 1위 ', s.winner, '\n\n',
         '| 순위 | 아티스트 | 해시태그 | 참여율 | 참여 인원 | 글 수 |\n',
         '| --- | --- | --- | --- | --- | --- |\n',
         s.rows_md, '\n',
         '\n👉 [최종 순위 자세히 보기](/events/hashtag/', e.id, '?all=true#ranking)\n\n',
         '다음 해시태그 총공에도 많은 참여 부탁드려요!'
       ),
       1, NULL, 0, NULL,
       DATE_ADD(e.finalized_at, INTERVAL 1 DAY), DATE_ADD(e.finalized_at, INTERVAL 1 DAY)
FROM `hashtag_event` e
JOIN (
  SELECT r.event_id,
         COUNT(*) AS teams,
         SUM(r.final_post_count) AS posts,
         SUM(r.final_participant_count) AS parts,
         MAX(CASE WHEN r.final_rank = 1 THEN CONCAT(r.artist_name, ' (참여율 ', r.rate, '%)') COLLATE utf8mb4_unicode_ci END) AS winner,
         GROUP_CONCAT(
           CONCAT('| ', r.final_rank, ' | ', r.artist_name, ' | ', r.hashtag, ' | ', r.rate, '%',
                  ' | ', FORMAT(r.final_participant_count, 0), ' / ', FORMAT(r.final_member_count, 0), '명',
                  ' | ', FORMAT(r.final_post_count, 0), ' |')
           ORDER BY r.final_rank SEPARATOR '\n') AS rows_md
  FROM (
    SELECT t.event_id, t.final_rank, t.hashtag, t.final_member_count, t.final_participant_count, t.final_post_count,
           CONVERT(COALESCE(ap.stage_name, u.nickname) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS artist_name,
           CONVERT(CAST(IF(t.final_member_count > 0, ROUND(t.final_participant_count * 100 / t.final_member_count, 1), 0.0) AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS rate
    FROM `hashtag_event_target` t
    JOIN `users` u ON u.id = t.artist_id
    LEFT JOIN `artist_profiles` ap ON ap.user_id = t.artist_id
    WHERE t.event_id IN (901, 903, 904)
  ) r
  GROUP BY r.event_id
) s ON s.event_id = e.id
WHERE e.id IN (901, 903, 904)
  AND e.finalized_at IS NOT NULL
  AND e.finalized_at <= NOW(6);

-- ------------------------------------------------------------
-- [8] 관리자 로그 (관리자 4명이 나눠서 처리) - 날짜는 위 데이터에 맞춤
-- ------------------------------------------------------------
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`)
SELECT x.id, x.actor_id, x.action, x.target_type, x.target_id, x.reason, x.ip, DATE_SUB(NOW(6), INTERVAL x.mins_ago MINUTE)
FROM (
            SELECT 7101 AS id, 1001 AS actor_id, 'PARTNERSHIP_APPLICATION_APPROVE' AS action, 'PARTNERSHIP_APPLICATION' AS target_type, 806 AS target_id, '노을엔터테인먼트 등록 신청 승인 (소속사 계정 발급)' AS reason, '127.0.0.1' AS ip, 30240 AS mins_ago
  UNION ALL SELECT 7102, 1001, 'AGENCY_PERMISSION_APPROVE', 'AGENCY_PERMISSION', 1014, '노을 매니저 소속사 권한 승인', '127.0.0.1', 28800
  UNION ALL SELECT 7103, 1002, 'PARTNERSHIP_APPLICATION_APPROVE', 'PARTNERSHIP_APPLICATION', 807, '파도뮤직 등록 신청 승인 (소속사 계정 발급)', '127.0.0.1', 18720
  UNION ALL SELECT 7104, 1002, 'PARTNERSHIP_ACTIVATION_RESEND', 'PARTNERSHIP_APPLICATION', 807, '파도뮤직 계정 활성화 메일 재발송 (메일 미수신 문의)', '127.0.0.1', 17900
  UNION ALL SELECT 7105, 1002, 'AGENCY_PERMISSION_APPROVE', 'AGENCY_PERMISSION', 1015, '파도 매니저 소속사 권한 승인', '127.0.0.1', 17280
  UNION ALL SELECT 7106, 1001, 'AGENCY_PERMISSION_APPROVE', 'AGENCY_PERMISSION', 1017, '스타라이트 마케팅 소속사 권한 승인', '127.0.0.1', 8640
  UNION ALL SELECT 7107, 1004, 'PROJECT_APPROVE', 'PROJECT', 513, '소라 생일 카페 승인', '127.0.0.1', 119520
  UNION ALL SELECT 7108, 1004, 'SETTLEMENT_ACCOUNT_VERIFY', 'SETTLEMENT', 513, '소라 생일 카페정산 계좌 확인 완료', '127.0.0.1', 64800
  UNION ALL SELECT 7109, 1004, 'SETTLEMENT_COMPLETE', 'SETTLEMENT', 513, '소라 생일 카페정산 완료', '127.0.0.1', 63360
  UNION ALL SELECT 7110, 1003, 'PROJECT_APPROVE', 'PROJECT', 515, '나윤 생일 전광판 승인', '127.0.0.1', 53280
  UNION ALL SELECT 7111, 1001, 'PROJECT_APPROVE', 'PROJECT', 514, 'KAITO 팬미팅 응원 승인', '127.0.0.1', 46080
  UNION ALL SELECT 7112, 1001, 'PROJECT_APPROVE', 'PROJECT', 508, '하람 생일 버스광고 승인', '127.0.0.1', 17280
  UNION ALL SELECT 7113, 1002, 'PROJECT_APPROVE', 'PROJECT', 509, 'ECLIPSE 4주년 서포트 승인', '127.0.0.1', 10080
  UNION ALL SELECT 7114, 1003, 'PROJECT_APPROVE', 'PROJECT', 510, '유나 생일카페 프로젝트 승인', '127.0.0.1', 7200
  UNION ALL SELECT 7115, 1002, 'PROJECT_APPROVE', 'PROJECT', 511, '하린 콘서트 응원 화환 승인', '127.0.0.1', 1440
  UNION ALL SELECT 7116, 1003, 'KEYWORD_CREATE', 'KEYWORD', 4855, '금칙어 등록 (욕설 변형 표현)', '127.0.0.1', 21600
  UNION ALL SELECT 7117, 1003, 'KEYWORD_CREATE', 'KEYWORD', 4856, '금칙어 등록 (티켓 암표 홍보 문구)', '127.0.0.1', 21590
  UNION ALL SELECT 7118, 1003, 'KEYWORD_UPDATE', 'KEYWORD', 4856, '금칙어 수정 (띄어쓰기 변형 포함)', '127.0.0.1', 12960
  UNION ALL SELECT 7119, 1004, 'KEYWORD_DELETE', 'KEYWORD', 4830, '금칙어 삭제 (일반 단어 오탐 문의)', '127.0.0.1', 5760
  UNION ALL SELECT 7120, 1001, 'NOTICE_CREATE', 'NOTICE', 127, '[안내] 휴면 계정 전환 안내', '127.0.0.1', 47520
  UNION ALL SELECT 7121, 1002, 'NOTICE_CREATE', 'NOTICE', 125, '[안내] 라이브 방송 이용 안내', '127.0.0.1', 36000
  UNION ALL SELECT 7122, 1002, 'NOTICE_CREATE', 'NOTICE', 121, '[안내] 팬 프로젝트 등록 자격 안내', '127.0.0.1', 28800
  UNION ALL SELECT 7123, 1001, 'NOTICE_CREATE', 'NOTICE', 123, '[업데이트] AI 번역·요약 기능 안내', '127.0.0.1', 21600
  UNION ALL SELECT 7124, 1003, 'NOTICE_CREATE', 'NOTICE', 122, '[안내] 불법 티켓 거래 주의', '127.0.0.1', 12960
  UNION ALL SELECT 7125, 1004, 'NOTICE_CREATE', 'NOTICE', 124, '[이벤트] 가을 굿즈 기획전 2탄', '127.0.0.1', 5760
  UNION ALL SELECT 7126, 1003, 'NOTICE_CREATE', 'NOTICE', 126, '[점검] 결제 시스템 점검 안내', '127.0.0.1', 2880
  UNION ALL SELECT 7127, 1001, 'NOTICE_UPDATE', 'NOTICE', 121, '[안내] 팬 프로젝트 등록 자격 안내 - 상단 고정', '127.0.0.1', 2700
  UNION ALL SELECT 7128, 1001, 'NOTICE_CREATE', 'NOTICE', 128, '[점검] 11월 정기 서버 점검 안내 (예약 공지)', '127.0.0.1', 30
  UNION ALL SELECT 7129, 1002, 'NOTICE_DELETE', 'NOTICE', 99, '[테스트] 공지 작성 테스트 - 잘못 올린 글 삭제', '127.0.0.1', 25920
  UNION ALL SELECT 7130, 1003, 'USER_SUSPEND', 'USER', 1412, '사생활 침해 댓글 작성 (7일 정지)', '127.0.0.1', 12960
  UNION ALL SELECT 7131, 1003, 'USER_REINSTATE', 'USER', 1412, '정지 기간 종료 - 재발 시 영구 정지 안내', '127.0.0.1', 2880
  UNION ALL SELECT 7132, 1002, 'COMMUNITY_MEMBER_UNBLOCK', 'USER', 1313, '커뮤니티 차단 해제 : PRISM / 오늘도덕질', '127.0.0.1', 7300
  UNION ALL SELECT 7133, 1004, 'REPORT_DISMISS', 'REPORT', 10006, '게시글 신고 1건 기각', '127.0.0.1', 10100
  UNION ALL SELECT 7134, 1004, 'REPORT_RESOLVE', 'REPORT', 10298, '게시글 신고 처리 : 콘텐츠 삭제 (단톡방 홍보 링크)', '127.0.0.1', 4300
  UNION ALL SELECT 7135, 1002, 'HASHTAG_EVENT_UPDATE', 'HASHTAG_EVENT', 903, '8월 여름 총공 수정 (해시태그 오타 수정)', '127.0.0.1', 95040
  UNION ALL SELECT 7136, 1002, 'HASHTAG_EVENT_CREATE', 'HASHTAG_EVENT', 903, '8월 여름 총공 등록 (5팀)', '127.0.0.1', 97920
  UNION ALL SELECT 7137, 1002, 'HASHTAG_EVENT_FINALIZE', 'HASHTAG_EVENT', 903, '8월 여름 총공 집계 확정', '127.0.0.1', 77760
  UNION ALL SELECT 7138, 1001, 'HASHTAG_EVENT_CREATE', 'HASHTAG_EVENT', 904, '7월 데뷔 기념 총공 등록 (3팀)', '127.0.0.1', 146880
  UNION ALL SELECT 7139, 1001, 'HASHTAG_EVENT_FINALIZE', 'HASHTAG_EVENT', 904, '7월 데뷔 기념 총공 집계 확정', '127.0.0.1', 126720
) x;

-- 결과 공지 등록 로그 (실제로 만들어진 결과 공지만)
INSERT INTO `admin_action_logs` (`id`, `actor_id`, `action`, `target_type`, `target_id`, `reason`, `ip_address`, `created_at`)
SELECT 7140 + ROW_NUMBER() OVER (ORDER BY n.id), 1001, 'NOTICE_CREATE', 'NOTICE', n.id, n.title, '127.0.0.1', n.created_at
FROM `site_notice` n
WHERE n.id BETWEEN 131 AND 140;

COMMIT;
SET SQL_SAFE_UPDATES = @old_safe_updates;

-- ------------------------------------------------------------
-- [확인] 넣은 데이터 개수
-- ------------------------------------------------------------
SELECT '소속사(전체)' AS 항목, COUNT(*) AS 개수 FROM `agencies`
UNION ALL SELECT '소속사 권한 승인 대기', COUNT(*) FROM `agency_profiles` WHERE `approved_at` IS NULL
UNION ALL SELECT '추가 굿즈', COUNT(*) FROM `shop_goods` WHERE `id` BETWEEN 4101 AND 4199
UNION ALL SELECT '추가 일정', COUNT(*) FROM `artist_schedule` WHERE `id` BETWEEN 201 AND 299
UNION ALL SELECT '추가 전체공지', COUNT(*) FROM `site_notice` WHERE `id` BETWEEN 121 AND 130
UNION ALL SELECT '총공 결과 공지', COUNT(*) FROM `site_notice` WHERE `id` BETWEEN 131 AND 140
UNION ALL SELECT '추가 팬 프로젝트', COUNT(*) FROM `fan_project` WHERE `id` BETWEEN 508 AND 515
UNION ALL SELECT '집계 확정된 총공', COUNT(*) FROM `hashtag_event` WHERE `finalized_at` IS NOT NULL
UNION ALL SELECT '추가 관리자 로그', COUNT(*) FROM `admin_action_logs` WHERE `id` BETWEEN 7101 AND 7199;
