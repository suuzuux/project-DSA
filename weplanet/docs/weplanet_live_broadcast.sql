-- ============================================================
-- WePlaNet 증분 마이그레이션: 라이브 방송 테이블
-- 이미 운영 중인 DB에 아래만 실행하면 됩니다.
-- ============================================================

CREATE TABLE IF NOT EXISTS `live_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '라이브 세션 PK',
  `artist_id` bigint NOT NULL COMMENT '방송 아티스트(users.id)',
  `host_id` bigint NOT NULL COMMENT '송출 호스트(아티스트 또는 에이전시 users.id)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LIVE / ENDED',
  `started_at` datetime(6) NOT NULL COMMENT '방송 시작 시각',
  `ended_at` datetime(6) DEFAULT NULL COMMENT '방송 종료 시각',
  PRIMARY KEY (`id`),
  KEY `idx_live_session_artist_status` (`artist_id`, `status`),
  KEY `fk_live_session_host` (`host_id`),
  CONSTRAINT `fk_live_session_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_live_session_host` FOREIGN KEY (`host_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 라이브 방송 세션';

CREATE TABLE IF NOT EXISTS `live_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '라이브 댓글 PK',
  `session_id` bigint NOT NULL COMMENT 'live_session.id',
  `author_id` bigint NOT NULL COMMENT '작성자(users.id)',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '댓글 본문',
  `created_at` datetime(6) NOT NULL COMMENT '작성 시각',
  PRIMARY KEY (`id`),
  KEY `idx_live_comment_session_created` (`session_id`, `created_at`),
  KEY `fk_live_comment_author` (`author_id`),
  CONSTRAINT `fk_live_comment_session` FOREIGN KEY (`session_id`) REFERENCES `live_session` (`id`),
  CONSTRAINT `fk_live_comment_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라이브 방송 실시간 댓글';

CREATE TABLE IF NOT EXISTS `live_comment_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '라이브 댓글 신고 PK',
  `comment_id` bigint NOT NULL COMMENT 'live_comment.id',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  `reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPAM / ABUSE / SEXUAL / ETC',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_live_comment_report_comment_reporter` (`comment_id`, `reporter_id`),
  KEY `fk_live_comment_report_reporter` (`reporter_id`),
  CONSTRAINT `fk_live_comment_report_comment` FOREIGN KEY (`comment_id`) REFERENCES `live_comment` (`id`),
  CONSTRAINT `fk_live_comment_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라이브 방송 댓글 신고';
