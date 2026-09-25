-- ============================================================
-- WePlaNet 입점 승인 → 소속사 계정 활성화 증분 스키마
-- 기존 데이터는 건드리지 않고 CHECK 제약에 새 값만 추가한다.
--   users.status               + PENDING_ACTIVATION (활성화 대기)
--   email_verification.purpose + AGENCY_ACTIVATION  (소속사 계정 활성화 링크)
-- ============================================================

USE `weplanet`;

ALTER TABLE `users`
DROP CHECK `ck_users_status`;

ALTER TABLE `users`
    ADD CONSTRAINT `ck_users_status`
        CHECK (`status` IN (
                            _utf8mb4'ACTIVE',
                            _utf8mb4'DORMANT',
                            _utf8mb4'SUSPENDED',
                            _utf8mb4'WITHDRAWN',
                            _utf8mb4'PENDING_ACTIVATION'
            ));

ALTER TABLE `email_verification`
DROP CHECK `ck_email_verification_purpose`;

ALTER TABLE `email_verification`
    ADD CONSTRAINT `ck_email_verification_purpose`
        CHECK (`purpose` IN (
                             _utf8mb4'SIGNUP',
                             _utf8mb4'FAN_PROJECT_CREATE',
                             _utf8mb4'ADMIN_LOGIN',
                             _utf8mb4'AGENCY_ACTIVATION'
            ));