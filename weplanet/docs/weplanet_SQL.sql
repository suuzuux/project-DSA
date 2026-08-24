-- =====================================================
--  weplanet 스키마
--  MySQL 8.0.16+ (CHECK 제약 사용)
-- =====================================================

CREATE DATABASE weplanet CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE weplanet;

-- =====================================================
--  기획사
-- =====================================================
CREATE TABLE agencies (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    business_no VARCHAR(20)  NULL,
    ceo_name    VARCHAR(30)  NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_agencies_name  (name),
    UNIQUE KEY uk_agencies_bizno (business_no),
    CONSTRAINT ck_agencies_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);

-- =====================================================
--  사용자
-- =====================================================
CREATE TABLE users (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(50)    NOT NULL,
    password          VARCHAR(60)    NOT NULL,          -- BCrypt 60자 고정
    role              VARCHAR(20)    NOT NULL,          -- FAN / ARTIST / AGENCY / ADMIN
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    real_name         VARBINARY(255) NOT NULL,          -- AES 암호화
    nickname          VARCHAR(50)    NOT NULL,
    email             VARCHAR(255)   NOT NULL,
    phone             VARBINARY(255) NULL,              -- AES 암호화
    phone_hash        CHAR(64)       NULL,              -- SHA-256, 검색용
    birth_date        DATE           NULL,
    gender            VARCHAR(10)    NULL,
    zipcode           VARCHAR(10)    NULL,
    address1          VARCHAR(255)   NULL,              -- 도로명/지번
    address2          VARBINARY(512) NULL,              -- 상세주소, AES 암호화
    email_verified_at DATETIME(6)    NULL,
    last_login_at     DATETIME(6)    NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    deleted_at        DATETIME(6)    NULL,              -- soft delete
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email    (email),
    UNIQUE KEY uk_users_nickname (nickname),
    KEY idx_users_role_status (role, status),
    KEY idx_users_phone_hash  (phone_hash),
    CONSTRAINT ck_users_role   CHECK (role   IN ('FAN','ARTIST','AGENCY','ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','DORMANT','SUSPENDED','WITHDRAWN')),
    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER'))
);

-- =====================================================
--  아티스트 그룹
-- =====================================================
CREATE TABLE artist_groups (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    agency_id   BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    name_en     VARCHAR(100) NULL,
    fandom_name VARCHAR(50)  NULL,
    debut_date  DATE         NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_group_name (name),
    KEY idx_group_agency (agency_id),
    CONSTRAINT fk_group_agency FOREIGN KEY (agency_id) REFERENCES agencies(id),
    CONSTRAINT ck_group_status CHECK (status IN ('ACTIVE','HIATUS','DISBANDED'))
);

-- =====================================================
--  아티스트 프로필 (PK = FK = users.id)
-- =====================================================
CREATE TABLE artist_profiles (
    user_id     BIGINT PRIMARY KEY,
    agency_id   BIGINT       NOT NULL,
    stage_name  VARCHAR(50)  NOT NULL,
    debut_date  DATE         NULL,
    position    VARCHAR(50)  NULL,             -- 보컬, 래퍼...
    bio         TEXT         NULL,
    profile_img VARCHAR(500) NULL,
    KEY idx_ap_agency (agency_id),
    CONSTRAINT fk_ap_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_ap_agency FOREIGN KEY (agency_id) REFERENCES agencies(id)
);

-- =====================================================
--  그룹 멤버 (그룹 : 아티스트 = N : M)
-- =====================================================
CREATE TABLE group_members (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id  BIGINT  NOT NULL,
    artist_id BIGINT  NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at DATE    NOT NULL,
    left_at   DATE    NULL,
    UNIQUE KEY uk_gm (group_id, artist_id, joined_at),
    KEY idx_gm_artist (artist_id),
    CONSTRAINT fk_gm_group  FOREIGN KEY (group_id)  REFERENCES artist_groups(id),
    CONSTRAINT fk_gm_artist FOREIGN KEY (artist_id) REFERENCES artist_profiles(user_id),
    CONSTRAINT ck_gm_period CHECK (left_at IS NULL OR left_at >= joined_at)
);

