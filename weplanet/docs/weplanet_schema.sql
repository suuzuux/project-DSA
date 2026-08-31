-- ============================================================
-- WePlaNet 통합 스키마 + 테스트 데이터
-- ------------------------------------------------------------
-- 이 파일 하나만 실행하면 DB가 완성됩니다.
-- 기존에 흩어져 있던 SQL과 팬 프로젝트 최신 스키마를 합친 것입니다.
-- 팀 공통 스키마, 팬 프로젝트 최신 스키마, 테스트 계정과 배지 시드를 포함합니다.
--
-- ------------------------------------------------------------
-- 구성
--   [1] 스키마 : 테이블 40개 (DROP -> CREATE) + 배지 카탈로그 25종
--   [2] 테스트 계정 시드 : 6개 (비밀번호 전부 weplanet1234!)
--   [3] 배지 소유 / 팔로우 시드
--
-- ------------------------------------------------------------
-- !! 주의 : [1]에 DROP TABLE 이 포함되어 있습니다.
--          실행하면 기존 데이터가 전부 지워집니다.
--
-- 실행 방법 (한글 깨짐 방지를 위해 charset 옵션 필수)
--   mysql -uroot -p --default-character-set=utf8mb4 < docs/weplanet_schema.sql
--
-- 테스트 계정 (비밀번호 공통: weplanet1234!)
--   artist_hwiwon   ARTIST  휘원공주
--   artist_jungsik  ARTIST  정식왕자
--   asd123          FAN     빛나는여우135
--   qatest99        FAN     QA테스터
--   admin_test      ADMIN   관리자테스트   <- 금칙어 관리 화면(/chat/admin/keywords)
--   aifan_bot       FAN     AI팬봇
-- ============================================================

-- ============================================================
-- [1] 스키마 + 배지 카탈로그
-- ============================================================
CREATE DATABASE IF NOT EXISTS `weplanet`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `weplanet`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;

