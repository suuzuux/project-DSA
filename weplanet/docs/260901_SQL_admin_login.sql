-- ============================================================
-- 최고관리자 로그인 이메일 인증(ADMIN-1) 추가분 - 260901 정휘원
-- ------------------------------------------------------------
-- email_verification.purpose 에 ADMIN_LOGIN 값을 허용하도록 CHECK 제약을 바꾼다.
--
-- 왜 필요한가
--   이메일 인증은 목적(purpose)별로 따로 관리된다. 지금은 SIGNUP / FAN_PROJECT_CREATE
--   두 가지만 허용돼 있어서, 관리자 로그인 인증을 저장하려 하면 아래 오류로 막힌다.
--     ERROR 3819 : Check constraint 'ck_email_verification_purpose' is violated.
--
--   목적을 나눠두는 이유는 보안 때문이다. 목적 구분이 없으면 "회원가입용으로 받은 인증"이
--   "관리자 로그인 인증"으로도 통과되어 버린다.
--
-- 실행법
--   mysql -uroot -proot --default-character-set=utf8mb4 weplanet < docs/260901_SQL_admin_login.sql
--
-- 안전성
--   여러 번 실행해도 안전하다(멱등). 기존 데이터는 지우지 않는다.
-- ============================================================

USE `weplanet`;

DROP PROCEDURE IF EXISTS wp_email_purpose_add_admin_login;

DELIMITER $$

CREATE PROCEDURE wp_email_purpose_add_admin_login()
BEGIN
    -- 이미 ADMIN_LOGIN 이 허용돼 있으면 아무것도 하지 않는다.
    IF EXISTS (SELECT 1
               FROM information_schema.CHECK_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE()
                 AND CONSTRAINT_NAME = 'ck_email_verification_purpose'
                 AND CHECK_CLAUSE LIKE '%ADMIN_LOGIN%') THEN

        SELECT 'email_verification.purpose : 이미 ADMIN_LOGIN 허용됨 (건너뜀)' AS result;

    ELSE
        -- CHECK 제약은 수정이 안 되므로 지우고 다시 만든다.
        ALTER TABLE `email_verification`
            DROP CHECK `ck_email_verification_purpose`;

        ALTER TABLE `email_verification`
            ADD CONSTRAINT `ck_email_verification_purpose`
                CHECK (`purpose` IN ('SIGNUP', 'FAN_PROJECT_CREATE', 'ADMIN_LOGIN'));

        SELECT 'email_verification.purpose : ADMIN_LOGIN 추가 완료' AS result;
    END IF;
END$$

DELIMITER ;

CALL wp_email_purpose_add_admin_login();

DROP PROCEDURE wp_email_purpose_add_admin_login;


-- 확인용
SELECT CONSTRAINT_NAME AS 제약, CHECK_CLAUSE AS 허용값
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'ck_email_verification_purpose';
