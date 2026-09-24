-- 미디어 멤버십 전용 공개 여부
ALTER TABLE `board_media`
  ADD COLUMN `membership_only` tinyint(1) NOT NULL DEFAULT 0
  COMMENT '멤버십 전용 여부 (1=전용)'
  AFTER `like_count`;