-- ------------------------------------------------------------
-- DROP (자식 → 부모 역순)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `community_profiles`;
DROP TABLE IF EXISTS `community_members`;
DROP TABLE IF EXISTS `board_media_files`;
DROP TABLE IF EXISTS `board_media`;
DROP TABLE IF EXISTS `comment_report`;
DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS `report`;
DROP TABLE IF EXISTS `post_like`;
DROP TABLE IF EXISTS `post_bookmark`;
DROP TABLE IF EXISTS `post_attachment`;
DROP TABLE IF EXISTS `post`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_quota`;
DROP TABLE IF EXISTS `portal_notice`;
DROP TABLE IF EXISTS `artist_block`;
DROP TABLE IF EXISTS `artist_attendance`;
DROP TABLE IF EXISTS `artist_schedule`;
DROP TABLE IF EXISTS `artist_profile`;
DROP TABLE IF EXISTS `artist_group_profiles`;
DROP TABLE IF EXISTS `fan_project_fraud_check`;
DROP TABLE IF EXISTS `fan_project_settlement_account`;
DROP TABLE IF EXISTS `fan_project_contribution`;
DROP TABLE IF EXISTS `fan_project_cover_image`;
DROP TABLE IF EXISTS `fan_project`;
DROP TABLE IF EXISTS `email_verification`;
DROP TABLE IF EXISTS `fan_badge_ownership`;
DROP TABLE IF EXISTS `fan_badge`;
DROP TABLE IF EXISTS `group_schedule`;
DROP TABLE IF EXISTS `notification_setting`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `group_follow`;
DROP TABLE IF EXISTS `group_members`;
DROP TABLE IF EXISTS `membership`;
DROP TABLE IF EXISTS `admin_action_logs`;
DROP TABLE IF EXISTS `admin_profiles`;
DROP TABLE IF EXISTS `agency_profiles`;
DROP TABLE IF EXISTS `artist_profiles`;
DROP TABLE IF EXISTS `artist_groups`;
DROP TABLE IF EXISTS `filter_keyword`;
DROP TABLE IF EXISTS `agencies`;
DROP TABLE IF EXISTS `users`;

-- ------------------------------------------------------------
-- CREATE (부모 → 자식)
-- ------------------------------------------------------------

-- users: 팬/아티스트/소속사/관리자 공통 계정 (AUTH)
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '회원 PK',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '로그인 아이디',
  `password` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '비밀번호(BCrypt 해시)',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '역할: FAN/ARTIST/AGENCY/ADMIN',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태: ACTIVE/DORMANT/SUSPENDED/WITHDRAWN',
  `real_name` varbinary(255) NOT NULL COMMENT '실명(암호화 저장)',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '플랫폼 공통 닉네임(미입력 시 자동생성)',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이메일(로그인·알림 수신)',
  `phone` varbinary(255) DEFAULT NULL COMMENT '휴대폰번호(암호화 저장)',
  `phone_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '휴대폰 SHA-256 해시(검색·중복확인)',
  `birth_date` date DEFAULT NULL COMMENT '생년월일',
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '성별: MALE/FEMALE/OTHER',
  `zipcode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '우편번호(배송·지역알림 확장용)',
  `address1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '기본 주소',
  `address2` varbinary(512) DEFAULT NULL COMMENT '상세주소(암호화 저장)',
  `email_verified_at` datetime(6) DEFAULT NULL COMMENT '이메일 인증 완료 시각(NULL=미인증)',
  `last_login_at` datetime(6) DEFAULT NULL COMMENT '최종 로그인 시각',
  `created_at` datetime(6) NOT NULL COMMENT '가입 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '정보 수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '탈퇴(soft delete) 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_nickname` (`nickname`),
  KEY `idx_users_role_status` (`role`, `status`),
  KEY `idx_users_phone_hash` (`phone_hash`),
  CONSTRAINT `ck_users_gender` CHECK ((`gender` IS NULL) OR (`gender` IN (_utf8mb4'MALE', _utf8mb4'FEMALE', _utf8mb4'OTHER'))),
  CONSTRAINT `ck_users_role` CHECK (`role` IN (_utf8mb4'FAN', _utf8mb4'ARTIST', _utf8mb4'AGENCY', _utf8mb4'ADMIN')),
  CONSTRAINT `ck_users_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'DORMANT', _utf8mb4'SUSPENDED', _utf8mb4'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공통 회원 계정';

-- email_verification: 이메일 인증 기록
CREATE TABLE email_verification (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '이메일 인증 PK',
  user_id bigint DEFAULT NULL COMMENT '프로젝트 인증 회원(users.id), 회원가입 인증은 NULL',
  email varchar(255) NOT NULL COMMENT '인증 대상 이메일',
  purpose varchar(30) NOT NULL COMMENT '인증 목적: SIGNUP/FAN_PROJECT_CREATE',
  verification_key varchar(36) NOT NULL COMMENT '인증 요청 식별 UUID',
  code_hash varchar(255) NOT NULL COMMENT '인증번호 BCrypt 해시',
  attempt_count int NOT NULL DEFAULT 0 COMMENT '인증번호 실패 횟수',
  expires_at datetime(6) NOT NULL COMMENT '인증 만료 시각',
  verified_at datetime(6) DEFAULT NULL COMMENT '인증 완료 시각',
  consumed_at datetime(6) DEFAULT NULL COMMENT '회원가입/프로젝트 등록에 사용된 시각',
  created_at datetime(6) NOT NULL COMMENT '인증 요청 생성 시각',
  updated_at datetime(6) NOT NULL COMMENT '인증 정보 수정 시각',

  PRIMARY KEY (id),
  UNIQUE KEY uk_email_verification_key (verification_key),
  KEY idx_email_verification_email (email, purpose, created_at),
  KEY idx_email_verification_user (user_id, purpose, created_at),

  CONSTRAINT fk_email_verification_user
   FOREIGN KEY (user_id) REFERENCES users (id)
   ON DELETE CASCADE,

  CONSTRAINT ck_email_verification_purpose
   CHECK (purpose IN ('SIGNUP', 'FAN_PROJECT_CREATE')),

  CONSTRAINT ck_email_verification_attempt_count
   CHECK (attempt_count BETWEEN 0 AND 5),

  CONSTRAINT ck_email_verification_consumed
   CHECK (consumed_at IS NULL OR verified_at IS NOT NULL),

  CONSTRAINT ck_email_verification_expiration
   CHECK (expires_at > created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='회원가입 및 팬 프로젝트 이메일 인증';

-- agencies: 소속사 마스터
CREATE TABLE `agencies` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '소속사 PK',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '소속사명',
  `business_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사업자등록번호',
  `ceo_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '대표자명',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '소속사 상태: ACTIVE/SUSPENDED',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agencies_name` (`name`),
  UNIQUE KEY `uk_agencies_bizno` (`business_no`),
  CONSTRAINT `ck_agencies_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소속사 마스터';

-- filter_keyword: 채팅 금칙어 (CHAT-03/04)
CREATE TABLE `filter_keyword` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '키워드 PK',
  `keyword` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '필터링 대상 문자열',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj55c0tyqc5n2qto80hyjpegy1` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채팅 부적절 언어 필터 키워드';

