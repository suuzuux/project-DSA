-- ============================================================
-- 팬 프로젝트 참여 결제: 모의결제 -> 토스 가상계좌 전환
-- 반영: 2026-09-21 (prjPAYMENT-1)
-- ------------------------------------------------------------
-- fan_project_contribution 테이블만 변경 (기존 데이터 유지)
--   - depositor_name 삭제 (가상계좌는 주문마다 계좌가 달라 입금자명 대조 불필요)
--   - 가상계좌 정보 컬럼 4개 추가 (READY 상태에선 NULL)
--   - payment_status 허용값에 WAITING_FOR_DEPOSIT, EXPIRED 추가
--
-- 실행 예:
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < 20260921_fan_project_contribution_virtual_account.sql
-- ============================================================

SET NAMES utf8mb4;

-- 1) 기존 상태값 제약 제거 (새 상태값을 넣기 위해)
ALTER TABLE `fan_project_contribution`
DROP CHECK `ck_fan_project_contribution_status`;

-- 2) 컬럼 변경
ALTER TABLE `fan_project_contribution`
DROP COLUMN `depositor_name`,
  ADD COLUMN `virtual_bank_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '가상계좌 은행 코드(토스 코드)' AFTER `amount`,
  ADD COLUMN `virtual_account_number` varbinary(255) DEFAULT NULL
      COMMENT '가상계좌 번호(현재 변환 저장, 추후 암호화)' AFTER `virtual_bank_code`,
  ADD COLUMN `due_date` datetime(6) DEFAULT NULL
      COMMENT '가상계좌 입금기한' AFTER `virtual_account_number`,
  ADD COLUMN `deposit_secret` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '입금 웹훅 검증값(외부 노출 금지)' AFTER `due_date`,
  MODIFY COLUMN `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY'
      COMMENT '결제 상태: READY/WAITING_FOR_DEPOSIT/PAID/FAILED/EXPIRED/CANCELLED/REFUND_REQUESTED/REFUNDED';

-- 3) 새 상태값 포함해서 제약 다시 추가
ALTER TABLE `fan_project_contribution`
    ADD CONSTRAINT `ck_fan_project_contribution_status` CHECK (`payment_status` IN (
                                                                                    _utf8mb4'READY', _utf8mb4'WAITING_FOR_DEPOSIT', _utf8mb4'PAID', _utf8mb4'FAILED',
                                                                                    _utf8mb4'EXPIRED', _utf8mb4'CANCELLED', _utf8mb4'REFUND_REQUESTED', _utf8mb4'REFUNDED'
        ));