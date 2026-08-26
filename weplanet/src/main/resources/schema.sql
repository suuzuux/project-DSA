CREATE TABLE IF NOT EXISTS artist_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    artist_id BIGINT NOT NULL,
    intro TEXT NULL,
    header_image_url VARCHAR(500) NULL,
    logo_image_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_artist_profile_artist UNIQUE (artist_id),
    CONSTRAINT fk_artist_profile_artist FOREIGN KEY (artist_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS portal_notice (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    artist_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_portal_notice_artist FOREIGN KEY (artist_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS artist_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    artist_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    location VARCHAR(255) NULL,
    schedule_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_artist_schedule_artist FOREIGN KEY (artist_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS artist_block (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    artist_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_artist_block UNIQUE (artist_id, blocked_user_id),
    CONSTRAINT fk_artist_block_artist FOREIGN KEY (artist_id) REFERENCES users(id),
    CONSTRAINT fk_artist_block_user FOREIGN KEY (blocked_user_id) REFERENCES users(id)
);
