-- 굿즈샵 필터(MD / DIGITAL / MEMBERSHIP) 등록 카테고리
ALTER TABLE `shop_goods`
  ADD COLUMN `shop_category` varchar(20) NOT NULL DEFAULT 'MD'
  COMMENT '샵 필터: MD / DIGITAL / MEMBERSHIP'
  AFTER `membership_only`;

UPDATE `shop_goods`
SET `shop_category` = 'MEMBERSHIP'
WHERE `membership_only` = 1;
