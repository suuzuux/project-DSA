-- ============================================================
-- WePlaNet BADGE-2 : 멤버십 가입/갱신 이력 테이블 (증분)
-- ------------------------------------------------------------
-- 대상: 이미 weplanet DB를 사용 중인 팀원
-- 실행: 이 파일 하나만 MySQL Workbench에서 전체 선택 후 실행
--
-- 왜 필요한가
--   membership 테이블은 (fan_id, artist_id) 당 한 줄이고 만료일을 덮어쓰기 때문에
--   "몇 년째 유지 중인지"를 알 수 없다. 가입/갱신 때마다 한 줄씩 쌓아 이력을 남긴다.
--
-- streak_count : 이 기간이 연속 몇 번째인지. 새 시작일이 직전 만료일 + 7일 안이면 +1, 넘으면 1.
--                넣을 때 계산해서 저장하므로 배지 판정은 마지막 한 줄만 보면 된다.
--
-- 기존 데이터는 건드리지 않으며 여러 번 실행해도 안전합니다.
-- ============================================================

USE `weplanet`;

CREATE TABLE IF NOT EXISTS `membership_period` (
                                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '멤버십 기간 PK',
                                                   `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
                                                   `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
                                                   `started_at` datetime(6) NOT NULL COMMENT '이 기간의 가입/갱신 시각',
    `expires_at` datetime(6) NOT NULL COMMENT '이 기간의 만료 시각',
    `streak_count` int NOT NULL DEFAULT 1 COMMENT '연속 몇 번째 기간인지(1=첫 가입)',
    `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
    PRIMARY KEY (`id`),
    KEY `idx_membership_period_latest` (`fan_id`, `artist_id`, `started_at`),
    KEY `fk_membership_period_artist` (`artist_id`),
    CONSTRAINT `fk_membership_period_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_membership_period_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
    CONSTRAINT `ck_membership_period` CHECK (`expires_at` > `started_at`),
    CONSTRAINT `ck_membership_streak` CHECK (`streak_count` >= 1)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='멤버십 가입/갱신 이력';

-- 이미 멤버십에 가입돼 있는 사람들의 "현재 기간"을 이력으로 옮겨둔다.
-- (이력이 하나도 없으면 다음 갱신이 1번째로 잡혀서 연속 계산이 어긋난다)
INSERT INTO `membership_period` (`fan_id`, `artist_id`, `started_at`, `expires_at`, `streak_count`, `created_at`)
SELECT m.`fan_id`, m.`artist_id`, m.`created_at`, m.`expires_at`, 1, NOW(6)
FROM `membership` m
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_period` p
    WHERE p.`fan_id` = m.`fan_id` AND p.`artist_id` = m.`artist_id`
);

-- [확인] membership 줄 수 이상이 나오면 정상입니다.
SELECT COUNT(*) AS periods FROM `membership_period`;