-- admin_profiles: 플랫폼 운영자 확장 프로필
CREATE TABLE `admin_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (ADMIN 1:1)',
  `admin_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STAFF' COMMENT '관리 등급: SUPER/STAFF',
  `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '소속 부서',
  `employee_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '사번',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_adp_empno` (`employee_no`),
  CONSTRAINT `fk_adp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_adp_level` CHECK (`admin_level` IN (_utf8mb4'SUPER', _utf8mb4'STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='운영자(ADMIN) 프로필';

-- admin_action_logs: 운영 조치 감사 로그
CREATE TABLE `admin_action_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '로그 PK',
  `actor_id` bigint NOT NULL COMMENT '조치 수행 운영자(users.id)',
  `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '조치 유형(제재/승인 등)',
  `target_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '대상 종류(USER/GROUP/AGENCY 등)',
  `target_id` bigint NOT NULL COMMENT '대상 ID(다형 참조)',
  `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '조치 사유',
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '요청 IP(IPv6 포함)',
  `created_at` datetime(6) NOT NULL COMMENT '기록 시각',
  PRIMARY KEY (`id`),
  KEY `idx_aal_actor` (`actor_id`, `created_at`),
  KEY `idx_aal_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_aal_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='운영자 조치 감사 로그';

-- agency_profiles: 소속사 담당자 계정 확장
CREATE TABLE `agency_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (AGENCY 1:1)',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '담당 부서',
  `position` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '직책',
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

-- artist_profiles: 아티스트 활동 메타(소속/스테이지명)
CREATE TABLE `artist_profiles` (
  `user_id` bigint NOT NULL COMMENT 'users.id (ARTIST 1:1)',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `stage_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '활동명(스테이지명)',
  `debut_date` date DEFAULT NULL COMMENT '개인 데뷔일',
  `position` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '포지션(보컬/래퍼 등)',
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '아티스트 소개글',
  `profile_img` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '프로필 이미지 URL/경로',
  PRIMARY KEY (`user_id`),
  KEY `idx_ap_agency` (`agency_id`),
  CONSTRAINT `fk_ap_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `fk_ap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 계정 전용 프로필';

-- artist_groups: 커뮤니티(그룹) 단위
CREATE TABLE `artist_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '그룹(커뮤니티) PK',
  `agency_id` bigint NOT NULL COMMENT '소속 소속사(agencies.id)',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '그룹명(한글)',
  `name_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '그룹명(영문)',
  `fandom_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '팬덤명',
  `debut_date` date DEFAULT NULL COMMENT '그룹 데뷔일',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '상태: ACTIVE/HIATUS/DISBANDED',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_name` (`name`),
  KEY `idx_group_agency` (`agency_id`),
  CONSTRAINT `fk_group_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
  CONSTRAINT `ck_group_status` CHECK (`status` IN (_utf8mb4'ACTIVE', _utf8mb4'HIATUS', _utf8mb4'DISBANDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아티스트 그룹/커뮤니티';

-- artist_profile: 포털 테마용 소개·배너
CREATE TABLE `artist_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '포털 프로필 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `intro` text COMMENT '커뮤니티 포털 소개문',
  `header_image_url` varchar(500) DEFAULT NULL COMMENT '헤더/배너 이미지 URL',
  `logo_image_url` varchar(500) DEFAULT NULL COMMENT '로고/아이콘 이미지 URL',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_profile_artist` (`artist_id`),
  CONSTRAINT `fk_artist_profile_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='아티스트 커뮤니티 포털 테마/소개';

