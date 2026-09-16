-- 반영: 2026-09-16, 굿즈 카테고리(다대다) + 옵션(key-value)

CREATE TABLE IF NOT EXISTS `shop_goods_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL COMMENT 'CLOTHING/ACCESSORY/BAG/SHOES/OTHER',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_category` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_category_goods` FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `shop_goods_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL,
  `option_key` varchar(30) NOT NULL COMMENT 'SIZE, SHOE_MM, WIDTH, HEIGHT, DEPTH, NOTE',
  `option_value` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_option_goods` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_option_goods` FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
