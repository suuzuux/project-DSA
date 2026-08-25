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
