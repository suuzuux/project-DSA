USE WEPLANET;

CREATE TABLE IF NOT EXISTS `shop_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `buyer_id` bigint NOT NULL,
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TOSS',
  `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` bigint NOT NULL,
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `virtual_bank_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `virtual_account_number` varbinary(255) DEFAULT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `deposit_secret` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY',
  `paid_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_order_no` (`order_no`),
  UNIQUE KEY `uk_shop_order_idempotency` (`idempotency_key`),
  KEY `idx_shop_order_buyer` (`buyer_id`, `created_at`),
  KEY `idx_shop_order_status` (`payment_status`),
  CONSTRAINT `fk_shop_order_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈샵 토스 결제 주문';

CREATE TABLE IF NOT EXISTS `shop_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `variant_id` bigint NOT NULL,
  `product_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_order_item_order` (`order_id`),
  CONSTRAINT `fk_shop_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `shop_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈샵 주문 라인';

CREATE TABLE IF NOT EXISTS `membership_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fan_id` bigint NOT NULL,
  `artist_id` bigint NOT NULL,
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TOSS',
  `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` bigint NOT NULL,
  `virtual_bank_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `virtual_account_number` varbinary(255) DEFAULT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `deposit_secret` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY',
  `paid_at` datetime(6) DEFAULT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_membership_order_no` (`order_no`),
  UNIQUE KEY `uk_membership_order_idempotency` (`idempotency_key`),
  KEY `idx_membership_order_fan` (`fan_id`, `created_at`),
  KEY `idx_membership_order_status` (`payment_status`),
  CONSTRAINT `fk_membership_order_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_membership_order_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='멤버십 토스 결제 주문';
