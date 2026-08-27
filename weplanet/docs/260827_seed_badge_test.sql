-- ============================================================
-- 배지 화면(BADGE-1) 확인용 테스트 데이터 - 260827 정휘원
-- ------------------------------------------------------------
-- "나의 컬렉션" 화면을 눈으로 확인하려면 두 가지가 필요하다.
--   ① 가입한 커뮤니티      (group_follow)          - 없으면 카드가 0장
--   ② 획득한 배지          (fan_badge_ownership)   - 없으면 전부 흑백
--
-- 선행 조건
--   docs/260827_SQL_아직ㄴㄴ.sql 을 먼저 실행해서 fan_badge 25종이 있어야 한다.
--   (이 파일은 카탈로그를 만들지 않고, 있는 것을 참조만 한다)
--
-- 실행법
--   mysql -uroot -proot --default-character-set=utf8mb4 weplanet < docs/260827_seed_badge_test.sql
--
-- 안전성
--   여러 번 실행해도 안전하다(INSERT IGNORE). 기존 데이터는 지우지 않는다.
--   users.id 를 직접 쓰지 않고 username 으로 찾으므로, 각자 DB의 id 가 달라도 동작한다.
--
-- 심는 데이터
--   팬 hwiwhi, asd123 을 두 커뮤니티에 가입시키고 배지를 서로 다르게 준다.
--   (asd123 은 팀 공용 테스트 계정 - seed_test_accounts.sql 로 먼저 만들어야 함)
--     휘원공주  : 일반 8 + 스페셜 2 = 10/25 (40%)
--     정식왕자  : 일반 3 + 스페셜 0 =  3/25 (12%)
--   달성률이 다른 카드 두 장이 보이고, 모달에서 컬러/흑백이 섞여 나온다.
-- ============================================================

USE `weplanet`;


-- ------------------------------------------------------------
-- 1) 커뮤니티 가입 (group_follow)
--    group_id 는 그 아티스트의 users.id 와 같은 값으로 쓴다.
--    (artist_groups 를 MediaGroupDataInitializer 가 그렇게 시딩해둠)
-- ------------------------------------------------------------
INSERT IGNORE INTO `group_follow` (`fan_id`, `group_id`, `created_at`)
SELECT f.id, a.id, NOW(6)
FROM `users` f
         JOIN `users` a ON a.username IN ('artist_hwiwon', 'artist_jungsik')
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.id IN (SELECT id FROM `artist_groups`);


-- ------------------------------------------------------------
-- 2) 획득 배지 - 휘원공주 (일반 8 + 스페셜 2)
--    badge_name / badge_type 은 카탈로그에서 그대로 복사한다.
--    (수여 시점 값을 스냅샷으로 남기는 컬럼이라 직접 적지 않고 SELECT 로 가져옴)
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge_ownership`
    (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT f.id, a.id, b.badge_code, b.badge_name, b.badge_type, NOW(6), NOW(6)
FROM `users` f,
     `users` a,
     `fan_badge` b
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.username = 'artist_hwiwon'
  AND b.badge_code IN (
    -- 일반 8
                       'BASIC_FIRST_JOIN',
                       'BASIC_FIRST_POST',
                       'BASIC_COMMENT_5',
                       'BASIC_MEDIA_VIEW',
                       'BASIC_DAY_100',
                       'BASIC_LIKE_10',
                       'BASIC_LIKED_5',
                       'BASIC_FOLLOW_ARTIST',
    -- 스페셜 2
                       'SPECIAL_DEBUT_1',
                       'SPECIAL_MEMBERSHIP_1'
    );


-- ------------------------------------------------------------
-- 3) 획득 배지 - 정식왕자 (일반 3, 스페셜 0)
--    같은 팬이라도 아티스트가 다르면 배지가 따로 쌓인다는 걸 보여주기 위함
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge_ownership`
    (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT f.id, a.id, b.badge_code, b.badge_name, b.badge_type, NOW(6), NOW(6)
FROM `users` f,
     `users` a,
     `fan_badge` b
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.username = 'artist_jungsik'
  AND b.badge_code IN (
                       'BASIC_FIRST_JOIN',
                       'BASIC_FIRST_POST',
                       'BASIC_MEDIA_VIEW'
    );


-- ------------------------------------------------------------
-- 확인용
-- ------------------------------------------------------------
SELECT u.nickname                                             AS 아티스트,
       SUM(o.badge_type = 'BASIC')                            AS 일반,
       SUM(o.badge_type = 'SPECIAL')                          AS 스페셜,
       COUNT(*)                                               AS 합계,
       CONCAT(ROUND(COUNT(*) * 100 /
                    (SELECT COUNT(*) FROM `fan_badge`)), '%') AS 달성률
FROM `fan_badge_ownership` o
         JOIN `users` u ON u.id = o.artist_id
WHERE o.revoked_at IS NULL
GROUP BY u.nickname;
