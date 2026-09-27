-- ============================================================
-- WePlaNet 소속사 그룹 멤버 계정 증분 스키마
-- 기존 데이터는 건드리지 않고 CHECK 제약에 새 값만 추가한다.
--   users.role                 + ARTIST_MEMBER     (그룹 소속 멤버 계정)
--   email_verification.purpose + ARTIST_ACTIVATION (소속사가 등록한 아티스트 그룹 계정 활성화 링크)
--   users.nickname             UNIQUE 해제 → 일반 인덱스
--                              (팬 닉네임과 아티스트(멤버) 닉네임은 겹쳐도 된다. 중복 규칙은 코드에서 검사)
-- group_members.is_leader 는 사용하지 않는다(기본값 0 유지).
-- ============================================================

USE `weplanet`;

ALTER TABLE `users`
DROP CHECK `ck_users_role`;

ALTER TABLE `users`
    ADD CONSTRAINT `ck_users_role`
        CHECK (`role` IN (
                          _utf8mb4'FAN',
                          _utf8mb4'ARTIST',
                          _utf8mb4'AGENCY',
                          _utf8mb4'ADMIN',
                          _utf8mb4'ARTIST_MEMBER'
            ));

ALTER TABLE `email_verification`
DROP CHECK `ck_email_verification_purpose`;

ALTER TABLE `email_verification`
    ADD CONSTRAINT `ck_email_verification_purpose`
        CHECK (`purpose` IN (
                             _utf8mb4'SIGNUP',
                             _utf8mb4'FAN_PROJECT_CREATE',
                             _utf8mb4'ADMIN_LOGIN',
                             _utf8mb4'AGENCY_ACTIVATION',
                             _utf8mb4'ARTIST_ACTIVATION'
            ));

-- 닉네임 UNIQUE 해제. 중복 검사는 "같은 쪽 계정끼리"만 코드에서 한다.
--   팬 쪽(FAN/AGENCY/ADMIN)끼리 : UserRepository.existsByNicknameAndRoleNotIn(nickname, Role.ARTIST_SIDE)
--   그룹(커뮤니티 이름)끼리      : UserRepository.existsByNicknameAndRole(name, Role.ARTIST)
--   같은 그룹 멤버끼리          : GroupMemberRepository.existsByGroupIdAndLeftAtIsNullAndMember_Nickname
-- 주의: DROP INDEX 는 이미 지운 상태에서 다시 실행하면 에러가 난다. 이 두 문장은 한 번만 실행.
ALTER TABLE `users`
DROP INDEX `uk_users_nickname`;

ALTER TABLE `users`
    ADD KEY `idx_users_nickname` (`nickname`);