-- ============================================================
-- WePlaNet 로컬 DB 적용 - 테이블 간소화(v2) 변경분 [데이터 유지]
-- ------------------------------------------------------------
-- 대상: 지금 쓰던 DB(테이블 60개)의 데이터를 그대로 두고 v2 구조(56개)로 바꾸려는 팀원
-- 실행: MySQL Workbench에서 전체 선택 후 실행 (한 번만 실행)
--       ※ 실행 전에 Data Export로 백업을 떠 두세요. 테이블 구조를 바꾸는 문장(ALTER/DROP)은 되돌릴 수 없습니다.
--       ※ main-details 의 코드(엔티티 변경)와 짝입니다. 이 SQL을 실행한 DB로 예전 코드를 띄우면
--         서버가 시작할 때 DB 구조 검사(ddl-auto=validate)에서 실패합니다.
--
-- 변경 내용 (정휘원 / 2026-10-03)
--   1) artist_groups <- artist_group_profiles 통합 (커뮤니티 탐색 필터, 그룹당 1행)
--      - gender / member_count / nationality / category 컬럼을 artist_groups 로 옮긴다
--      - debut_date 는 두 테이블에 중복돼 있었다. artist_groups 값을 쓰고, 비어 있을 때만 옮겨 채운다
--      - 그룹 행이 없는 탐색 정보가 있으면 그룹 행을 새로 만들어 옮긴다 (아티스트 닉네임을 그룹명으로)
--      - artist_groups.id -> users.id 외래키(fk_group_artist)는 짝이 안 맞는 행이 없을 때만 추가한다
--   2) community_members <- community_profiles 통합 (커뮤니티별 프로필, 가입당 1행)
--      - nickname / bio / avatar_stored_name / background_stored_name / content_hidden / updated_at
--      - 프로필 없이 가입만 된 행이 있으면 계정 닉네임(최대 10자)으로 채운다
--   3) 코드에서 쓰지 않던 테이블 삭제: group_schedule, notification_setting
--
-- - 이미 적용한 DB에서 다시 실행하면 "Duplicate column" 오류가 나는데, 그 DB는 이미 적용된 상태입니다.
-- - 빈 DB를 새로 만들 때는 weplanet_schema_full_reset_v2.sql 을 사용하세요.
-- ============================================================

USE `weplanet`;

-- Workbench 의 Safe Updates 설정이 켜져 있으면 JOIN 으로 고치는 UPDATE 가 막혀서(Error 1175) 잠시 끈다
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ------------------------------------------------------------
-- 1) artist_groups <- artist_group_profiles
-- ------------------------------------------------------------
ALTER TABLE `artist_groups`
    ADD COLUMN `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '그룹/아티스트 성별(탐색 필터)' AFTER `status`,
    ADD COLUMN `member_count` int DEFAULT NULL COMMENT '구성 인원 수(탐색 필터)' AFTER `gender`,
    ADD COLUMN `nationality` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '국적(탐색 필터)' AFTER `member_count`,
    ADD COLUMN `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직업/카테고리(탐색 필터)' AFTER `nationality`;

-- 그룹 행이 없는 탐색 정보 -> 그룹 행을 먼저 만든다 (id = 아티스트 users.id, MediaGroupDataInitializer 와 같은 규칙)
INSERT INTO `artist_groups` (`id`, `agency_id`, `name`, `debut_date`, `status`, `created_at`, `updated_at`)
SELECT p.`artist_id`,
       COALESCE(u.`agency_id`, (SELECT MIN(`id`) FROM `agencies`)),
       u.`nickname`,
       p.`debut_date`,
       'ACTIVE',
       NOW(6),
       NOW(6)
FROM `artist_group_profiles` p
JOIN `users` u ON u.`id` = p.`artist_id`
LEFT JOIN `artist_groups` g ON g.`id` = p.`artist_id`
WHERE g.`id` IS NULL;

UPDATE `artist_groups` g
JOIN `artist_group_profiles` p ON p.`artist_id` = g.`id`
SET g.`gender`       = p.`gender`,
    g.`member_count` = p.`member_count`,
    g.`nationality`  = p.`nationality`,
    g.`category`     = p.`category`,
    g.`debut_date`   = COALESCE(g.`debut_date`, p.`debut_date`);

