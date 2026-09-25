-- ============================================================
-- WePlaNet 로컬 DB 적용 - 이 파일 하나만 실행하면 됩니다 (데이터 보존)
-- ------------------------------------------------------------
-- 대상: main을 pull 받은 뒤 앱이 안 뜨거나("Schema validation: missing ..."),
--       "참여하기"에서 오류가 나는 팀원
-- 실행: MySQL Workbench에서 이 파일 전체 선택 후 실행 (Ctrl+Shift+Enter)
--
-- 기준: docs/weplanet_schema_full_reset.sql (이 파일은 그 스키마로부터 생성됨)
-- 동작:
--   1) 없는 테이블만 CREATE TABLE IF NOT EXISTS
--   2) 기존 테이블에 없는 컬럼만 ADD COLUMN
--   3) 옛 구조 전환 (결제 가상계좌 전환 / 팔로우 통합)
--   4) 배지 카탈로그(fan_badge) 중 없는 코드만 INSERT IGNORE
--   5) 마지막 [검증 1] [검증 2]가 모두 0행이면 적용 완료
--
-- - 기존 데이터는 삭제/수정하지 않으며, 여러 번 실행해도 안전합니다.
-- - 기존 행에 NOT NULL 컬럼이 추가되면 MySQL 암묵 기본값('' / 0 / 0000-00-00)이 채워집니다.
-- - 기존 테이블에는 컬럼만 추가하고 인덱스/UNIQUE/CHECK/FK는 추가하지 않습니다.
--   (Hibernate validate는 컬럼 존재·타입만 검사하므로 앱 기동에는 영향 없음)
-- - 빈 DB를 새로 만들 때는 weplanet_schema_full_reset.sql을 사용하세요.
-- ============================================================

USE `weplanet`;
SET NAMES utf8mb4;
SET @wp_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = '';
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS `wp_sync_add_column`;

DELIMITER $$

CREATE PROCEDURE `wp_sync_add_column`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT,
    IN p_drop_default TINYINT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @wp_sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE wp_stmt FROM @wp_sql;
        EXECUTE wp_stmt;
        DEALLOCATE PREPARE wp_stmt;
        -- 기존 행을 현재 시각으로 채우려고 임시로 둔 DEFAULT를 스키마와 같게 제거한다.
        IF p_drop_default = 1 THEN
            SET @wp_sql = CONCAT('ALTER TABLE `', p_table, '` ALTER COLUMN `', p_column, '` DROP DEFAULT');
            PREPARE wp_stmt FROM @wp_sql;
            EXECUTE wp_stmt;
            DEALLOCATE PREPARE wp_stmt;
        END IF;
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- [1] 없는 테이블 생성
--     (구조 전환보다 먼저 한다. 오래된 DB에는 전환 대상 테이블 자체가 없을 수 있다)
-- ============================================================