-- =====================================================
--  기획사 직원 프로필
-- =====================================================
CREATE TABLE agency_profiles (
    user_id     BIGINT PRIMARY KEY,
    agency_id   BIGINT      NOT NULL,          -- 반드시 소속 있음
    department  VARCHAR(50) NULL,
    position    VARCHAR(50) NULL,              -- 매니저, 팀장...
    is_owner    BOOLEAN     NOT NULL DEFAULT FALSE,
    approved_by BIGINT      NULL,              -- 승인한 ADMIN
    approved_at DATETIME(6) NULL,
    KEY idx_agp_agency (agency_id),
    CONSTRAINT fk_agp_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_agp_agency   FOREIGN KEY (agency_id)   REFERENCES agencies(id),
    CONSTRAINT fk_agp_approver FOREIGN KEY (approved_by) REFERENCES users(id)
);

-- =====================================================
--  관리자 프로필
-- =====================================================
CREATE TABLE admin_profiles (
    user_id     BIGINT PRIMARY KEY,
    admin_level VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    department  VARCHAR(50) NULL,
    employee_no VARCHAR(30) NULL,
    UNIQUE KEY uk_adp_empno (employee_no),
    CONSTRAINT fk_adp_user  FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_adp_level CHECK (admin_level IN ('SUPER','STAFF'))
);

-- =====================================================
--  관리자/기획사 액션 로그
-- =====================================================
CREATE TABLE admin_action_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id    BIGINT      NOT NULL,          -- ADMIN or AGENCY
    action      VARCHAR(50) NOT NULL,          -- SUSPEND_USER, APPROVE_AGENCY...
    target_type VARCHAR(30) NOT NULL,          -- USER, GROUP, AGENCY
    target_id   BIGINT      NOT NULL,
    reason      TEXT        NULL,
    ip_address  VARCHAR(45) NULL,
    created_at  DATETIME(6) NOT NULL,
    KEY idx_aal_actor  (actor_id, created_at),
    KEY idx_aal_target (target_type, target_id),
    CONSTRAINT fk_aal_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

-- =====================================================
--  그룹별 미디어 게시판 (게시글)
-- =====================================================
CREATE TABLE board_media (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id    BIGINT        NOT NULL,
    uploader_id BIGINT        NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    content     VARCHAR(2000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    deleted_at  DATETIME(6)   NULL,
    KEY idx_bm_group    (group_id, created_at),
    KEY idx_bm_uploader (uploader_id),
    CONSTRAINT fk_bm_group    FOREIGN KEY (group_id)    REFERENCES artist_groups(id),
    CONSTRAINT fk_bm_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
) COMMENT = '그룹별 미디어 게시판';

-- =====================================================
--  게시글 첨부 미디어 (1:N)
-- =====================================================
CREATE TABLE board_media_files (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    board_id     BIGINT       NOT NULL,
    original_name VARCHAR(255) NULL,
    stored_name  VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NULL,
    media_type   VARCHAR(20)  NOT NULL,
    file_size    BIGINT       NULL,
    sort_order   INT          NOT NULL DEFAULT 0 COMMENT '표시 순서',
    created_at   DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_bmf_stored (stored_name),
    KEY idx_bmf_board (board_id, sort_order),
    CONSTRAINT fk_bmf_board FOREIGN KEY (board_id) REFERENCES board_media(id) ON DELETE CASCADE,
    CONSTRAINT ck_bmf_media_type CHECK (media_type IN ('IMAGE','VIDEO'))
) COMMENT = '게시글 첨부 미디어';

-- =====================================================
--  샘플 데이터
-- =====================================================
INSERT INTO users (
    username, password, role, status,
    real_name, nickname, email,
    created_at, updated_at
) VALUES (
    'testuser', '$2a$10$dummyEncodedPasswordValueHere1234567890', 'FAN', 'ACTIVE',
    UNHEX(HEX('테스트유저')), '테스트닉네임', 'test@test.com',
    NOW(), NOW()
);
