-- ============================================================
-- FEED : 댓글의 대댓글(답글) — 증분 적용
-- ------------------------------------------------------------
-- 대상: 이미 weplanet DB를 사용 중인 팀원
-- 실행: 이 파일 하나만 MySQL Workbench에서 전체 선택 후 실행
--
-- comment 테이블에 컬럼 2개를 추가한다. 기존 댓글 데이터는 건드리지 않는다.
--   parent_id  : 답글이면 부모 댓글(comment.id), 일반 댓글이면 NULL
--   deleted_at : 답글이 남아 있는 원댓글을 지웠을 때의 삭제 시각
--                답글을 살려두기 위해 행은 남기고 "삭제된 댓글입니다"로만 보여준다
--
-- 여러 번 실행해도 안전합니다.
-- 빈 DB를 처음 구성하는 경우에는 weplanet_schema_full_reset.sql 을 사용하세요.
-- ============================================================

USE `weplanet`;

DROP PROCEDURE IF EXISTS `wp_apply_comment_reply`;

DELIMITER $$

CREATE PROCEDURE `wp_apply_comment_reply`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'comment 테이블이 없습니다. 빈 DB는 weplanet_schema_full_reset.sql 을 먼저 실행하세요.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment'
          AND COLUMN_NAME = 'parent_id'
    ) THEN
        ALTER TABLE `comment`
            ADD COLUMN `parent_id` bigint DEFAULT NULL
                COMMENT '답글이면 부모 댓글(comment.id), 일반 댓글이면 NULL'
                AFTER `post_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment'
          AND COLUMN_NAME = 'deleted_at'
    ) THEN
        ALTER TABLE `comment`
            ADD COLUMN `deleted_at` datetime(6) DEFAULT NULL
                COMMENT '답글이 남아 있는 원댓글의 삭제 시각 (NULL이면 정상 댓글)'
                AFTER `parent_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comment'
          AND INDEX_NAME = 'idx_comment_parent'
    ) THEN
        ALTER TABLE `comment` ADD KEY `idx_comment_parent` (`parent_id`);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'comment'
          AND CONSTRAINT_NAME = 'fk_comment_parent'
    ) THEN
        ALTER TABLE `comment`
            ADD CONSTRAINT `fk_comment_parent`
                FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`);
    END IF;
END$$

DELIMITER ;

CALL `wp_apply_comment_reply`();
DROP PROCEDURE `wp_apply_comment_reply`;

-- [확인] parent_id, deleted_at 두 줄이 보이면 적용 완료
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'comment'
  AND COLUMN_NAME IN ('parent_id', 'deleted_at');
