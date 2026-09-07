-- ============================================================
-- WePlaNet 증분 마이그레이션: 아티스트 DM용 가상 AI 팬 계정 5명
-- 앱 재시작 시 AiFanDataInitializer 가 없으면 만들어 주므로,
-- 이 SQL은 수동으로 맞춰두고 싶을 때만 실행하면 됩니다.
-- 비밀번호: weplanet1234!
-- ============================================================

INSERT INTO `users`
  (`username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
SELECT * FROM (
  SELECT 'aifan_mina' AS username, '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq' AS password,
         'FAN' AS role, 'ACTIVE' AS status, NULL AS agency_id, '별빛민아' AS real_name, '별빛민아' AS nickname,
         'aifan_mina@weplanet.test' AS email, NOW(6) AS email_verified_at, NOW(6) AS created_at, NOW(6) AS updated_at
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'aifan_mina');

INSERT INTO `users`
  (`username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
SELECT * FROM (
  SELECT 'aifan_hayul', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq',
         'FAN', 'ACTIVE', NULL, '하율짱', '하율짱',
         'aifan_hayul@weplanet.test', NOW(6), NOW(6), NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'aifan_hayul');

INSERT INTO `users`
  (`username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
SELECT * FROM (
  SELECT 'aifan_haerin', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq',
         'FAN', 'ACTIVE', NULL, '달콤해린', '달콤해린',
         'aifan_haerin@weplanet.test', NOW(6), NOW(6), NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'aifan_haerin');

INSERT INTO `users`
  (`username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
SELECT * FROM (
  SELECT 'aifan_jun', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq',
         'FAN', 'ACTIVE', NULL, '우주준', '우주준',
         'aifan_jun@weplanet.test', NOW(6), NOW(6), NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'aifan_jun');

INSERT INTO `users`
  (`username`, `password`, `role`, `status`, `agency_id`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
SELECT * FROM (
  SELECT 'aifan_yuna', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq',
         'FAN', 'ACTIVE', NULL, '햇살유나', '햇살유나',
         'aifan_yuna@weplanet.test', NOW(6), NOW(6), NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'aifan_yuna');
