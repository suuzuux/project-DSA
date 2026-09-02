-- ============================================================
-- WePlaNet 증분 DB 적용 (2026-09-02)
-- ------------------------------------------------------------
-- 브랜치: REPORT-01
-- 대상: report / comment_report 두 테이블에 status(처리 상태) 컬럼 추가
--
-- [이 파일이 하는 일]
--   1) report, comment_report 테이블이 아직 없는 완전 새 DB라면, status 컬럼까지
--      포함된 최신 구조로 두 테이블을 새로 생성 (post/comment/users 테이블은
--      이미 weplanet_schema.sql로 만들어져 있다는 전제)
--   2) 두 테이블이 이미 있는데 status 컬럼만 없는 경우 - 컬럼만 추가
--      (DEFAULT 'PENDING'으로 넣기 때문에 기존에 쌓여있던 신고 데이터도
--       자동으로 "처리 대기" 상태가 됨 - 별도 백필 쿼리 필요 없음)
--
-- [이 파일이 하지 않는 일]
--   · docs/weplanet_schema.sql은 건드리지 않음 - 그 파일의 report/comment_report
--     정의는 아직 status 컬럼이 없는 예전 버전 그대로임.
--     → 그래서 앞으로 새로 DB를 세팅하는 사람은 weplanet_schema.sql을 실행한
--       "다음"에 반드시 이 파일도 이어서 실행해야 함 (순서 중요).
--   · 기존 report / comment_report 데이터를 지우거나 건드리지 않음.
--
-- [적용 후 확인]
--   · Spring Boot 재시작 → ddl-auto=validate 스키마 검증 통과 확인
--   · 이 파일 맨 아래 [확인] SELECT 결과에 status 컬럼과 PENDING 집계가 보이면 OK
--
-- ============================================================
-- 실행 방법 (아래 중 편한 것 하나만 선택)
-- ============================================================
--
-- ■ 방법 A — MySQL Workbench (GUI, 추천)
--   1) MySQL Workbench 실행 → Local instance(MySQL 8.x) 더블클릭으로 접속
--   2) 메뉴 File → Open SQL Script…
--   3) 이 파일(20260902_report_status_증분_DB적용.sql) 선택
--   4) 상단 ⚡ Execute (번개 아이콘) 클릭 — 또는 Ctrl+Shift+Enter
--   5) 하단 Output 탭에 에러 없이 완료됐는지 확인
--
-- ■ 방법 B — IntelliJ IDEA / Cursor Database 도구
--   1) 우측 Database 패널 → + → Data Source → MySQL
--   2) Host: localhost, Port: 3306, Database: weplanet, User/Password 입력 → Test Connection → OK
--   3) weplanet 데이터소스 우클릭 → New → Query Console
--   4) 이 파일 내용 전체 붙여넣기 (또는 파일 열기)
--   5) 녹색 ▶ Run 버튼 (Execute) 클릭 — 또는 Ctrl+Enter
--
-- ■ 방법 C — 명령줄 (mysql 클라이언트)
--   프로젝트 weplanet 폴더에서:
--   mysql -uroot -p --default-character-set=utf8mb4 weplanet < docs/20260902_report_status_증분_DB적용.sql
--
-- ■ 방법 D — DBeaver
--   1) weplanet 연결 더블클릭
--   2) SQL Editor → Open SQL script → 이 파일 선택
--   3) Execute SQL Script (Alt+X) 실행
--
-- [주의]
--   · Duplicate column name 메시지가 나오면 이미 적용된 것입니다 (재실행해도 안전).
--
-- ============================================================

USE weplanet;

-- ------------------------------------------------------------
-- [0] report / comment_report 테이블 자체가 아직 없는 완전 새 DB라면
--     status 컬럼까지 포함된 최신 구조로 생성 (이미 있으면 아무 일도 안 함)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE','ETC','SEXUAL','SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `status` enum('PENDING','DISMISSED','RESOLVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (대기/기각/처리완료)',
  `post_id` bigint NOT NULL COMMENT '신고 대상 게시글(post.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK59baeft7frgypa05ajup9wrij` (`post_id`, `reporter_id`),
  KEY `FKqbhdxqd3ly7fkhly5nrl2j93k` (`reporter_id`),
  CONSTRAINT `FKnuqod1y014fp5bmqjeoffcgqy` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKqbhdxqd3ly7fkhly5nrl2j93k` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 신고';

CREATE TABLE IF NOT EXISTS `comment_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '신고 PK',
  `created_at` datetime(6) NOT NULL COMMENT '신고 시각',
  `reason` enum('ABUSE','ETC','SEXUAL','SPAM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '신고 사유',
  `status` enum('PENDING','DISMISSED','RESOLVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (대기/기각/처리완료)',
  `comment_id` bigint NOT NULL COMMENT '신고 대상 댓글(comment.id)',
  `reporter_id` bigint NOT NULL COMMENT '신고자(users.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7a7j27uutr1ew9m87et35eily` (`comment_id`, `reporter_id`),
  KEY `FKn7ue556scerw6fa5epexg2g4j` (`reporter_id`),
  CONSTRAINT `FK8ugevhla12t9n0uw4o0rkvnth` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
  CONSTRAINT `FKn7ue556scerw6fa5epexg2g4j` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 신고';

-- ------------------------------------------------------------
-- [1] report.status - 테이블은 이미 있는데 컬럼만 없는 경우 추가
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'report'
      AND COLUMN_NAME = 'status'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE report ADD COLUMN status enum(''PENDING'',''DISMISSED'',''RESOLVED'') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING'' COMMENT ''처리 상태 (대기/기각/처리완료)'' AFTER reason',
    'SELECT ''SKIP: report.status already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- [2] comment_report.status - 위와 동일
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'comment_report'
      AND COLUMN_NAME = 'status'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE comment_report ADD COLUMN status enum(''PENDING'',''DISMISSED'',''RESOLVED'') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''PENDING'' COMMENT ''처리 상태 (대기/기각/처리완료)'' AFTER reason',
    'SELECT ''SKIP: comment_report.status already exists'' AS migration_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [확인] 아래 결과를 보고 적용 여부를 판단하세요.
-- ============================================================

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('report', 'comment_report')
  AND COLUMN_NAME = 'status';

SELECT 'report' AS table_name, status, COUNT(*) AS cnt FROM report GROUP BY status
UNION ALL
SELECT 'comment_report' AS table_name, status, COUNT(*) AS cnt FROM comment_report GROUP BY status;

SELECT '증분 적용 완료. 기존 신고 데이터는 전부 PENDING으로 채워져 있어야 정상입니다.' AS done;
