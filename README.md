# WePlaNet

![WePlaNet 로고](weplanet/src/main/resources/static/img/brand/weplanet-logo-horizontal.png)

팬과 아티스트가 한 공간에서 소통하고, 소속사가 콘텐츠와 활동을 운영하는 팬 커뮤니티 웹 서비스입니다. DSA BUSAN 14기 IT 4조가 함께 개발했습니다.

팬 활동에 필요한 게시판, DM, 라이브, 일정, 굿즈 구매와 프로젝트 참여를 아티스트별 커뮤니티 안에 모았습니다. 팬이 이용하는 화면과 아티스트·소속사의 운영 포털, 관리자의 승인·신고 처리 화면을 하나의 서비스로 연결했습니다.

## 이용자와 화면

| 이용자 | 할 수 있는 일 |
| --- | --- |
| 팬 | 커뮤니티 탐색·가입, 프로필 편집, 게시글·댓글·팔로우, DM·라이브 참여, 멤버십·굿즈 구매, 팬 프로젝트 개설·참여 |
| 아티스트 | 공식 게시글 작성, 멤버 프로필 선택, 팬 메시지 확인·답장, 라이브와 커뮤니티 활동 |
| 소속사 | 소속 아티스트와 멤버 관리, 프로필·공지·일정·미디어·굿즈 운영 |
| 관리자 | 입점 신청 검토, 회원·소속사·아티스트 관리, 신고·제재, 프로젝트 심사·정산 관리, 배너·이벤트 운영 |

## 주요 기능

### 계정과 회원정보

아이디·비밀번호 및 Google·Kakao·LINE 소셜 로그인을 지원합니다. 이메일 인증을 거쳐 가입하거나 계정을 찾고, 설정에서 회원정보·언어·알림 수신 여부와 소셜 연결을 관리할 수 있습니다. 장기 미접속 계정의 휴면 전환과 재활성화, 탈퇴 시 개인정보 정리도 포함합니다.

팬·아티스트·소속사·관리자에 따라 접근할 수 있는 화면을 구분합니다. 로그인 시도 제한, 동시 로그인 세션 제한과 관리자 이메일 인증을 적용했습니다.

### 커뮤니티와 게시판

검색과 필터로 아티스트 커뮤니티를 찾고, 커뮤니티마다 닉네임과 프로필을 설정해 가입합니다. 팬·아티스트 게시판에서 글, 댓글·답글, 좋아요, 북마크와 첨부파일을 이용할 수 있습니다. 하이라이트에는 최근 활동과 주요 콘텐츠를 모아 보여줍니다.

커뮤니티별 팔로우와 콘텐츠 공개 설정을 지원하며, 게시글 조회·작성과 요약·번역 요청에도 계정 역할과 가입 여부를 확인합니다.

### DM과 라이브

WebSocket/STOMP로 DM과 라이브 댓글을 송수신합니다. 팬 메시지는 멤버십과 일일 전송 한도를 확인하고, 아티스트 메시지는 팬에게 전달합니다. 금칙어 필터와 신고 기능을 함께 제공합니다.

라이브는 WebRTC 기반 영상 송출과 실시간 댓글로 구성했습니다. Gemini를 활용한 AI 팬 DM과 라이브 댓글 기능도 연결했습니다.

### 미디어와 일정

커뮤니티에서 공지, 미디어와 라이브 다시보기를 확인합니다. 소속사는 포털에서 콘텐츠와 아티스트 일정을 등록·관리하며, 팬은 캘린더에서 일정과 생일 등을 확인합니다. 공지 상단 고정과 활동 알림을 지원합니다.

### 굿즈와 멤버십

굿즈 검색·분류, 옵션별 재고, 장바구니와 주문서를 제공합니다. 소속사는 상품을 등록·관리하고, 팬은 배송지를 입력해 구매합니다. 멤버십 전용 상품의 접근 제한과 최근 배송지 저장을 적용했습니다.

굿즈·멤버십·팬 프로젝트 결제에는 Toss Payments를 연동했습니다. 가상계좌 입금 상태는 웹훅과 주기 조회로 확인하며 결제 금액과 주문 상태를 서버에서 검증합니다.

### 팬 프로젝트와 이벤트

팬 프로젝트는 개설 조건과 이메일 확인을 거친 뒤 관리자 심사를 받아 진행합니다. 프로젝트 소개, 참여 결제, 진행 상태와 정산 정보를 관리합니다.

해시태그 이벤트는 서비스 내부 팬 게시글을 대상으로 참여를 집계합니다. 참여 조건과 일일 한도를 검사하고 커뮤니티별 참여율 순위를 표시하며, 종료 후 결과 공지 초안을 생성합니다.

### 운영과 다국어

관리자는 입점 신청을 승인·반려하고 회원·커뮤니티·신고·제재를 관리합니다. 주요 운영 조치는 로그로 남깁니다. 승인된 소속사·아티스트의 계정 발급과 활성화 흐름도 포함합니다.

