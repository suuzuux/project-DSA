-- 기존 busan14 DB에 post 테이블 컬럼이 없을 때 실행
-- (Post 엔티티: artist_id, hidden_from_artist, link_url)
-- 이미 컬럼이 있으면 해당 ALTER는 에러가 나므로, 필요한 것만 골라 실행하세요.

USE busan14;

-- artist_id
ALTER TABLE post
  ADD COLUMN artist_id BIGINT NULL AFTER author_id;

ALTER TABLE post
  ADD KEY idx_post_artist (artist_id);

ALTER TABLE post
  ADD CONSTRAINT fk_post_artist FOREIGN KEY (artist_id) REFERENCES users (id);

-- Hide from Artists (팬 게시판)
ALTER TABLE post
  ADD COLUMN hidden_from_artist TINYINT(1) NOT NULL DEFAULT 0 AFTER like_count;

-- 링크 첨부 (선택)
ALTER TABLE post
  ADD COLUMN link_url VARCHAR(500) NULL AFTER hidden_from_artist;
