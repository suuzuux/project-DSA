USE weplanet;

-- 팀 공통 커뮤니티가 users.id(ARTIST)를 기준으로 동작하므로,
-- 팬 뱃지와 팬 프로젝트의 대상도 artist_groups.id에서 users.id로 변경한다.
-- 기존 두 테이블에 데이터가 있다면 먼저 백업하고 artist_id 매핑을 확인해야 한다.

ALTER TABLE fan_badge_ownership
    DROP FOREIGN KEY fk_fan_badge_group,
    DROP INDEX fk_fan_badge_group,
    DROP INDEX idx_fan_badge_count,
    DROP INDEX uk_fan_badge_ownership,
    CHANGE COLUMN group_id artist_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_fan_badge_ownership (fan_id, artist_id, badge_code),
    ADD KEY idx_fan_badge_count (fan_id, artist_id, badge_type, revoked_at),
    ADD CONSTRAINT fk_fan_badge_artist
        FOREIGN KEY (artist_id) REFERENCES users(id);

ALTER TABLE fan_project
    DROP FOREIGN KEY fk_fan_project_group,
    DROP INDEX idx_fan_project_group_status,
    CHANGE COLUMN group_id artist_id BIGINT NOT NULL,
    ADD KEY idx_fan_project_artist_status (artist_id, status, funding_start_at),
    ADD CONSTRAINT fk_fan_project_artist
        FOREIGN KEY (artist_id) REFERENCES users(id);