-- artist_group_profiles: 탐색 필터용 메타 (EXPLORE-02)
CREATE TABLE `artist_group_profiles` (
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

-- group_members: 그룹-아티스트 소속 이력
CREATE TABLE `group_members` (
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

-- group_follow: 팬의 그룹 팔로우
CREATE TABLE `group_follow` (
  `fan_id` bigint NOT NULL COMMENT '팬(users.id)',
  `group_id` bigint NOT NULL COMMENT '팔로우한 그룹(artist_groups.id)',
  `created_at` datetime(6) NOT NULL COMMENT '팔로우 시각',
  PRIMARY KEY (`fan_id`, `group_id`),
  KEY `fk_gf_group` (`group_id`),
  CONSTRAINT `fk_gf_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_gf_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬–그룹 팔로우';

-- membership: 유료 멤버십 (DM 등 혜택)
CREATE TABLE `membership` (
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

-- artist_block: 아티스트의 유저 차단
CREATE TABLE `artist_block` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '차단 PK',
  `artist_id` bigint NOT NULL COMMENT '차단한 아티스트(users.id)',
  `blocked_user_id` bigint NOT NULL COMMENT '차단된 유저(users.id)',
  `reason` varchar(255) DEFAULT NULL COMMENT '차단 사유',
  `created_at` datetime NOT NULL COMMENT '차단 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_block` (`artist_id`, `blocked_user_id`),
  KEY `fk_artist_block_user` (`blocked_user_id`),
  KEY `idx_artist_block_artist_created_at` (`artist_id`, `created_at`),
  CONSTRAINT `fk_artist_block_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_artist_block_user` FOREIGN KEY (`blocked_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='아티스트 유저 차단';

-- artist_schedule: 아티스트 단위 일정
CREATE TABLE `artist_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '일정 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `category` varchar(30) DEFAULT 'OTHER' COMMENT '스케줄 카테고리',
  `title` varchar(200) NOT NULL COMMENT '일정 제목',
  `description` text COMMENT '일정 상세 설명',
  `location` varchar(255) DEFAULT NULL COMMENT '장소',
  `ticket_url` varchar(500) DEFAULT NULL COMMENT '티켓 URL',
  `schedule_at` datetime NOT NULL COMMENT '일정 일시',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_artist_schedule_artist_schedule_at` (`artist_id`, `schedule_at`),
  CONSTRAINT `fk_artist_schedule_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='아티스트 스케줄';

-- artist_attendance: 아티스트 홈페이지 방문 출석(팬에게 보이는 고양이 발바닥 도장)
CREATE TABLE `artist_attendance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '출석 PK',
  `artist_id` bigint NOT NULL COMMENT '아티스트(users.id)',
  `visit_date` date NOT NULL COMMENT '홈페이지 방문일',
  `paw_color` varchar(20) NOT NULL COMMENT '고양이 발바닥 색상(hex)',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_attendance_artist_date` (`artist_id`, `visit_date`),
  KEY `idx_artist_attendance_artist_date` (`artist_id`, `visit_date`),
  CONSTRAINT `fk_artist_attendance_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='아티스트 홈페이지 출석(발바닥)';

-- portal_notice: 커뮤니티 공지 (NOTICE)
CREATE TABLE `portal_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '공지 PK',
  `artist_id` bigint NOT NULL COMMENT '커뮤니티 아티스트(users.id)',
  `title` varchar(200) NOT NULL COMMENT '공지 제목',
  `content` text NOT NULL COMMENT '공지 본문',
  `published` bit(1) NOT NULL DEFAULT b'1' COMMENT '게시 여부(1=공개)',
  `created_at` datetime NOT NULL COMMENT '등록 시각',
  `updated_at` datetime NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_portal_notice_artist_created_at` (`artist_id`, `created_at`),
  CONSTRAINT `fk_portal_notice_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='아티스트 커뮤니티 공지';

-- community_members: 커뮤니티 가입 (EXPLORE-03)
CREATE TABLE `community_members` (
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

-- community_profiles: 커뮤니티별 프로필 (PROFILE-01)
CREATE TABLE `community_profiles` (
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

-- post: 팬/아티스트 게시판 글 (FEED)
CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '게시글 PK',
  `board_type` enum('ARTIST', 'FAN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '게시판 구분: ARTIST/FAN',
  `artist_id` bigint DEFAULT NULL COMMENT '커뮤니티 소속 아티스트(users.id)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '본문',
  `created_at` datetime(6) NOT NULL COMMENT '작성 시각',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '제목',
  `author_id` bigint DEFAULT NULL COMMENT '작성자(users.id)',
  `like_count` int NOT NULL COMMENT '좋아요 수(비정규화 카운트)',
  `hidden_from_artist` tinyint(1) NOT NULL DEFAULT '0' COMMENT '아티스트에게 숨김(신고/가리기)',
  `link_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '첨부 링크 URL',
  PRIMARY KEY (`id`),
  KEY `FK1mpebp1ayl0twrwm7ruiof778` (`author_id`),
  KEY `idx_post_artist_board` (`artist_id`, `board_type`, `created_at`),
  CONSTRAINT `FK1mpebp1ayl0twrwm7ruiof778` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_post_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='커뮤니티 게시글';

-- post_attachment: 게시글 첨부 파일
CREATE TABLE `post_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '첨부 PK',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 타입',
  `created_at` datetime(6) NOT NULL COMMENT '업로드 시각',
  `file_size` bigint DEFAULT NULL COMMENT '파일 크기(byte)',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '원본 파일명(표시용)',
  `stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `post_id` bigint NOT NULL COMMENT '소속 게시글(post.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgvqlowil11nsrhy82c6qqhcxa` (`stored_name`),
  KEY `FKmof1y73w0oea4caub8rpkhlmi` (`post_id`),
  CONSTRAINT `FKmof1y73w0oea4caub8rpkhlmi` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부파일';

-- post_like: 게시글 좋아요 (FEED-05)
CREATE TABLE `post_like` (
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

-- post_bookmark: 게시글 북마크
CREATE TABLE `post_bookmark` (
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

-- comment: 게시글 댓글 (FEED-04)
CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '댓글 PK',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '댓글 내용',
  `created_at` datetime(6) NOT NULL COMMENT '작성 시각',
  `author_id` bigint NOT NULL COMMENT '작성자(users.id)',
  `post_id` bigint NOT NULL COMMENT '원글(post.id)',
  PRIMARY KEY (`id`),
  KEY `FKir20vhrx08eh4itgpbfxip0s1` (`author_id`),
  KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
  CONSTRAINT `FKir20vhrx08eh4itgpbfxip0s1` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 댓글';

-- comment_report: 댓글 신고
CREATE TABLE `comment_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE', 'ETC', 'SEXUAL', 'SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `comment_id` bigint NOT NULL COMMENT '신고 대상 댓글(comment.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7a7j27uutr1ew9m87et35eily` (`comment_id`, `reporter_id`),
  KEY `FKn7ue556scerw6fa5epexg2g4j` (`reporter_id`),
  CONSTRAINT `FK8ugevhla12t9n0uw4o0rkvnth` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
  CONSTRAINT `FKn7ue556scerw6fa5epexg2g4j` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 신고';

-- report: 게시글 신고 (FEED-06)
CREATE TABLE `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE', 'ETC', 'SEXUAL', 'SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `post_id` bigint NOT NULL COMMENT '신고 대상 게시글(post.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK59baeft7frgypa05ajup9wrij` (`post_id`, `reporter_id`),
  KEY `FKqbhdxqd3ly7fkhly5nrl2j93k` (`reporter_id`),
  CONSTRAINT `FKnuqod1y014fp5bmqjeoffcgqy` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKqbhdxqd3ly7fkhly5nrl2j93k` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 신고';

-- board_media: 미디어 탭 게시글 (MEDIA-01/02)
CREATE TABLE `board_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '미디어 게시글 PK',
  `group_id` bigint NOT NULL COMMENT '그룹(artist_groups.id)',
  `uploader_id` bigint NOT NULL COMMENT '업로더(users.id, 주로 소속사)',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '제목',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '본문/캡션',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각',
  `deleted_at` datetime(6) DEFAULT NULL COMMENT '삭제(soft delete) 시각',
  PRIMARY KEY (`id`),
  KEY `idx_bm_group` (`group_id`, `created_at`),
  KEY `idx_bm_uploader` (`uploader_id`),
  CONSTRAINT `fk_bm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
  CONSTRAINT `fk_bm_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹별 미디어 게시판';

-- board_media_files: 미디어 첨부 (이미지/영상)
CREATE TABLE `board_media_files` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '미디어 파일 PK',
  `board_id` bigint NOT NULL COMMENT '미디어 게시글(board_media.id)',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '원본 파일명(표시용)',
  `stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '서버 저장 파일명',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 타입',
  `media_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '미디어 종류: IMAGE/VIDEO',
  `file_size` bigint DEFAULT NULL COMMENT '파일 크기(byte)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '표시 순서',
  `created_at` datetime(6) NOT NULL COMMENT '등록 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bmf_stored` (`stored_name`),
  KEY `idx_bmf_board` (`board_id`, `sort_order`),
  CONSTRAINT `fk_bmf_board` FOREIGN KEY (`board_id`) REFERENCES `board_media` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_bmf_media_type` CHECK (`media_type` IN (_utf8mb4'IMAGE', _utf8mb4'VIDEO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='미디어 게시글 첨부 파일';

-- chat_message: 팬–아티스트 DM (CHAT-01/02)
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '메시지 PK',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '메시지 본문',
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

-- chat_quota: 팬 메시지 전송 횟수 (CHAT-05)
CREATE TABLE `chat_quota` (
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

-- fan_badge: 배지 카탈로그(마스터). 모든 아티스트 공통이라 artist_id를 두지 않는다.
--            여기 등록된 행 수가 배지 달성률의 분모가 된다. (현재 BASIC 15 + SPECIAL 10 = 25)
CREATE TABLE `fan_badge` (
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

-- fan_badge_ownership: 팬이 "어느 아티스트 커뮤니티에서" 어떤 배지를 획득했는지
--                      배지 정의는 fan_badge(공통)이고, 획득은 아티스트별로 따로 쌓인다.
--                      fan_badge 와는 badge_code 로 연결한다. (FK를 걸지 않은 이유는 문서 하단 참고)
CREATE TABLE `fan_badge_ownership` (
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
  CONSTRAINT `fk_fan_badge_awarded_by` FOREIGN KEY (`awarded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_badge_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_fan_badge_period` CHECK ((`revoked_at` IS NULL) OR (`revoked_at` >= `awarded_at`)),
  CONSTRAINT `ck_fan_badge_type` CHECK (`badge_type` IN (_utf8mb4'BASIC', _utf8mb4'SPECIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 배지 보유/회수';


-- fan_project: 팬 프로젝트 개설·모금 (prjPROJECT)
CREATE TABLE `fan_project` (
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
  CONSTRAINT `fk_fan_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_fan_project_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
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

-- fan_project_cover_image: 프로젝트 대표 이미지
CREATE TABLE `fan_project_cover_image` (
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

-- fan_project_contribution: 후원/결제
CREATE TABLE `fan_project_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '후원/결제 PK',
  `project_id` bigint NOT NULL COMMENT '프로젝트(fan_project.id)',
  `contributor_id` bigint NOT NULL COMMENT '후원자(users.id)',
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문번호',
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '결제 멱등 키(중복요청 방지)',
  `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT '결제 제공자',
  `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '제공자 거래 ID',
  `amount` bigint NOT NULL COMMENT '결제 금액(원)',
  `depositor_name` varbinary(255) NOT NULL COMMENT '입금자명(현재 변환 저장, 추후 암호화)',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '0' COMMENT '닉네임 비공개 참여 여부: 0/1',
  `refund_policy_agreed_at` datetime(6) NOT NULL COMMENT '환불 규정 동의 시각',
  `refund_amount` bigint NOT NULL DEFAULT '0' COMMENT '환불 금액(원)',
  `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY' COMMENT '결제 상태: READY/PAID/FAILED 등',
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
    _utf8mb4'READY', _utf8mb4'PAID', _utf8mb4'FAILED',
    _utf8mb4'CANCELLED', _utf8mb4'REFUND_REQUESTED', _utf8mb4'REFUNDED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팬 프로젝트 후원/결제';

-- fan_project_settlement_account: 정산 계좌
CREATE TABLE `fan_project_settlement_account` (
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

-- fan_project_fraud_check: 사기조회 결과
CREATE TABLE `fan_project_fraud_check` (
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

-- group_schedule: 그룹 캘린더 일정 (SCHEDULE)
CREATE TABLE `group_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '그룹 일정 PK',
  `group_id` bigint NOT NULL COMMENT '그룹(artist_groups.id)',
  `created_by` bigint NOT NULL COMMENT '등록자(users.id)',
  `event_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '유형: BROADCAST/LIVE/TICKET_OPEN/CONCERT',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '일정 제목',
  `place` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '장소',
  `start_at` datetime(6) NOT NULL COMMENT '시작 시각',
  `end_at` datetime(6) DEFAULT NULL COMMENT '종료 시각',
  `ticket_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '티켓 링크',
  `ticket_image_stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '티켓 이미지 저장명',
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

-- notification: 알림함 (ALARM-01/02)
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '알림 PK',
  `recipient_id` bigint NOT NULL COMMENT '수신자(users.id)',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 유형(공지/댓글/채팅 등)',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 제목',
  `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 본문',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '출처 엔티티 종류(다형)',
  `source_id` bigint DEFAULT NULL COMMENT '출처 엔티티 ID',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '읽음 여부(0=미읽음)',
  `created_at` datetime(6) NOT NULL COMMENT '발송 시각',
  PRIMARY KEY (`id`),
  KEY `idx_noti_recipient` (`recipient_id`, `is_read`, `created_at`),
  CONSTRAINT `fk_noti_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 알림';

-- notification_setting: 알림 수신 설정 (ALARM-03)
CREATE TABLE `notification_setting` (
  `user_id` bigint NOT NULL COMMENT '회원(users.id)',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '알림 유형',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '해당 유형 수신 on/off',
  PRIMARY KEY (`user_id`, `type`),
  CONSTRAINT `fk_ns_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림 유형별 수신 설정';

-- ------------------------------------------------------------
-- 배지 카탈로그 25종 (일반 15 + 스페셜 10) - 전 아티스트 공통
-- 여러 번 실행해도 안전하도록 INSERT IGNORE 사용 (badge_code 가 유니크)
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge`
  (`badge_code`, `badge_name`, `badge_type`, `icon`, `description`, `sort_order`, `created_at`)
VALUES
  -- 일반 배지 15종
  ('BASIC_FIRST_JOIN',    '커뮤니티 첫 가입',      'BASIC', '🎉', '커뮤니티에 처음 가입하면 획득',              1,  NOW(6)),
  ('BASIC_FIRST_POST',    '첫 게시글 작성',        'BASIC', '✍️', '팬 게시판에 첫 글을 쓰면 획득',           2,  NOW(6)),
  ('BASIC_COMMENT_5',     '댓글 5개 작성',         'BASIC', '💬', '이 커뮤니티에 댓글 5개를 쓰면 획득',      3,  NOW(6)),
  ('BASIC_MEDIA_VIEW',    '미디어 시청',           'BASIC', '🎬', 'Media 탭 콘텐츠를 보면 획득',             4,  NOW(6)),
  ('BASIC_DAY_100',       '가입 후 100일',         'BASIC', '💯', '가입 후 100일이 지나면 획득',        5,  NOW(6)),
  ('BASIC_DAY_200',       '가입 후 200일',         'BASIC', '📅', '가입 후 200일이 지나면 획득',        6,  NOW(6)),
  ('BASIC_DAY_300',       '가입 후 300일',         'BASIC', '🗓️', '가입 후 300일이 지나면 획득',        7,  NOW(6)),
  ('BASIC_LIKE_10',       '좋아요 10개',           'BASIC', '👍', '게시글에 좋아요를 10번 누르면 획득',          8,  NOW(6)),
  ('BASIC_LIKED_5',       '받은 좋아요 5개',       'BASIC', '❤️', '내 게시글이 좋아요 5개를 받으면 획득',        9,  NOW(6)),
  ('BASIC_FOLLOW_ARTIST', '아티스트 프로필 팔로우', 'BASIC', '⭐', '아티스트 프로필을 팔로우하면 획득',          10, NOW(6)),
  ('BASIC_SHOP_PURCHASE', '샵 구매',               'BASIC', '🛍️', 'Shop에서 상품을 구매하면 획득',              11, NOW(6)),
  ('BASIC_LIVE_VIEW',     '라이브 시청',           'BASIC', '📡', 'Live 방송을 보면 획득',                  12, NOW(6)),
  ('BASIC_YEAR_1',        '커뮤니티 가입 후 1년',  'BASIC', '🥉', '가입 후 1년이 지나면 획득',         13, NOW(6)),
  ('BASIC_YEAR_2',        '커뮤니티 가입 후 2년',  'BASIC', '🥈', '가입 후 2년이 지나면 획득',         14, NOW(6)),
  ('BASIC_YEAR_3',        '커뮤니티 가입 후 3년',  'BASIC', '🥇', '가입 후 3년이 지나면 획득',         15, NOW(6)),

  -- 스페셜 배지 10종
  ('SPECIAL_DEBUT_1',      '아티스트 데뷔 1주년',  'SPECIAL', '🎂', '데뷔 1주년을 함께하면 획득',        1,  NOW(6)),
  ('SPECIAL_DEBUT_2',      '아티스트 데뷔 2주년',  'SPECIAL', '🎊', '데뷔 2주년을 함께하면 획득',        2,  NOW(6)),
  ('SPECIAL_DEBUT_3',      '아티스트 데뷔 3주년',  'SPECIAL', '🏆', '데뷔 3주년을 함께하면 획득',        3,  NOW(6)),
  ('SPECIAL_FOLLOWER_10',  '팔로워 10명 달성',     'SPECIAL', '👥', '내 팔로워가 10명이 되면 획득',               4,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_1', '첫 멤버십 가입',       'SPECIAL', '💎', '멤버십에 처음 가입하면 획득',      5,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_2', '멤버십 연속 2년',      'SPECIAL', '💠', '멤버십을 2년 연속 유지하면 획득',            6,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_3', '멤버십 연속 3년',      'SPECIAL', '🔷', '멤버십을 3년 연속 유지하면 획득',            7,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_4', '멤버십 연속 4년',      'SPECIAL', '🔶', '멤버십을 4년 연속 유지하면 획득',            8,  NOW(6)),
  ('SPECIAL_MEMBERSHIP_5', '멤버십 연속 5년',      'SPECIAL', '👑', '멤버십을 5년 연속 유지하면 획득',            9,  NOW(6)),
  ('SPECIAL_PROJECT_CREATE','프로젝트 등록 달성',  'SPECIAL', '🚀', '팬 프로젝트를 등록하면 획득',               10, NOW(6));

SET UNIQUE_CHECKS = 1;
SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- [2] 테스트 계정 시드
-- ============================================================
INSERT IGNORE INTO `users`
  (`username`, `password`, `role`, `status`, `real_name`, `nickname`, `email`, `email_verified_at`, `created_at`, `updated_at`)
VALUES
  ('artist_hwiwon',  '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'ARTIST', 'ACTIVE', '휘원',    '휘원공주',      'hwiwon@weplanet.test',   NOW(6), NOW(6), NOW(6)),
  ('artist_jungsik', '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'ARTIST', 'ACTIVE', '정식',    '정식왕자',      'jungsik@weplanet.test', NOW(6), NOW(6), NOW(6)),
  ('asd123',         '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'FAN',    'ACTIVE', '김화평',  '빛나는여우135', 'asdojuasdoa@gmail.com', NOW(6), NOW(6), NOW(6)),
  ('qatest99',       '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'FAN',    'ACTIVE', 'QA테스터', 'QA테스터',     'qatest99@example.com',  NOW(6), NOW(6), NOW(6)),
  ('admin_test',     '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'ADMIN',  'ACTIVE', '관리자테스트', '관리자테스트', 'admin_test@weplanet.test', NOW(6), NOW(6), NOW(6)),
  ('aifan_bot',      '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq', 'FAN',    'ACTIVE', 'AI팬봇', 'AI팬봇', 'aifan_bot@weplanet.test', NOW(6), NOW(6), NOW(6));

-- 이미 계정이 있던 사람(=INSERT IGNORE로 스킵됨)도 비밀번호를 위 해시로 맞추고 싶으면 같이 실행:
UPDATE `users` SET `password` = '$2b$10$LoJ/IaLBEwYSO6MoOm/aC.5eh4LZw6ONIL2Mk05PB0ScDFV4.bnVq'
WHERE `username` IN ('artist_hwiwon', 'artist_jungsik', 'asd123', 'qatest99', 'admin_test', 'aifan_bot');

-- 테스트 FAN은 회원가입 이메일 인증이 완료된 상태여야 프로젝트용 재인증을 진행할 수 있다.
UPDATE `users`
SET `email_verified_at` = COALESCE(`email_verified_at`, NOW(6))
WHERE `username` IN ('asd123', 'qatest99', 'aifan_bot');


-- ============================================================
-- [3] 배지 소유 / 팔로우 시드 (테스트 계정용)
-- ============================================================
-- ------------------------------------------------------------
-- 1) 커뮤니티 가입 (group_follow)
--    group_id 는 그 아티스트의 users.id 와 같은 값으로 쓴다.
--    (artist_groups 를 MediaGroupDataInitializer 가 그렇게 시딩해둠)
-- ------------------------------------------------------------
INSERT IGNORE INTO `group_follow` (`fan_id`, `group_id`, `created_at`)
SELECT f.id, a.id, NOW(6)
FROM `users` f
         JOIN `users` a ON a.username IN ('artist_hwiwon', 'artist_jungsik')
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.id IN (SELECT id FROM `artist_groups`);


-- ------------------------------------------------------------
-- 2) 획득 배지 - 휘원공주 (일반 8 + 스페셜 2)
--    badge_name / badge_type 은 카탈로그에서 그대로 복사한다.
--    (수여 시점 값을 스냅샷으로 남기는 컬럼이라 직접 적지 않고 SELECT 로 가져옴)
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge_ownership`
    (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT f.id, a.id, b.badge_code, b.badge_name, b.badge_type, NOW(6), NOW(6)
FROM `users` f,
     `users` a,
     `fan_badge` b
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.username = 'artist_hwiwon'
  AND b.badge_code IN (
    -- 일반 8
                       'BASIC_FIRST_JOIN',
                       'BASIC_FIRST_POST',
                       'BASIC_COMMENT_5',
                       'BASIC_MEDIA_VIEW',
                       'BASIC_DAY_100',
                       'BASIC_LIKE_10',
                       'BASIC_LIKED_5',
                       'BASIC_FOLLOW_ARTIST',
    -- 스페셜 2
                       'SPECIAL_DEBUT_1',
                       'SPECIAL_MEMBERSHIP_1'
    );


-- ------------------------------------------------------------
-- 3) 획득 배지 - 정식왕자 (일반 3, 스페셜 0)
--    같은 팬이라도 아티스트가 다르면 배지가 따로 쌓인다는 걸 보여주기 위함
-- ------------------------------------------------------------
INSERT IGNORE INTO `fan_badge_ownership`
    (`fan_id`, `artist_id`, `badge_code`, `badge_name`, `badge_type`, `awarded_at`, `created_at`)
SELECT f.id, a.id, b.badge_code, b.badge_name, b.badge_type, NOW(6), NOW(6)
FROM `users` f,
     `users` a,
     `fan_badge` b
WHERE f.username IN ('hwiwhi', 'asd123')
  AND a.username = 'artist_jungsik'
  AND b.badge_code IN (
                       'BASIC_FIRST_JOIN',
                       'BASIC_FIRST_POST',
                       'BASIC_MEDIA_VIEW'
    );


-- ------------------------------------------------------------
-- 확인용
-- ------------------------------------------------------------


