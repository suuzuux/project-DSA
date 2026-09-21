-- ============================================================
-- 굿즈 스키마 리셋 (팀 공유용 · 이 파일만 실행)
-- 반영: 2026-09-17 (옵션별 재고 Variant)
-- ------------------------------------------------------------
-- 대상 테이블만 DROP 후 CREATE (다른 DB 테이블은 건드리지 않음)
--   - shop_goods_variant  ← 옵션(사이즈)별 재고
--   - shop_goods_option   ← 가방 치수·메모 (재고 없음)
--   - shop_goods_category
--   - shop_goods          ← membership_only 포함, stock_quantity 없음
--
-- !! 주의: 위 테이블의 기존 굿즈 데이터는 삭제됩니다.
--
-- 실행 예:
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < 20260917_shop_goods_alter_membership.sql
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `shop_goods_variant`;
DROP TABLE IF EXISTS `shop_goods_option`;
DROP TABLE IF EXISTS `shop_goods_category`;
DROP TABLE IF EXISTS `shop_goods`;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `shop_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '굿즈 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `name` varchar(200) NOT NULL COMMENT '상품명',
  `description` text COMMENT '상세 설명(마크다운)',
  `price` int NOT NULL COMMENT '가격(원)',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 저장 파일명',
  `official_url` varchar(500) DEFAULT NULL COMMENT '공식 판매처 URL',
  `status` varchar(20) NOT NULL COMMENT 'ON_SALE / HIDDEN (공개여부, 품절과 무관)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '유저 샵 노출 순서(오름차순)',
  `membership_only` tinyint(1) NOT NULL DEFAULT 0 COMMENT '멤버십 전용 여부 (1=전용)',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '소프트 삭제 시각',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_artist_sort` (`artist_id`, `deleted_at`, `sort_order`, `id`),
  KEY `idx_shop_goods_status` (`status`, `deleted_at`, `sort_order`, `id`),
  CONSTRAINT `fk_shop_goods_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='에이전시 등록 굿즈';

CREATE TABLE `shop_goods_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL COMMENT 'CLOTHING/SHOES/BAG/ACCESSORY/OTHER',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_category` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_category_goods`
    FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 가방 치수·자유 메모 등 (재고 없음)
CREATE TABLE `shop_goods_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL,
  `option_key` varchar(30) NOT NULL COMMENT 'WIDTH, HEIGHT, DEPTH, NOTE',
  `option_value` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_option_goods` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_option_goods`
    FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 판매단위 + 옵션별 재고 (굿즈+옵션값 조합)
CREATE TABLE `shop_goods_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `option_key` varchar(30) NOT NULL COMMENT 'SIZE / SHOE_MM / DEFAULT',
  `option_value` varchar(50) NOT NULL DEFAULT '',
  `stock_quantity` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_variant` (`goods_id`, `option_key`, `option_value`),
  KEY `idx_shop_goods_variant_goods` (`goods_id`),
  CONSTRAINT `fk_shop_goods_variant_goods`
    FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
