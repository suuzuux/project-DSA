USE WEPLANET;
-- 미디어 멤버십 전용 공개 여부
ALTER TABLE `board_media`
  ADD COLUMN `membership_only` tinyint(1) NOT NULL DEFAULT 0
  COMMENT '멤버십 전용 여부 (1=전용)'
  AFTER `like_count`;

-- 굿즈샵 필터(MD / DIGITAL / MEMBERSHIP) 등록 카테고리
ALTER TABLE `shop_goods`
    ADD COLUMN `shop_category` varchar(20) NOT NULL DEFAULT 'MD'
    COMMENT '샵 필터: MD / DIGITAL / MEMBERSHIP'
  AFTER `membership_only`;

UPDATE `shop_goods`
SET `shop_category` = 'MEMBERSHIP'
WHERE `membership_only` = 1;