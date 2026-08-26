-- ============================================================
-- WePlaNet 테스트 계정 공유용 시드 스크립트 (김화평)
-- ------------------------------------------------------------
-- 로컬 weplanet DB에 그대로 실행하면 됨: 이미 같은 username이 있으면
-- 조용히 건너뜀(INSERT IGNORE)이라 여러 번 돌려도 안전함.
--
-- 계정 목록 (비밀번호는 4명 다 동일: weplanet1234! — 이것도 톡으로 같이 전달):
--   artist_hwiwon  (ARTIST, 휘원공주)
--   artist_jungsik (ARTIST, 정식왕자)
--   asd123         (FAN,    빛나는여우135)
--   qatest99       (FAN,    QA테스터)
--
-- 참고: password 컬럼은 BCrypt 해시라 그대로 복사해도 원문 비번은 유출 안 됨.
--       같은 해시니까 원문 비번만 알면 그대로 로그인 가능.
-- ============================================================

USE `weplanet`;

INSERT IGNORE INTO `users`
  (`username`, `password`, `role`, `status`, `real_name`, `nickname`, `email`, `created_at`, `updated_at`)
VALUES
  ('artist_hwiwon',  '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'ARTIST', 'ACTIVE', '휘원',    '휘원공주',      'hwiwon@weplanet.test',  NOW(), NOW()),
  ('artist_jungsik', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'ARTIST', 'ACTIVE', '정식',    '정식왕자',      'jungsik@weplanet.test', NOW(), NOW()),
  ('asd123',         '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'FAN',    'ACTIVE', '김화평',  '빛나는여우135', 'asdojuasdoa@gmail.com', NOW(), NOW()),
  ('qatest99',       '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'FAN',    'ACTIVE', 'QA테스터', 'QA테스터',     'qatest99@example.com',  NOW(), NOW());

-- 이미 계정이 있던 사람(=INSERT IGNORE로 스킵됨)도 비밀번호를 위 해시로 맞추고 싶으면 같이 실행:
UPDATE `users` SET `password` = '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq'
WHERE `username` IN ('artist_hwiwon', 'artist_jungsik', 'asd123', 'qatest99');