팬·포털 화면과 안내 메일은 한국어·일본어·영어를 지원하고 관리자 화면은 한국어를 사용합니다. 게시글·댓글·공지와 배너에는 Gemini 번역·요약을 연결했으며, 다크·라이트 테마와 주요 팬 화면의 반응형 구성을 적용했습니다.

## 🎬 프로젝트 시연 영상

일본어 자막이 포함된 실제 시연 영상에서 결제, 팬 참여 이벤트, 관리자 운영 이력을 기능별로 확인할 수 있습니다.

### 결제 시스템 (Payment System)

Toss Payments API를 연동해 굿즈·멤버십 구매와 팬 프로젝트 참여 결제를 처리합니다. 서버에서 주문 금액과 상태를 검증하고, 가상계좌 발급 후 입금 여부를 확인해 결제 상태를 반영합니다.

**日本語:** グッズ・メンバーシップの購入とファン企画への支払いにToss Payments APIを連携し、仮想口座の発行から入金確認まで対応しています。

**영상 내용 (10초):** 팬 프로젝트 선택 → Toss 결제창 → 가상계좌 안내

https://github.com/user-attachments/assets/d4c2f482-816e-4315-bb02-341d6a6078cb

### 해시태그 총공 이벤트 (Hashtag Event)

이벤트 기간에 지정 해시태그를 포함한 서비스 내부 팬 게시글을 집계합니다. 커뮤니티 가입 여부와 1인 1일 3건 한도를 검사하고, 아티스트에게 숨긴 글은 집계에서 제외합니다. 커뮤니티별 참여율 순위와 관리자 집계 현황을 확인할 수 있습니다.

**日本語:** サービス内のハッシュタグ付きファン投稿を集計し、コミュニティごとの参加率ランキングを表示します。参加条件と1人1日3件の上限を設けています。

**영상 내용 (20초):** 이벤트·해시태그 확인 → 팬 게시글 작성 → 참여율 변화와 순위 확인

https://github.com/user-attachments/assets/ca6e87cd-84f6-4aad-9dbe-6701d177dc60

### 관리자 로그 관리 (Admin Log Management)

입점 승인·반려, 회원 제재 등 관리자의 주요 운영 조치를 기록합니다. 관리자·조치 종류·대상·기간·검색어로 이력을 조회하고, 처리 일시·조치 사유·접속 IP와 전체·오늘 발생 로그 등의 통계를 확인할 수 있습니다.

**日本語:** 管理者による承認・制裁などの操作履歴を記録し、担当者・操作種別・対象・期間・キーワードによる検索と件数の確認ができます。

**영상 내용 (4초):** 관리자 로그 화면 → 통계·로그 목록 확인 → 조치 사유와 처리 이력 확인

https://github.com/user-attachments/assets/744fa344-1cf0-4c5d-bd1b-1c9b2bb52df9

### 풀버전 (Full Demo · 4분 41초)

팬·아티스트·소속사·관리자 화면의 전체 이용 흐름을 일본어 자막과 함께 확인할 수 있습니다.

**日本語:** ファン・アーティスト・所属事務所・管理者の画面を通して、サービス全体の利用の流れを日本語字幕付きでご紹介します。

https://github.com/user-attachments/assets/469769ad-212e-4769-8841-e56bdd0d54f3

