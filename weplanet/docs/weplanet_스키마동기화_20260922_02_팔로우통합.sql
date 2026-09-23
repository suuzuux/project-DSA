-- ============================================================
-- WePlaNet 로컬 DB 동기화 - GroupFollow/UserFollow 통합 (2)
-- ------------------------------------------------------------
-- 용도: FOLLOW-01 브랜치(GroupFollow/UserFollow 통합)를 이미 반영된 코드로 pull 받았는데
--       DB는 아직 예전 group_follow 테이블을 쓰고 있는 경우, 이 파일 하나만 실행하면 된다.
--       빈 DB를 새로 만드는 경우에는 이 파일 대신 weplanet_schema_full_reset.sql을 쓴다.
-- 실행: MySQL Workbench에서 이 파일 전체 선택 후 실행 (Ctrl+Shift+Enter)
--
-- 무엇을 하는가:
--   - group_follow(팬-아티스트 그룹 팔로우) 테이블을 없애고, 그 데이터를 전부
--     user_follows로 옮긴다 (GroupFollow.groupId == UserFollow.following_id == UserFollow.community_id).
--   - user_follows에 community_id 컬럼을 추가하고, 기본키를
--     (follower_id, following_id) -> (follower_id, following_id, community_id)로 바꾼다.
--     (팔로우가 특정 커뮤니티에 종속되도록 - 자세한 이유는 UserFollow.java 주석 참고)
--   - 이 스크립트는 여러 번 실행해도 안전하다 (이미 적용됐으면 각 단계를 건너뛴다).
-- - 기존 데이터(가입자, 게시글 등)는 건드리지 않는다.
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
SET @wp_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = '';
SET FOREIGN_KEY_CHECKS = 0;

-- [1] user_follows에 community_id 컬럼이 없으면 추가 (NOT NULL이라 기본값이 필요 - 우선 0으로 채운 뒤 아래
--     [2]에서 실제 값으로 채워지지 않는 행은 이미 하나도 없어야 정상. group_follow 이관 전에 실행되므로
--     이 시점엔 user_follows가 비어있는 게 보통이라 문제 없음).
DROP PROCEDURE IF EXISTS `wp_follow_merge_add_community_id`;
DELIMITER $$
CREATE PROCEDURE `wp_follow_merge_add_community_id`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND COLUMN_NAME = 'community_id'
    ) THEN
        ALTER TABLE `user_follows`
            ADD COLUMN `community_id` BIGINT NOT NULL DEFAULT 0
                COMMENT '이 팔로우가 속한 커뮤니티(그 커뮤니티 아티스트의 users.id)'
                AFTER `following_id`;
    END IF;
END$$
DELIMITER ;
CALL `wp_follow_merge_add_community_id`();
DROP PROCEDURE IF EXISTS `wp_follow_merge_add_community_id`;

-- [2] group_follow(아티스트 팔로우)가 아직 있으면, user_follows로 이관 후 group_follow는 삭제한다.
--     group_follow.group_id는 항상 그 아티스트의 User.id와 같으므로 following_id/community_id 둘 다 group_id.
DROP PROCEDURE IF EXISTS `wp_follow_merge_migrate_group_follow`;
DELIMITER $$
CREATE PROCEDURE `wp_follow_merge_migrate_group_follow`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_follow'
    ) THEN
        INSERT IGNORE INTO `user_follows` (`follower_id`, `following_id`, `community_id`, `created_at`)
        SELECT `fan_id`, `group_id`, `group_id`, `created_at` FROM `group_follow`;

        DROP TABLE `group_follow`;
    END IF;
END$$
DELIMITER ;
CALL `wp_follow_merge_migrate_group_follow`();
DROP PROCEDURE IF EXISTS `wp_follow_merge_migrate_group_follow`;

-- [3] 기본키를 (follower_id, following_id, community_id)로 바꾼다 - 아직 2컬럼짜리 기본키인 경우에만.
DROP PROCEDURE IF EXISTS `wp_follow_merge_fix_pk`;
DELIMITER $$
CREATE PROCEDURE `wp_follow_merge_fix_pk`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND (
        SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND CONSTRAINT_NAME = 'PRIMARY'
    ) < 3 THEN
        ALTER TABLE `user_follows`
            DROP PRIMARY KEY,
            ADD PRIMARY KEY (`follower_id`, `following_id`, `community_id`);
    END IF;
END$$
DELIMITER ;
CALL `wp_follow_merge_fix_pk`();
DROP PROCEDURE IF EXISTS `wp_follow_merge_fix_pk`;

-- [4] community_id 인덱스/FK가 없으면 추가.
DROP PROCEDURE IF EXISTS `wp_follow_merge_add_fk`;
DELIMITER $$
CREATE PROCEDURE `wp_follow_merge_add_fk`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND INDEX_NAME = 'fk_uf_community'
    ) THEN
        ALTER TABLE `user_follows` ADD KEY `fk_uf_community` (`community_id`);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND CONSTRAINT_NAME = 'fk_uf_community'
    ) THEN
        ALTER TABLE `user_follows`
            ADD CONSTRAINT `fk_uf_community` FOREIGN KEY (`community_id`) REFERENCES `users` (`id`);
    END IF;
END$$
DELIMITER ;
CALL `wp_follow_merge_add_fk`();
DROP PROCEDURE IF EXISTS `wp_follow_merge_add_fk`;

SET FOREIGN_KEY_CHECKS = 1;
SET SESSION sql_mode = @wp_old_sql_mode;

-- ------------------------------------------------------------
-- [확인] user_follows에 community_id가 있고, group_follow는 사라졌어야 정상입니다.
-- ------------------------------------------------------------
SELECT COUNT(*) AS user_follows_rows FROM `user_follows`;
SELECT COUNT(*) AS group_follow_still_exists
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_follow';
