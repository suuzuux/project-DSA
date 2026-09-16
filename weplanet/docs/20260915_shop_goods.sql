-- 반영: 2026-09-15, 에이전시 굿즈 관리 (shop_goods)
-- ddl-auto=validate 환경용 증분 스크립트

CREATE TABLE IF NOT EXISTS `shop_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '굿즈 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `name` varchar(200) NOT NULL COMMENT '상품명',
  `description` text COMMENT '상세 설명(마크다운)',
  `price` int NOT NULL COMMENT '가격(원)',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 저장 파일명',
  `official_url` varchar(500) DEFAULT NULL COMMENT '공식 판매처 URL',
  `status` varchar(20) NOT NULL COMMENT 'ON_SALE / HIDDEN',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '유저 샵 노출 순서(오름차순)',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '소프트 삭제 시각',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_artist_sort` (`artist_id`, `deleted_at`, `sort_order`, `id`),
  KEY `idx_shop_goods_status` (`status`, `deleted_at`, `sort_order`, `id`),
  CONSTRAINT `fk_shop_goods_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='에이전시 등록 굿즈';