CREATE TABLE IF NOT EXISTS `agencies` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '소속사 PK',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '소속사명',
  `business_no` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사업자등록번호',
  `ceo_name` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '대표자명',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '소속사 상태: ACTIVE/SUSPENDED',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agencies_name` (`name`),
  UNIQUE KEY `uk_agencies_bizno` (`business_no`),
  CONSTRAINT `ck_agencies_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소속사 마스터';

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '회원 PK',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '로그인 아이디',
  `password` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '비밀번호(BCrypt 해시, 소셜 전용 가입자는 NULL)',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '역할: FAN/ARTIST/AGENCY/ADMIN',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태: ACTIVE/DORMANT/SUSPENDED/WITHDRAWN',
  `agency_id` bigint DEFAULT NULL COMMENT '소속사(agencies.id). 주로 ARTIST 계정에 사용, 없으면 NULL',
  `real_name` varbinary(255) NOT NULL COMMENT '실명(암호화 저장)',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '플랫폼 공통 닉네임(미입력 시 자동생성)',
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이메일(로그인·알림 수신)',
  `phone` varbinary(255) DEFAULT NULL COMMENT '휴대폰번호(암호화 저장)',
  `phone_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '휴대폰 SHA-256 해시(검색·중복확인)',
  `birth_date` date DEFAULT NULL COMMENT '생년월일',
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '성별: MALE/FEMALE/OTHER',
  `zipcode` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '우편번호(배송·지역알림 확장용)',
  `address1` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '기본 주소',
  `address2` varbinary(512) DEFAULT NULL COMMENT '상세주소(암호화 저장)',
  `email_verified_at` datetime(6) DEFAULT NULL COMMENT '이메일 인증 완료 시각(NULL=미인증)',
  `last_login_at` datetime(6) DEFAULT NULL COMMENT '최종 로그인 시각',
  `dormant_notice_sent_at` datetime(6) DEFAULT NULL COMMENT '휴면 전환 30일 전 사전 안내 메일 발송 시각',
  `created_at` datetime(6) NOT NULL COMMENT '가입 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '정보 수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '탈퇴(soft delete) 시각',
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '연동된 소셜 provider: GOOGLE/KAKAO/LINE (연동 없으면 NULL)',
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '소셜 플랫폼 고유 ID (LOCAL 가입자는 NULL)',
  `marketing_consent` tinyint(1) NOT NULL DEFAULT '0' COMMENT '광고성 정보 수신 동의 (회원가입 체크박스와 공유)',
  `community_activity_email_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '가입한 아티스트 활동(게시글/공지/라이브) 이메일 수신 여부',
  `night_notification_allowed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '오후 9시~오전 8시(KST) 알림 수신 여부',
  `preferred_language` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'KO' COMMENT '기본 서비스 언어 (KO/JA/EN) - 게시글/댓글 AI 번역 대상 언어로도 재사용',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_nickname` (`nickname`),
  UNIQUE KEY `uk_users_provider_provider_id` (`provider`, `provider_id`),
  KEY `idx_users_role_status` (`role`, `status`),
  KEY `idx_users_phone_hash` (`phone_hash`),
  KEY `idx_users_agency_role` (`agency_id`, `role`),
  CONSTRAINT `fk_users_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `ck_users_gender` CHECK ((`gender` IS NULL) OR (`gender` IN (_utf8mb4'MALE', _utf8mb4'FEMALE', _utf8mb4'OTHER'))),
  CONSTRAINT `ck_users_role` CHECK (`role` IN (_utf8mb4'FAN', _utf8mb4'ARTIST', _utf8mb4'AGENCY', _utf8mb4'ADMIN')),
    CONSTRAINT `ck_users_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'DORMANT', _utf8mb4'SUSPENDED', _utf8mb4'WITHDRAWN', _utf8mb4'PENDING_ACTIVATION')),
  CONSTRAINT `ck_users_provider` CHECK ((`provider` IS NULL) OR (`provider` IN (_utf8mb4'GOOGLE', _utf8mb4'KAKAO', _utf8mb4'LINE'))),
  CONSTRAINT `ck_users_preferred_language` CHECK (`preferred_language` IN (_utf8mb4'KO', _utf8mb4'JA', _utf8mb4'EN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공통 회원 계정';

CREATE TABLE IF NOT EXISTS `filter_keyword` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '키워드 PK',
  `keyword` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '필터링 대상 문자열',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj55c0tyqc5n2qto80hyjpegy1` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채팅 부적절 언어 필터 키워드';

CREATE TABLE IF NOT EXISTS `fan_badge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '배지 PK',
  `badge_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 코드(전체 고유)',
  `badge_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 표시명',
  `badge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 유형: BASIC/SPECIAL',
  `icon` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '🏅' COMMENT '표시용 이모지',
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배지 이미지 경로. 있으면 이미지, 없으면 icon 이모지로 표시',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '획득 조건 안내 문구',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '유형 내 표시 순서(작을수록 앞)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_badge_code` (`badge_code`),
  KEY `idx_fan_badge_list` (`badge_type`, `sort_order`),
  CONSTRAINT `ck_fan_badge_master_type` CHECK (`badge_type` IN (_utf8mb4'BASIC', _utf8mb4'SPECIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='배지 카탈로그(전 아티스트 공통)';

CREATE TABLE IF NOT EXISTS `admin_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (ADMIN 1:1)',
  `admin_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STAFF' COMMENT '관리 등급: SUPER/STAFF',
  `department` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '소속 부서',
  `employee_no` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사번',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_adp_empno` (`employee_no`),
  CONSTRAINT `fk_adp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_adp_level` CHECK (`admin_level` IN (_utf8mb4'SUPER', _utf8mb4'STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='운영자(ADMIN) 프로필';

CREATE TABLE IF NOT EXISTS `admin_action_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '로그 PK',
  `actor_id` bigint NOT NULL COMMENT '조치 수행 운영자(users.id)',
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조치 유형(제재/승인 등)',
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 종류(USER/GROUP/AGENCY 등)',
  `target_id` bigint NOT NULL COMMENT '대상 ID(다형 참조)',
  `reason` text COLLATE utf8mb4_unicode_ci COMMENT '조치 사유',
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '요청 IP(IPv6 포함)',
  `created_at` datetime(6) NOT NULL COMMENT '기록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_aal_actor` (`actor_id`, `created_at`),
  KEY `idx_aal_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_aal_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='운영자 조치 감사 로그';

CREATE TABLE IF NOT EXISTS `agency_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (AGENCY 1:1)',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `department` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '담당 부서',
  `position` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직책',
  `is_owner` tinyint(1) NOT NULL DEFAULT '0' COMMENT '소속사 대표 계정 여부',
  `approved_by` bigint DEFAULT NULL COMMENT '승인 운영자(users.id)',
  `approved_at` datetime(6) DEFAULT NULL COMMENT '승인 시각(NULL=미승인)',
  PRIMARY KEY (`user_id`),
  KEY `idx_agp_agency` (`agency_id`),
  KEY `fk_agp_approver` (`approved_by`),
  CONSTRAINT `fk_agp_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `fk_agp_approver` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_agp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소속사 담당자(AGENCY) 프로필';

CREATE TABLE IF NOT EXISTS `artist_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (ARTIST 1:1)',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `stage_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '활동명(스테이지명)',
  `debut_date` date DEFAULT NULL COMMENT '개인 데뷔일',
  `position` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '포지션(보컬/래퍼 등)',
  `bio` text COLLATE utf8mb4_unicode_ci COMMENT '아티스트 소개글',
  `profile_img` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '프로필 이미지 URL/경로',
  PRIMARY KEY (`user_id`),
  KEY `idx_ap_agency` (`agency_id`),
  CONSTRAINT `fk_ap_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `fk_ap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 계정 전용 프로필';

CREATE TABLE IF NOT EXISTS `artist_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '그룹(커뮤니티) PK',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '그룹명(한글)',
  `name_en` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '그룹명(영문)',
  `fandom_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '팬덤명',
  `debut_date` date DEFAULT NULL COMMENT '그룹 데뷔일',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '상태: ACTIVE/HIATUS/DISBANDED',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_name` (`name`),
  KEY `idx_group_agency` (`agency_id`),
  CONSTRAINT `fk_group_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `ck_group_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'HIATUS', _utf8mb4'DISBANDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 그룹/커뮤니티';

CREATE TABLE IF NOT EXISTS `artist_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '포털 프로필 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `intro` text COLLATE utf8mb4_unicode_ci COMMENT '커뮤니티 포털 소개문',
  `header_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '헤더/배너 이미지 URL',
  `logo_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '로고/아이콘 이미지 URL',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_profile_artist` (`artist_id`),
  CONSTRAINT `fk_artist_profile_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 커뮤니티 포털 테마/소개';

CREATE TABLE IF NOT EXISTS `artist_group_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '탐색 프로필 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '그룹/아티스트 성별(탐색 필터)',
  `member_count` int DEFAULT NULL COMMENT '구성 인원 수(탐색 필터)',
  `nationality` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '국적(탐색 필터)',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직업/카테고리(탐색 필터)',
  `debut_date` date DEFAULT NULL COMMENT '데뷔일(탐색 필터)',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agp_artist` (`artist_id`),
  CONSTRAINT `fk_agp_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='커뮤니티 탐색 필터용 메타';

CREATE TABLE IF NOT EXISTS `group_members` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '멤버십 이력 PK',
  `group_id` bigint NOT NULL COMMENT '그룹(artist_groups.id)',
  `artist_id` bigint NOT NULL COMMENT '아티스트(artist_profiles.user_id)',
  `is_leader` tinyint(1) NOT NULL DEFAULT '0' COMMENT '리더 여부',
  `joined_at` date NOT NULL COMMENT '합류일',
  `left_at` date DEFAULT NULL COMMENT '탈퇴일(NULL=활동중)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gm` (`group_id`, `artist_id`, `joined_at`),
  KEY `idx_gm_artist` (`artist_id`),
  CONSTRAINT `fk_gm_artist` FOREIGN KEY (`artist_id`) REFERENCES `artist_profiles` (`user_id`),
  CONSTRAINT `fk_gm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
  CONSTRAINT `ck_gm_period` CHECK ((`left_at` IS NULL) OR (`left_at` >= `joined_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹–아티스트 소속 이력';

CREATE TABLE IF NOT EXISTS `user_follows` (
  `follower_id` bigint NOT NULL COMMENT '팔로우 하는 사람(users.id)',
  `following_id` bigint NOT NULL COMMENT '팔로우 당하는 사람(users.id)',
  `community_id` bigint NOT NULL COMMENT '이 팔로우가 속한 커뮤니티(그 커뮤니티 아티스트의 users.id)',
  `created_at` datetime(6) NOT NULL COMMENT '팔로우 시각',
  PRIMARY KEY (`follower_id`, `following_id`, `community_id`),
  KEY `fk_uf_following` (`following_id`),
  KEY `fk_uf_community` (`community_id`),
  CONSTRAINT `fk_uf_follower` FOREIGN KEY (`follower_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_uf_following` FOREIGN KEY (`following_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_uf_community` FOREIGN KEY (`community_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사람↔사람 팔로우(커뮤니티에 종속)';

CREATE TABLE IF NOT EXISTS `membership` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '멤버십 PK',
  `created_at` datetime(6) NOT NULL COMMENT '가입 시각',
  `expires_at` datetime(6) NOT NULL COMMENT '만료 시각(만료 시 DM 제한)',
  `artist_id` bigint NOT NULL COMMENT '대상 아티스트(users.id)',
  `fan_id` bigint NOT NULL COMMENT '가입 팬(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_membership` (`fan_id`, `artist_id`),
  KEY `fk_membership_artist` (`artist_id`),
  CONSTRAINT `fk_membership_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_membership_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬–아티스트 유료 멤버십';

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

CREATE TABLE IF NOT EXISTS `artist_block` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '차단 PK',
  `artist_id` bigint NOT NULL COMMENT '차단한 아티스트(users.id)',
  `blocked_user_id` bigint NOT NULL COMMENT '차단된 유저(users.id)',
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '차단 사유',
  `created_at` datetime NOT NULL COMMENT '차단 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_block` (`artist_id`, `blocked_user_id`),
  KEY `fk_artist_block_user` (`blocked_user_id`),
  KEY `idx_artist_block_artist_created_at` (`artist_id`, `created_at`),
  CONSTRAINT `fk_artist_block_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_artist_block_user` FOREIGN KEY (`blocked_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 유저 차단';

CREATE TABLE IF NOT EXISTS `artist_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '일정 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `category` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'OTHER' COMMENT '스케줄 카테고리(TV_BROADCAST/YOUTUBE/CONCERT/RADIO/AWARDS/PHOTO_MAGAZINE/BIRTHDAY/OTHER)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '일정 제목',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '일정 상세 설명',
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '장소',
  `ticket_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '티켓 URL',
  `schedule_at` datetime NOT NULL COMMENT '일정 일시',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_artist_schedule_artist_schedule_at` (`artist_id`, `schedule_at`),
  CONSTRAINT `fk_artist_schedule_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 스케줄';

CREATE TABLE IF NOT EXISTS `artist_attendance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '출석 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `visit_date` date NOT NULL COMMENT '홈페이지 방문일',
  `paw_color` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '고양이 발바닥 색상(hex)',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_attendance_artist_date` (`artist_id`, `visit_date`),
  KEY `idx_artist_attendance_artist_date` (`artist_id`, `visit_date`),
  CONSTRAINT `fk_artist_attendance_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 홈페이지 출석(발바닥)';

CREATE TABLE IF NOT EXISTS `portal_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '공지 PK',
  `artist_id` bigint NOT NULL COMMENT '커뮤니티 아티스트(users.id)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '공지 제목',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '공지 본문',
  `published` bit(1) NOT NULL DEFAULT b'1' COMMENT '게시 여부(1=공개)',
  `pinned` bit(1) NOT NULL DEFAULT b'0' COMMENT '목록 상단 노출 여부',
  `pin_order` int DEFAULT NULL COMMENT '상단 노출 순서(1부터, 작을수록 위, 최대 5개)',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_portal_notice_artist_created_at` (`artist_id`, `created_at`),
  KEY `idx_portal_notice_artist_pinned` (`artist_id`, `pinned`, `pin_order`),
  CONSTRAINT `fk_portal_notice_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 커뮤니티 공지';

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

CREATE TABLE IF NOT EXISTS `site_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '홈페이지 공지 PK',
  `author_id` bigint NOT NULL COMMENT '작성 관리자(users.id)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '공지 제목',
  `category` enum('GENERAL', 'EVENT', 'MAINTENANCE', 'UPDATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '공지 분류 (일반/이벤트/점검/업데이트)',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '공지 본문',
  `published` bit(1) NOT NULL DEFAULT b'1' COMMENT '게시 여부(1=공개)',
  `publish_at` datetime(6) DEFAULT NULL COMMENT '예약 발행 시각 (NULL이면 예약 없음)',
  `pinned` bit(1) NOT NULL DEFAULT b'0' COMMENT '목록 상단 노출 여부',
  `pin_order` int DEFAULT NULL COMMENT '상단 노출 순서(1부터, 작을수록 위, 최대 5개)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_site_notice_created_at` (`created_at`),
  KEY `idx_site_notice_pinned` (`pinned`, `pin_order`),
  KEY `fk_site_notice_author` (`author_id`),
  CONSTRAINT `fk_site_notice_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='홈페이지 관리 공지사항';

CREATE TABLE IF NOT EXISTS `community_members` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '가입 PK',
  `fan_id` bigint NOT NULL COMMENT '가입 팬(users.id)',
  `artist_id` bigint NOT NULL COMMENT '커뮤니티 아티스트(users.id)',
  `joined_at` datetime(6) NOT NULL COMMENT '가입 시각(디데이 기준)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_member` (`fan_id`, `artist_id`),
  KEY `fk_cm_artist` (`artist_id`),
  CONSTRAINT `fk_cm_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_cm_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬의 아티스트 커뮤니티 가입';

CREATE TABLE IF NOT EXISTS `community_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 프로필 PK',
  `community_member_id` bigint NOT NULL COMMENT 'community_members.id (1:1)',
  `nickname` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '해당 커뮤니티 전용 닉네임',
  `bio` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '짧은 소개',
  `avatar_stored_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '아바타 저장 파일명',
  `background_stored_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배경 이미지 저장 파일명',
  `content_hidden` tinyint(1) NOT NULL DEFAULT 0 COMMENT '프로필 콘텐츠 숨기기(1=비공개)',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cp_member` (`community_member_id`),
  CONSTRAINT `fk_cp_member` FOREIGN KEY (`community_member_id`) REFERENCES `community_members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='커뮤니티별 독립 프로필';

CREATE TABLE IF NOT EXISTS `post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '게시글 PK',
  `board_type` enum('ARTIST', 'FAN') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '게시판 구분: ARTIST/FAN',
  `artist_id` bigint DEFAULT NULL COMMENT '커뮤니티 소속 아티스트(users.id)',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '본문',
  `created_at` datetime(6) NOT NULL COMMENT '작성 시각',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '제목',
  `author_id` bigint DEFAULT NULL COMMENT '작성자(users.id)',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '좋아요 수(비정규화 카운트)',
  `hidden_from_artist` tinyint(1) NOT NULL DEFAULT '0' COMMENT '아티스트에게 숨김(신고/가리기)',
  `link_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '첨부 링크 URL',
  PRIMARY KEY (`id`),
  KEY `FK1mpebp1ayl0twrwm7ruiof778` (`author_id`),
  KEY `idx_post_artist_board` (`artist_id`, `board_type`, `created_at`),
  CONSTRAINT `FK1mpebp1ayl0twrwm7ruiof778` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_post_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='커뮤니티 게시글';

CREATE TABLE IF NOT EXISTS `post_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '첨부 PK',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 타입',
  `created_at` datetime(6) NOT NULL COMMENT '업로드 시각',
  `file_size` bigint DEFAULT NULL COMMENT '파일 크기(byte)',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '원본 파일명(표시용)',
  `stored_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `post_id` bigint NOT NULL COMMENT '소속 게시글(post.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgvqlowil11nsrhy82c6qqhcxa` (`stored_name`),
  KEY `FKmof1y73w0oea4caub8rpkhlmi` (`post_id`),
  CONSTRAINT `FKmof1y73w0oea4caub8rpkhlmi` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부파일';

CREATE TABLE IF NOT EXISTS `post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '좋아요 PK',
  `created_at` datetime(6) NOT NULL COMMENT '좋아요 시각',
  `post_id` bigint NOT NULL COMMENT '게시글(post.id)',
  `user_id` bigint NOT NULL COMMENT '좋아요한 회원(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpmmko3h7yonaqhy5gxvnmdeue` (`post_id`, `user_id`),
  KEY `FKijnjmw0imnatadr3agtk0udip` (`user_id`),
  CONSTRAINT `FKijnjmw0imnatadr3agtk0udip` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKj7iy0k7n3d0vkh8o7ibjna884` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 좋아요';

CREATE TABLE IF NOT EXISTS `post_bookmark` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '북마크 PK',
  `created_at` datetime(6) NOT NULL COMMENT '북마크 시각',
  `post_id` bigint NOT NULL COMMENT '게시글(post.id)',
  `user_id` bigint NOT NULL COMMENT '저장한 회원(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_bookmark` (`post_id`, `user_id`),
  KEY `fk_post_bookmark_user` (`user_id`),
  CONSTRAINT `fk_post_bookmark_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `fk_post_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 북마크';

CREATE TABLE IF NOT EXISTS `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '댓글 PK',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '댓글 내용',
  `created_at` datetime(6) NOT NULL COMMENT '작성 시각',
  `author_id` bigint NOT NULL COMMENT '작성자(users.id)',
  `post_id` bigint NOT NULL COMMENT '원글(post.id)',
  PRIMARY KEY (`id`),
  KEY `FKir20vhrx08eh4itgpbfxip0s1` (`author_id`),
  KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
  CONSTRAINT `FKir20vhrx08eh4itgpbfxip0s1` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 댓글';

CREATE TABLE IF NOT EXISTS `comment_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE', 'ETC', 'SEXUAL', 'SPAM') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `status` enum('PENDING', 'DISMISSED', 'RESOLVED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (대기/기각/처리완료)',
  `resolved_at` datetime(6) DEFAULT NULL COMMENT '신고 처리 시각 (대기중이면 NULL)',
  `comment_id` bigint NOT NULL COMMENT '신고 대상 댓글(comment.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7a7j27uutr1ew9m87et35eily` (`comment_id`, `reporter_id`),
  KEY `FKn7ue556scerw6fa5epexg2g4j` (`reporter_id`),
  CONSTRAINT `FK8ugevhla12t9n0uw4o0rkvnth` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
  CONSTRAINT `FKn7ue556scerw6fa5epexg2g4j` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 신고';

CREATE TABLE IF NOT EXISTS `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE', 'ETC', 'SEXUAL', 'SPAM') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `status` enum('PENDING', 'DISMISSED', 'RESOLVED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (대기/기각/처리완료)',
  `resolved_at` datetime(6) DEFAULT NULL COMMENT '신고 처리 시각 (대기중이면 NULL)',
  `post_id` bigint NOT NULL COMMENT '신고 대상 게시글(post.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK59baeft7frgypa05ajup9wrij` (`post_id`, `reporter_id`),
  KEY `FKqbhdxqd3ly7fkhly5nrl2j93k` (`reporter_id`),
  CONSTRAINT `FKnuqod1y014fp5bmqjeoffcgqy` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKqbhdxqd3ly7fkhly5nrl2j93k` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 신고';

CREATE TABLE IF NOT EXISTS `board_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '미디어 게시글 PK',
  `group_id` bigint NOT NULL COMMENT '그룹(artist_groups.id)',
  `uploader_id` bigint NOT NULL COMMENT '업로더(users.id, 주로 소속사)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '제목',
  `content` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '본문/캡션',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '삭제(soft delete) 시각',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '좋아요 수(비정규화 카운트)',
  PRIMARY KEY (`id`),
  KEY `idx_bm_group` (`group_id`, `created_at`),
  KEY `idx_bm_uploader` (`uploader_id`),
  CONSTRAINT `fk_bm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
  CONSTRAINT `fk_bm_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹별 미디어 게시판';

CREATE TABLE IF NOT EXISTS `board_media_files` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '미디어 파일 PK',
  `board_id` bigint NOT NULL COMMENT '미디어 게시글(board_media.id)',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '원본 파일명(표시용)',
  `stored_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 타입',
  `media_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '미디어 종류: IMAGE/VIDEO',
  `file_size` bigint DEFAULT NULL COMMENT '파일 크기(byte)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '표시 순서',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bmf_stored` (`stored_name`),
  KEY `idx_bmf_board` (`board_id`, `sort_order`),
  CONSTRAINT `fk_bmf_board` FOREIGN KEY (`board_id`) REFERENCES `board_media` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_bmf_media_type` CHECK (`media_type` IN (_utf8mb4'IMAGE', _utf8mb4'VIDEO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='미디어 게시글 첨부 파일';

CREATE TABLE IF NOT EXISTS `board_media_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '좋아요 PK',
  `board_id` bigint NOT NULL COMMENT '미디어 게시글(board_media.id)',
  `user_id` bigint NOT NULL COMMENT '좋아요한 회원(users.id)',
  `created_at` datetime(6) NOT NULL COMMENT '좋아요 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_board_media_like` (`board_id`, `user_id`),
  KEY `idx_bml_user` (`user_id`),
  CONSTRAINT `fk_bml_board` FOREIGN KEY (`board_id`) REFERENCES `board_media` (`id`),
  CONSTRAINT `fk_bml_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='미디어 게시글 좋아요';

CREATE TABLE IF NOT EXISTS `shop_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '굿즈 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `name` varchar(200) NOT NULL COMMENT '상품명',
  `description` text COMMENT '상세 설명(마크다운)',
  `price` int NOT NULL COMMENT '가격(원)',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 저장 파일명',
  `official_url` varchar(500) DEFAULT NULL COMMENT '공식 판매처 URL',
  `status` varchar(20) NOT NULL COMMENT 'ON_SALE / HIDDEN (공개여부, 품절과 무관)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '유저 샵 노출 순서(오름차순)',
  `membership_only` tinyint(1) NOT NULL DEFAULT 0 COMMENT '멤버십 전용 여부 (1=전용)',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '소프트 삭제 시각',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_artist_sort` (`artist_id`, `deleted_at`, `sort_order`, `id`),
  KEY `idx_shop_goods_status` (`status`, `deleted_at`, `sort_order`, `id`),
  CONSTRAINT `fk_shop_goods_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='에이전시 등록 굿즈';

CREATE TABLE IF NOT EXISTS `shop_goods_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL COMMENT 'CLOTHING/SHOES/BAG/ACCESSORY/OTHER',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_category` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_category_goods` FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈 카테고리';

CREATE TABLE IF NOT EXISTS `shop_goods_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL,
  `option_key` varchar(30) NOT NULL COMMENT 'WIDTH, HEIGHT, DEPTH, NOTE',
  `option_value` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_goods_option_goods` (`goods_id`, `category`),
  CONSTRAINT `fk_shop_goods_option_goods` FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈 옵션(치수·메모, 재고 없음)';

CREATE TABLE IF NOT EXISTS `shop_goods_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `goods_id` bigint NOT NULL,
  `option_key` varchar(30) NOT NULL COMMENT 'SIZE / SHOE_MM / DEFAULT',
  `option_value` varchar(50) NOT NULL DEFAULT '',
  `stock_quantity` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_variant` (`goods_id`, `option_key`, `option_value`),
  KEY `idx_shop_goods_variant_goods` (`goods_id`),
  CONSTRAINT `fk_shop_goods_variant_goods` FOREIGN KEY (`goods_id`) REFERENCES `shop_goods` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈 옵션별 재고(Variant)';

CREATE TABLE IF NOT EXISTS `shop_cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '장바구니 항목 PK',
  `user_id` bigint NOT NULL COMMENT '회원(users.id)',
  `product_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '목업 카탈로그 상품 ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '수량',
  `unit_price` int NOT NULL COMMENT '담을 당시 단가(원)',
  `created_at` datetime(6) NOT NULL COMMENT '담은 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_cart_user_product` (`user_id`, `product_id`),
  KEY `idx_shop_cart_user` (`user_id`, `updated_at`),
  CONSTRAINT `fk_shop_cart_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='굿즈샵 장바구니';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '메시지 PK',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '메시지 본문',
  `created_at` datetime(6) NOT NULL COMMENT '전송 시각',
  `artist_id` bigint NOT NULL COMMENT '대화방 아티스트(users.id)',
  `fan_id` bigint DEFAULT NULL COMMENT '대화방 팬(users.id, 방 식별)',
  `sender_id` bigint NOT NULL COMMENT '실제 발신자(users.id)',
  `visible_to_artist` tinyint(1) NOT NULL DEFAULT '1' COMMENT '아티스트 화면 노출 여부(CHAT-02 비대칭 수신, 전송 시점에 확정)',
  PRIMARY KEY (`id`),
  KEY `FKckmqpdmndn0mcp8i1bhlhpwki` (`artist_id`),
  KEY `FKn3161qsj1g6xx74stn3ak14nf` (`fan_id`),
  KEY `FK5f82aoyy0jiwpj08qapfrxbh6` (`sender_id`),
  CONSTRAINT `FK5f82aoyy0jiwpj08qapfrxbh6` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKckmqpdmndn0mcp8i1bhlhpwki` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKn3161qsj1g6xx74stn3ak14nf` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬–아티스트 채팅 메시지';

CREATE TABLE IF NOT EXISTS `chat_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '쿼터 PK',
  `charged_date` date NOT NULL COMMENT '쿼터 충전(기준)일',
  `remaining_count` int NOT NULL COMMENT '남은 전송 횟수',
  `artist_id` bigint NOT NULL COMMENT '대상 아티스트(users.id)',
  `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK24ma26kvjdg8f06vyvylpor9v` (`fan_id`, `artist_id`),
  KEY `FKryrpa4l7agt3qkw1dwdwkm4o7` (`artist_id`),
  CONSTRAINT `FK4ayjraxy8git4xiit6p3uht2j` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKryrpa4l7agt3qkw1dwdwkm4o7` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채팅 전송 횟수 쿼터';

CREATE TABLE IF NOT EXISTS `fan_badge_ownership` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '배지 보유 PK',
  `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id) - 커뮤니티 단위',
  `badge_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 코드(fan_badge.badge_code)',
  `badge_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '수여 시점 배지명 스냅샷',
  `badge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배지 유형: BASIC/SPECIAL',
  `awarded_at` datetime(6) NOT NULL COMMENT '수여 시각',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '회수 시각(NULL=유효)',
  `awarded_by` bigint DEFAULT NULL COMMENT '수여자(users.id), NULL=시스템 자동',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_badge_ownership` (`fan_id`, `artist_id`, `badge_code`),
  KEY `idx_fan_badge_count` (`fan_id`, `artist_id`, `badge_type`, `revoked_at`),
  KEY `idx_fan_badge_awarded_by` (`awarded_by`),
  KEY `fk_fan_badge_artist` (`artist_id`),
  CONSTRAINT `fk_fan_badge_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_awarded_by` FOREIGN KEY (`awarded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_badge_period` CHECK ((`revoked_at` IS NULL) OR (`revoked_at` >= `awarded_at`)),
  CONSTRAINT `ck_fan_badge_type` CHECK (`badge_type` IN (_utf8mb4'BASIC', _utf8mb4'SPECIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 배지 보유/회수';

CREATE TABLE IF NOT EXISTS `email_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '이메일 인증 PK',
  `user_id` bigint DEFAULT NULL COMMENT '프로젝트 인증 회원(users.id), 회원가입 인증은 NULL',
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '인증 대상 이메일',
  `purpose` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '인증 목적: SIGNUP/FAN_PROJECT_CREATE/ADMIN_LOGIN',
  `verification_key` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '인증 요청 식별 UUID',
  `code_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '인증번호 BCrypt 해시',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '인증번호 실패 횟수',
  `expires_at` datetime(6) NOT NULL COMMENT '인증 만료 시각',
  `verified_at` datetime(6) DEFAULT NULL COMMENT '인증 완료 시각',
  `consumed_at` datetime(6) DEFAULT NULL COMMENT '회원가입/프로젝트 등록에 사용된 시각',
  `created_at` datetime(6) NOT NULL COMMENT '인증 요청 생성 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '인증 정보 수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_verification_key` (`verification_key`),
  KEY `idx_email_verification_email` (`email`, `purpose`, `created_at`),
  KEY `idx_email_verification_user` (`user_id`, `purpose`, `created_at`),
  CONSTRAINT `fk_email_verification_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_email_verification_attempt_count` CHECK (`attempt_count` BETWEEN 0 AND 5),
  CONSTRAINT `ck_email_verification_consumed` CHECK ((`consumed_at` IS NULL) OR (`verified_at` IS NOT NULL)),
  CONSTRAINT `ck_email_verification_expiration` CHECK (`expires_at` > `created_at`),
    CONSTRAINT `ck_email_verification_purpose` CHECK (`purpose` IN (_utf8mb4'SIGNUP', _utf8mb4'FAN_PROJECT_CREATE', _utf8mb4'ADMIN_LOGIN', _utf8mb4'AGENCY_ACTIVATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원가입, 팬 프로젝트 및 관리자 로그인 이메일 인증';

CREATE TABLE IF NOT EXISTS `fan_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '프로젝트 PK',
  `artist_id` bigint NOT NULL COMMENT '대상 아티스트 커뮤니티(users.id)',
  `creator_id` bigint NOT NULL COMMENT '개설 신청자(users.id)',
  `title` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '프로젝트 제목',
  `event_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이벤트 유형: BIRTHDAY_CAFE/BILLBOARD/CONCERT/ETC',
  `goal_amount` bigint NOT NULL COMMENT '목표 모금액(원, 1만~300만)',
  `funding_start_at` datetime(6) NOT NULL COMMENT '모금 시작 시각',
  `funding_end_at` datetime(6) NOT NULL COMMENT '모금 종료 시각',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '프로젝트 소개',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '상태: 승인대기/승인/반려/모금중 등',
  `special_badge_count_at_apply` int NOT NULL COMMENT '신청 시점 SPECIAL 배지 수(자격 스냅샷)',
  `basic_badge_count_at_apply` int NOT NULL COMMENT '신청 시점 BASIC 배지 수(자격 스냅샷)',
  `eligibility_rule_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SPECIAL_1_AND_BASIC_5' COMMENT '개설 자격 규칙 코드',
  `identity_verification_method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EMAIL' COMMENT '프로젝트 등록 인증 방식(EMAIL)',
  `identity_verified_at` datetime(6) NOT NULL COMMENT '본인인증 완료 시각',
  `reviewed_by` bigint DEFAULT NULL COMMENT '검토자(users.id)',
  `reviewed_at` datetime(6) DEFAULT NULL COMMENT '검토 시각',
  `rejection_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '반려 사유(REJECTED 시)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '삭제(soft delete) 시각',
  PRIMARY KEY (`id`),
  KEY `idx_fan_project_artist_status` (`artist_id`, `status`, `funding_start_at`),
  KEY `idx_fan_project_creator` (`creator_id`, `created_at`),
  KEY `idx_fan_project_funding_end` (`status`, `funding_end_at`),
  KEY `idx_fan_project_reviewer` (`reviewed_by`, `reviewed_at`),
  CONSTRAINT `fk_fan_project_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_project_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_badge_counts` CHECK ((`special_badge_count_at_apply` >= 0) AND (`basic_badge_count_at_apply` >= 0)),
  CONSTRAINT `ck_fan_project_creation_eligibility` CHECK ((`special_badge_count_at_apply` >= 1) AND (`basic_badge_count_at_apply` >= 5)),
  CONSTRAINT `ck_fan_project_event_type` CHECK (`event_type` IN (_utf8mb4'BIRTHDAY_CAFE', _utf8mb4'BILLBOARD', _utf8mb4'CONCERT', _utf8mb4'ETC')),
  CONSTRAINT `ck_fan_project_funding_period` CHECK (`funding_end_at` > `funding_start_at`),
  CONSTRAINT `ck_fan_project_goal_amount` CHECK (`goal_amount` BETWEEN 10000 AND 3000000),
  CONSTRAINT `ck_fan_project_identity_method` CHECK (`identity_verification_method` = _utf8mb4'EMAIL'),
  CONSTRAINT `ck_fan_project_rejection_reason` CHECK ((`status` <> _utf8mb4'REJECTED') OR (`rejection_reason` IS NOT NULL)),
  CONSTRAINT `ck_fan_project_review` CHECK (
    ((`status` = _utf8mb4'PENDING_APPROVAL') AND (`reviewed_by` IS NULL) AND (`reviewed_at` IS NULL))
    OR (`status` NOT IN (_utf8mb4'PENDING_APPROVAL', _utf8mb4'APPROVED', _utf8mb4'REJECTED'))
    OR ((`status` IN (_utf8mb4'APPROVED', _utf8mb4'REJECTED')) AND (`reviewed_by` IS NOT NULL) AND (`reviewed_at` IS NOT NULL))
  ),
  CONSTRAINT `ck_fan_project_status` CHECK (`status` IN (
    _utf8mb4'PENDING_APPROVAL', _utf8mb4'APPROVED', _utf8mb4'REJECTED',
    _utf8mb4'FUNDING', _utf8mb4'FUNDING_CLOSED', _utf8mb4'COMPLETED', _utf8mb4'CANCELLED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트(개설·승인·모금)';

CREATE TABLE IF NOT EXISTS `fan_project_cover_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '커버 이미지 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id) 1:1',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '원본 파일명',
  `stored_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME(image/*)',
  `file_size` bigint NOT NULL COMMENT '파일 크기(byte)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_cover_project` (`project_id`),
  UNIQUE KEY `uk_fan_project_cover_stored` (`stored_name`),
  CONSTRAINT `fk_fan_project_cover_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_fan_project_cover_size` CHECK (`file_size` > 0),
  CONSTRAINT `ck_fan_project_cover_type` CHECK (`content_type` LIKE _utf8mb4'image/%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 커버 이미지';

CREATE TABLE IF NOT EXISTS `fan_project_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '후원/결제 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id)',
  `contributor_id` bigint NOT NULL COMMENT '후원자(users.id)',
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문번호',
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '결제 멱등 키(중복요청 방지)',
  `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT '결제 제공자',
  `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '제공자 거래 ID',
  `amount` bigint NOT NULL COMMENT '결제 금액(원)',
  `virtual_bank_code` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '가상계좌 은행 코드(토스 코드)',
  `virtual_account_number` varbinary(255) DEFAULT NULL COMMENT '가상계좌 번호(현재 변환 저장, 추후 암호화)',
  `due_date` datetime(6) DEFAULT NULL COMMENT '가상계좌 입금기한',
  `deposit_secret` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '입금 웹훅 검증값(외부 노출 금지)',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '0' COMMENT '닉네임 비공개 참여 여부: 0/1',
  `refund_policy_agreed_at` datetime(6) NOT NULL COMMENT '환불 규정 동의 시각',
  `refund_amount` bigint NOT NULL DEFAULT '0' COMMENT '환불 금액(원)',
  `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY' COMMENT '결제 상태: READY/WAITING_FOR_DEPOSIT/PAID/FAILED/EXPIRED/CANCELLED/REFUND_REQUESTED/REFUNDED',
  `paid_at` datetime(6) DEFAULT NULL COMMENT '결제 완료 시각',
  `cancelled_at` datetime(6) DEFAULT NULL COMMENT '취소 시각',
  `refunded_at` datetime(6) DEFAULT NULL COMMENT '환불 시각',
  `refund_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '환불 사유',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_contribution_order` (`order_no`),
  UNIQUE KEY `uk_fan_project_contribution_idempotency` (`idempotency_key`),
  KEY `idx_fan_project_contribution_total` (`project_id`, `payment_status`),
  KEY `idx_fan_project_contributor_history` (`contributor_id`, `created_at`),
  CONSTRAINT `fk_fan_project_contribution_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`),
  CONSTRAINT `fk_fan_project_contribution_user` FOREIGN KEY (`contributor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_contribution_amount` CHECK (`amount` > 0),
  CONSTRAINT `ck_fan_project_contribution_anonymous` CHECK (`is_anonymous` IN (0, 1)),
  CONSTRAINT `ck_fan_project_contribution_refund` CHECK ((`refund_amount` >= 0) AND (`refund_amount` <= `amount`)),
  CONSTRAINT `ck_fan_project_contribution_status` CHECK (`payment_status` IN (
    _utf8mb4'READY', _utf8mb4'WAITING_FOR_DEPOSIT', _utf8mb4'PAID', _utf8mb4'FAILED',
    _utf8mb4'EXPIRED', _utf8mb4'CANCELLED', _utf8mb4'REFUND_REQUESTED', _utf8mb4'REFUNDED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 후원/결제';

CREATE TABLE IF NOT EXISTS `fan_project_settlement_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '정산 계좌 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id) 1:1',
  `bank_code` char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '은행코드(3자리)',
  `account_number_enc` varbinary(512) NOT NULL COMMENT '계좌번호(암호화)',
  `account_number_hmac` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '계좌번호 HMAC(검색용)',
  `account_number_last4` char(4) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '계좌 끝 4자리(표시용)',
  `verification_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNVERIFIED' COMMENT '계좌 검증: UNVERIFIED/VERIFIED/FAILED',
  `verified_at` datetime(6) DEFAULT NULL COMMENT '검증 완료 시각',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_project_settlement_project` (`project_id`),
  KEY `idx_fan_project_settlement_hmac` (`account_number_hmac`),
  CONSTRAINT `fk_fan_project_settlement_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_fan_project_account_last4` CHECK (regexp_like(`account_number_last4`, _utf8mb4'^[0-9]{4}$')),
  CONSTRAINT `ck_fan_project_account_verification` CHECK (`verification_status` IN (_utf8mb4'UNVERIFIED', _utf8mb4'VERIFIED', _utf8mb4'FAILED')),
  CONSTRAINT `ck_fan_project_bank_code` CHECK (`bank_code` IN (
    _utf8mb4'004', _utf8mb4'088', _utf8mb4'011', _utf8mb4'090', _utf8mb4'020',
    _utf8mb4'081', _utf8mb4'092', _utf8mb4'032', _utf8mb4'031'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 정산 계좌';

CREATE TABLE IF NOT EXISTS `fan_project_fraud_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '사기조회 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id)',
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조회 대상: ACCOUNT',
  `target_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 식별 해시',
  `bank_code` char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '은행코드(계좌 조회 시)',
  `provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'THECHEAT' COMMENT '조회 제공자',
  `provider_result_code` int DEFAULT NULL COMMENT '제공자 결과 코드',
  `result_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '결과: PENDING/CLEAR/CAUTION/ERROR',
  `caution_yn` char(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주의 여부 Y/N',
  `search_window_start_at` datetime(6) DEFAULT NULL COMMENT '조회 기간 시작',
  `search_window_end_at` datetime(6) DEFAULT NULL COMMENT '조회 기간 종료',
  `checked_at` datetime(6) DEFAULT NULL COMMENT '조회 수행 시각',
  `requested_by` bigint DEFAULT NULL COMMENT '요청자(users.id)',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_fan_project_fraud_latest` (`project_id`, `target_type`, `checked_at`),
  KEY `idx_fan_project_fraud_target` (`target_fingerprint`, `checked_at`),
  KEY `idx_fan_project_fraud_requester` (`requested_by`),
  CONSTRAINT `fk_fan_project_fraud_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_fan_project_fraud_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_project_fraud_bank_code` CHECK ((`bank_code` IS NULL) OR regexp_like(`bank_code`, _utf8mb4'^[0-9]{3}$')),
  CONSTRAINT `ck_fan_project_fraud_caution` CHECK ((`caution_yn` IS NULL) OR (`caution_yn` IN (_utf8mb4'Y', _utf8mb4'N'))),
  CONSTRAINT `ck_fan_project_fraud_status` CHECK (`result_status` IN (_utf8mb4'PENDING', _utf8mb4'CLEAR', _utf8mb4'CAUTION', _utf8mb4'ERROR')),
  CONSTRAINT `ck_fan_project_fraud_target` CHECK (`target_type` = _utf8mb4'ACCOUNT'),
  CONSTRAINT `ck_fan_project_fraud_window` CHECK (
    (`search_window_start_at` IS NULL) OR (`search_window_end_at` IS NULL)
    OR (`search_window_end_at` >= `search_window_start_at`)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 사기조회';

CREATE TABLE IF NOT EXISTS `group_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '그룹 일정 PK',
  `group_id` bigint NOT NULL COMMENT '그룹(artist_groups.id)',
  `created_by` bigint NOT NULL COMMENT '등록자(users.id)',
  `event_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '유형: BROADCAST/LIVE/TICKET_OPEN/CONCERT',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '일정 제목',
  `place` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '장소',
  `start_at` datetime(6) NOT NULL COMMENT '시작 시각',
  `end_at` datetime(6) DEFAULT NULL COMMENT '종료 시각',
  `ticket_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '티켓 링크',
  `ticket_image_stored_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '티켓 이미지 저장명',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '삭제(soft delete) 시각',
  PRIMARY KEY (`id`),
  KEY `idx_gs_group_start` (`group_id`, `start_at`),
  KEY `fk_gs_creator` (`created_by`),
  CONSTRAINT `fk_gs_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_gs_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
  CONSTRAINT `ck_gs_event_type` CHECK (`event_type` IN (_utf8mb4'BROADCAST', _utf8mb4'LIVE', _utf8mb4'TICKET_OPEN', _utf8mb4'CONCERT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹 스케줄/캘린더';

CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '알림 PK',
  `recipient_id` bigint NOT NULL COMMENT '수신자(users.id)',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 유형(공지/댓글/채팅 등)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 제목',
  `message` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 본문',
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '출처 엔티티 종류(다형)',
  `source_id` bigint DEFAULT NULL COMMENT '출처 엔티티 ID',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '읽음 여부(0=미읽음)',
  `created_at` datetime(6) NOT NULL COMMENT '발송 시각',
  PRIMARY KEY (`id`),
  KEY `idx_noti_recipient` (`recipient_id`, `is_read`, `created_at`),
  CONSTRAINT `fk_noti_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 알림';

CREATE TABLE IF NOT EXISTS `notification_setting` (
  `user_id` bigint NOT NULL COMMENT '회원(users.id)',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 유형',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '해당 유형 수신 on/off',
  PRIMARY KEY (`user_id`, `type`),
  CONSTRAINT `fk_ns_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림 유형별 수신 설정';

-- ============================================================
-- [2] 기존 테이블에 없는 컬럼 추가
-- ============================================================

-- agencies
CALL `wp_sync_add_column`('agencies', 'name', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''소속사명''', 0);
CALL `wp_sync_add_column`('agencies', 'business_no', 'varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''사업자등록번호''', 0);
CALL `wp_sync_add_column`('agencies', 'ceo_name', 'varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''대표자명''', 0);
CALL `wp_sync_add_column`('agencies', 'status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''ACTIVE'' COMMENT ''소속사 상태: ACTIVE/SUSPENDED''', 0);
CALL `wp_sync_add_column`('agencies', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('agencies', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- users
CALL `wp_sync_add_column`('users', 'username', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''로그인 아이디''', 0);
CALL `wp_sync_add_column`('users', 'password', 'varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''비밀번호(BCrypt 해시, 소셜 전용 가입자는 NULL)''', 0);
CALL `wp_sync_add_column`('users', 'role', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''역할: FAN/ARTIST/AGENCY/ADMIN''', 0);
CALL `wp_sync_add_column`('users', 'status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''ACTIVE'' COMMENT ''계정 상태: ACTIVE/DORMANT/SUSPENDED/WITHDRAWN''', 0);
CALL `wp_sync_add_column`('users', 'agency_id', 'bigint DEFAULT NULL COMMENT ''소속사(agencies.id). 주로 ARTIST 계정에 사용, 없으면 NULL''', 0);
CALL `wp_sync_add_column`('users', 'real_name', 'varbinary(255) NOT NULL COMMENT ''실명(암호화 저장)''', 0);
CALL `wp_sync_add_column`('users', 'nickname', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''플랫폼 공통 닉네임(미입력 시 자동생성)''', 0);
CALL `wp_sync_add_column`('users', 'email', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''이메일(로그인·알림 수신)''', 0);
CALL `wp_sync_add_column`('users', 'phone', 'varbinary(255) DEFAULT NULL COMMENT ''휴대폰번호(암호화 저장)''', 0);
CALL `wp_sync_add_column`('users', 'phone_hash', 'char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''휴대폰 SHA-256 해시(검색·중복확인)''', 0);
CALL `wp_sync_add_column`('users', 'birth_date', 'date DEFAULT NULL COMMENT ''생년월일''', 0);
CALL `wp_sync_add_column`('users', 'gender', 'varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''성별: MALE/FEMALE/OTHER''', 0);
CALL `wp_sync_add_column`('users', 'zipcode', 'varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''우편번호(배송·지역알림 확장용)''', 0);
CALL `wp_sync_add_column`('users', 'address1', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''기본 주소''', 0);
CALL `wp_sync_add_column`('users', 'address2', 'varbinary(512) DEFAULT NULL COMMENT ''상세주소(암호화 저장)''', 0);
CALL `wp_sync_add_column`('users', 'email_verified_at', 'datetime(6) DEFAULT NULL COMMENT ''이메일 인증 완료 시각(NULL=미인증)''', 0);
CALL `wp_sync_add_column`('users', 'last_login_at', 'datetime(6) DEFAULT NULL COMMENT ''최종 로그인 시각''', 0);
CALL `wp_sync_add_column`('users', 'dormant_notice_sent_at', 'datetime(6) DEFAULT NULL COMMENT ''휴면 전환 30일 전 사전 안내 메일 발송 시각''', 0);
CALL `wp_sync_add_column`('users', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''가입 시각''', 1);
CALL `wp_sync_add_column`('users', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''정보 수정 시각''', 1);
CALL `wp_sync_add_column`('users', 'deleted_at', 'datetime(6) DEFAULT NULL COMMENT ''탈퇴(soft delete) 시각''', 0);
CALL `wp_sync_add_column`('users', 'provider', 'varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''연동된 소셜 provider: GOOGLE/KAKAO/LINE (연동 없으면 NULL)''', 0);
CALL `wp_sync_add_column`('users', 'provider_id', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''소셜 플랫폼 고유 ID (LOCAL 가입자는 NULL)''', 0);
CALL `wp_sync_add_column`('users', 'marketing_consent', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''광고성 정보 수신 동의 (회원가입 체크박스와 공유)''', 0);
CALL `wp_sync_add_column`('users', 'community_activity_email_enabled', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''가입한 아티스트 활동(게시글/공지/라이브) 이메일 수신 여부''', 0);
CALL `wp_sync_add_column`('users', 'night_notification_allowed', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''오후 9시~오전 8시(KST) 알림 수신 여부''', 0);
CALL `wp_sync_add_column`('users', 'preferred_language', 'varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''KO'' COMMENT ''기본 서비스 언어 (KO/JA/EN) - 게시글/댓글 AI 번역 대상 언어로도 재사용''', 0);

-- filter_keyword
CALL `wp_sync_add_column`('filter_keyword', 'keyword', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''필터링 대상 문자열''', 0);

-- fan_badge
CALL `wp_sync_add_column`('fan_badge', 'badge_code', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''배지 코드(전체 고유)''', 0);
CALL `wp_sync_add_column`('fan_badge', 'badge_name', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''배지 표시명''', 0);
CALL `wp_sync_add_column`('fan_badge', 'badge_type', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''배지 유형: BASIC/SPECIAL''', 0);
CALL `wp_sync_add_column`('fan_badge', 'icon', 'varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''🏅'' COMMENT ''표시용 이모지''', 0);
CALL `wp_sync_add_column`('fan_badge', 'image_url', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''배지 이미지 경로. 있으면 이미지, 없으면 icon 이모지로 표시''', 0);
CALL `wp_sync_add_column`('fan_badge', 'description', 'varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''획득 조건 안내 문구''', 0);
CALL `wp_sync_add_column`('fan_badge', 'sort_order', 'int NOT NULL DEFAULT 0 COMMENT ''유형 내 표시 순서(작을수록 앞)''', 0);
CALL `wp_sync_add_column`('fan_badge', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 0);

-- admin_profiles
CALL `wp_sync_add_column`('admin_profiles', 'user_id', 'bigint NOT NULL COMMENT ''users.id (ADMIN 1:1)''', 0);
CALL `wp_sync_add_column`('admin_profiles', 'admin_level', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''STAFF'' COMMENT ''관리 등급: SUPER/STAFF''', 0);
CALL `wp_sync_add_column`('admin_profiles', 'department', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''소속 부서''', 0);
CALL `wp_sync_add_column`('admin_profiles', 'employee_no', 'varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''사번''', 0);

-- admin_action_logs
CALL `wp_sync_add_column`('admin_action_logs', 'actor_id', 'bigint NOT NULL COMMENT ''조치 수행 운영자(users.id)''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'action', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''조치 유형(제재/승인 등)''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'target_type', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''대상 종류(USER/GROUP/AGENCY 등)''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'target_id', 'bigint NOT NULL COMMENT ''대상 ID(다형 참조)''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'reason', 'text COLLATE utf8mb4_unicode_ci COMMENT ''조치 사유''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'ip_address', 'varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''요청 IP(IPv6 포함)''', 0);
CALL `wp_sync_add_column`('admin_action_logs', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''기록 시각''', 1);

-- agency_profiles
CALL `wp_sync_add_column`('agency_profiles', 'user_id', 'bigint NOT NULL COMMENT ''users.id (AGENCY 1:1)''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'agency_id', 'bigint NOT NULL COMMENT ''소속 소속사(agencies.id)''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'department', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''담당 부서''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'position', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''직책''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'is_owner', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''소속사 대표 계정 여부''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'approved_by', 'bigint DEFAULT NULL COMMENT ''승인 운영자(users.id)''', 0);
CALL `wp_sync_add_column`('agency_profiles', 'approved_at', 'datetime(6) DEFAULT NULL COMMENT ''승인 시각(NULL=미승인)''', 0);

-- artist_profiles
CALL `wp_sync_add_column`('artist_profiles', 'user_id', 'bigint NOT NULL COMMENT ''users.id (ARTIST 1:1)''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'agency_id', 'bigint NOT NULL COMMENT ''소속 소속사(agencies.id)''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'stage_name', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''활동명(스테이지명)''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'debut_date', 'date DEFAULT NULL COMMENT ''개인 데뷔일''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'position', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''포지션(보컬/래퍼 등)''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'bio', 'text COLLATE utf8mb4_unicode_ci COMMENT ''아티스트 소개글''', 0);
CALL `wp_sync_add_column`('artist_profiles', 'profile_img', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''프로필 이미지 URL/경로''', 0);

-- artist_groups
CALL `wp_sync_add_column`('artist_groups', 'agency_id', 'bigint NOT NULL COMMENT ''소속 소속사(agencies.id)''', 0);
CALL `wp_sync_add_column`('artist_groups', 'name', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''그룹명(한글)''', 0);
CALL `wp_sync_add_column`('artist_groups', 'name_en', 'varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''그룹명(영문)''', 0);
CALL `wp_sync_add_column`('artist_groups', 'fandom_name', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''팬덤명''', 0);
CALL `wp_sync_add_column`('artist_groups', 'debut_date', 'date DEFAULT NULL COMMENT ''그룹 데뷔일''', 0);
CALL `wp_sync_add_column`('artist_groups', 'status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''ACTIVE'' COMMENT ''상태: ACTIVE/HIATUS/DISBANDED''', 0);
CALL `wp_sync_add_column`('artist_groups', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('artist_groups', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- artist_profile
CALL `wp_sync_add_column`('artist_profile', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('artist_profile', 'intro', 'text COLLATE utf8mb4_unicode_ci COMMENT ''커뮤니티 포털 소개문''', 0);
CALL `wp_sync_add_column`('artist_profile', 'header_image_url', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''헤더/배너 이미지 URL''', 0);
CALL `wp_sync_add_column`('artist_profile', 'logo_image_url', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''로고/아이콘 이미지 URL''', 0);
CALL `wp_sync_add_column`('artist_profile', 'created_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('artist_profile', 'updated_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''수정 시각''', 1);

-- artist_group_profiles
CALL `wp_sync_add_column`('artist_group_profiles', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'gender', 'varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''그룹/아티스트 성별(탐색 필터)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'member_count', 'int DEFAULT NULL COMMENT ''구성 인원 수(탐색 필터)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'nationality', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''국적(탐색 필터)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'category', 'varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''직업/카테고리(탐색 필터)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'debut_date', 'date DEFAULT NULL COMMENT ''데뷔일(탐색 필터)''', 0);
CALL `wp_sync_add_column`('artist_group_profiles', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- group_members
CALL `wp_sync_add_column`('group_members', 'group_id', 'bigint NOT NULL COMMENT ''그룹(artist_groups.id)''', 0);
CALL `wp_sync_add_column`('group_members', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(artist_profiles.user_id)''', 0);
CALL `wp_sync_add_column`('group_members', 'is_leader', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''리더 여부''', 0);
CALL `wp_sync_add_column`('group_members', 'joined_at', 'date NOT NULL DEFAULT (CURRENT_DATE) COMMENT ''합류일''', 1);
CALL `wp_sync_add_column`('group_members', 'left_at', 'date DEFAULT NULL COMMENT ''탈퇴일(NULL=활동중)''', 0);

-- user_follows
CALL `wp_sync_add_column`('user_follows', 'follower_id', 'bigint NOT NULL COMMENT ''팔로우 하는 사람(users.id)''', 0);
CALL `wp_sync_add_column`('user_follows', 'following_id', 'bigint NOT NULL COMMENT ''팔로우 당하는 사람(users.id)''', 0);
CALL `wp_sync_add_column`('user_follows', 'community_id', 'bigint NOT NULL COMMENT ''이 팔로우가 속한 커뮤니티(그 커뮤니티 아티스트의 users.id)''', 0);
CALL `wp_sync_add_column`('user_follows', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''팔로우 시각''', 1);

-- membership
CALL `wp_sync_add_column`('membership', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''가입 시각''', 1);
CALL `wp_sync_add_column`('membership', 'expires_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''만료 시각(만료 시 DM 제한)''', 1);
CALL `wp_sync_add_column`('membership', 'artist_id', 'bigint NOT NULL COMMENT ''대상 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('membership', 'fan_id', 'bigint NOT NULL COMMENT ''가입 팬(users.id)''', 0);

-- membership_period
CALL `wp_sync_add_column`('membership_period', 'fan_id', 'bigint NOT NULL COMMENT ''팬(users.id)''', 0);
CALL `wp_sync_add_column`('membership_period', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('membership_period', 'started_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''이 기간의 가입/갱신 시각''', 1);
CALL `wp_sync_add_column`('membership_period', 'expires_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''이 기간의 만료 시각''', 1);
CALL `wp_sync_add_column`('membership_period', 'streak_count', 'int NOT NULL DEFAULT 1 COMMENT ''연속 몇 번째 기간인지(1=첫 가입)''', 0);
CALL `wp_sync_add_column`('membership_period', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);

-- artist_block
CALL `wp_sync_add_column`('artist_block', 'artist_id', 'bigint NOT NULL COMMENT ''차단한 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('artist_block', 'blocked_user_id', 'bigint NOT NULL COMMENT ''차단된 유저(users.id)''', 0);
CALL `wp_sync_add_column`('artist_block', 'reason', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''차단 사유''', 0);
CALL `wp_sync_add_column`('artist_block', 'created_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''차단 시각''', 1);

-- artist_schedule
CALL `wp_sync_add_column`('artist_schedule', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'category', 'varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT ''OTHER'' COMMENT ''스케줄 카테고리(TV_BROADCAST/YOUTUBE/CONCERT/RADIO/AWARDS/PHOTO_MAGAZINE/BIRTHDAY/OTHER)''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''일정 제목''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'description', 'text COLLATE utf8mb4_unicode_ci COMMENT ''일정 상세 설명''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'location', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''장소''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'ticket_url', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''티켓 URL''', 0);
CALL `wp_sync_add_column`('artist_schedule', 'schedule_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''일정 일시''', 1);
CALL `wp_sync_add_column`('artist_schedule', 'created_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('artist_schedule', 'updated_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''수정 시각''', 1);

-- artist_attendance
CALL `wp_sync_add_column`('artist_attendance', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('artist_attendance', 'visit_date', 'date NOT NULL DEFAULT (CURRENT_DATE) COMMENT ''홈페이지 방문일''', 1);
CALL `wp_sync_add_column`('artist_attendance', 'paw_color', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''고양이 발바닥 색상(hex)''', 0);
CALL `wp_sync_add_column`('artist_attendance', 'created_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''등록 시각''', 1);

-- portal_notice
CALL `wp_sync_add_column`('portal_notice', 'artist_id', 'bigint NOT NULL COMMENT ''커뮤니티 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('portal_notice', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''공지 제목''', 0);
CALL `wp_sync_add_column`('portal_notice', 'content', 'text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''공지 본문''', 0);
CALL `wp_sync_add_column`('portal_notice', 'published', 'bit(1) NOT NULL DEFAULT b''1'' COMMENT ''게시 여부(1=공개)''', 0);
CALL `wp_sync_add_column`('portal_notice', 'pinned', 'bit(1) NOT NULL DEFAULT b''0'' COMMENT ''목록 상단 노출 여부''', 0);
CALL `wp_sync_add_column`('portal_notice', 'pin_order', 'int DEFAULT NULL COMMENT ''상단 노출 순서(1부터, 작을수록 위, 최대 5개)''', 0);
CALL `wp_sync_add_column`('portal_notice', 'created_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('portal_notice', 'updated_at', 'datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''수정 시각''', 1);

-- live_session
CALL `wp_sync_add_column`('live_session', 'artist_id', 'bigint NOT NULL COMMENT ''방송 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('live_session', 'host_id', 'bigint NOT NULL COMMENT ''송출 호스트(아티스트 또는 에이전시 users.id)''', 0);
CALL `wp_sync_add_column`('live_session', 'status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''LIVE / ENDED''', 0);
CALL `wp_sync_add_column`('live_session', 'started_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''방송 시작 시각''', 1);
CALL `wp_sync_add_column`('live_session', 'ended_at', 'datetime(6) DEFAULT NULL COMMENT ''방송 종료 시각''', 0);

-- live_comment
CALL `wp_sync_add_column`('live_comment', 'session_id', 'bigint NOT NULL COMMENT ''live_session.id''', 0);
CALL `wp_sync_add_column`('live_comment', 'author_id', 'bigint NOT NULL COMMENT ''작성자(users.id)''', 0);
CALL `wp_sync_add_column`('live_comment', 'content', 'varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''댓글 본문''', 0);
CALL `wp_sync_add_column`('live_comment', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''작성 시각''', 1);

-- live_comment_report
CALL `wp_sync_add_column`('live_comment_report', 'comment_id', 'bigint NOT NULL COMMENT ''live_comment.id''', 0);
CALL `wp_sync_add_column`('live_comment_report', 'reporter_id', 'bigint NOT NULL COMMENT ''신고자(users.id)''', 0);
CALL `wp_sync_add_column`('live_comment_report', 'reason', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''SPAM / ABUSE / SEXUAL / ETC''', 0);
CALL `wp_sync_add_column`('live_comment_report', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''신고 시각''', 1);

-- site_notice
CALL `wp_sync_add_column`('site_notice', 'author_id', 'bigint NOT NULL COMMENT ''작성 관리자(users.id)''', 0);
CALL `wp_sync_add_column`('site_notice', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''공지 제목''', 0);
CALL `wp_sync_add_column`('site_notice', 'category', 'enum(''GENERAL'', ''EVENT'', ''MAINTENANCE'', ''UPDATE'') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''GENERAL'' COMMENT ''공지 분류 (일반/이벤트/점검/업데이트)''', 0);
CALL `wp_sync_add_column`('site_notice', 'content', 'text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''공지 본문''', 0);
CALL `wp_sync_add_column`('site_notice', 'published', 'bit(1) NOT NULL DEFAULT b''1'' COMMENT ''게시 여부(1=공개)''', 0);
CALL `wp_sync_add_column`('site_notice', 'publish_at', 'datetime(6) DEFAULT NULL COMMENT ''예약 발행 시각 (NULL이면 예약 없음)''', 0);
CALL `wp_sync_add_column`('site_notice', 'pinned', 'bit(1) NOT NULL DEFAULT b''0'' COMMENT ''목록 상단 노출 여부''', 0);
CALL `wp_sync_add_column`('site_notice', 'pin_order', 'int DEFAULT NULL COMMENT ''상단 노출 순서(1부터, 작을수록 위, 최대 5개)''', 0);
CALL `wp_sync_add_column`('site_notice', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('site_notice', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- community_members
CALL `wp_sync_add_column`('community_members', 'fan_id', 'bigint NOT NULL COMMENT ''가입 팬(users.id)''', 0);
CALL `wp_sync_add_column`('community_members', 'artist_id', 'bigint NOT NULL COMMENT ''커뮤니티 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('community_members', 'joined_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''가입 시각(디데이 기준)''', 1);

-- community_profiles
CALL `wp_sync_add_column`('community_profiles', 'community_member_id', 'bigint NOT NULL COMMENT ''community_members.id (1:1)''', 0);
CALL `wp_sync_add_column`('community_profiles', 'nickname', 'varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''해당 커뮤니티 전용 닉네임''', 0);
CALL `wp_sync_add_column`('community_profiles', 'bio', 'varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''짧은 소개''', 0);
CALL `wp_sync_add_column`('community_profiles', 'avatar_stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''아바타 저장 파일명''', 0);
CALL `wp_sync_add_column`('community_profiles', 'background_stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''배경 이미지 저장 파일명''', 0);
CALL `wp_sync_add_column`('community_profiles', 'content_hidden', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''프로필 콘텐츠 숨기기(1=비공개)''', 0);
CALL `wp_sync_add_column`('community_profiles', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''생성 시각''', 1);
CALL `wp_sync_add_column`('community_profiles', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- post
CALL `wp_sync_add_column`('post', 'board_type', 'enum(''ARTIST'', ''FAN'') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''게시판 구분: ARTIST/FAN''', 0);
CALL `wp_sync_add_column`('post', 'artist_id', 'bigint DEFAULT NULL COMMENT ''커뮤니티 소속 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('post', 'content', 'text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''본문''', 0);
CALL `wp_sync_add_column`('post', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''작성 시각''', 1);
CALL `wp_sync_add_column`('post', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''제목''', 0);
CALL `wp_sync_add_column`('post', 'author_id', 'bigint DEFAULT NULL COMMENT ''작성자(users.id)''', 0);
CALL `wp_sync_add_column`('post', 'like_count', 'int NOT NULL DEFAULT 0 COMMENT ''좋아요 수(비정규화 카운트)''', 0);
CALL `wp_sync_add_column`('post', 'hidden_from_artist', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''아티스트에게 숨김(신고/가리기)''', 0);
CALL `wp_sync_add_column`('post', 'link_url', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''첨부 링크 URL''', 0);

-- post_attachment
CALL `wp_sync_add_column`('post_attachment', 'content_type', 'varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''MIME 타입''', 0);
CALL `wp_sync_add_column`('post_attachment', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''업로드 시각''', 1);
CALL `wp_sync_add_column`('post_attachment', 'file_size', 'bigint DEFAULT NULL COMMENT ''파일 크기(byte)''', 0);
CALL `wp_sync_add_column`('post_attachment', 'original_name', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''원본 파일명(표시용)''', 0);
CALL `wp_sync_add_column`('post_attachment', 'stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''서버 저장 파일명''', 0);
CALL `wp_sync_add_column`('post_attachment', 'post_id', 'bigint NOT NULL COMMENT ''소속 게시글(post.id)''', 0);

-- post_like
CALL `wp_sync_add_column`('post_like', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''좋아요 시각''', 1);
CALL `wp_sync_add_column`('post_like', 'post_id', 'bigint NOT NULL COMMENT ''게시글(post.id)''', 0);
CALL `wp_sync_add_column`('post_like', 'user_id', 'bigint NOT NULL COMMENT ''좋아요한 회원(users.id)''', 0);

-- post_bookmark
CALL `wp_sync_add_column`('post_bookmark', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''북마크 시각''', 1);
CALL `wp_sync_add_column`('post_bookmark', 'post_id', 'bigint NOT NULL COMMENT ''게시글(post.id)''', 0);
CALL `wp_sync_add_column`('post_bookmark', 'user_id', 'bigint NOT NULL COMMENT ''저장한 회원(users.id)''', 0);

-- comment
CALL `wp_sync_add_column`('comment', 'content', 'varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''댓글 내용''', 0);
CALL `wp_sync_add_column`('comment', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''작성 시각''', 1);
CALL `wp_sync_add_column`('comment', 'author_id', 'bigint NOT NULL COMMENT ''작성자(users.id)''', 0);
CALL `wp_sync_add_column`('comment', 'post_id', 'bigint NOT NULL COMMENT ''원글(post.id)''', 0);

-- comment_report
CALL `wp_sync_add_column`('comment_report', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''신고 시각''', 1);
CALL `wp_sync_add_column`('comment_report', 'reason', 'enum(''ABUSE'', ''ETC'', ''SEXUAL'', ''SPAM'') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''신고 사유''', 0);
CALL `wp_sync_add_column`('comment_report', 'status', 'enum(''PENDING'', ''DISMISSED'', ''RESOLVED'') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING'' COMMENT ''처리 상태 (대기/기각/처리완료)''', 0);
CALL `wp_sync_add_column`('comment_report', 'resolved_at', 'datetime(6) DEFAULT NULL COMMENT ''신고 처리 시각 (대기중이면 NULL)''', 0);
CALL `wp_sync_add_column`('comment_report', 'comment_id', 'bigint NOT NULL COMMENT ''신고 대상 댓글(comment.id)''', 0);
CALL `wp_sync_add_column`('comment_report', 'reporter_id', 'bigint NOT NULL COMMENT ''신고자(users.id)''', 0);

-- report
CALL `wp_sync_add_column`('report', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''신고 시각''', 1);
CALL `wp_sync_add_column`('report', 'reason', 'enum(''ABUSE'', ''ETC'', ''SEXUAL'', ''SPAM'') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''신고 사유''', 0);
CALL `wp_sync_add_column`('report', 'status', 'enum(''PENDING'', ''DISMISSED'', ''RESOLVED'') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING'' COMMENT ''처리 상태 (대기/기각/처리완료)''', 0);
CALL `wp_sync_add_column`('report', 'resolved_at', 'datetime(6) DEFAULT NULL COMMENT ''신고 처리 시각 (대기중이면 NULL)''', 0);
CALL `wp_sync_add_column`('report', 'post_id', 'bigint NOT NULL COMMENT ''신고 대상 게시글(post.id)''', 0);
CALL `wp_sync_add_column`('report', 'reporter_id', 'bigint NOT NULL COMMENT ''신고자(users.id)''', 0);

-- board_media
CALL `wp_sync_add_column`('board_media', 'group_id', 'bigint NOT NULL COMMENT ''그룹(artist_groups.id)''', 0);
CALL `wp_sync_add_column`('board_media', 'uploader_id', 'bigint NOT NULL COMMENT ''업로더(users.id, 주로 소속사)''', 0);
CALL `wp_sync_add_column`('board_media', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''제목''', 0);
CALL `wp_sync_add_column`('board_media', 'content', 'varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''본문/캡션''', 0);
CALL `wp_sync_add_column`('board_media', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('board_media', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);
CALL `wp_sync_add_column`('board_media', 'deleted_at', 'datetime(6) DEFAULT NULL COMMENT ''삭제(soft delete) 시각''', 0);
CALL `wp_sync_add_column`('board_media', 'like_count', 'int NOT NULL DEFAULT ''0'' COMMENT ''좋아요 수(비정규화 카운트)''', 0);

-- board_media_files
CALL `wp_sync_add_column`('board_media_files', 'board_id', 'bigint NOT NULL COMMENT ''미디어 게시글(board_media.id)''', 0);
CALL `wp_sync_add_column`('board_media_files', 'original_name', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''원본 파일명(표시용)''', 0);
CALL `wp_sync_add_column`('board_media_files', 'stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''서버 저장 파일명''', 0);
CALL `wp_sync_add_column`('board_media_files', 'content_type', 'varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''MIME 타입''', 0);
CALL `wp_sync_add_column`('board_media_files', 'media_type', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''미디어 종류: IMAGE/VIDEO''', 0);
CALL `wp_sync_add_column`('board_media_files', 'file_size', 'bigint DEFAULT NULL COMMENT ''파일 크기(byte)''', 0);
CALL `wp_sync_add_column`('board_media_files', 'sort_order', 'int NOT NULL DEFAULT ''0'' COMMENT ''표시 순서''', 0);
CALL `wp_sync_add_column`('board_media_files', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);

-- board_media_like
CALL `wp_sync_add_column`('board_media_like', 'board_id', 'bigint NOT NULL COMMENT ''미디어 게시글(board_media.id)''', 0);
CALL `wp_sync_add_column`('board_media_like', 'user_id', 'bigint NOT NULL COMMENT ''좋아요한 회원(users.id)''', 0);
CALL `wp_sync_add_column`('board_media_like', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''좋아요 시각''', 1);

-- shop_goods
CALL `wp_sync_add_column`('shop_goods', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'name', 'varchar(200) NOT NULL COMMENT ''상품명''', 0);
CALL `wp_sync_add_column`('shop_goods', 'description', 'text COMMENT ''상세 설명(마크다운)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'price', 'int NOT NULL COMMENT ''가격(원)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'thumbnail_url', 'varchar(500) DEFAULT NULL COMMENT ''썸네일 저장 파일명''', 0);
CALL `wp_sync_add_column`('shop_goods', 'official_url', 'varchar(500) DEFAULT NULL COMMENT ''공식 판매처 URL''', 0);
CALL `wp_sync_add_column`('shop_goods', 'status', 'varchar(20) NOT NULL COMMENT ''ON_SALE / HIDDEN (공개여부, 품절과 무관)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'sort_order', 'int NOT NULL DEFAULT 0 COMMENT ''유저 샵 노출 순서(오름차순)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'membership_only', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''멤버십 전용 여부 (1=전용)''', 0);
CALL `wp_sync_add_column`('shop_goods', 'deleted_at', 'datetime(6) DEFAULT NULL COMMENT ''소프트 삭제 시각''', 0);
CALL `wp_sync_add_column`('shop_goods', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('shop_goods', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- shop_goods_category
CALL `wp_sync_add_column`('shop_goods_category', 'goods_id', 'bigint NOT NULL', 0);
CALL `wp_sync_add_column`('shop_goods_category', 'category', 'varchar(20) NOT NULL COMMENT ''CLOTHING/SHOES/BAG/ACCESSORY/OTHER''', 0);

-- shop_goods_option
CALL `wp_sync_add_column`('shop_goods_option', 'goods_id', 'bigint NOT NULL', 0);
CALL `wp_sync_add_column`('shop_goods_option', 'category', 'varchar(20) NOT NULL', 0);
CALL `wp_sync_add_column`('shop_goods_option', 'option_key', 'varchar(30) NOT NULL COMMENT ''WIDTH, HEIGHT, DEPTH, NOTE''', 0);
CALL `wp_sync_add_column`('shop_goods_option', 'option_value', 'varchar(500) NOT NULL', 0);

-- shop_goods_variant
CALL `wp_sync_add_column`('shop_goods_variant', 'goods_id', 'bigint NOT NULL', 0);
CALL `wp_sync_add_column`('shop_goods_variant', 'option_key', 'varchar(30) NOT NULL COMMENT ''SIZE / SHOE_MM / DEFAULT''', 0);
CALL `wp_sync_add_column`('shop_goods_variant', 'option_value', 'varchar(50) NOT NULL DEFAULT ''''', 0);
CALL `wp_sync_add_column`('shop_goods_variant', 'stock_quantity', 'int NOT NULL DEFAULT 0', 0);

-- shop_cart_item
CALL `wp_sync_add_column`('shop_cart_item', 'user_id', 'bigint NOT NULL COMMENT ''회원(users.id)''', 0);
CALL `wp_sync_add_column`('shop_cart_item', 'product_id', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''목업 카탈로그 상품 ID''', 0);
CALL `wp_sync_add_column`('shop_cart_item', 'quantity', 'int NOT NULL DEFAULT ''1'' COMMENT ''수량''', 0);
CALL `wp_sync_add_column`('shop_cart_item', 'unit_price', 'int NOT NULL COMMENT ''담을 당시 단가(원)''', 0);
CALL `wp_sync_add_column`('shop_cart_item', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''담은 시각''', 1);
CALL `wp_sync_add_column`('shop_cart_item', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- chat_message
CALL `wp_sync_add_column`('chat_message', 'content', 'text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''메시지 본문''', 0);
CALL `wp_sync_add_column`('chat_message', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''전송 시각''', 1);
CALL `wp_sync_add_column`('chat_message', 'artist_id', 'bigint NOT NULL COMMENT ''대화방 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('chat_message', 'fan_id', 'bigint DEFAULT NULL COMMENT ''대화방 팬(users.id, 방 식별)''', 0);
CALL `wp_sync_add_column`('chat_message', 'sender_id', 'bigint NOT NULL COMMENT ''실제 발신자(users.id)''', 0);
CALL `wp_sync_add_column`('chat_message', 'visible_to_artist', 'tinyint(1) NOT NULL DEFAULT ''1'' COMMENT ''아티스트 화면 노출 여부(CHAT-02 비대칭 수신, 전송 시점에 확정)''', 0);

-- chat_quota
CALL `wp_sync_add_column`('chat_quota', 'charged_date', 'date NOT NULL DEFAULT (CURRENT_DATE) COMMENT ''쿼터 충전(기준)일''', 1);
CALL `wp_sync_add_column`('chat_quota', 'remaining_count', 'int NOT NULL COMMENT ''남은 전송 횟수''', 0);
CALL `wp_sync_add_column`('chat_quota', 'artist_id', 'bigint NOT NULL COMMENT ''대상 아티스트(users.id)''', 0);
CALL `wp_sync_add_column`('chat_quota', 'fan_id', 'bigint NOT NULL COMMENT ''팬(users.id)''', 0);

-- fan_badge_ownership
CALL `wp_sync_add_column`('fan_badge_ownership', 'fan_id', 'bigint NOT NULL COMMENT ''팬(users.id)''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'artist_id', 'bigint NOT NULL COMMENT ''아티스트(users.id) - 커뮤니티 단위''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'badge_code', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''배지 코드(fan_badge.badge_code)''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'badge_name', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''수여 시점 배지명 스냅샷''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'badge_type', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''배지 유형: BASIC/SPECIAL''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'awarded_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수여 시각''', 1);
CALL `wp_sync_add_column`('fan_badge_ownership', 'revoked_at', 'datetime(6) DEFAULT NULL COMMENT ''회수 시각(NULL=유효)''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'awarded_by', 'bigint DEFAULT NULL COMMENT ''수여자(users.id), NULL=시스템 자동''', 0);
CALL `wp_sync_add_column`('fan_badge_ownership', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);

-- email_verification
CALL `wp_sync_add_column`('email_verification', 'user_id', 'bigint DEFAULT NULL COMMENT ''프로젝트 인증 회원(users.id), 회원가입 인증은 NULL''', 0);
CALL `wp_sync_add_column`('email_verification', 'email', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''인증 대상 이메일''', 0);
CALL `wp_sync_add_column`('email_verification', 'purpose', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''인증 목적: SIGNUP/FAN_PROJECT_CREATE/ADMIN_LOGIN''', 0);
CALL `wp_sync_add_column`('email_verification', 'verification_key', 'varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''인증 요청 식별 UUID''', 0);
CALL `wp_sync_add_column`('email_verification', 'code_hash', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''인증번호 BCrypt 해시''', 0);
CALL `wp_sync_add_column`('email_verification', 'attempt_count', 'int NOT NULL DEFAULT 0 COMMENT ''인증번호 실패 횟수''', 0);
CALL `wp_sync_add_column`('email_verification', 'expires_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''인증 만료 시각''', 1);
CALL `wp_sync_add_column`('email_verification', 'verified_at', 'datetime(6) DEFAULT NULL COMMENT ''인증 완료 시각''', 0);
CALL `wp_sync_add_column`('email_verification', 'consumed_at', 'datetime(6) DEFAULT NULL COMMENT ''회원가입/프로젝트 등록에 사용된 시각''', 0);
CALL `wp_sync_add_column`('email_verification', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''인증 요청 생성 시각''', 1);
CALL `wp_sync_add_column`('email_verification', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''인증 정보 수정 시각''', 1);

-- fan_project
CALL `wp_sync_add_column`('fan_project', 'artist_id', 'bigint NOT NULL COMMENT ''대상 아티스트 커뮤니티(users.id)''', 0);
CALL `wp_sync_add_column`('fan_project', 'creator_id', 'bigint NOT NULL COMMENT ''개설 신청자(users.id)''', 0);
CALL `wp_sync_add_column`('fan_project', 'title', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''프로젝트 제목''', 0);
CALL `wp_sync_add_column`('fan_project', 'event_type', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''이벤트 유형: BIRTHDAY_CAFE/BILLBOARD/CONCERT/ETC''', 0);
CALL `wp_sync_add_column`('fan_project', 'goal_amount', 'bigint NOT NULL COMMENT ''목표 모금액(원, 1만~300만)''', 0);
CALL `wp_sync_add_column`('fan_project', 'funding_start_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''모금 시작 시각''', 1);
CALL `wp_sync_add_column`('fan_project', 'funding_end_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''모금 종료 시각''', 1);
CALL `wp_sync_add_column`('fan_project', 'description', 'varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''프로젝트 소개''', 0);
CALL `wp_sync_add_column`('fan_project', 'status', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING_APPROVAL'' COMMENT ''상태: 승인대기/승인/반려/모금중 등''', 0);
CALL `wp_sync_add_column`('fan_project', 'special_badge_count_at_apply', 'int NOT NULL COMMENT ''신청 시점 SPECIAL 배지 수(자격 스냅샷)''', 0);
CALL `wp_sync_add_column`('fan_project', 'basic_badge_count_at_apply', 'int NOT NULL COMMENT ''신청 시점 BASIC 배지 수(자격 스냅샷)''', 0);
CALL `wp_sync_add_column`('fan_project', 'eligibility_rule_code', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''SPECIAL_1_AND_BASIC_5'' COMMENT ''개설 자격 규칙 코드''', 0);
CALL `wp_sync_add_column`('fan_project', 'identity_verification_method', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''EMAIL'' COMMENT ''프로젝트 등록 인증 방식(EMAIL)''', 0);
CALL `wp_sync_add_column`('fan_project', 'identity_verified_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''본인인증 완료 시각''', 1);
CALL `wp_sync_add_column`('fan_project', 'reviewed_by', 'bigint DEFAULT NULL COMMENT ''검토자(users.id)''', 0);
CALL `wp_sync_add_column`('fan_project', 'reviewed_at', 'datetime(6) DEFAULT NULL COMMENT ''검토 시각''', 0);
CALL `wp_sync_add_column`('fan_project', 'rejection_reason', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''반려 사유(REJECTED 시)''', 0);
CALL `wp_sync_add_column`('fan_project', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('fan_project', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);
CALL `wp_sync_add_column`('fan_project', 'deleted_at', 'datetime(6) DEFAULT NULL COMMENT ''삭제(soft delete) 시각''', 0);

-- fan_project_cover_image
CALL `wp_sync_add_column`('fan_project_cover_image', 'project_id', 'bigint NOT NULL COMMENT ''프로젝트(fan_project.id) 1:1''', 0);
CALL `wp_sync_add_column`('fan_project_cover_image', 'original_name', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''원본 파일명''', 0);
CALL `wp_sync_add_column`('fan_project_cover_image', 'stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''서버 저장 파일명''', 0);
CALL `wp_sync_add_column`('fan_project_cover_image', 'content_type', 'varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''MIME(image/*)''', 0);
CALL `wp_sync_add_column`('fan_project_cover_image', 'file_size', 'bigint NOT NULL COMMENT ''파일 크기(byte)''', 0);
CALL `wp_sync_add_column`('fan_project_cover_image', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);

-- fan_project_contribution
CALL `wp_sync_add_column`('fan_project_contribution', 'project_id', 'bigint NOT NULL COMMENT ''프로젝트(fan_project.id)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'contributor_id', 'bigint NOT NULL COMMENT ''후원자(users.id)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'order_no', 'varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''주문번호''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'idempotency_key', 'varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''결제 멱등 키(중복요청 방지)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'payment_provider', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''MOCK'' COMMENT ''결제 제공자''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'provider_transaction_id', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''제공자 거래 ID''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'amount', 'bigint NOT NULL COMMENT ''결제 금액(원)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'virtual_bank_code', 'varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''가상계좌 은행 코드(토스 코드)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'virtual_account_number', 'varbinary(255) DEFAULT NULL COMMENT ''가상계좌 번호(현재 변환 저장, 추후 암호화)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'due_date', 'datetime(6) DEFAULT NULL COMMENT ''가상계좌 입금기한''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'deposit_secret', 'varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''입금 웹훅 검증값(외부 노출 금지)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'is_anonymous', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''닉네임 비공개 참여 여부: 0/1''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'refund_policy_agreed_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''환불 규정 동의 시각''', 1);
CALL `wp_sync_add_column`('fan_project_contribution', 'refund_amount', 'bigint NOT NULL DEFAULT ''0'' COMMENT ''환불 금액(원)''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'payment_status', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''READY'' COMMENT ''결제 상태: READY/WAITING_FOR_DEPOSIT/PAID/FAILED/EXPIRED/CANCELLED/REFUND_REQUESTED/REFUNDED''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'paid_at', 'datetime(6) DEFAULT NULL COMMENT ''결제 완료 시각''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'cancelled_at', 'datetime(6) DEFAULT NULL COMMENT ''취소 시각''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'refunded_at', 'datetime(6) DEFAULT NULL COMMENT ''환불 시각''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'refund_reason', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''환불 사유''', 0);
CALL `wp_sync_add_column`('fan_project_contribution', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''생성 시각''', 1);
CALL `wp_sync_add_column`('fan_project_contribution', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- fan_project_settlement_account
CALL `wp_sync_add_column`('fan_project_settlement_account', 'project_id', 'bigint NOT NULL COMMENT ''프로젝트(fan_project.id) 1:1''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'bank_code', 'char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''은행코드(3자리)''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'account_number_enc', 'varbinary(512) NOT NULL COMMENT ''계좌번호(암호화)''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'account_number_hmac', 'char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''계좌번호 HMAC(검색용)''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'account_number_last4', 'char(4) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''계좌 끝 4자리(표시용)''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'verification_status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''UNVERIFIED'' COMMENT ''계좌 검증: UNVERIFIED/VERIFIED/FAILED''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'verified_at', 'datetime(6) DEFAULT NULL COMMENT ''검증 완료 시각''', 0);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('fan_project_settlement_account', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);

-- fan_project_fraud_check
CALL `wp_sync_add_column`('fan_project_fraud_check', 'project_id', 'bigint NOT NULL COMMENT ''프로젝트(fan_project.id)''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'target_type', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''조회 대상: ACCOUNT''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'target_fingerprint', 'char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''대상 식별 해시''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'bank_code', 'char(3) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''은행코드(계좌 조회 시)''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'provider', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''THECHEAT'' COMMENT ''조회 제공자''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'provider_result_code', 'int DEFAULT NULL COMMENT ''제공자 결과 코드''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'result_status', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING'' COMMENT ''결과: PENDING/CLEAR/CAUTION/ERROR''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'caution_yn', 'char(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''주의 여부 Y/N''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'search_window_start_at', 'datetime(6) DEFAULT NULL COMMENT ''조회 기간 시작''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'search_window_end_at', 'datetime(6) DEFAULT NULL COMMENT ''조회 기간 종료''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'checked_at', 'datetime(6) DEFAULT NULL COMMENT ''조회 수행 시각''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'requested_by', 'bigint DEFAULT NULL COMMENT ''요청자(users.id)''', 0);
CALL `wp_sync_add_column`('fan_project_fraud_check', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);

-- group_schedule
CALL `wp_sync_add_column`('group_schedule', 'group_id', 'bigint NOT NULL COMMENT ''그룹(artist_groups.id)''', 0);
CALL `wp_sync_add_column`('group_schedule', 'created_by', 'bigint NOT NULL COMMENT ''등록자(users.id)''', 0);
CALL `wp_sync_add_column`('group_schedule', 'event_type', 'varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''유형: BROADCAST/LIVE/TICKET_OPEN/CONCERT''', 0);
CALL `wp_sync_add_column`('group_schedule', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''일정 제목''', 0);
CALL `wp_sync_add_column`('group_schedule', 'place', 'varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''장소''', 0);
CALL `wp_sync_add_column`('group_schedule', 'start_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''시작 시각''', 1);
CALL `wp_sync_add_column`('group_schedule', 'end_at', 'datetime(6) DEFAULT NULL COMMENT ''종료 시각''', 0);
CALL `wp_sync_add_column`('group_schedule', 'ticket_url', 'varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''티켓 링크''', 0);
CALL `wp_sync_add_column`('group_schedule', 'ticket_image_stored_name', 'varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''티켓 이미지 저장명''', 0);
CALL `wp_sync_add_column`('group_schedule', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''등록 시각''', 1);
CALL `wp_sync_add_column`('group_schedule', 'updated_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''수정 시각''', 1);
CALL `wp_sync_add_column`('group_schedule', 'deleted_at', 'datetime(6) DEFAULT NULL COMMENT ''삭제(soft delete) 시각''', 0);

-- notification
CALL `wp_sync_add_column`('notification', 'recipient_id', 'bigint NOT NULL COMMENT ''수신자(users.id)''', 0);
CALL `wp_sync_add_column`('notification', 'type', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''알림 유형(공지/댓글/채팅 등)''', 0);
CALL `wp_sync_add_column`('notification', 'title', 'varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''알림 제목''', 0);
CALL `wp_sync_add_column`('notification', 'message', 'varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''알림 본문''', 0);
CALL `wp_sync_add_column`('notification', 'source_type', 'varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''출처 엔티티 종류(다형)''', 0);
CALL `wp_sync_add_column`('notification', 'source_id', 'bigint DEFAULT NULL COMMENT ''출처 엔티티 ID''', 0);
CALL `wp_sync_add_column`('notification', 'is_read', 'tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''읽음 여부(0=미읽음)''', 0);
CALL `wp_sync_add_column`('notification', 'created_at', 'datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''발송 시각''', 1);

-- notification_setting
CALL `wp_sync_add_column`('notification_setting', 'user_id', 'bigint NOT NULL COMMENT ''회원(users.id)''', 0);
CALL `wp_sync_add_column`('notification_setting', 'type', 'varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''알림 유형''', 0);
CALL `wp_sync_add_column`('notification_setting', 'enabled', 'tinyint(1) NOT NULL DEFAULT ''1'' COMMENT ''해당 유형 수신 on/off''', 0);

DROP PROCEDURE IF EXISTS `wp_sync_add_column`;

-- ============================================================
-- [3] 구조 전환 - 이미 쓰던 DB의 옛 구조를 현재 구조로 바꾼다.
--     (빈 DB나 이미 최신인 DB에서는 각 단계를 건너뛴다)
-- ============================================================

-- 3-1) 팬 프로젝트 결제: 모의결제 -> 토스 가상계좌 (prjPAYMENT-1)
--   - depositor_name 제거: 가상계좌는 주문마다 계좌가 달라 입금자명 대조가 필요 없다.
--     이 컬럼이 NOT NULL로 남아 있으면 엔티티가 값을 채우지 않아 참여 INSERT가 전부 실패한다.
--     (앱은 정상 기동하고 "참여하기"를 누를 때만 오류가 나서 발견이 늦다)
--   - 결제 상태 제약에 WAITING_FOR_DEPOSIT, EXPIRED 추가.
DROP PROCEDURE IF EXISTS `wp_payment_migrate`;
DELIMITER $$
CREATE PROCEDURE `wp_payment_migrate`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fan_project_contribution'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fan_project_contribution'
              AND COLUMN_NAME = 'depositor_name'
        ) THEN
            ALTER TABLE `fan_project_contribution` DROP COLUMN `depositor_name`;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fan_project_contribution'
              AND CONSTRAINT_NAME = 'ck_fan_project_contribution_status'
        ) THEN
            ALTER TABLE `fan_project_contribution` DROP CHECK `ck_fan_project_contribution_status`;
        END IF;

        ALTER TABLE `fan_project_contribution`
            ADD CONSTRAINT `ck_fan_project_contribution_status` CHECK (`payment_status` IN (
                'READY', 'WAITING_FOR_DEPOSIT', 'PAID', 'FAILED',
                'EXPIRED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED'
            ));
    END IF;
END$$
DELIMITER ;
CALL `wp_payment_migrate`();
DROP PROCEDURE IF EXISTS `wp_payment_migrate`;

-- 3-2) GroupFollow/UserFollow 통합 (FOLLOW-01)
--   group_follow의 데이터를 user_follows로 옮기고, 기본키를
--   (follower_id, following_id) -> (follower_id, following_id, community_id)로 바꾼다.
--   group_follow.group_id는 그 아티스트의 users.id와 같으므로 following_id/community_id 둘 다 group_id.
DROP PROCEDURE IF EXISTS `wp_follow_merge`;
DELIMITER $$
CREATE PROCEDURE `wp_follow_merge`()
BEGIN
    -- community_id 컬럼 추가 (이관 전이라 보통 user_follows는 비어 있다)
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND COLUMN_NAME = 'community_id'
    ) THEN
        ALTER TABLE `user_follows`
            ADD COLUMN `community_id` BIGINT NOT NULL DEFAULT 0
                COMMENT '이 팔로우가 속한 커뮤니티(그 커뮤니티 아티스트의 users.id)'
                AFTER `following_id`;
    END IF;

    -- group_follow -> user_follows 이관 후 group_follow 제거
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_follow'
    ) THEN
        INSERT IGNORE INTO `user_follows` (`follower_id`, `following_id`, `community_id`, `created_at`)
        SELECT `fan_id`, `group_id`, `group_id`, `created_at` FROM `group_follow`;

        DROP TABLE `group_follow`;
    END IF;

    -- 기본키를 3컬럼으로 (아직 2컬럼짜리인 경우에만)
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND (
        SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND CONSTRAINT_NAME = 'PRIMARY'
    ) BETWEEN 1 AND 2 THEN
        ALTER TABLE `user_follows`
            DROP PRIMARY KEY,
            ADD PRIMARY KEY (`follower_id`, `following_id`, `community_id`);
    END IF;

    -- community_id 인덱스/외래키
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND INDEX_NAME = 'fk_uf_community'
    ) THEN
        ALTER TABLE `user_follows` ADD KEY `fk_uf_community` (`community_id`);
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows' AND CONSTRAINT_NAME = 'fk_uf_community'
    ) THEN
        ALTER TABLE `user_follows`
            ADD CONSTRAINT `fk_uf_community` FOREIGN KEY (`community_id`) REFERENCES `users` (`id`);
    END IF;
END$$
DELIMITER ;
CALL `wp_follow_merge`();
DROP PROCEDURE IF EXISTS `wp_follow_merge`;

-- ============================================================
-- [4] 배지 카탈로그: 없는 배지 코드만 추가
-- ============================================================
INSERT IGNORE INTO `fan_badge`
  (`badge_code`, `badge_name`, `badge_type`, `icon`, `image_url`, `description`, `sort_order`, `created_at`)
VALUES
  ('BASIC_FIRST_JOIN',    '커뮤니티 첫 가입',      'BASIC', '🎉', 'community-first-join.svg',  '커뮤니티에 처음 가입하면 획득',              1,  NOW(6)),
  ('BASIC_FIRST_POST',    '첫 게시글 작성',        'BASIC', '✍️', 'first-post.svg',            '팬 게시판에 첫 글을 쓰면 획득',           2,  NOW(6)),
  ('BASIC_COMMENT_5',     '댓글 5개 작성',         'BASIC', '💬', 'five-comments.svg',         '이 커뮤니티에 댓글 5개를 쓰면 획득',      3,  NOW(6)),
  ('BASIC_MEDIA_VIEW',    '미디어 시청',           'BASIC', '🎬', 'media-view.svg',            'Media 탭 콘텐츠를 보면 획득',             4,  NOW(6)),
  ('BASIC_DAY_100',       '가입 후 100일',         'BASIC', '💯', 'member-100-days.svg',       '가입 후 100일이 지나면 획득',        5,  NOW(6)),
  ('BASIC_DAY_200',       '가입 후 200일',         'BASIC', '📅', 'member-200-days.svg',       '가입 후 200일이 지나면 획득',        6,  NOW(6)),
  ('BASIC_DAY_300',       '가입 후 300일',         'BASIC', '🗓️', 'member-300-days.svg',       '가입 후 300일이 지나면 획득',        7,  NOW(6)),
  ('BASIC_LIKE_10',       '좋아요 10개',           'BASIC', '👍', 'ten-likes.svg',             '게시글에 좋아요를 10번 누르면 획득',          8,  NOW(6)),
  ('BASIC_LIKED_5',       '받은 좋아요 5개',       'BASIC', '❤️', 'five-likes-received.svg',   '내 게시글이 좋아요 5개를 받으면 획득',        9,  NOW(6)),
  ('BASIC_FOLLOW_ARTIST', '아티스트 프로필 팔로우', 'BASIC', '⭐', 'artist-profile-follow.svg', '아티스트 프로필을 팔로우하면 획득',          10, NOW(6)),
  ('BASIC_SHOP_PURCHASE', '샵 구매',               'BASIC', '🛍️', 'shop-purchase.svg',         'Shop에서 상품을 구매하면 획득',              11, NOW(6)),
  ('BASIC_LIVE_VIEW',     '라이브 시청',           'BASIC', '📡', 'live-view.svg',             'Live 방송을 보면 획득',                  12, NOW(6)),
  ('BASIC_YEAR_1',        '커뮤니티 가입 후 1년',  'BASIC', '🥇', 'community-1-year.svg',      '가입 후 1년이 지나면 획득',         13, NOW(6)),
  ('BASIC_YEAR_2',        '커뮤니티 가입 후 2년',  'BASIC', '🥈', 'community-2-years.svg',     '가입 후 2년이 지나면 획득',         14, NOW(6)),
  ('BASIC_YEAR_3',        '커뮤니티 가입 후 3년',  'BASIC', '🥉', 'community-3-years.svg',     '가입 후 3년이 지나면 획득',         15, NOW(6)),
  ('SPECIAL_DEBUT_1',       '아티스트 데뷔 1주년',  'SPECIAL', '🎂', 'artist-debut-1-year.svg',  '데뷔 1주년을 함께하면 획득',        1,  NOW(6)),
  ('SPECIAL_DEBUT_2',       '아티스트 데뷔 2주년',  'SPECIAL', '🎊', 'artist-debut-2-years.svg', '데뷔 2주년을 함께하면 획득',        2,  NOW(6)),
  ('SPECIAL_DEBUT_3',       '아티스트 데뷔 3주년',  'SPECIAL', '🏆', 'artist-debut-3-years.svg', '데뷔 3주년을 함께하면 획득',        3,  NOW(6)),
  ('SPECIAL_FOLLOWER_10',   '팔로워 10명 달성',     'SPECIAL', '👥', 'ten-followers.svg',        '내 팔로워가 10명이 되면 획득',               4,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_1',  '첫 멤버십 가입',       'SPECIAL', '💎', 'first-membership.svg',     '멤버십에 처음 가입하면 획득',      5,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_2',  '멤버십 연속 2년',      'SPECIAL', '💠', 'membership-2-years.svg',   '멤버십을 2년 연속 유지하면 획득',            6,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_3',  '멤버십 연속 3년',      'SPECIAL', '🔷', 'membership-3-years.svg',   '멤버십을 3년 연속 유지하면 획득',            7,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_4',  '멤버십 연속 4년',      'SPECIAL', '🔶', 'membership-4-years.svg',   '멤버십을 4년 연속 유지하면 획득',            8,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_5',  '멤버십 연속 5년',      'SPECIAL', '👑', 'membership-5-years.svg',   '멤버십을 5년 연속 유지하면 획득',            9,  NOW(6)),
  ('SPECIAL_PROJECT_CREATE','프로젝트 참여',        'SPECIAL', '🚀', 'project-registered.svg',   '팬 프로젝트에 참여(결제 완료)하면 획득',    10, NOW(6));

SET FOREIGN_KEY_CHECKS = 1;
SET SESSION sql_mode = @wp_old_sql_mode;

-- ============================================================
-- [검증 1] 0행이면 정상. 행이 나오면 빠진 테이블/컬럼이 있다는 뜻입니다.
--   MISSING_TABLE / MISSING_COLUMN / TYPE_MISMATCH(기존 컬럼 타입이 스키마와 다름)
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS `wp_expected_columns`;
CREATE TEMPORARY TABLE `wp_expected_columns` (
  `table_name` VARCHAR(64) NOT NULL,
  `column_name` VARCHAR(64) NOT NULL,
  `data_type` VARCHAR(64) NOT NULL
);
INSERT INTO `wp_expected_columns` VALUES
  ('agencies', 'id', 'bigint'),
  ('agencies', 'name', 'varchar'),
  ('agencies', 'business_no', 'varchar'),
  ('agencies', 'ceo_name', 'varchar'),
  ('agencies', 'status', 'varchar'),
  ('agencies', 'created_at', 'datetime'),
  ('agencies', 'updated_at', 'datetime'),
  ('users', 'id', 'bigint'),
  ('users', 'username', 'varchar'),
  ('users', 'password', 'varchar'),
  ('users', 'role', 'varchar'),
  ('users', 'status', 'varchar'),
  ('users', 'agency_id', 'bigint'),
  ('users', 'real_name', 'varbinary'),
  ('users', 'nickname', 'varchar'),
  ('users', 'email', 'varchar'),
  ('users', 'phone', 'varbinary'),
  ('users', 'phone_hash', 'char'),
  ('users', 'birth_date', 'date'),
  ('users', 'gender', 'varchar'),
  ('users', 'zipcode', 'varchar'),
  ('users', 'address1', 'varchar'),
  ('users', 'address2', 'varbinary'),
  ('users', 'email_verified_at', 'datetime'),
  ('users', 'last_login_at', 'datetime'),
  ('users', 'dormant_notice_sent_at', 'datetime'),
  ('users', 'created_at', 'datetime'),
  ('users', 'updated_at', 'datetime'),
  ('users', 'deleted_at', 'datetime'),
  ('users', 'provider', 'varchar'),
  ('users', 'provider_id', 'varchar'),
  ('users', 'marketing_consent', 'tinyint'),
  ('users', 'community_activity_email_enabled', 'tinyint'),
  ('users', 'night_notification_allowed', 'tinyint'),
  ('users', 'preferred_language', 'varchar'),
  ('filter_keyword', 'id', 'bigint'),
  ('filter_keyword', 'keyword', 'varchar'),
  ('fan_badge', 'id', 'bigint'),
  ('fan_badge', 'badge_code', 'varchar'),
  ('fan_badge', 'badge_name', 'varchar'),
  ('fan_badge', 'badge_type', 'varchar'),
  ('fan_badge', 'icon', 'varchar'),
  ('fan_badge', 'image_url', 'varchar'),
  ('fan_badge', 'description', 'varchar'),
  ('fan_badge', 'sort_order', 'int'),
  ('fan_badge', 'created_at', 'datetime'),
  ('admin_profiles', 'user_id', 'bigint'),
  ('admin_profiles', 'admin_level', 'varchar'),
  ('admin_profiles', 'department', 'varchar'),
  ('admin_profiles', 'employee_no', 'varchar'),
  ('admin_action_logs', 'id', 'bigint'),
  ('admin_action_logs', 'actor_id', 'bigint'),
  ('admin_action_logs', 'action', 'varchar'),
  ('admin_action_logs', 'target_type', 'varchar'),
  ('admin_action_logs', 'target_id', 'bigint'),
  ('admin_action_logs', 'reason', 'text'),
  ('admin_action_logs', 'ip_address', 'varchar'),
  ('admin_action_logs', 'created_at', 'datetime'),
  ('agency_profiles', 'user_id', 'bigint'),
  ('agency_profiles', 'agency_id', 'bigint'),
  ('agency_profiles', 'department', 'varchar'),
  ('agency_profiles', 'position', 'varchar'),
  ('agency_profiles', 'is_owner', 'tinyint'),
  ('agency_profiles', 'approved_by', 'bigint'),
  ('agency_profiles', 'approved_at', 'datetime'),
  ('artist_profiles', 'user_id', 'bigint'),
  ('artist_profiles', 'agency_id', 'bigint'),
  ('artist_profiles', 'stage_name', 'varchar'),
  ('artist_profiles', 'debut_date', 'date'),
  ('artist_profiles', 'position', 'varchar'),
  ('artist_profiles', 'bio', 'text'),
  ('artist_profiles', 'profile_img', 'varchar'),
  ('artist_groups', 'id', 'bigint'),
  ('artist_groups', 'agency_id', 'bigint'),
  ('artist_groups', 'name', 'varchar'),
  ('artist_groups', 'name_en', 'varchar'),
  ('artist_groups', 'fandom_name', 'varchar'),
  ('artist_groups', 'debut_date', 'date'),
  ('artist_groups', 'status', 'varchar'),
  ('artist_groups', 'created_at', 'datetime'),
  ('artist_groups', 'updated_at', 'datetime'),
  ('artist_profile', 'id', 'bigint'),
  ('artist_profile', 'artist_id', 'bigint'),
  ('artist_profile', 'intro', 'text'),
  ('artist_profile', 'header_image_url', 'varchar'),
  ('artist_profile', 'logo_image_url', 'varchar'),
  ('artist_profile', 'created_at', 'datetime'),
  ('artist_profile', 'updated_at', 'datetime'),
  ('artist_group_profiles', 'id', 'bigint'),
  ('artist_group_profiles', 'artist_id', 'bigint'),
  ('artist_group_profiles', 'gender', 'varchar'),
  ('artist_group_profiles', 'member_count', 'int'),
  ('artist_group_profiles', 'nationality', 'varchar'),
  ('artist_group_profiles', 'category', 'varchar'),
  ('artist_group_profiles', 'debut_date', 'date'),
  ('artist_group_profiles', 'updated_at', 'datetime'),
  ('group_members', 'id', 'bigint'),
  ('group_members', 'group_id', 'bigint'),
  ('group_members', 'artist_id', 'bigint'),
  ('group_members', 'is_leader', 'tinyint'),
  ('group_members', 'joined_at', 'date'),
  ('group_members', 'left_at', 'date'),
  ('user_follows', 'follower_id', 'bigint'),
  ('user_follows', 'following_id', 'bigint'),
  ('user_follows', 'community_id', 'bigint'),
  ('user_follows', 'created_at', 'datetime'),
  ('membership', 'id', 'bigint'),
  ('membership', 'created_at', 'datetime'),
  ('membership', 'expires_at', 'datetime'),
  ('membership', 'artist_id', 'bigint'),
  ('membership', 'fan_id', 'bigint'),
  ('membership_period', 'id', 'bigint'),
  ('membership_period', 'fan_id', 'bigint'),
  ('membership_period', 'artist_id', 'bigint'),
  ('membership_period', 'started_at', 'datetime'),
  ('membership_period', 'expires_at', 'datetime'),
  ('membership_period', 'streak_count', 'int'),
  ('membership_period', 'created_at', 'datetime'),
  ('artist_block', 'id', 'bigint'),
  ('artist_block', 'artist_id', 'bigint'),
  ('artist_block', 'blocked_user_id', 'bigint'),
  ('artist_block', 'reason', 'varchar'),
  ('artist_block', 'created_at', 'datetime'),
  ('artist_schedule', 'id', 'bigint'),
  ('artist_schedule', 'artist_id', 'bigint'),
  ('artist_schedule', 'category', 'varchar'),
  ('artist_schedule', 'title', 'varchar'),
  ('artist_schedule', 'description', 'text'),
  ('artist_schedule', 'location', 'varchar'),
  ('artist_schedule', 'ticket_url', 'varchar'),
  ('artist_schedule', 'schedule_at', 'datetime'),
  ('artist_schedule', 'created_at', 'datetime'),
  ('artist_schedule', 'updated_at', 'datetime'),
  ('artist_attendance', 'id', 'bigint'),
  ('artist_attendance', 'artist_id', 'bigint'),
  ('artist_attendance', 'visit_date', 'date'),
  ('artist_attendance', 'paw_color', 'varchar'),
  ('artist_attendance', 'created_at', 'datetime'),
  ('portal_notice', 'id', 'bigint'),
  ('portal_notice', 'artist_id', 'bigint'),
  ('portal_notice', 'title', 'varchar'),
  ('portal_notice', 'content', 'text'),
  ('portal_notice', 'published', 'bit'),
  ('portal_notice', 'pinned', 'bit'),
  ('portal_notice', 'pin_order', 'int'),
  ('portal_notice', 'created_at', 'datetime'),
  ('portal_notice', 'updated_at', 'datetime'),
  ('live_session', 'id', 'bigint'),
  ('live_session', 'artist_id', 'bigint'),
  ('live_session', 'host_id', 'bigint'),
  ('live_session', 'status', 'varchar'),
  ('live_session', 'started_at', 'datetime'),
  ('live_session', 'ended_at', 'datetime'),
  ('live_comment', 'id', 'bigint'),
  ('live_comment', 'session_id', 'bigint'),
  ('live_comment', 'author_id', 'bigint'),
  ('live_comment', 'content', 'varchar'),
  ('live_comment', 'created_at', 'datetime'),
  ('live_comment_report', 'id', 'bigint'),
  ('live_comment_report', 'comment_id', 'bigint'),
  ('live_comment_report', 'reporter_id', 'bigint'),
  ('live_comment_report', 'reason', 'varchar'),
  ('live_comment_report', 'created_at', 'datetime'),
  ('site_notice', 'id', 'bigint'),
  ('site_notice', 'author_id', 'bigint'),
  ('site_notice', 'title', 'varchar'),
  ('site_notice', 'category', 'enum'),
  ('site_notice', 'content', 'text'),
  ('site_notice', 'published', 'bit'),
  ('site_notice', 'publish_at', 'datetime'),
  ('site_notice', 'pinned', 'bit'),
  ('site_notice', 'pin_order', 'int'),
  ('site_notice', 'created_at', 'datetime'),
  ('site_notice', 'updated_at', 'datetime'),
  ('community_members', 'id', 'bigint'),
  ('community_members', 'fan_id', 'bigint'),
  ('community_members', 'artist_id', 'bigint'),
  ('community_members', 'joined_at', 'datetime'),
  ('community_profiles', 'id', 'bigint'),
  ('community_profiles', 'community_member_id', 'bigint'),
  ('community_profiles', 'nickname', 'varchar'),
  ('community_profiles', 'bio', 'varchar'),
  ('community_profiles', 'avatar_stored_name', 'varchar'),
  ('community_profiles', 'background_stored_name', 'varchar'),
  ('community_profiles', 'content_hidden', 'tinyint'),
  ('community_profiles', 'created_at', 'datetime'),
  ('community_profiles', 'updated_at', 'datetime'),
  ('post', 'id', 'bigint'),
  ('post', 'board_type', 'enum'),
  ('post', 'artist_id', 'bigint'),
  ('post', 'content', 'text'),
  ('post', 'created_at', 'datetime'),
  ('post', 'title', 'varchar'),
  ('post', 'author_id', 'bigint'),
  ('post', 'like_count', 'int'),
  ('post', 'hidden_from_artist', 'tinyint'),
  ('post', 'link_url', 'varchar'),
  ('post_attachment', 'id', 'bigint'),
  ('post_attachment', 'content_type', 'varchar'),
  ('post_attachment', 'created_at', 'datetime'),
  ('post_attachment', 'file_size', 'bigint'),
  ('post_attachment', 'original_name', 'varchar'),
  ('post_attachment', 'stored_name', 'varchar'),
  ('post_attachment', 'post_id', 'bigint'),
  ('post_like', 'id', 'bigint'),
  ('post_like', 'created_at', 'datetime'),
  ('post_like', 'post_id', 'bigint'),
  ('post_like', 'user_id', 'bigint'),
  ('post_bookmark', 'id', 'bigint'),
  ('post_bookmark', 'created_at', 'datetime'),
  ('post_bookmark', 'post_id', 'bigint'),
  ('post_bookmark', 'user_id', 'bigint'),
  ('comment', 'id', 'bigint'),
  ('comment', 'content', 'varchar'),
  ('comment', 'created_at', 'datetime'),
  ('comment', 'author_id', 'bigint'),
  ('comment', 'post_id', 'bigint'),
  ('comment_report', 'id', 'bigint'),
  ('comment_report', 'created_at', 'datetime'),
  ('comment_report', 'reason', 'enum'),
  ('comment_report', 'status', 'enum'),
  ('comment_report', 'resolved_at', 'datetime'),
  ('comment_report', 'comment_id', 'bigint'),
  ('comment_report', 'reporter_id', 'bigint'),
  ('report', 'id', 'bigint'),
  ('report', 'created_at', 'datetime'),
  ('report', 'reason', 'enum'),
  ('report', 'status', 'enum'),
  ('report', 'resolved_at', 'datetime'),
  ('report', 'post_id', 'bigint'),
  ('report', 'reporter_id', 'bigint'),
  ('board_media', 'id', 'bigint'),
  ('board_media', 'group_id', 'bigint'),
  ('board_media', 'uploader_id', 'bigint'),
  ('board_media', 'title', 'varchar'),
  ('board_media', 'content', 'varchar'),
  ('board_media', 'created_at', 'datetime'),
  ('board_media', 'updated_at', 'datetime'),
  ('board_media', 'deleted_at', 'datetime'),
  ('board_media', 'like_count', 'int'),
  ('board_media_files', 'id', 'bigint'),
  ('board_media_files', 'board_id', 'bigint'),
  ('board_media_files', 'original_name', 'varchar'),
  ('board_media_files', 'stored_name', 'varchar'),
  ('board_media_files', 'content_type', 'varchar'),
  ('board_media_files', 'media_type', 'varchar'),
  ('board_media_files', 'file_size', 'bigint'),
  ('board_media_files', 'sort_order', 'int'),
  ('board_media_files', 'created_at', 'datetime'),
  ('board_media_like', 'id', 'bigint'),
  ('board_media_like', 'board_id', 'bigint'),
  ('board_media_like', 'user_id', 'bigint'),
  ('board_media_like', 'created_at', 'datetime'),
  ('shop_goods', 'id', 'bigint'),
  ('shop_goods', 'artist_id', 'bigint'),
  ('shop_goods', 'name', 'varchar'),
  ('shop_goods', 'description', 'text'),
  ('shop_goods', 'price', 'int'),
  ('shop_goods', 'thumbnail_url', 'varchar'),
  ('shop_goods', 'official_url', 'varchar'),
  ('shop_goods', 'status', 'varchar'),
  ('shop_goods', 'sort_order', 'int'),
  ('shop_goods', 'membership_only', 'tinyint'),
  ('shop_goods', 'deleted_at', 'datetime'),
  ('shop_goods', 'created_at', 'datetime'),
  ('shop_goods', 'updated_at', 'datetime'),
  ('shop_goods_category', 'id', 'bigint'),
  ('shop_goods_category', 'goods_id', 'bigint'),
  ('shop_goods_category', 'category', 'varchar'),
  ('shop_goods_option', 'id', 'bigint'),
  ('shop_goods_option', 'goods_id', 'bigint'),
  ('shop_goods_option', 'category', 'varchar'),
  ('shop_goods_option', 'option_key', 'varchar'),
  ('shop_goods_option', 'option_value', 'varchar'),
  ('shop_goods_variant', 'id', 'bigint'),
  ('shop_goods_variant', 'goods_id', 'bigint'),
  ('shop_goods_variant', 'option_key', 'varchar'),
  ('shop_goods_variant', 'option_value', 'varchar'),
  ('shop_goods_variant', 'stock_quantity', 'int'),
  ('shop_cart_item', 'id', 'bigint'),
  ('shop_cart_item', 'user_id', 'bigint'),
  ('shop_cart_item', 'product_id', 'varchar'),
  ('shop_cart_item', 'quantity', 'int'),
  ('shop_cart_item', 'unit_price', 'int'),
  ('shop_cart_item', 'created_at', 'datetime'),
  ('shop_cart_item', 'updated_at', 'datetime'),
  ('chat_message', 'id', 'bigint'),
  ('chat_message', 'content', 'text'),
  ('chat_message', 'created_at', 'datetime'),
  ('chat_message', 'artist_id', 'bigint'),
  ('chat_message', 'fan_id', 'bigint'),
  ('chat_message', 'sender_id', 'bigint'),
  ('chat_message', 'visible_to_artist', 'tinyint'),
  ('chat_quota', 'id', 'bigint'),
  ('chat_quota', 'charged_date', 'date'),
  ('chat_quota', 'remaining_count', 'int'),
  ('chat_quota', 'artist_id', 'bigint'),
  ('chat_quota', 'fan_id', 'bigint'),
  ('fan_badge_ownership', 'id', 'bigint'),
  ('fan_badge_ownership', 'fan_id', 'bigint'),
  ('fan_badge_ownership', 'artist_id', 'bigint'),
  ('fan_badge_ownership', 'badge_code', 'varchar'),
  ('fan_badge_ownership', 'badge_name', 'varchar'),
  ('fan_badge_ownership', 'badge_type', 'varchar'),
  ('fan_badge_ownership', 'awarded_at', 'datetime'),
  ('fan_badge_ownership', 'revoked_at', 'datetime'),
  ('fan_badge_ownership', 'awarded_by', 'bigint'),
  ('fan_badge_ownership', 'created_at', 'datetime'),
  ('email_verification', 'id', 'bigint'),
  ('email_verification', 'user_id', 'bigint'),
  ('email_verification', 'email', 'varchar'),
  ('email_verification', 'purpose', 'varchar'),
  ('email_verification', 'verification_key', 'varchar'),
  ('email_verification', 'code_hash', 'varchar'),
  ('email_verification', 'attempt_count', 'int'),
  ('email_verification', 'expires_at', 'datetime'),
  ('email_verification', 'verified_at', 'datetime'),
  ('email_verification', 'consumed_at', 'datetime'),
  ('email_verification', 'created_at', 'datetime'),
  ('email_verification', 'updated_at', 'datetime'),
  ('fan_project', 'id', 'bigint'),
  ('fan_project', 'artist_id', 'bigint'),
  ('fan_project', 'creator_id', 'bigint'),
  ('fan_project', 'title', 'varchar'),
  ('fan_project', 'event_type', 'varchar'),
  ('fan_project', 'goal_amount', 'bigint'),
  ('fan_project', 'funding_start_at', 'datetime'),
  ('fan_project', 'funding_end_at', 'datetime'),
  ('fan_project', 'description', 'varchar'),
  ('fan_project', 'status', 'varchar'),
  ('fan_project', 'special_badge_count_at_apply', 'int'),
  ('fan_project', 'basic_badge_count_at_apply', 'int'),
  ('fan_project', 'eligibility_rule_code', 'varchar'),
  ('fan_project', 'identity_verification_method', 'varchar'),
  ('fan_project', 'identity_verified_at', 'datetime'),
  ('fan_project', 'reviewed_by', 'bigint'),
  ('fan_project', 'reviewed_at', 'datetime'),
  ('fan_project', 'rejection_reason', 'varchar'),
  ('fan_project', 'created_at', 'datetime'),
  ('fan_project', 'updated_at', 'datetime'),
  ('fan_project', 'deleted_at', 'datetime'),
  ('fan_project_cover_image', 'id', 'bigint'),
  ('fan_project_cover_image', 'project_id', 'bigint'),
  ('fan_project_cover_image', 'original_name', 'varchar'),
  ('fan_project_cover_image', 'stored_name', 'varchar'),
  ('fan_project_cover_image', 'content_type', 'varchar'),
  ('fan_project_cover_image', 'file_size', 'bigint'),
  ('fan_project_cover_image', 'created_at', 'datetime'),
  ('fan_project_contribution', 'id', 'bigint'),
  ('fan_project_contribution', 'project_id', 'bigint'),
  ('fan_project_contribution', 'contributor_id', 'bigint'),
  ('fan_project_contribution', 'order_no', 'varchar'),
  ('fan_project_contribution', 'idempotency_key', 'varchar'),
  ('fan_project_contribution', 'payment_provider', 'varchar'),
  ('fan_project_contribution', 'provider_transaction_id', 'varchar'),
  ('fan_project_contribution', 'amount', 'bigint'),
  ('fan_project_contribution', 'virtual_bank_code', 'varchar'),
  ('fan_project_contribution', 'virtual_account_number', 'varbinary'),
  ('fan_project_contribution', 'due_date', 'datetime'),
  ('fan_project_contribution', 'deposit_secret', 'varchar'),
  ('fan_project_contribution', 'is_anonymous', 'tinyint'),
  ('fan_project_contribution', 'refund_policy_agreed_at', 'datetime'),
  ('fan_project_contribution', 'refund_amount', 'bigint'),
  ('fan_project_contribution', 'payment_status', 'varchar'),
  ('fan_project_contribution', 'paid_at', 'datetime'),
  ('fan_project_contribution', 'cancelled_at', 'datetime'),
  ('fan_project_contribution', 'refunded_at', 'datetime'),
  ('fan_project_contribution', 'refund_reason', 'varchar'),
  ('fan_project_contribution', 'created_at', 'datetime'),
  ('fan_project_contribution', 'updated_at', 'datetime'),
  ('fan_project_settlement_account', 'id', 'bigint'),
  ('fan_project_settlement_account', 'project_id', 'bigint'),
  ('fan_project_settlement_account', 'bank_code', 'char'),
  ('fan_project_settlement_account', 'account_number_enc', 'varbinary'),
  ('fan_project_settlement_account', 'account_number_hmac', 'char'),
  ('fan_project_settlement_account', 'account_number_last4', 'char'),
  ('fan_project_settlement_account', 'verification_status', 'varchar'),
  ('fan_project_settlement_account', 'verified_at', 'datetime'),
  ('fan_project_settlement_account', 'created_at', 'datetime'),
  ('fan_project_settlement_account', 'updated_at', 'datetime'),
  ('fan_project_fraud_check', 'id', 'bigint'),
  ('fan_project_fraud_check', 'project_id', 'bigint'),
  ('fan_project_fraud_check', 'target_type', 'varchar'),
  ('fan_project_fraud_check', 'target_fingerprint', 'char'),
  ('fan_project_fraud_check', 'bank_code', 'char'),
  ('fan_project_fraud_check', 'provider', 'varchar'),
  ('fan_project_fraud_check', 'provider_result_code', 'int'),
  ('fan_project_fraud_check', 'result_status', 'varchar'),
  ('fan_project_fraud_check', 'caution_yn', 'char'),
  ('fan_project_fraud_check', 'search_window_start_at', 'datetime'),
  ('fan_project_fraud_check', 'search_window_end_at', 'datetime'),
  ('fan_project_fraud_check', 'checked_at', 'datetime'),
  ('fan_project_fraud_check', 'requested_by', 'bigint'),
  ('fan_project_fraud_check', 'created_at', 'datetime'),
  ('group_schedule', 'id', 'bigint'),
  ('group_schedule', 'group_id', 'bigint'),
  ('group_schedule', 'created_by', 'bigint'),
  ('group_schedule', 'event_type', 'varchar'),
  ('group_schedule', 'title', 'varchar'),
  ('group_schedule', 'place', 'varchar'),
  ('group_schedule', 'start_at', 'datetime'),
  ('group_schedule', 'end_at', 'datetime'),
  ('group_schedule', 'ticket_url', 'varchar'),
  ('group_schedule', 'ticket_image_stored_name', 'varchar'),
  ('group_schedule', 'created_at', 'datetime'),
  ('group_schedule', 'updated_at', 'datetime'),
  ('group_schedule', 'deleted_at', 'datetime'),
  ('notification', 'id', 'bigint'),
  ('notification', 'recipient_id', 'bigint'),
  ('notification', 'type', 'varchar'),
  ('notification', 'title', 'varchar'),
  ('notification', 'message', 'varchar'),
  ('notification', 'source_type', 'varchar'),
  ('notification', 'source_id', 'bigint'),
  ('notification', 'is_read', 'tinyint'),
  ('notification', 'created_at', 'datetime'),
  ('notification_setting', 'user_id', 'bigint'),
  ('notification_setting', 'type', 'varchar'),
  ('notification_setting', 'enabled', 'tinyint');

SELECT e.`table_name`, e.`column_name`, e.`data_type` AS expected_type, c.DATA_TYPE AS actual_type,
       CASE
         WHEN t.TABLE_NAME IS NULL THEN 'MISSING_TABLE'
         WHEN c.COLUMN_NAME IS NULL THEN 'MISSING_COLUMN'
         ELSE 'TYPE_MISMATCH'
       END AS problem
FROM `wp_expected_columns` e
LEFT JOIN information_schema.TABLES t
       ON t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = e.`table_name`
LEFT JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = e.`table_name` AND c.COLUMN_NAME = e.`column_name`
WHERE t.TABLE_NAME IS NULL
   OR c.COLUMN_NAME IS NULL
   OR c.DATA_TYPE <> e.`data_type`
ORDER BY e.`table_name`, e.`column_name`;

-- ============================================================
-- [검증 2] 스키마에는 없는데 DB에만 남아 있고, NOT NULL이라 기본값도 없는 컬럼.
--   브랜치를 오가며 동기화하면 생길 수 있다. 엔티티가 이 컬럼을 채우지 않으므로
--   INSERT가 "doesn't have a default value"로 실패한다(앱 기동은 정상이라 발견이 늦다).
--   행이 나오면 해당 컬럼을 NULL 허용으로 바꾸면 된다:
--     ALTER TABLE <table> MODIFY COLUMN <column> <타입> NULL;
-- ============================================================
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.COLUMN_TYPE
FROM information_schema.COLUMNS c
JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
LEFT JOIN `wp_expected_columns` e
  ON e.`table_name` = c.TABLE_NAME AND e.`column_name` = c.COLUMN_NAME
WHERE c.TABLE_SCHEMA = DATABASE()
  AND t.TABLE_TYPE = 'BASE TABLE'
  AND e.`column_name` IS NULL
  AND c.IS_NULLABLE = 'NO'
  AND c.COLUMN_DEFAULT IS NULL
  AND c.EXTRA NOT LIKE '%auto_increment%'
  AND c.EXTRA NOT LIKE '%GENERATED%'
ORDER BY c.TABLE_NAME, c.COLUMN_NAME;

DROP TEMPORARY TABLE IF EXISTS `wp_expected_columns`;
