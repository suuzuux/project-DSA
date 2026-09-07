-- ============================================================
-- WePlaNet 테스트 계정 비밀번호 통일
-- ------------------------------------------------------------
-- 작성: 2026-09-06
-- 목적: 모든 users.password 를 공통 비밀번호 weplanet1234! 로 맞춤
--
-- !! 주의 !!
--   - 개발/테스트 DB 전용입니다. 운영 DB에서는 실행하지 마세요.
--   - 기존 비밀번호 해시를 전부 덮어씁니다.
--   - 앱이 떠 있는 동안에도 실행 가능하지만, 이미 로그인된 세션은
--     그대로 유지됩니다. 확인은 로그아웃 후 다시 로그인하세요.
--
-- 실행 방법 (예)
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < 20260906_test_password_unify_weplanet1234.sql
--
-- 통일 비밀번호: weplanet1234!
-- 해시: weplanet_schema_full_reset.sql 시드와 동일 ($2b$ BCrypt)
-- ============================================================

USE `weplanet`;

SET NAMES utf8mb4;

UPDATE `users`
SET
  `password` = '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq',
  `updated_at` = NOW(6);

-- 확인용 (비밀번호 해시 자체는 출력하지 않음)
SELECT
  `id`,
  `username`,
  `role`,
  `status`,
  `nickname`,
  CASE
    WHEN `password` = '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq'
      THEN 'weplanet1234! (통일됨)'
    ELSE '다른 해시'
  END AS `password_status`,
  `updated_at`
FROM `users`
ORDER BY `role`, `username`;
