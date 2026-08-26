CREATE DATABASE IF NOT EXISTS `weplanet` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `weplanet`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `password` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
                         `real_name` varbinary(255) NOT NULL,
                         `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                         `phone` varbinary(255) DEFAULT NULL,
                         `phone_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `phone_verified_at` datetime(6) DEFAULT NULL,
                         `birth_date` date DEFAULT NULL,
                         `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `zipcode` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `address1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `address2` varbinary(512) DEFAULT NULL,
                         `email_verified_at` datetime(6) DEFAULT NULL,
                         `last_login_at` datetime(6) DEFAULT NULL,
                         `created_at` datetime(6) NOT NULL,
                         `updated_at` datetime(6) NOT NULL,
                         `deleted_at` datetime(6) DEFAULT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_users_username` (`username`),
                         UNIQUE KEY `uk_users_email` (`email`),
                         UNIQUE KEY `uk_users_nickname` (`nickname`),
                         KEY `idx_users_role_status` (`role`,`status`),
                         KEY `idx_users_phone_hash` (`phone_hash`),
                         CONSTRAINT `ck_users_gender` CHECK (((`gender` is null) or (`gender` in (_utf8mb4'MALE',_utf8mb4'FEMALE',_utf8mb4'OTHER')))),
                         CONSTRAINT `ck_users_role` CHECK ((`role` in (_utf8mb4'FAN',_utf8mb4'ARTIST',_utf8mb4'AGENCY',_utf8mb4'ADMIN'))),
                         CONSTRAINT `ck_users_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DORMANT',_utf8mb4'SUSPENDED',_utf8mb4'WITHDRAWN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `agencies`
--

