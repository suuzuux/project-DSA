-- ============================================================
-- weplanet 프로젝트 - 전체 스키마 + 테스트 데이터 (통합본)
-- ------------------------------------------------------------
-- 원본 스키마: Gemini로 정리한 통합 스키마 (2026-08-25, 김화평)
-- 검증: 김화평 담당(FEED/CHAT) 엔티티 12개 전부 컬럼명/타입/제약조건 일치 확인,
--       ddl-auto=validate 상태로 서버 실제 기동 + 전체 기능(게시판/댓글/좋아요/
--       북마크/번역보기/DM채팅/AI팬메시지) 테스트 통과 확인 완료
-- 실행법: mysql -u root -p weplanet < docs/weplanet_full_schema.sql
-- 주의: 기존 데이터를 전부 지우고 새로 만드는 스크립트입니다 (DROP TABLE 포함)
-- 참고: weplanet_SQL.sql(정휘원 작성분)과 별개 파일이며, 이 파일이 현재
--       가장 최신으로 검증된 전체 스키마입니다.
-- ============================================================

USE
weplanet;
SET
FOREIGN_KEY_CHECKS = 0;

-- 1. users
CREATE TABLE `users`
(
    `id`                bigint                                  NOT NULL AUTO_INCREMENT,
    `username`          varchar(50) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `password`          varchar(60) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `role`              varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `status`            varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'ACTIVE',
    `real_name`         varbinary(255) NOT NULL,
    `nickname`          varchar(50) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `email`             varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `phone`             varbinary(255) DEFAULT NULL,
    `phone_hash`        char(64) COLLATE utf8mb4_unicode_ci              DEFAULT NULL,
    `birth_date`        date                                             DEFAULT NULL,
    `gender`            varchar(10) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `zipcode`           varchar(10) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `address1`          varchar(255) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `address2`          varbinary(512) DEFAULT NULL,
    `email_verified_at` datetime(6) DEFAULT NULL,
    `last_login_at`     datetime(6) DEFAULT NULL,
    `created_at`        datetime(6) NOT NULL,
    `updated_at`        datetime(6) NOT NULL,
    `deleted_at`        datetime(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_nickname` (`nickname`),
    KEY                 `idx_users_role_status` (`role`,`status`),
    KEY                 `idx_users_phone_hash` (`phone_hash`),
    CONSTRAINT `ck_users_gender` CHECK (((`gender` is null) or
                                         (`gender` in (_utf8mb4'MALE', _utf8mb4'FEMALE', _utf8mb4'OTHER')))),
    CONSTRAINT `ck_users_role` CHECK ((`role` in (_utf8mb4'FAN', _utf8mb4'ARTIST', _utf8mb4'AGENCY', _utf8mb4'ADMIN'))),
    CONSTRAINT `ck_users_status` CHECK ((`status` in (_utf8mb4'ACTIVE', _utf8mb4'DORMANT', _utf8mb4'SUSPENDED',
                                                      _utf8mb4'WITHDRAWN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. admin_action_logs
CREATE TABLE `admin_action_logs`
(
    `id`          bigint                                 NOT NULL AUTO_INCREMENT,
    `actor_id`    bigint                                 NOT NULL,
    `action`      varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
    `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
    `target_id`   bigint                                 NOT NULL,
    `reason`      text COLLATE utf8mb4_unicode_ci,
    `ip_address`  varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `created_at`  datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY           `idx_aal_actor` (`actor_id`,`created_at`),
    KEY           `idx_aal_target` (`target_type`,`target_id`),
    CONSTRAINT `fk_aal_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. admin_profiles
CREATE TABLE `admin_profiles`
(
    `user_id`     bigint                                 NOT NULL,
    `admin_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STAFF',
    `department`  varchar(50) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `employee_no` varchar(30) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_adp_empno` (`employee_no`),
    CONSTRAINT `fk_adp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `ck_adp_level` CHECK ((`admin_level` in (_utf8mb4'SUPER', _utf8mb4'STAFF')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. agencies
CREATE TABLE `agencies`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `name`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `business_no` varchar(20) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `ceo_name`    varchar(30) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `status`      varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'ACTIVE',
    `created_at`  datetime(6) NOT NULL,
    `updated_at`  datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agencies_name` (`name`),
    UNIQUE KEY `uk_agencies_bizno` (`business_no`),
    CONSTRAINT `ck_agencies_status` CHECK ((`status` in (_utf8mb4'ACTIVE', _utf8mb4'SUSPENDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. agency_profiles
CREATE TABLE `agency_profiles`
(
    `user_id`     bigint NOT NULL,
    `agency_id`   bigint NOT NULL,
    `department`  varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `position`    varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `is_owner`    tinyint(1) NOT NULL DEFAULT '0',
    `approved_by` bigint                                 DEFAULT NULL,
    `approved_at` datetime(6) DEFAULT NULL,
    PRIMARY KEY (`user_id`),
    KEY           `idx_agp_agency` (`agency_id`),
    KEY           `fk_agp_approver` (`approved_by`),
    CONSTRAINT `fk_agp_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
    CONSTRAINT `fk_agp_approver` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_agp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. artist_groups
CREATE TABLE `artist_groups`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `agency_id`   bigint                                  NOT NULL,
    `name`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `name_en`     varchar(100) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `fandom_name` varchar(50) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `debut_date`  date                                             DEFAULT NULL,
    `status`      varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'ACTIVE',
    `created_at`  datetime(6) NOT NULL,
    `updated_at`  datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`name`),
    KEY           `idx_group_agency` (`agency_id`),
    CONSTRAINT `fk_group_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
    CONSTRAINT `ck_group_status` CHECK ((`status` in (_utf8mb4'ACTIVE', _utf8mb4'HIATUS', _utf8mb4'DISBANDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. artist_profiles
CREATE TABLE `artist_profiles`
(
    `user_id`     bigint                                 NOT NULL,
    `agency_id`   bigint                                 NOT NULL,
    `stage_name`  varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
    `debut_date`  date                                    DEFAULT NULL,
    `position`    varchar(50) COLLATE utf8mb4_unicode_ci  DEFAULT NULL,
    `bio`         text COLLATE utf8mb4_unicode_ci,
    `profile_img` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    PRIMARY KEY (`user_id`),
    KEY           `idx_ap_agency` (`agency_id`),
    CONSTRAINT `fk_ap_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
    CONSTRAINT `fk_ap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. board_media
CREATE TABLE `board_media`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `group_id`    bigint                                  NOT NULL,
    `uploader_id` bigint                                  NOT NULL,
    `title`       varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
    `content`     varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `created_at`  datetime(6) NOT NULL,
    `updated_at`  datetime(6) NOT NULL,
    `deleted_at`  datetime(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY           `idx_bm_group` (`group_id`,`created_at`),
    KEY           `idx_bm_uploader` (`uploader_id`),
    CONSTRAINT `fk_bm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
    CONSTRAINT `fk_bm_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹별 미디어 게시판';

-- 9. board_media_files
CREATE TABLE `board_media_files`
(
    `id`            bigint                                  NOT NULL AUTO_INCREMENT,
    `board_id`      bigint                                  NOT NULL,
    `original_name` varchar(255) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `stored_name`   varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `content_type`  varchar(100) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `media_type`    varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `file_size`     bigint                                           DEFAULT NULL,
    `sort_order`    int                                     NOT NULL DEFAULT '0' COMMENT '표시 순서',
    `created_at`    datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bmf_stored` (`stored_name`),
    KEY             `idx_bmf_board` (`board_id`,`sort_order`),
    CONSTRAINT `fk_bmf_board` FOREIGN KEY (`board_id`) REFERENCES `board_media` (`id`) ON DELETE CASCADE,
    CONSTRAINT `ck_bmf_media_type` CHECK ((`media_type` in (_utf8mb4'IMAGE', _utf8mb4'VIDEO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부 미디어';

-- 10. chat_message

CREATE TABLE `chat_message`
(
    `id`         bigint                          NOT NULL AUTO_INCREMENT,
    `content`    text COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `artist_id`  bigint                          NOT NULL,
    `fan_id`     bigint DEFAULT NULL,
    `sender_id`  bigint                          NOT NULL,
    PRIMARY KEY (`id`),
    KEY          `FKckmqpdmndn0mcp8i1bhlhpwki` (`artist_id`),
    KEY          `FKn3161qsj1g6xx74stn3ak14nf` (`fan_id`),
    KEY          `FK5f82aoyy0jiwpj08qapfrxbh6` (`sender_id`),
    CONSTRAINT `FK5f82aoyy0jiwpj08qapfrxbh6` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FKckmqpdmndn0mcp8i1bhlhpwki` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FKn3161qsj1g6xx74stn3ak14nf` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. chat_quota
CREATE TABLE `chat_quota`
(
    `id`              bigint NOT NULL AUTO_INCREMENT,
    `charged_date`    date   NOT NULL,
    `remaining_count` int    NOT NULL,
    `artist_id`       bigint NOT NULL,
    `fan_id`          bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK24ma26kvjdg8f06vyvylpor9v` (`fan_id`,`artist_id`),
    KEY               `FKryrpa4l7agt3qkw1dwdwkm4o7` (`artist_id`),
    CONSTRAINT `FK4ayjraxy8git4xiit6p3uht2j` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FKryrpa4l7agt3qkw1dwdwkm4o7` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. post
CREATE TABLE `post`
(
    `id`         bigint                                  NOT NULL AUTO_INCREMENT,
    `board_type` enum('ARTIST','FAN') COLLATE utf8mb4_unicode_ci NOT NULL,
    `content`    text COLLATE utf8mb4_unicode_ci         NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `title`      varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
    `author_id`  bigint DEFAULT NULL,
    `like_count` int                                     NOT NULL,
    PRIMARY KEY (`id`),
    KEY          `FK1mpebp1ayl0twrwm7ruiof778` (`author_id`),
    CONSTRAINT `FK1mpebp1ayl0twrwm7ruiof778` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. comment
CREATE TABLE `comment`
(
    `id`         bigint                                  NOT NULL AUTO_INCREMENT,
    `content`    varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `author_id`  bigint                                  NOT NULL,
    `post_id`    bigint                                  NOT NULL,
    PRIMARY KEY (`id`),
    KEY          `FKir20vhrx08eh4itgpbfxip0s1` (`author_id`),
    KEY          `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
    CONSTRAINT `FKir20vhrx08eh4itgpbfxip0s1` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. comment_report
CREATE TABLE `comment_report`
(
    `id`          bigint NOT NULL AUTO_INCREMENT,
    `created_at`  datetime(6) NOT NULL,
    `reason`      enum('ABUSE','ETC','SEXUAL','SPAM') COLLATE utf8mb4_unicode_ci NOT NULL,
    `comment_id`  bigint NOT NULL,
    `reporter_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK7a7j27uutr1ew9m87et35eily` (`comment_id`,`reporter_id`),
    KEY           `FKn7ue556scerw6fa5epexg2g4j` (`reporter_id`),
    CONSTRAINT `FK8ugevhla12t9n0uw4o0rkvnth` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
    CONSTRAINT `FKn7ue556scerw6fa5epexg2g4j` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. filter_keyword
CREATE TABLE `filter_keyword`
(
    `id`      bigint                                 NOT NULL AUTO_INCREMENT,
    `keyword` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKj55c0tyqc5n2qto80hyjpegy1` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. group_members
CREATE TABLE `group_members`
(
    `id`        bigint NOT NULL AUTO_INCREMENT,
    `group_id`  bigint NOT NULL,
    `artist_id` bigint NOT NULL,
    `is_leader` tinyint(1) NOT NULL DEFAULT '0',
    `joined_at` date   NOT NULL,
    `left_at`   date DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gm` (`group_id`,`artist_id`,`joined_at`),
    KEY         `idx_gm_artist` (`artist_id`),
    CONSTRAINT `fk_gm_artist` FOREIGN KEY (`artist_id`) REFERENCES `artist_profiles` (`user_id`),
    CONSTRAINT `fk_gm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
    CONSTRAINT `ck_gm_period` CHECK (((`left_at` is null) or (`left_at` >= `joined_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. membership
CREATE TABLE `membership`
(
    `id`         bigint NOT NULL AUTO_INCREMENT,
    `created_at` datetime(6) NOT NULL,
    `expires_at` datetime(6) NOT NULL,
    `artist_id`  bigint NOT NULL,
    `fan_id`     bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_membership` (`fan_id`,`artist_id`),
    KEY          `fk_membership_artist` (`artist_id`),
    CONSTRAINT `fk_membership_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_membership_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 18. post_attachment
CREATE TABLE `post_attachment`
(
    `id`            bigint                                  NOT NULL AUTO_INCREMENT,
    `content_type`  varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `created_at`    datetime(6) NOT NULL,
    `file_size`     bigint                                  DEFAULT NULL,
    `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `stored_name`   varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `post_id`       bigint                                  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKgvqlowil11nsrhy82c6qqhcxa` (`stored_name`),
    KEY             `FKmof1y73w0oea4caub8rpkhlmi` (`post_id`),
    CONSTRAINT `FKmof1y73w0oea4caub8rpkhlmi` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. post_bookmark
CREATE TABLE `post_bookmark`
(
    `id`         bigint NOT NULL AUTO_INCREMENT,
    `created_at` datetime(6) NOT NULL,
    `post_id`    bigint NOT NULL,
    `user_id`    bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_bookmark` (`post_id`,`user_id`),
    KEY          `fk_post_bookmark_user` (`user_id`),
    CONSTRAINT `fk_post_bookmark_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
    CONSTRAINT `fk_post_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. post_like
CREATE TABLE `post_like`
(
    `id`         bigint NOT NULL AUTO_INCREMENT,
    `created_at` datetime(6) NOT NULL,
    `post_id`    bigint NOT NULL,
    `user_id`    bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKpmmko3h7yonaqhy5gxvnmdeue` (`post_id`,`user_id`),
    KEY          `FKijnjmw0imnatadr3agtk0udip` (`user_id`),
    CONSTRAINT `FKijnjmw0imnatadr3agtk0udip` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FKj7iy0k7n3d0vkh8o7ibjna884` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 21. report
CREATE TABLE `report`
(
    `id`          bigint NOT NULL AUTO_INCREMENT,
    `created_at`  datetime(6) NOT NULL,
    `reason`      enum('ABUSE','ETC','SEXUAL','SPAM') COLLATE utf8mb4_unicode_ci NOT NULL,
    `post_id`     bigint NOT NULL,
    `reporter_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK59baeft7frgypa05ajup9wrij` (`post_id`,`reporter_id`),
    KEY           `FKqbhdxqd3ly7fkhly5nrl2j93k` (`reporter_id`),
    CONSTRAINT `FKnuqod1y014fp5bmqjeoffcgqy` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
    CONSTRAINT `FKqbhdxqd3ly7fkhly5nrl2j93k` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET
FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- 테스트 데이터 (김화평 FEED/CHAT 테스트용 - 2026-08-25)
-- ============================================================

-- 테스트 계정 4개 (id 1~4번으로 고정되도록, 반드시 위 스키마 직후 가장 먼저 실행)
-- 비밀번호는 전부 더미 값이라 실제 로그인은 안 됨 (testUserId 파라미터로 테스트하는 용도)
INSERT INTO users (username, password, role, status, real_name, nickname, email, created_at, updated_at)
VALUES ('testuser', '$2a$10$dummyEncodedPasswordValueHere1234567890', 'FAN', 'ACTIVE', '테스트유저', '테스트닉네임',
        'testuser@example.com', NOW(6), NOW(6)),
       ('testartist', '$2a$10$dummyEncodedPasswordValueHere1234567890', 'ARTIST', 'ACTIVE', '테스트아티스트', '테스트아티스트닉네임',
        'testartist@example.com', NOW(6), NOW(6)),
       ('testadmin', '$2a$10$dummyEncodedPasswordValueHere1234567890', 'ADMIN', 'ACTIVE', '테스트관리자', '테스트관리자닉네임',
        'testadmin@example.com', NOW(6), NOW(6)),
       ('aifan_bot', '$2a$10$dummyEncodedPasswordValueHere1234567890', 'FAN', 'ACTIVE', 'AI팬봇', 'AI팬',
        'aifanbot@example.com', NOW(6), NOW(6));

-- 채팅 금칙어
INSERT INTO filter_keyword (keyword)
VALUES ('바보'),
       ('멍청이'),
       ('시발');



-- ============================================================
-- FAN PROJECT (팬 프로젝트)
-- 작성 자격: 스페셜 뱃지 1개 이상 AND 기본 뱃지 5개 이상
-- 승인 권한: ADMIN만 가능 (AGENCY/ARTIST는 조회도 허용하지 않음)
-- 결제 방식: 현재 MOCK, 추후 실제 PG 결제/취소/환불로 확장 가능
-- ============================================================

CREATE TABLE IF NOT EXISTS fan_badge_ownership (
                                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                                   fan_id BIGINT NOT NULL,
                                                   artist_id BIGINT NOT NULL,
                                                   badge_code VARCHAR(50) NOT NULL,
    badge_name VARCHAR(100) NOT NULL,
    badge_type VARCHAR(20) NOT NULL,
    awarded_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    awarded_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_fan_badge_ownership (fan_id, artist_id, badge_code),
    KEY idx_fan_badge_count (fan_id, artist_id, badge_type, revoked_at),
    KEY idx_fan_badge_awarded_by (awarded_by),

    CONSTRAINT fk_fan_badge_fan
    FOREIGN KEY (fan_id) REFERENCES users(id),
    CONSTRAINT fk_fan_badge_artist
    FOREIGN KEY (artist_id) REFERENCES users(id),
    CONSTRAINT fk_fan_badge_awarded_by
    FOREIGN KEY (awarded_by) REFERENCES users(id),
    CONSTRAINT ck_fan_badge_type
    CHECK (badge_type IN ('BASIC', 'SPECIAL')),
    CONSTRAINT ck_fan_badge_period
    CHECK (revoked_at IS NULL OR revoked_at >= awarded_at)
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS fan_project (
                                           id BIGINT NOT NULL AUTO_INCREMENT,
                                           artist_id BIGINT NOT NULL,
                                           creator_id BIGINT NOT NULL,
                                           title VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    goal_amount BIGINT NOT NULL,
    funding_start_at DATETIME(6) NOT NULL,
    funding_end_at DATETIME(6) NOT NULL,
    description VARCHAR(1000) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',

    special_badge_count_at_apply INT NOT NULL,
    basic_badge_count_at_apply INT NOT NULL,
    eligibility_rule_code VARCHAR(50) NOT NULL DEFAULT 'SPECIAL_1_AND_BASIC_5',

    identity_verification_method VARCHAR(20) NOT NULL DEFAULT 'PHONE',
    identity_verified_at DATETIME(6) NOT NULL,

    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    rejection_reason VARCHAR(500) NULL,

    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,

    PRIMARY KEY (id),

    KEY idx_fan_project_artist_status (artist_id, status, funding_start_at),
    KEY idx_fan_project_creator (creator_id, created_at),
    KEY idx_fan_project_funding_end (status, funding_end_at),
    KEY idx_fan_project_reviewer (reviewed_by, reviewed_at),

    CONSTRAINT fk_fan_project_artist
    FOREIGN KEY (artist_id) REFERENCES users(id),
    CONSTRAINT fk_fan_project_creator
    FOREIGN KEY (creator_id) REFERENCES users(id),
    CONSTRAINT fk_fan_project_reviewer
    FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT ck_fan_project_event_type
    CHECK (event_type IN ('BIRTHDAY_CAFE', 'BILLBOARD', 'CONCERT', 'ETC')),
    CONSTRAINT ck_fan_project_goal_amount
    CHECK (goal_amount BETWEEN 10000 AND 3000000),
    CONSTRAINT ck_fan_project_funding_period
    CHECK (funding_end_at > funding_start_at),
    CONSTRAINT ck_fan_project_status
    CHECK (status IN (
           'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'FUNDING',
           'FUNDING_CLOSED', 'COMPLETED', 'CANCELLED'
                     )),
    CONSTRAINT ck_fan_project_badge_counts
    CHECK (special_badge_count_at_apply >= 0 AND basic_badge_count_at_apply >= 0),
    CONSTRAINT ck_fan_project_creation_eligibility
    CHECK (special_badge_count_at_apply >= 1 AND basic_badge_count_at_apply >= 5),
    CONSTRAINT ck_fan_project_identity_method
    CHECK (identity_verification_method = 'PHONE'),
    CONSTRAINT ck_fan_project_review
    CHECK (
(status = 'PENDING_APPROVAL' AND reviewed_by IS NULL AND reviewed_at IS NULL)
    OR (status NOT IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
    OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_fan_project_rejection_reason
    CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS fan_project_cover_image (
                                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                                       project_id BIGINT NOT NULL,
                                                       original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_fan_project_cover_project (project_id),
    UNIQUE KEY uk_fan_project_cover_stored (stored_name),

    CONSTRAINT fk_fan_project_cover_project
    FOREIGN KEY (project_id) REFERENCES fan_project(id) ON DELETE CASCADE,
    CONSTRAINT ck_fan_project_cover_type
    CHECK (content_type LIKE 'image/%'),
    CONSTRAINT ck_fan_project_cover_size
    CHECK (file_size > 0)
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- 프로젝트 개설자의 실제 정산계좌.
-- 예금주명은 별도 입력/저장하지 않고 본인인증된 users.real_name을 사용한다.
-- Toss 가상계좌는 추후 결제 기능에서 별도 관리한다.
CREATE TABLE IF NOT EXISTS fan_project_settlement_account (
                                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                                              project_id BIGINT NOT NULL,
                                                              bank_code CHAR(3) NOT NULL,
    account_number_enc VARBINARY(512) NOT NULL,
    account_number_hmac CHAR(64) NOT NULL,
    account_number_last4 CHAR(4) NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_fan_project_settlement_project (project_id),
    KEY idx_fan_project_settlement_hmac (account_number_hmac),

    CONSTRAINT fk_fan_project_settlement_project
    FOREIGN KEY (project_id) REFERENCES fan_project(id) ON DELETE CASCADE,
    CONSTRAINT ck_fan_project_bank_code
    CHECK (bank_code IN ('004', '088', '011', '090', '020', '081', '092', '032', '031')),
    CONSTRAINT ck_fan_project_account_last4
    CHECK (account_number_last4 REGEXP '^[0-9]{4}$'),
    CONSTRAINT ck_fan_project_account_verification
    CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'FAILED'))
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS fan_project_fraud_check (
                                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                                       project_id BIGINT NOT NULL,
                                                       target_type VARCHAR(20) NOT NULL,
    target_fingerprint CHAR(64) NOT NULL,
    bank_code CHAR(3) NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'THECHEAT',
    provider_result_code INT NULL,
    result_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    caution_yn CHAR(1) NULL,
    search_window_start_at DATETIME(6) NULL,
    search_window_end_at DATETIME(6) NULL,
    checked_at DATETIME(6) NULL,
    requested_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    KEY idx_fan_project_fraud_latest (project_id, target_type, checked_at),
    KEY idx_fan_project_fraud_target (target_fingerprint, checked_at),
    KEY idx_fan_project_fraud_requester (requested_by),

    CONSTRAINT fk_fan_project_fraud_project
    FOREIGN KEY (project_id) REFERENCES fan_project(id) ON DELETE CASCADE,
    CONSTRAINT fk_fan_project_fraud_requester
    FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT ck_fan_project_fraud_target
    CHECK (target_type IN ('PHONE', 'ACCOUNT')),
    CONSTRAINT ck_fan_project_fraud_status
    CHECK (result_status IN ('PENDING', 'CLEAR', 'CAUTION', 'ERROR')),
    CONSTRAINT ck_fan_project_fraud_caution
    CHECK (caution_yn IS NULL OR caution_yn IN ('Y', 'N')),
    CONSTRAINT ck_fan_project_fraud_bank_code
    CHECK (bank_code IS NULL OR bank_code REGEXP '^[0-9]{3}$'),
    CONSTRAINT ck_fan_project_fraud_window
    CHECK (
              search_window_start_at IS NULL
              OR search_window_end_at IS NULL
              OR search_window_end_at >= search_window_start_at
          )
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS fan_project_contribution (
                                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                                        project_id BIGINT NOT NULL,
                                                        contributor_id BIGINT NOT NULL,
                                                        order_no VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    payment_provider VARCHAR(30) NOT NULL DEFAULT 'MOCK',
    provider_transaction_id VARCHAR(255) NULL,
    amount BIGINT NOT NULL,
    refund_amount BIGINT NOT NULL DEFAULT 0,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'READY',
    paid_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    refunded_at DATETIME(6) NULL,
    refund_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_fan_project_contribution_order (order_no),
    UNIQUE KEY uk_fan_project_contribution_idempotency (idempotency_key),
    KEY idx_fan_project_contribution_total (project_id, payment_status),
    KEY idx_fan_project_contributor_history (contributor_id, created_at),

    CONSTRAINT fk_fan_project_contribution_project
    FOREIGN KEY (project_id) REFERENCES fan_project(id),
    CONSTRAINT fk_fan_project_contribution_user
    FOREIGN KEY (contributor_id) REFERENCES users(id),
    CONSTRAINT ck_fan_project_contribution_amount
    CHECK (amount > 0),
    CONSTRAINT ck_fan_project_contribution_refund
    CHECK (refund_amount >= 0 AND refund_amount <= amount),
    CONSTRAINT ck_fan_project_contribution_status
    CHECK (payment_status IN (
           'READY', 'PAID', 'FAILED', 'CANCELLED',
           'REFUND_REQUESTED', 'REFUNDED'
                             ))
    )
    ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD COLUMN phone_verified_at DATETIME(6) NULL AFTER phone_hash;
