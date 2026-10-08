-- WePlaNet 로컬 DB 적용 - 테이블 간소화(v2, 60 → 56개)를 데이터를 유지한 채 반영 (실행 전 백업, 한 번만 실행).

USE `weplanet`;

-- Safe Updates 가 켜져 있으면 JOIN UPDATE 가 막혀 잠시 끈다.
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- 1) artist_groups <- artist_group_profiles
ALTER TABLE `artist_groups`
    ADD COLUMN `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '그룹/아티스트 성별(탐색 필터)' AFTER `status`,
    ADD COLUMN `member_count` int DEFAULT NULL COMMENT '구성 인원 수(탐색 필터)' AFTER `gender`,
    ADD COLUMN `nationality` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '국적(탐색 필터)' AFTER `member_count`,
    ADD COLUMN `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직업/카테고리(탐색 필터)' AFTER `nationality`;

-- 그룹 행이 없는 탐색 정보는 그룹 행을 먼저 만든다 (id = 아티스트 users.id).
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

-- 짝이 안 맞는 행이 없을 때만 외래키를 건다.
SET @orphan_groups = (SELECT COUNT(*) FROM `artist_groups` g LEFT JOIN `users` u ON u.`id` = g.`id` WHERE u.`id` IS NULL);
SET @fk_sql = IF(@orphan_groups = 0,
    'ALTER TABLE `artist_groups` ADD CONSTRAINT `fk_group_artist` FOREIGN KEY (`id`) REFERENCES `users` (`id`)',
    'SELECT CONCAT(''fk_group_artist 를 건너뜀: users 에 없는 그룹 id '', @orphan_groups, ''개'') AS notice');
PREPARE fk_stmt FROM @fk_sql;
EXECUTE fk_stmt;
DEALLOCATE PREPARE fk_stmt;

ALTER TABLE `artist_groups` COMMENT = '아티스트 그룹/커뮤니티 (탐색 필터 포함)';

DROP TABLE `artist_group_profiles`;

-- 2) community_members <- community_profiles (NULL 허용으로 추가 후 채우고 NOT NULL 로 변경)
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

-- 프로필 없이 가입만 된 행은 계정 닉네임으로 채운다.
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

-- 3) 쓰지 않는 테이블 삭제
DROP TABLE `group_schedule`;
DROP TABLE `notification_setting`;

SET SQL_SAFE_UPDATES = @old_safe_updates;

-- 56 이 나오면 정상
SELECT COUNT(*) AS `table_count` FROM information_schema.tables WHERE table_schema = 'weplanet';