-- artist_groups.id 는 그룹 계정 users.id 와 같은 번호여야 한다. 짝이 안 맞는 행이 없을 때만 외래키를 건다.
SET @orphan_groups = (SELECT COUNT(*) FROM `artist_groups` g LEFT JOIN `users` u ON u.`id` = g.`id` WHERE u.`id` IS NULL);
SET @fk_sql = IF(@orphan_groups = 0,
    'ALTER TABLE `artist_groups` ADD CONSTRAINT `fk_group_artist` FOREIGN KEY (`id`) REFERENCES `users` (`id`)',
    'SELECT CONCAT(''fk_group_artist 를 건너뜀: users 에 없는 그룹 id '', @orphan_groups, ''개'') AS notice');
PREPARE fk_stmt FROM @fk_sql;
EXECUTE fk_stmt;
DEALLOCATE PREPARE fk_stmt;

ALTER TABLE `artist_groups` COMMENT = '아티스트 그룹/커뮤니티 (탐색 필터 포함)';

DROP TABLE `artist_group_profiles`;

-- ------------------------------------------------------------
-- 2) community_members <- community_profiles
-- ------------------------------------------------------------
-- 옮기기 전에는 비어 있으니 NULL 허용으로 추가하고, 다 채운 뒤 NOT NULL 로 바꾼다
ALTER TABLE `community_members`
    ADD COLUMN `nickname` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '해당 커뮤니티 전용 닉네임' AFTER `joined_at`,
    ADD COLUMN `bio` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '짧은 소개' AFTER `nickname`,
    ADD COLUMN `avatar_stored_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '아바타 저장 파일명' AFTER `bio`,
    ADD COLUMN `background_stored_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배경 이미지 저장 파일명' AFTER `avatar_stored_name`,
    ADD COLUMN `content_hidden` tinyint(1) NOT NULL DEFAULT 0 COMMENT '프로필 콘텐츠 숨기기(1=비공개)' AFTER `background_stored_name`,
    ADD COLUMN `updated_at` datetime(6) DEFAULT NULL COMMENT '프로필 수정 시각' AFTER `content_hidden`;

UPDATE `community_members` m
JOIN `community_profiles` p ON p.`community_member_id` = m.`id`
SET m.`nickname`               = p.`nickname`,
    m.`bio`                    = p.`bio`,
    m.`avatar_stored_name`     = p.`avatar_stored_name`,
    m.`background_stored_name` = p.`background_stored_name`,
    m.`content_hidden`         = p.`content_hidden`,
    m.`updated_at`             = p.`updated_at`;

-- 프로필 없이 가입만 된 행 (정상 흐름에서는 생기지 않음) -> 계정 닉네임으로 채운다
UPDATE `community_members` m
JOIN `users` u ON u.`id` = m.`fan_id`
SET m.`nickname` = TRIM(LEFT(COALESCE(NULLIF(TRIM(u.`nickname`), ''), 'Member'), 10))
WHERE m.`nickname` IS NULL;

UPDATE `community_members`
SET `updated_at` = `joined_at`
WHERE `updated_at` IS NULL;

ALTER TABLE `community_members`
    MODIFY COLUMN `nickname` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '해당 커뮤니티 전용 닉네임',
    MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '프로필 수정 시각',
    MODIFY COLUMN `joined_at` datetime(6) NOT NULL COMMENT '가입 시각(디데이 기준, 프로필 생성 시각)',
    COMMENT = '팬의 커뮤니티 가입 + 커뮤니티별 프로필';

DROP TABLE `community_profiles`;

-- ------------------------------------------------------------
-- 3) 코드에서 쓰지 않던 테이블 삭제
-- ------------------------------------------------------------
DROP TABLE `group_schedule`;
DROP TABLE `notification_setting`;

SET SQL_SAFE_UPDATES = @old_safe_updates;

-- 확인용: 56 이 나오면 정상
SELECT COUNT(*) AS `table_count` FROM information_schema.tables WHERE table_schema = 'weplanet';