**원본 화질 (1080p):** [영상 다운로드 / オリジナル動画をダウンロード](https://github.com/suuzuux/project-DSA/releases/download/demo-video-original/weplanet-full-demo-original-1080p.mp4) · 4분 41초 · 약 76MB

## 기술 구성

| 구분 | 사용 기술 |
| --- | --- |
| 서버 | Java 21, Spring Boot 4.1.0, Spring MVC |
| 인증·권한 | Spring Security, OAuth2 Client, 세션 인증 |
| 데이터 | MySQL, Spring Data JPA, Hibernate |
| 화면 | Thymeleaf, HTML, CSS, JavaScript, Fetch API |
| 실시간 | WebSocket, STOMP, SockJS, WebRTC |
| 외부 연동 | Toss Payments, Gemini API, Gmail SMTP, Google·Kakao·LINE OAuth |
| 편집·이미지 | Toast UI Editor, Cropper.js |
| 빌드·테스트 | Gradle Wrapper, JUnit, Spring Test |

## 저장소 구성

```text
project-DSA/
├─ README.md
├─ achievement-badges-v1/       배지 원본 이미지
└─ weplanet/                   애플리케이션 실행 위치
   ├─ build.gradle
   ├─ gradlew / gradlew.bat
   ├─ docs/                   DB 스키마와 데모 데이터
   └─ src/
      ├─ main/java/megane6/weplanet/
      │  ├─ controller/       기능별 요청 처리
      │  ├─ service/          업무 처리와 외부 서비스 연동
      │  ├─ repository/       데이터 조회·저장
      │  ├─ domain/           엔티티·DTO·이벤트
      │  ├─ security/         인증·권한·세션
      │  └─ i18n/             언어 처리
      ├─ main/resources/
      │  ├─ templates/        팬·포털·관리자 화면
      │  ├─ static/           CSS·JavaScript·이미지
      │  └─ application.properties
      └─ test/                기능·권한·상태 전환 테스트
```

## 로컬 실행

JDK 21과 MySQL을 준비합니다. 외부 연동 기능을 사용하려면 각 서비스의 개발용 키와 메일 계정도 필요합니다. 실행 명령은 `weplanet` 폴더에서 사용합니다.

### 1. 코드 받기

```bash
git clone --branch 20261008final https://github.com/suuzuux/project-DSA.git
cd project-DSA/weplanet
```

### 2. 데이터베이스 준비

새 로컬 DB에는 [전체 스키마 v2](weplanet/docs/weplanet_schema_full_reset_v2.sql)를 적용합니다. 이 파일은 기존 테이블을 삭제하고 다시 만들므로 보존할 데이터가 있는 DB에는 실행하지 않습니다. 기존 팀 DB를 갱신할 때는 현재 스키마를 확인한 뒤 [v2 변경 SQL](weplanet/docs/weplanet_DB적용_v2_간소화.sql)의 적용 범위를 확인합니다.

시연 데이터는 `weplanet/docs/demo/`의 `weplanet_demo_seed.sql`부터 번호순으로 구성되어 있습니다. 이미지가 필요하면 `demo_images`의 파일을 `weplanet/uploads/`로 복사합니다. 데모 계정은 SQL에 정의된 로컬 확인용 데이터이며 운영 계정으로 사용하지 않습니다.

애플리케이션은 `ddl-auto=validate`로 DB 구조를 검사합니다. 실행 시 테이블을 자동 생성하지 않으므로 스키마를 먼저 준비해야 합니다.

### 3. 환경변수 설정

`weplanet/.env`에 아래 항목을 설정합니다. `.env`는 Git 추적 대상에서 제외되어 있습니다. 값이 없는 예시이므로 실제 발급값을 채워야 합니다.

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/weplanet
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

FAN_PROJECT_ACCOUNT_ENCRYPTION_KEY=
FAN_PROJECT_ACCOUNT_HMAC_KEY=
PERSONAL_DATA_ENCRYPTION_KEY=

MAIL_USERNAME=
MAIL_PASSWORD=

GEMINI_API_KEY=
GEMINI_TRANSLATION_API_KEY=

TOSS_CLIENT_KEY=
TOSS_SECRET_KEY=

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
LINE_CLIENT_ID=
LINE_CLIENT_SECRET=

WEPLANET_BASE_URL=http://localhost:9999
```

암호화 키는 Base64 형식으로 준비합니다. 정산 계좌·개인정보 암호화 키는 각각 32바이트, 계좌 HMAC 키는 32바이트 이상이어야 합니다. 개인정보 전용 키를 사용하지 않으면 `PERSONAL_DATA_ENCRYPTION_KEY` 항목을 삭제해 정산 계좌 암호화 키를 사용합니다. 빈 값으로 남겨두면 키 검증에 실패합니다. 이미 암호화된 데이터를 공유하는 환경에서는 같은 키를 사용해야 합니다. 번역 전용 Gemini 키는 선택 항목입니다.

소셜 로그인은 개발자 콘솔에 콜백 주소(`/login/oauth2/code/google`, `/login/oauth2/code/kakao`, `/login/oauth2/code/line`)를 등록해야 합니다. 결제는 테스트 키와 입금 확인을 받을 수 있는 웹훅 환경을 사용합니다.

### 4. 서버 실행

Windows:

```powershell
.\gradlew.bat bootRun
```

macOS / Linux:

```bash
bash ./gradlew bootRun
```

기본 접속 주소는 [http://localhost:9999](http://localhost:9999)입니다. 팬 로그인은 `/login`, 아티스트·소속사 로그인은 `/portal/login`, 관리자 로그인은 `/admin/login`에서 시작합니다.

### 5. 테스트

```powershell
.\gradlew.bat test
```

macOS / Linux에서는 `bash ./gradlew test`를 사용합니다. DB 연결이 필요한 테스트도 있으므로 운영 데이터와 분리된 로컬 확인 환경에서 실행합니다. 결과는 `weplanet/build/reports/tests/test/index.html`에서 확인할 수 있습니다.

## 팀 개발

인증·회원정보, 커뮤니티·게시판, 채팅·라이브, 미디어·일정, 굿즈·멤버십, 팬 프로젝트·이벤트, 포털·관리자 영역을 나누어 개발하고 공통 화면과 DB에 통합했습니다. 이 저장소와 소개 문서는 특정 개인의 결과물이 아닌 팀 전체의 공동 작업을 담습니다.

소셜 아이콘의 출처는 [아이콘 출처 문서](weplanet/docs/social-icon-sources.md)에 정리했습니다. 데모 데이터와 이미지는 기능 확인 및 시연용입니다.