DROP TABLE IF EXISTS `agencies`;
CREATE TABLE `agencies` (
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                            `business_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            `ceo_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
                            `created_at` datetime(6) NOT NULL,
                            `updated_at` datetime(6) NOT NULL,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_agencies_name` (`name`),
                            UNIQUE KEY `uk_agencies_bizno` (`business_no`),
                            CONSTRAINT `ck_agencies_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'SUSPENDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `admin_profiles`
--

DROP TABLE IF EXISTS `admin_profiles`;
CREATE TABLE `admin_profiles` (
                                  `user_id` bigint NOT NULL,
                                  `admin_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STAFF',
                                  `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  `employee_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  PRIMARY KEY (`user_id`),
                                  UNIQUE KEY `uk_adp_empno` (`employee_no`),
                                  CONSTRAINT `fk_adp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
                                  CONSTRAINT `ck_adp_level` CHECK ((`admin_level` in (_utf8mb4'SUPER',_utf8mb4'STAFF')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `admin_action_logs`
--

DROP TABLE IF EXISTS `admin_action_logs`;
CREATE TABLE `admin_action_logs` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `actor_id` bigint NOT NULL,
                                     `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `target_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `target_id` bigint NOT NULL,
                                     `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                                     `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                     `created_at` datetime(6) NOT NULL,
                                     PRIMARY KEY (`id`),
                                     KEY `idx_aal_actor` (`actor_id`,`created_at`),
                                     KEY `idx_aal_target` (`target_type`,`target_id`),
                                     CONSTRAINT `fk_aal_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `agency_profiles`
--

DROP TABLE IF EXISTS `agency_profiles`;
CREATE TABLE `agency_profiles` (
                                   `user_id` bigint NOT NULL,
                                   `agency_id` bigint NOT NULL,
                                   `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   `position` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   `is_owner` tinyint(1) NOT NULL DEFAULT '0',
                                   `approved_by` bigint DEFAULT NULL,
                                   `approved_at` datetime(6) DEFAULT NULL,
                                   PRIMARY KEY (`user_id`),
                                   KEY `idx_agp_agency` (`agency_id`),
                                   KEY `fk_agp_approver` (`approved_by`),
                                   CONSTRAINT `fk_agp_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
                                   CONSTRAINT `fk_agp_approver` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
                                   CONSTRAINT `fk_agp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `artist_profiles`
--

DROP TABLE IF EXISTS `artist_profiles`;
CREATE TABLE `artist_profiles` (
                                   `user_id` bigint NOT NULL,
                                   `agency_id` bigint NOT NULL,
                                   `stage_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `debut_date` date DEFAULT NULL,
                                   `position` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                                   `profile_img` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   PRIMARY KEY (`user_id`),
                                   KEY `idx_ap_agency` (`agency_id`),
                                   CONSTRAINT `fk_ap_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
                                   CONSTRAINT `fk_ap_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `artist_groups`
--

DROP TABLE IF EXISTS `artist_groups`;
CREATE TABLE `artist_groups` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `agency_id` bigint NOT NULL,
                                 `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                 `name_en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                 `fandom_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                 `debut_date` date DEFAULT NULL,
                                 `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
                                 `created_at` datetime(6) NOT NULL,
                                 `updated_at` datetime(6) NOT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_group_name` (`name`),
                                 KEY `idx_group_agency` (`agency_id`),
                                 CONSTRAINT `fk_group_agency` FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`id`),
                                 CONSTRAINT `ck_group_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'HIATUS',_utf8mb4'DISBANDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `group_members`
--

DROP TABLE IF EXISTS `group_members`;
CREATE TABLE `group_members` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `group_id` bigint NOT NULL,
                                 `artist_id` bigint NOT NULL,
                                 `is_leader` tinyint(1) NOT NULL DEFAULT '0',
                                 `joined_at` date NOT NULL,
                                 `left_at` date DEFAULT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_gm` (`group_id`,`artist_id`,`joined_at`),
                                 KEY `idx_gm_artist` (`artist_id`),
                                 CONSTRAINT `fk_gm_artist` FOREIGN KEY (`artist_id`) REFERENCES `artist_profiles` (`user_id`),
                                 CONSTRAINT `fk_gm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
                                 CONSTRAINT `ck_gm_period` CHECK (((`left_at` is null) or (`left_at` >= `joined_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `membership`
--

DROP TABLE IF EXISTS `membership`;
CREATE TABLE `membership` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `created_at` datetime(6) NOT NULL,
                              `expires_at` datetime(6) NOT NULL,
                              `artist_id` bigint NOT NULL,
                              `fan_id` bigint NOT NULL,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_membership` (`fan_id`,`artist_id`),
                              KEY `fk_membership_artist` (`artist_id`),
                              CONSTRAINT `fk_membership_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
                              CONSTRAINT `fk_membership_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `post`
--

DROP TABLE IF EXISTS `post`;
CREATE TABLE `post` (
                        `id` bigint NOT NULL AUTO_INCREMENT,
                        `board_type` enum('ARTIST','FAN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        `created_at` datetime(6) NOT NULL,
                        `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        `author_id` bigint DEFAULT NULL,
                        `artist_id` bigint DEFAULT NULL,
                        `like_count` int NOT NULL,
                        `hidden_from_artist` tinyint(1) NOT NULL DEFAULT '0',
                        `link_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                        PRIMARY KEY (`id`),
                        KEY `FK1mpebp1ayl0twrwm7ruiof778` (`author_id`),
                        KEY `idx_post_artist` (`artist_id`),
                        CONSTRAINT `FK1mpebp1ayl0twrwm7ruiof778` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
                        CONSTRAINT `fk_post_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `post_attachment`
--

DROP TABLE IF EXISTS `post_attachment`;
CREATE TABLE `post_attachment` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                   `created_at` datetime(6) NOT NULL,
                                   `file_size` bigint DEFAULT NULL,
                                   `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `post_id` bigint NOT NULL,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `UKgvqlowil11nsrhy82c6qqhcxa` (`stored_name`),
                                   KEY `FKmof1y73w0oea4caub8rpkhlmi` (`post_id`),
                                   CONSTRAINT `FKmof1y73w0oea4caub8rpkhlmi` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `post_like`
--

DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
                             `id` bigint NOT NULL AUTO_INCREMENT,
                             `created_at` datetime(6) NOT NULL,
                             `post_id` bigint NOT NULL,
                             `user_id` bigint NOT NULL,
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `UKpmmko3h7yonaqhy5gxvnmdeue` (`post_id`,`user_id`),
                             KEY `FKijnjmw0imnatadr3agtk0udip` (`user_id`),
                             CONSTRAINT `FKijnjmw0imnatadr3agtk0udip` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
                             CONSTRAINT `FKj7iy0k7n3d0vkh8o7ibjna884` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `post_bookmark`
--

DROP TABLE IF EXISTS `post_bookmark`;
CREATE TABLE `post_bookmark` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `created_at` datetime(6) NOT NULL,
                                 `post_id` bigint NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_post_bookmark` (`post_id`,`user_id`),
                                 KEY `fk_post_bookmark_user` (`user_id`),
                                 CONSTRAINT `fk_post_bookmark_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
                                 CONSTRAINT `fk_post_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
                           `id` bigint NOT NULL AUTO_INCREMENT,
                           `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                           `created_at` datetime(6) NOT NULL,
                           `author_id` bigint NOT NULL,
                           `post_id` bigint NOT NULL,
                           PRIMARY KEY (`id`),
                           KEY `FKir20vhrx08eh4itgpbfxip0s1` (`author_id`),
                           KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
                           CONSTRAINT `FKir20vhrx08eh4itgpbfxip0s1` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
                           CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `board_media`
--

DROP TABLE IF EXISTS `board_media`;
CREATE TABLE `board_media` (
                               `id` bigint NOT NULL AUTO_INCREMENT,
                               `group_id` bigint NOT NULL,
                               `uploader_id` bigint NOT NULL,
                               `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                               `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                               `created_at` datetime(6) NOT NULL,
                               `updated_at` datetime(6) NOT NULL,
                               `deleted_at` datetime(6) DEFAULT NULL,
                               PRIMARY KEY (`id`),
                               KEY `idx_bm_group` (`group_id`,`created_at`),
                               KEY `idx_bm_uploader` (`uploader_id`),
                               CONSTRAINT `fk_bm_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
                               CONSTRAINT `fk_bm_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹별 미디어 게시판';

--
-- Table structure for table `board_media_files`
--

DROP TABLE IF EXISTS `board_media_files`;
CREATE TABLE `board_media_files` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `board_id` bigint NOT NULL,
                                     `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                     `stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                     `media_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `file_size` bigint DEFAULT NULL,
                                     `sort_order` int NOT NULL DEFAULT '0' COMMENT '표시 순서',
                                     `created_at` datetime(6) NOT NULL,
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_bmf_stored` (`stored_name`),
                                     KEY `idx_bmf_board` (`board_id`,`sort_order`),
                                     CONSTRAINT `fk_bmf_board` FOREIGN KEY (`board_id`) REFERENCES `board_media` (`id`) ON DELETE CASCADE,
                                     CONSTRAINT `ck_bmf_media_type` CHECK ((`media_type` in (_utf8mb4'IMAGE',_utf8mb4'VIDEO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 첨부 미디어';

--
-- Table structure for table `chat_message`
--

DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `created_at` datetime(6) NOT NULL,
                                `artist_id` bigint NOT NULL,
                                `fan_id` bigint DEFAULT NULL,
                                `sender_id` bigint NOT NULL,
                                PRIMARY KEY (`id`),
                                KEY `FKckmqpdmndn0mcp8i1bhlhpwki` (`artist_id`),
                                KEY `FKn3161qsj1g6xx74stn3ak14nf` (`fan_id`),
                                KEY `FK5f82aoyy0jiwpj08qapfrxbh6` (`sender_id`),
                                CONSTRAINT `FK5f82aoyy0jiwpj08qapfrxbh6` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
                                CONSTRAINT `FKckmqpdmndn0mcp8i1bhlhpwki` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`),
                                CONSTRAINT `FKn3161qsj1g6xx74stn3ak14nf` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `chat_quota`
--

DROP TABLE IF EXISTS `chat_quota`;
CREATE TABLE `chat_quota` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `charged_date` date NOT NULL,
                              `remaining_count` int NOT NULL,
                              `artist_id` bigint NOT NULL,
                              `fan_id` bigint NOT NULL,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `UK24ma26kvjdg8f06vyvylpor9v` (`fan_id`,`artist_id`),
                              KEY `FKryrpa4l7agt3qkw1dwdwkm4o7` (`artist_id`),
                              CONSTRAINT `FK4ayjraxy8git4xiit6p3uht2j` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
                              CONSTRAINT `FKryrpa4l7agt3qkw1dwdwkm4o7` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_badge_ownership`
--

DROP TABLE IF EXISTS `fan_badge_ownership`;
CREATE TABLE `fan_badge_ownership` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `fan_id` bigint NOT NULL,
                                       `group_id` bigint NOT NULL,
                                       `badge_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `badge_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `badge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `awarded_at` datetime(6) NOT NULL,
                                       `revoked_at` datetime(6) DEFAULT NULL,
                                       `awarded_by` bigint DEFAULT NULL,
                                       `created_at` datetime(6) NOT NULL,
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_fan_badge_ownership` (`fan_id`,`group_id`,`badge_code`),
                                       KEY `idx_fan_badge_count` (`fan_id`,`group_id`,`badge_type`,`revoked_at`),
                                       KEY `idx_fan_badge_awarded_by` (`awarded_by`),
                                       KEY `fk_fan_badge_group` (`group_id`),
                                       CONSTRAINT `fk_fan_badge_awarded_by` FOREIGN KEY (`awarded_by`) REFERENCES `users` (`id`),
                                       CONSTRAINT `fk_fan_badge_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
                                       CONSTRAINT `fk_fan_badge_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
                                       CONSTRAINT `ck_fan_badge_period` CHECK (((`revoked_at` is null) or (`revoked_at` >= `awarded_at`))),
                                       CONSTRAINT `ck_fan_badge_type` CHECK ((`badge_type` in (_utf8mb4'BASIC',_utf8mb4'SPECIAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_project`
--

DROP TABLE IF EXISTS `fan_project`;
CREATE TABLE `fan_project` (
                               `id` bigint NOT NULL AUTO_INCREMENT,
                               `group_id` bigint NOT NULL,
                               `creator_id` bigint NOT NULL,
                               `title` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
                               `event_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
                               `goal_amount` bigint NOT NULL,
                               `funding_start_at` datetime(6) NOT NULL,
                               `funding_end_at` datetime(6) NOT NULL,
                               `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
                               `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL',
                               `special_badge_count_at_apply` int NOT NULL,
                               `basic_badge_count_at_apply` int NOT NULL,
                               `eligibility_rule_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SPECIAL_1_AND_BASIC_5',
                               `identity_verification_method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PHONE',
                               `identity_verified_at` datetime(6) NOT NULL,
                               `reviewed_by` bigint DEFAULT NULL,
                               `reviewed_at` datetime(6) DEFAULT NULL,
                               `rejection_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                               `created_at` datetime(6) NOT NULL,
                               `updated_at` datetime(6) NOT NULL,
                               `deleted_at` datetime(6) DEFAULT NULL,
                               PRIMARY KEY (`id`),
                               KEY `idx_fan_project_group_status` (`group_id`,`status`,`funding_start_at`),
                               KEY `idx_fan_project_creator` (`creator_id`,`created_at`),
                               KEY `idx_fan_project_funding_end` (`status`,`funding_end_at`),
                               KEY `idx_fan_project_reviewer` (`reviewed_by`,`reviewed_at`),
                               CONSTRAINT `fk_fan_project_creator` FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`),
                               CONSTRAINT `fk_fan_project_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
                               CONSTRAINT `fk_fan_project_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`),
                               CONSTRAINT `ck_fan_project_badge_counts` CHECK (((`special_badge_count_at_apply` >= 0) and (`basic_badge_count_at_apply` >= 0))),
                               CONSTRAINT `ck_fan_project_creation_eligibility` CHECK (((`special_badge_count_at_apply` >= 1) and (`basic_badge_count_at_apply` >= 5))),
                               CONSTRAINT `ck_fan_project_event_type` CHECK ((`event_type` in (_utf8mb4'BIRTHDAY_CAFE',_utf8mb4'BILLBOARD',_utf8mb4'CONCERT',_utf8mb4'ETC'))),
                               CONSTRAINT `ck_fan_project_funding_period` CHECK ((`funding_end_at` > `funding_start_at`)),
                               CONSTRAINT `ck_fan_project_goal_amount` CHECK ((`goal_amount` between 10000 and 3000000)),
                               CONSTRAINT `ck_fan_project_identity_method` CHECK ((`identity_verification_method` = _utf8mb4'PHONE')),
                               CONSTRAINT `ck_fan_project_rejection_reason` CHECK (((`status` <> _utf8mb4'REJECTED') or (`rejection_reason` is not null))),
                               CONSTRAINT `ck_fan_project_review` CHECK ((((`status` = _utf8mb4'PENDING_APPROVAL') and (`reviewed_by` is null) and (`reviewed_at` is null)) or (`status` not in (_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED',_utf8mb4'REJECTED')) or ((`status` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED')) and (`reviewed_by` is not null) and (`reviewed_at` is not null)))),
                               CONSTRAINT `ck_fan_project_status` CHECK ((`status` in (_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'FUNDING',_utf8mb4'FUNDING_CLOSED',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_project_cover_image`
--

DROP TABLE IF EXISTS `fan_project_cover_image`;
CREATE TABLE `fan_project_cover_image` (
                                           `id` bigint NOT NULL AUTO_INCREMENT,
                                           `project_id` bigint NOT NULL,
                                           `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                           `stored_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                           `content_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                                           `file_size` bigint NOT NULL,
                                           `created_at` datetime(6) NOT NULL,
                                           PRIMARY KEY (`id`),
                                           UNIQUE KEY `uk_fan_project_cover_project` (`project_id`),
                                           UNIQUE KEY `uk_fan_project_cover_stored` (`stored_name`),
                                           CONSTRAINT `fk_fan_project_cover_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
                                           CONSTRAINT `ck_fan_project_cover_size` CHECK ((`file_size` > 0)),
                                           CONSTRAINT `ck_fan_project_cover_type` CHECK ((`content_type` like _utf8mb4'image/%'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_project_contribution`
--

DROP TABLE IF EXISTS `fan_project_contribution`;
CREATE TABLE `fan_project_contribution` (
                                            `id` bigint NOT NULL AUTO_INCREMENT,
                                            `project_id` bigint NOT NULL,
                                            `contributor_id` bigint NOT NULL,
                                            `order_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                            `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
                                            `payment_provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK',
                                            `provider_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                            `amount` bigint NOT NULL,
                                            `refund_amount` bigint NOT NULL DEFAULT '0',
                                            `payment_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY',
                                            `paid_at` datetime(6) DEFAULT NULL,
                                            `cancelled_at` datetime(6) DEFAULT NULL,
                                            `refunded_at` datetime(6) DEFAULT NULL,
                                            `refund_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                            `created_at` datetime(6) NOT NULL,
                                            `updated_at` datetime(6) NOT NULL,
                                            PRIMARY KEY (`id`),
                                            UNIQUE KEY `uk_fan_project_contribution_order` (`order_no`),
                                            UNIQUE KEY `uk_fan_project_contribution_idempotency` (`idempotency_key`),
                                            KEY `idx_fan_project_contribution_total` (`project_id`,`payment_status`),
                                            KEY `idx_fan_project_contributor_history` (`contributor_id`,`created_at`),
                                            CONSTRAINT `fk_fan_project_contribution_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`),
                                            CONSTRAINT `fk_fan_project_contribution_user` FOREIGN KEY (`contributor_id`) REFERENCES `users` (`id`),
                                            CONSTRAINT `ck_fan_project_contribution_amount` CHECK ((`amount` > 0)),
                                            CONSTRAINT `ck_fan_project_contribution_refund` CHECK (((`refund_amount` >= 0) and (`refund_amount` <= `amount`))),
                                            CONSTRAINT `ck_fan_project_contribution_status` CHECK ((`payment_status` in (_utf8mb4'READY',_utf8mb4'PAID',_utf8mb4'FAILED',_utf8mb4'CANCELLED',_utf8mb4'REFUND_REQUESTED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_project_settlement_account`
--

DROP TABLE IF EXISTS `fan_project_settlement_account`;
CREATE TABLE `fan_project_settlement_account` (
                                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                                  `project_id` bigint NOT NULL,
                                                  `bank_code` char(3) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                  `account_number_enc` varbinary(512) NOT NULL,
                                                  `account_number_hmac` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                  `account_number_last4` char(4) COLLATE utf8mb4_unicode_ci NOT NULL,
                                                  `verification_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNVERIFIED',
                                                  `verified_at` datetime(6) DEFAULT NULL,
                                                  `created_at` datetime(6) NOT NULL,
                                                  `updated_at` datetime(6) NOT NULL,
                                                  PRIMARY KEY (`id`),
                                                  UNIQUE KEY `uk_fan_project_settlement_project` (`project_id`),
                                                  KEY `idx_fan_project_settlement_hmac` (`account_number_hmac`),
                                                  CONSTRAINT `fk_fan_project_settlement_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
                                                  CONSTRAINT `ck_fan_project_account_last4` CHECK (regexp_like(`account_number_last4`,_utf8mb4'^[0-9]{4}$')),
                                                  CONSTRAINT `ck_fan_project_account_verification` CHECK ((`verification_status` in (_utf8mb4'UNVERIFIED',_utf8mb4'VERIFIED',_utf8mb4'FAILED'))),
                                                  CONSTRAINT `ck_fan_project_bank_code` CHECK ((`bank_code` in (_utf8mb4'004',_utf8mb4'088',_utf8mb4'011',_utf8mb4'090',_utf8mb4'020',_utf8mb4'081',_utf8mb4'092',_utf8mb4'032',_utf8mb4'031')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fan_project_fraud_check`
--

DROP TABLE IF EXISTS `fan_project_fraud_check`;
CREATE TABLE `fan_project_fraud_check` (
                                           `id` bigint NOT NULL AUTO_INCREMENT,
                                           `project_id` bigint NOT NULL,
                                           `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
                                           `target_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
                                           `bank_code` char(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                           `provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'THECHEAT',
                                           `provider_result_code` int DEFAULT NULL,
                                           `result_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
                                           `caution_yn` char(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                           `search_window_start_at` datetime(6) DEFAULT NULL,
                                           `search_window_end_at` datetime(6) DEFAULT NULL,
                                           `checked_at` datetime(6) DEFAULT NULL,
                                           `requested_by` bigint DEFAULT NULL,
                                           `created_at` datetime(6) NOT NULL,
                                           PRIMARY KEY (`id`),
                                           KEY `idx_fan_project_fraud_latest` (`project_id`,`target_type`,`checked_at`),
                                           KEY `idx_fan_project_fraud_target` (`target_fingerprint`,`checked_at`),
                                           KEY `idx_fan_project_fraud_requester` (`requested_by`),
                                           CONSTRAINT `fk_fan_project_fraud_project` FOREIGN KEY (`project_id`) REFERENCES `fan_project` (`id`) ON DELETE CASCADE,
                                           CONSTRAINT `fk_fan_project_fraud_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
                                           CONSTRAINT `ck_fan_project_fraud_bank_code` CHECK (((`bank_code` is null) or regexp_like(`bank_code`,_utf8mb4'^[0-9]{3}$'))),
                                           CONSTRAINT `ck_fan_project_fraud_caution` CHECK (((`caution_yn` is null) or (`caution_yn` in (_utf8mb4'Y',_utf8mb4'N')))),
                                           CONSTRAINT `ck_fan_project_fraud_status` CHECK ((`result_status` in (_utf8mb4'PENDING',_utf8mb4'CLEAR',_utf8mb4'CAUTION',_utf8mb4'ERROR'))),
                                           CONSTRAINT `ck_fan_project_fraud_target` CHECK ((`target_type` in (_utf8mb4'PHONE',_utf8mb4'ACCOUNT'))),
                                           CONSTRAINT `ck_fan_project_fraud_window` CHECK (((`search_window_start_at` is null) or (`search_window_end_at` is null) or (`search_window_end_at` >= `search_window_start_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `report`
--

DROP TABLE IF EXISTS `report`;
CREATE TABLE `report` (
                          `id` bigint NOT NULL AUTO_INCREMENT,
                          `created_at` datetime(6) NOT NULL,
                          `reason` enum('ABUSE','ETC','SEXUAL','SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                          `post_id` bigint NOT NULL,
                          `reporter_id` bigint NOT NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `UK59baeft7frgypa05ajup9wrij` (`post_id`,`reporter_id`),
                          KEY `FKqbhdxqd3ly7fkhly5nrl2j93k` (`reporter_id`),
                          CONSTRAINT `FKnuqod1y014fp5bmqjeoffcgqy` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
                          CONSTRAINT `FKqbhdxqd3ly7fkhly5nrl2j93k` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `comment_report`
--

DROP TABLE IF EXISTS `comment_report`;
CREATE TABLE `comment_report` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `created_at` datetime(6) NOT NULL,
                                  `reason` enum('ABUSE','ETC','SEXUAL','SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `comment_id` bigint NOT NULL,
                                  `reporter_id` bigint NOT NULL,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `UK7a7j27uutr1ew9m87et35eily` (`comment_id`,`reporter_id`),
                                  KEY `FKn7ue556scerw6fa5epexg2g4j` (`reporter_id`),
                                  CONSTRAINT `FK8ugevhla12t9n0uw4o0rkvnth` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
                                  CONSTRAINT `FKn7ue556scerw6fa5epexg2g4j` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `filter_keyword`
--

DROP TABLE IF EXISTS `filter_keyword`;
CREATE TABLE `filter_keyword` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `keyword` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `UKj55c0tyqc5n2qto80hyjpegy1` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


--
-- Table structure for table `group_schedule`
--

DROP TABLE IF EXISTS `group_schedule`;
CREATE TABLE `group_schedule` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `group_id` bigint NOT NULL,
                                  `created_by` bigint NOT NULL,
                                  `event_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `place` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  `start_at` datetime(6) NOT NULL,
                                  `end_at` datetime(6) DEFAULT NULL,
                                  `ticket_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  `ticket_image_stored_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                  `created_at` datetime(6) NOT NULL,
                                  `updated_at` datetime(6) NOT NULL,
                                  `deleted_at` datetime(6) DEFAULT NULL,
                                  PRIMARY KEY (`id`),
                                  KEY `idx_gs_group_start` (`group_id`,`start_at`),
                                  KEY `fk_gs_creator` (`created_by`),
                                  CONSTRAINT `fk_gs_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
                                  CONSTRAINT `fk_gs_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`),
                                  CONSTRAINT `ck_gs_event_type` CHECK ((`event_type` in (_utf8mb4'BROADCAST',_utf8mb4'LIVE',_utf8mb4'TICKET_OPEN',_utf8mb4'CONCERT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `recipient_id` bigint NOT NULL,
                                `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `source_id` bigint DEFAULT NULL,
                                `is_read` tinyint(1) NOT NULL DEFAULT '0',
                                `created_at` datetime(6) NOT NULL,
                                PRIMARY KEY (`id`),
                                KEY `idx_noti_recipient` (`recipient_id`,`is_read`,`created_at`),
                                CONSTRAINT `fk_noti_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `notification_setting`
--

DROP TABLE IF EXISTS `notification_setting`;
CREATE TABLE `notification_setting` (
                                        `user_id` bigint NOT NULL,
                                        `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `enabled` tinyint(1) NOT NULL DEFAULT '1',
                                        PRIMARY KEY (`user_id`,`type`),
                                        CONSTRAINT `fk_ns_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `group_follow`
--

DROP TABLE IF EXISTS `group_follow`;
CREATE TABLE `group_follow` (
                                `fan_id` bigint NOT NULL,
                                `group_id` bigint NOT NULL,
                                `created_at` datetime(6) NOT NULL,
                                PRIMARY KEY (`fan_id`,`group_id`),
                                KEY `fk_gf_group` (`group_id`),
                                CONSTRAINT `fk_gf_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
                                CONSTRAINT `fk_gf_group` FOREIGN KEY (`group_id`) REFERENCES `artist_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `artist_group_profiles` (
                                         `id` bigint NOT NULL AUTO_INCREMENT,
                                         `artist_id` bigint NOT NULL,
                                         `gender` varchar(10) DEFAULT NULL,
                                         `member_count` int DEFAULT NULL,
                                         `nationality` varchar(50) DEFAULT NULL,
                                         `category` varchar(50) DEFAULT NULL,
                                         `debut_date` date DEFAULT NULL,
                                         `updated_at` datetime(6) NOT NULL,
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_agp_artist` (`artist_id`),
                                         CONSTRAINT `fk_agp_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `community_members` (
                                     `id` bigint NOT NULL AUTO_INCREMENT,
                                     `fan_id` bigint NOT NULL,
                                     `artist_id` bigint NOT NULL,
                                     `joined_at` datetime(6) NOT NULL,
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_community_member` (`fan_id`,`artist_id`),
                                     CONSTRAINT `fk_cm_fan` FOREIGN KEY (`fan_id`) REFERENCES `users` (`id`),
                                     CONSTRAINT `fk_cm_artist` FOREIGN KEY (`artist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `community_profiles` (
                                      `id` bigint NOT NULL AUTO_INCREMENT,
                                      `community_member_id` bigint NOT NULL,
                                      `nickname` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
                                      `bio` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                      `avatar_stored_name` VARCHAR(255) NULL,
                                      `background_stored_name` VARCHAR(255) NULL,
                                      `created_at` datetime(6) NOT NULL,
                                      `updated_at` datetime(6) NOT NULL,
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_cp_member` (`community_member_id`),
                                      CONSTRAINT `fk_cp_member` FOREIGN KEY (`community_member_id`) REFERENCES `community_members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

