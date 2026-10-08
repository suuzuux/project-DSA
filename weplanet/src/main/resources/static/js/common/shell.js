/** 공통 셸(메뉴·DM·멤버십 모달) 삽입 - data-shell="fan" 이면 그리고, 문구는 /api/i18n/shell 로 받는다. */
(function () {
  "use strict";

  const body = document.body;
  if (body.getAttribute("data-shell") !== "fan") return;

  const base = body.getAttribute("data-base") || "";
  const root = base.endsWith("/") ? base : (base ? base + "/" : "/");
  const dmExpired = body.getAttribute("data-dm-expired") === "true";
  const isAuthenticated = body.getAttribute("data-authenticated") === "true";
  // 관리자에게만 금칙어 관리 메뉴 표시
  const roleName = body.getAttribute("data-role") || "";
  const isAdmin = roleName === "ROLE_ADMIN";
    // 아티스트는 전용 채팅방을 쓴다.
  const isArtist = roleName === "ROLE_ARTIST" || roleName === "ROLE_ARTIST_MEMBER";
  const isAgency = roleName === "ROLE_AGENCY";
  // 로그인한 본인 id
  const myId = body.getAttribute("data-fan-id") || "";
  const nickname = body.getAttribute("data-nickname") || "";
  const artists = Array.isArray(window.__WEPLANET_ARTISTS__) ? window.__WEPLANET_ARTISTS__ : [];

  // 문구 요청 실패 시 쓰는 한국어 기본값
  const DEFAULT_I18N = {
    "shell.menu.title": "메뉴",
    "shell.menu.close": "메뉴 닫기",
    "shell.menu.open": "메뉴 열기",
    "shell.menu.greet": "{0} 님,<br />좋은 하루예요.",
    "shell.community.title": "커뮤니티",
    "shell.community.explore": "커뮤니티 찾아보기 ›",
    "shell.community.empty": "표시할 커뮤니티가 없어요.",
    "shell.community.emptyJoined": "아직 가입한 커뮤니티가 없어요.<br />좋아하는 아티스트 커뮤니티에 가입해보세요!",
    "shell.community.myCommunity": "내 커뮤니티",
    "shell.community.joinedCommunity": "가입한 커뮤니티",
    "shell.nav.collection": "나의 컬렉션",
    "shell.nav.notice": "공지사항",
    "shell.nav.noticeUnread": "공지사항, 새 공지 있음",
    "shell.nav.shop": "Shop",
    "shell.nav.settings": "회원정보 및 설정",
    "shell.nav.keyword": "금칙어 관리",
    "shell.guest.greet": "회원가입 후<br />다양한 서비스를<br />이용해보세요!",
    "shell.guest.signup": "회원가입",
    "shell.fab.calendar": "캘린더",
    "shell.fab.chatFan": "팬 채팅방",
    "shell.fab.chatDm": "채팅 (DM)",
    "shell.fab.chatOpen": "채팅 열기",
    "shell.admin.pageLink": "관리자 페이지로 이동",
    // 커뮤니티 검색·가입 모달
    "layout.header.searchTitle": "커뮤니티 검색",
    "main.search.placeholder": "아티스트/그룹명 검색",
    "main.search.gender": "성별",
    "main.search.all": "전체",
    "main.search.genderMale": "보이그룹/남성",
    "main.search.genderFemale": "걸그룹/여성",
    "main.search.genderMixed": "혼성",
    "main.search.category": "직업/카테고리",
    "main.search.categoryIdol": "아이돌",
    "main.search.categoryActor": "배우",
    "main.search.submit": "검색",
    "community.join.button": "커뮤니티 가입하기",
    "community.join.thisCommunity": "이 커뮤니티",
    "community.join.modalPrompt": "<span id=\"communityJoinArtistName\">{0}</span>에서 사용할 닉네임을 정해주세요.",
    "community.join.nicknameLabel": "닉네임",
    "community.join.nicknamePlaceholder": "최대 10자",
    "community.join.help": "커뮤니티에 가입하고 포스트 쓰기, 알림 설정 등 더 많은 서비스를 이용하세요.",
    "community.join.submit": "가입하기",
    // DM 패널 / 멤버십 모달
    "shell.artistFallback": "아티스트",
    "shell.dm.panelLabel": "DM 채팅",
    "shell.dm.addFriend": "친구 추가",
    "shell.dm.friend": "친구",
    "shell.dm.close": "닫기",
    "shell.dm.promoTitle": "구독 혜택 안내",
    "shell.dm.promoDesc": "아티스트 커뮤니티에서 멤버십 가입 시 이용할 수 있어요",
    "shell.dm.messages": "메시지",
    "shell.dm.noMessages": "아직 메시지가 없습니다.",
    "shell.dm.recommend": "추천",
    "shell.dm.noRecommend": "추천 아티스트가 없습니다.",
    "shell.dm.backToList": "목록으로",
    "shell.dm.search": "검색",
    "shell.dm.more": "더보기",
    "shell.dm.expiredTitle": "DM 구독 만료",
    "shell.dm.expiredDesc": "아티스트 커뮤니티에서 멤버십을 갱신해주세요.",
    "shell.dm.joinTitle": "멤버십 가입하고 DM을 시작해보세요",
    "shell.dm.joinDesc": "아티스트 커뮤니티에서 멤버십에 가입하면 아티스트와 1:1 DM을 나눌 수 있어요.",
    "shell.dm.startConversation": "대화를 시작해보세요.",
    "shell.dm.attach": "첨부",
    "shell.dm.inputPlaceholder": "메시지 입력",
    "shell.dm.send": "전송",
    "shell.dm.quota": "오늘 남은 메시지 {0}회",
    "shell.dm.fanDm": "팬 DM",
    "shell.dm.justNow": "방금",
    "shell.membership.heroTitle": "아티스트 팬클럽 공식 멤버십에 가입하고,<br />특별한 멤버십 혜택을 누려보세요.",
    "shell.membership.benefit1": "멤버십 전용 아티스트 공식 상품 구매 기회",
    "shell.membership.benefit2": "WePlaNet Shop 내 아티스트 콘텐츠 구매 관련 혜택",
    "shell.membership.benefit3": "공연 시 선예매, 추첨제 참여 기회",
    "shell.membership.benefit4": "WePlaNet 내 멤버십 전용 독점 콘텐츠",
    "shell.membership.benefit5": "멤버와 DM(1:1 채팅) 구독권 혜택",
    "shell.membership.price": "₩ 30,000 / 년",
    "shell.membership.vatIncluded": "VAT 포함",
    "shell.membership.join": "멤버십 가입하기",
    "shell.membership.detailTitle": "멤버십 상세 보기",
    "shell.membership.name": "이름",
    "shell.membership.number": "멤버십 고유 번호",
    "shell.membership.period": "기간",
    "shell.membership.email": "이메일",
    "shell.membership.phone": "전화번호",
    "shell.membership.cancelConfirm": "멤버십을 해지할까요? DM 등 멤버십 전용 혜택을 더 이상 이용할 수 없습니다.",
    "shell.membership.cancel": "멤버십 해지"
  };
  let I18N = DEFAULT_I18N;

  function t(key) {
    return (I18N && I18N[key] != null) ? I18N[key] : DEFAULT_I18N[key];
  }

  // 이모지 대신 쓰는 선 아이콘 (24x24, currentColor)
  const ICONS = {
    calendar: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>',
    send: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>',
    collection: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>',
    notice: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 5 6 9H2v6h4l5 4V5z"></path><path d="M15.5 8.5a5 5 0 0 1 0 7"></path><path d="M18.5 6a9 9 0 0 1 0 12"></path></svg>',
    shop: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"></path><line x1="3" y1="6" x2="21" y2="6"></line><path d="M16 10a4 4 0 0 1-8 0"></path></svg>',
    award: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="7"></circle><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"></polyline></svg>',
    settings: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>',
    search: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>',
    heart: '<svg class="icon" viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>',
    shield: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path><line x1="9" y1="9" x2="15" y2="15"></line><line x1="15" y1="9" x2="9" y2="15"></line></svg>',
  };

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function communitiesBlockHtml() {
    const allList = Array.isArray(window.__WEPLANET_OTHER_ARTISTS__) && window.__WEPLANET_OTHER_ARTISTS__.length
      ? window.__WEPLANET_OTHER_ARTISTS__
      : (Array.isArray(window.__WEPLANET_ARTISTS__) ? window.__WEPLANET_ARTISTS__ : artists);
    const primaryList = Array.isArray(window.__WEPLANET_JOINED_ARTISTS__)
      ? window.__WEPLANET_JOINED_ARTISTS__
      : [];

    function linksHtml(list) {
      return list
        .map((a) => {
          const name = escapeHtml(a.nickname || t("shell.artistFallback"));
          const logo = escapeHtml(a.logo || "?");
          const img = a.profileImageUrl
            ? `<img src="${escapeHtml(a.profileImageUrl)}" alt="">`
            : logo;
          // 영문 주소 우선, 없으면 숫자 주소
          const href = a.homeUrl ? escapeHtml(a.homeUrl) : `${root}community/${a.id}`;
          return `<a href="${href}"><span class="avatar avatar--sm">${img}</span> ${name}</a>`;
        })
        .join("");
    }

    // 비로그인: 모든 커뮤니티만
    if (!isAuthenticated) {
      if (!allList.length) {
        return `<p class="drawer-menu__section-title">${t("shell.community.title")}</p>
  <div class="drawer-menu__communities">
    <p class="text-xs text-muted" style="padding:8px 0;line-height:1.5;">${t("shell.community.empty")}</p>
  </div>`;
      }
      return `<p class="drawer-menu__section-title">${t("shell.community.title")}</p>
  <div class="drawer-menu__communities">${linksHtml(allList)}</div>`;
    }

    // 목록 아래 탐색 링크
    const exploreLink =
      `<a href="#" data-community-search style="color:var(--wp-brand);font-weight:600;font-size:var(--wp-fs-xs);">${t("shell.community.explore")}</a>`;

    let html = "";
    const primaryTitle = isArtist ? t("shell.community.myCommunity") : t("shell.community.joinedCommunity");
    if (primaryList.length) {
      html += `<p class="drawer-menu__section-title">${primaryTitle}</p>
  <div class="drawer-menu__communities">${linksHtml(primaryList)}${isArtist ? "" : exploreLink}</div>`;
    } else if (!isArtist) {
      html += `<p class="drawer-menu__section-title">${t("shell.community.joinedCommunity")}</p>
  <div class="drawer-menu__communities">
    <p class="text-xs text-muted" style="padding:8px 0;line-height:1.5;">
      ${t("shell.community.emptyJoined")}
    </p>
    ${exploreLink}
  </div>`;
    }

    // 전체 커뮤니티 목록 대신 검색 모달을 쓴다.
    return html;
  }

  // HTML 템플릿
  function shellHTML() {
    const greetName = `<em>${escapeHtml(nickname)}</em>`;
    const greetBlock = isAuthenticated && nickname
      ? `<p class="drawer-menu__greet">${t("shell.menu.greet").replace("{0}", greetName)}</p>`
      : "";

    const communitiesBlock = communitiesBlockHtml();

    // 금칙어 관리는 관리자에게만 노출
    const adminBlock = isAdmin
      ? `<a href="${root}chat/admin/keywords"><span class="nav-ico">${ICONS.shield}</span> ${t("shell.nav.keyword")}</a>`
      : "";

    // 나의 컬렉션은 아티스트·멤버 계정에 숨긴다.
    const collectionLink = isArtist
      ? ""
      : `<a href="${root}collection"><span class="nav-ico">${ICONS.collection}</span> ${t("shell.nav.collection")}</a>`;

    // 비로그인이면 메뉴 대신 가입 안내만 보여준다.
    const menuBody = isAuthenticated
      ? `${greetBlock}
  <div id="drawerCommunities">${communitiesBlock}</div>

  <nav class="drawer-menu__nav">
    ${collectionLink}
    <a href="${isAdmin ? root + "admin/notices" : root + "notices"}" data-site-notice-link>
      <span class="nav-ico">${ICONS.notice}</span> ${t("shell.nav.notice")}
      <span class="drawer-menu__badge" data-site-notice-badge hidden aria-hidden="true"></span>
    </a>
    <a href="${root}shop"><span class="nav-ico">${ICONS.shop}</span> ${t("shell.nav.shop")}</a>
    <a href="${root}settings"><span class="nav-ico">${ICONS.settings}</span> ${t("shell.nav.settings")}</a>
    ${adminBlock}
  </nav>`
      : `<p class="drawer-menu__greet">${t("shell.guest.greet")}</p>

  <nav class="drawer-menu__nav">
    <a href="${root}signup">${t("shell.guest.signup")}</a>
  </nav>`;

    // 관리자는 채팅 버튼을 노출하지 않는다.
    const chatFabHtml = isAdmin
      ? ""
      : `<button type="button" class="fab" id="fabChat" title="${isArtist ? t("shell.fab.chatFan") : t("shell.fab.chatDm")}" aria-label="${t("shell.fab.chatOpen")}">${ICONS.send}</button>`;

    return `
<!-- ========== Shell Backdrop ========== -->
<div class="shell-backdrop" id="shellBackdrop" hidden></div>

<!-- ========== 1. 좌측 메뉴 드로어 (P13/18/19) ========== -->
<aside class="drawer-menu" id="drawerMenu" aria-label="${t("shell.menu.title")}" aria-hidden="true">
  <div class="drawer-menu__top">
    <strong>${t("shell.menu.title")}</strong>
    <button type="button" class="icon-btn" data-shell-close="menu" aria-label="${t("shell.menu.close")}">✕</button>
  </div>
  ${menuBody}
</aside>

<!-- ========== 2. FAB ========== -->
<div class="fab-stack">
  <button type="button" class="fab fab--secondary" data-shell-open="calendar" title="${t("shell.fab.calendar")}" aria-label="${t("shell.fab.calendar")}">${ICONS.calendar}</button>
  ${chatFabHtml}
</div>

<!-- ========== 3. DM 패널 ========== -->
<div class="dm-panel" id="dmPanel" role="dialog" aria-label="${escapeHtml(t("shell.dm.panelLabel"))}" aria-hidden="true">

  <!-- 3-A. DM 목록 (P13) -->
  <div class="dm-list-view is-active" id="dmListView">
    <div class="dm-header">
      <strong class="dm-header__title">WePlaNet DM</strong>
      <button type="button" class="icon-btn" data-shell-alert="${escapeHtml(t("shell.dm.addFriend"))}" aria-label="${escapeHtml(t("shell.dm.friend"))}">＋</button>
      <button type="button" class="icon-btn" data-shell-close="dm" aria-label="${escapeHtml(t("shell.dm.close"))}">∨</button>
    </div>
    <div class="dm-body">
      <div class="dm-promo">
        <strong>${escapeHtml(t("shell.dm.promoTitle"))}</strong>
        <span class="text-xs text-muted">${escapeHtml(t("shell.dm.promoDesc"))}</span>
      </div>
      <p class="dm-section-label">${escapeHtml(t("shell.dm.messages"))}</p>
      <p class="text-xs text-muted" style="padding:16px 4px;">${escapeHtml(t("shell.dm.noMessages"))}</p>
      <p class="dm-section-label">${escapeHtml(t("shell.dm.recommend"))}</p>
      <p class="text-xs text-muted" style="padding:16px 4px;">${escapeHtml(t("shell.dm.noRecommend"))}</p>
    </div>
  </div>

  <!-- 3-B. DM 채팅방 (P18 / P19) -->
  <div class="dm-room" id="dmRoomView">
    <div class="dm-header">
      <button type="button" class="icon-btn" id="dmBackBtn" aria-label="${escapeHtml(t("shell.dm.backToList"))}">‹</button>
      <div class="dm-header__title">
        <span id="dmRoomName">DM</span> <span class="badge-verified">✓</span>
        <small>ARTIST · DM</small>
      </div>
      <button type="button" class="icon-btn" data-shell-alert="${escapeHtml(t("shell.dm.search"))}" aria-label="${escapeHtml(t("shell.dm.search"))}">${ICONS.search}</button>
      <button type="button" class="icon-btn" data-shell-alert="${escapeHtml(t("shell.dm.more"))}" aria-label="${escapeHtml(t("shell.dm.more"))}">⋮</button>
    </div>

    <!-- 구독 만료 배너 (data-room에 따라 표시)
         한 번도 가입 안 한 팬이면 is-never-subscribed 를 붙여 만료 문구 대신 가입 안내 문구를 보여줌 -->
    <div class="dm-expired ${dmExpired ? "" : "hidden"}" id="dmExpiredBanner">
      <div class="dm-expired__icon">${ICONS.heart}</div>
      <div class="dm-expired__text dm-expired__text--expired">
        <strong>${escapeHtml(t("shell.dm.expiredTitle"))}</strong>
        <span>${escapeHtml(t("shell.dm.expiredDesc"))}</span>
      </div>
      <div class="dm-expired__text dm-expired__text--join">
        <strong>${escapeHtml(t("shell.dm.joinTitle"))}</strong>
        <span>${escapeHtml(t("shell.dm.joinDesc"))}</span>
      </div>
    </div>

    <div class="dm-messages" id="dmMessages">
      <p class="text-xs text-muted" style="padding:24px 8px;text-align:center;">${escapeHtml(t("shell.dm.startConversation"))}</p>
    </div>

    <!-- 금칙어/전송 한도 초과 등 경고를 화면 안에서 보여주는 배너
         (예전엔 브라우저 기본 alert을 썼는데, 팬 채팅방 화면과 방식이 달라서 통일함) -->
    <div class="dm-warning hidden" id="dmWarningBanner" role="alert"></div>

    <form class="dm-composer" id="dmComposer">
      <button type="button" class="icon-btn" data-shell-alert="${escapeHtml(t("shell.dm.attach"))}" aria-label="${escapeHtml(t("shell.dm.attach"))}">＋</button>
      <input type="text" placeholder="${escapeHtml(t("shell.dm.inputPlaceholder"))}" autocomplete="off" id="dmInput" />
      <button type="submit" class="send-btn" aria-label="${escapeHtml(t("shell.dm.send"))}">${ICONS.send}</button>
    </form>

    <!-- 오늘 남은 전송 횟수 (CHAT-05 하루 전송 한도) -->
    <p class="dm-quota" id="dmQuota" hidden>${escapeHtml(t("shell.dm.quota")).replace("{0}", '<strong id="dmQuotaCount">-</strong>')}</p>
  </div>
</div>

<!-- ========== 4. 멤버십 가입 모달 (P27) – 전역에서 data-modal-open 가능 ========== -->
<div class="modal-backdrop" id="membershipJoinModal">
  <div class="modal">
    <div class="modal__head">
      <h2 class="modal__title">Membership</h2>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <div class="membership-hero" style="padding-top:8px;">
      <div class="membership-hero__badge">${ICONS.award}</div>
      <h1 style="font-size:18px;">${t("shell.membership.heroTitle")}</h1>
    </div>
    <ul class="membership-benefits">
      <li>${escapeHtml(t("shell.membership.benefit1"))}</li>
      <li>${escapeHtml(t("shell.membership.benefit2"))}</li>
      <li>${escapeHtml(t("shell.membership.benefit3"))}</li>
      <li>${escapeHtml(t("shell.membership.benefit4"))}</li>
      <li>${escapeHtml(t("shell.membership.benefit5"))}</li>
    </ul>
    <p class="membership-price">${escapeHtml(t("shell.membership.price"))}<small>${escapeHtml(t("shell.membership.vatIncluded"))}</small></p>
    <form id="membershipJoinForm" method="post">
      <button type="submit" class="btn btn--accent btn--block btn--lg">${escapeHtml(t("shell.membership.join"))}</button>
    </form>
  </div>
</div>

<!-- ========== 5. 멤버십 상세 모달 (P33) - 실데이터는 클릭 시 fetch로 채움 ========== -->
<div class="modal-backdrop" id="membershipDetailModal">
  <div class="modal">
    <div class="modal__head">
      <h2 class="modal__title">${escapeHtml(t("shell.membership.detailTitle"))}</h2>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <div class="membership-card-detail">
      <div class="membership-card-detail__row"><span>${escapeHtml(t("shell.membership.name"))}</span><strong id="membershipDetailName">-</strong></div>
      <div class="membership-card-detail__row"><span>${escapeHtml(t("shell.membership.number"))}</span><strong id="membershipDetailNo">-</strong></div>
      <div class="membership-card-detail__row"><span>${escapeHtml(t("shell.membership.period"))}</span><strong id="membershipDetailPeriod">-</strong></div>
    </div>
    <!-- [머지 충돌 해결] 이메일/전화번호는 양쪽 동일. 해지 폼은 HEAD에만 있고
         cancelMembership 엔드포인트가 유지되므로 HEAD 유지 -->
    <div class="settings-row"><span>${escapeHtml(t("shell.membership.email"))}</span><span id="membershipDetailEmail">-</span></div>
    <div class="settings-row"><span>${escapeHtml(t("shell.membership.phone"))}</span><span id="membershipDetailPhone">-</span></div>
    <form id="membershipCancelForm" method="post" style="margin-top:16px;"
          data-confirm="${escapeHtml(t("shell.membership.cancelConfirm"))}"
          onsubmit="return WePlaNet.confirmSubmit(this, this.dataset.confirm);">
      <button type="submit" class="btn btn--ghost btn--block" style="color:var(--wp-danger, #d33);">${escapeHtml(t("shell.membership.cancel"))}</button>
    </form>
  </div>
</div>
`;
  }

  function init() {
    // 삽입
    const wrap = document.createElement("div");
    wrap.id = "weplanet-shell";
    wrap.innerHTML = shellHTML();
    document.body.appendChild(wrap);

    // 헤더에 햄버거가 없으면 brand 앞에 삽입
    ensureMenuToggle();
    ensureAdminPageLink();

    // 시스템 공지 뱃지 - 메뉴 공지사항 링크에 미읽음 점 표시
    (function siteNoticeBadge() {
      const READ_KEY = "weplanet.site-notice.read";
      const link = document.querySelector("[data-site-notice-link]");
      const badge = document.querySelector("[data-site-notice-badge]");
      if (!link || !badge) return;

      function loadReadIds() {
        try {
          const raw = JSON.parse(localStorage.getItem(READ_KEY) || "[]");
          return Array.isArray(raw) ? raw.map(String) : [];
        } catch (e) {
          return [];
        }
      }

      function saveReadIds(ids) {
        const unique = Array.from(new Set(ids.map(String)));
        localStorage.setItem(READ_KEY, JSON.stringify(unique));
      }

      function setBadgeVisible(visible) {
        if (visible) {
          badge.removeAttribute("hidden");
          badge.setAttribute("aria-hidden", "false");
          link.setAttribute("aria-label", t("shell.nav.noticeUnread"));
        } else {
          badge.setAttribute("hidden", "");
          badge.setAttribute("aria-hidden", "true");
          link.removeAttribute("aria-label");
        }
      }

      function markAllRead(ids) {
        if (!ids.length) {
          setBadgeVisible(false);
          return;
        }
        saveReadIds(loadReadIds().concat(ids));
        setBadgeVisible(false);
      }

      fetch(root + "api/site-notices", { headers: { Accept: "application/json" } })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          const notices = data && Array.isArray(data.notices) ? data.notices : [];
          const ids = notices.map((n) => String(n.id));
          const path = location.pathname || "";
          if (/^\/notices(\/|$)/.test(path) || /^\/admin\/notices(\/|$)/.test(path)) {
            markAllRead(ids);
            return;
          }
          const read = loadReadIds();
          const unread = ids.filter((id) => read.indexOf(id) === -1);
          setBadgeVisible(unread.length > 0);
        })
        .catch(function () { /* ignore */ });
    })();

    // 페이지가 커뮤니티 목록을 안 넣었으면 서버에서 받아 메뉴를 다시 그린다.
    (function fillCommunitiesIfEmpty() {
      const joined = window.__WEPLANET_JOINED_ARTISTS__;
      const others = window.__WEPLANET_OTHER_ARTISTS__;
      const alreadyHas =
        (Array.isArray(joined) && joined.length) || (Array.isArray(others) && others.length);
      if (alreadyHas) return;

      fetch(root + "api/side-menu/communities", { headers: { Accept: "application/json" } })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (!data) return;
          window.__WEPLANET_JOINED_ARTISTS__ = data.joined || [];
          window.__WEPLANET_OTHER_ARTISTS__ = data.others || [];
          window.__WEPLANET_ARTISTS__ = window.__WEPLANET_JOINED_ARTISTS__;
          const box = document.getElementById("drawerCommunities");
          if (box) box.innerHTML = communitiesBlockHtml();
        })
        .catch(() => {
          // 목록을 못 받아도 메뉴는 그대로 쓴다.
        });
    })();

    // 현재 커뮤니티 번호 (헤더 data-artist-id 우선, 없으면 주소에서)
    function currentCommunityId() {
      const header = document.querySelector(".community-top__name[data-artist-id]");
      if (header && header.dataset.artistId) return header.dataset.artistId;
      const artistMatch = location.pathname.match(/^\/community\/(\d+)/);
      return artistMatch ? artistMatch[1] : null;
    }

    // 멤버십 가입 폼 action 을 현재 커뮤니티로 채운다.
    const membershipJoinForm = document.getElementById("membershipJoinForm");
    if (membershipJoinForm) {
      const communityId = currentCommunityId();
      if (communityId) {
        membershipJoinForm.action = "/community/" + communityId + "/membership/join";
      }
    }

    // 멤버십 해지 폼 action 도 같은 방식으로 채운다.
    const membershipCancelForm = document.getElementById("membershipCancelForm");
    if (membershipCancelForm) {
      const communityId = currentCommunityId();
      if (communityId) {
        membershipCancelForm.action = "/community/" + communityId + "/membership/cancel";
      }
    }

    // 멤버십 상세 모달 - 버튼을 누를 때 실제 가입일·만료일·연락처를 받아 채운다.
    document.addEventListener("click", (e) => {
      if (!e.target.closest('[data-modal-open="membershipDetailModal"]')) return;
      const communityId = currentCommunityId();
      if (!communityId) return;
      fetch("/community/" + communityId + "/membership/detail")
        .then((res) => res.json())
        .then((data) => {
          document.getElementById("membershipDetailName").textContent = data.name || "-";
          document.getElementById("membershipDetailNo").textContent = data.membershipNo || "-";
          document.getElementById("membershipDetailPeriod").textContent = data.period || "-";
          document.getElementById("membershipDetailEmail").textContent = data.email || "-";
          document.getElementById("membershipDetailPhone").textContent = data.phone || "-";
        });
    });

    // 컨트롤러
    const backdrop = document.getElementById("shellBackdrop");
    const drawer = document.getElementById("drawerMenu");
    const dmPanel = document.getElementById("dmPanel");
    const dmListView = document.getElementById("dmListView");
    const dmRoomView = document.getElementById("dmRoomView");
    const dmExpiredBanner = document.getElementById("dmExpiredBanner");
    const dmRoomName = document.getElementById("dmRoomName");

    function setHidden(el, hidden) {
      if (!el) return;
      if (hidden) {
        el.setAttribute("hidden", "");
        el.setAttribute("aria-hidden", "true");
      } else {
        el.removeAttribute("hidden");
        el.setAttribute("aria-hidden", "false");
      }
    }

    function syncBackdrop() {
      const anyOpen = drawer.classList.contains("is-open") || dmPanel.classList.contains("is-open");
      backdrop.classList.toggle("is-open", anyOpen);
      setHidden(backdrop, !anyOpen);
      document.body.style.overflow = anyOpen ? "hidden" : "";
    }

    function openMenu() {
      drawer.classList.add("is-open");
      drawer.setAttribute("aria-hidden", "false");
      syncBackdrop();
    }

    function closeMenu() {
      drawer.classList.remove("is-open");
      drawer.setAttribute("aria-hidden", "true");
      syncBackdrop();
    }

    function openDm() {
      dmPanel.classList.add("is-open");
      dmPanel.setAttribute("aria-hidden", "false");
      // 아티스트는 목록 없이 자기 DM 방을 바로 보여준다.
      if (isArtist) {
        showRoom(nickname || t("shell.dm.fanDm"), false);
      } else {
        showList();
      }
      syncBackdrop();
    }

    function closeDm() {
      dmPanel.classList.remove("is-open");
      dmPanel.setAttribute("aria-hidden", "true");
      syncBackdrop();
    }

    function showList() {
      dmListView.classList.add("is-active");
      dmRoomView.classList.remove("is-active");
    }

    function showRoom(name, expired, neverSubscribed) {
      dmRoomName.textContent = name;
      dmExpiredBanner.classList.toggle("hidden", !expired && !dmExpired);
      if (expired || dmExpired) dmExpiredBanner.classList.remove("hidden");
      else dmExpiredBanner.classList.add("hidden");
      dmExpiredBanner.classList.toggle("is-never-subscribed", !!neverSubscribed);
      dmListView.classList.remove("is-active");
      dmRoomView.classList.add("is-active");
    }

    function ensureAdminPageLink() {
      // 에이전시는 포털로, 관리자는 관리자 화면으로 돌아가는 버튼
      if (!isAgency && !isAdmin) return;
      if (document.querySelector("[data-admin-page-link]")) return;

      const actions = document.querySelector(".header-actions, .community-top__right");
      if (!actions) return;

      const link = document.createElement("a");
      link.href = isAdmin ? root + "admin" : "/portal/dashboard";
      link.className = "btn btn--ghost btn--sm";
      link.setAttribute("data-admin-page-link", "1");
      link.textContent = t("shell.admin.pageLink");
      // 로그아웃 버튼 바로 왼쪽에 둔다.
      const logoutForm = actions.querySelector('form[action$="/logout"]');
      actions.insertBefore(link, logoutForm || actions.firstChild);
    }

    function ensureMenuToggle() {
      // 이미 data-shell-open="menu" 버튼이 있으면 스킵
      if (document.querySelector('[data-shell-open="menu"]')) return;
      // 에이전시 최소 헤더에는 햄버거를 넣지 않는다.
      if (document.querySelector("[data-header-minimal]")) return;

      const header = document.querySelector(".site-header, .community-top");
      if (!header) return;

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "menu-toggle";
      btn.setAttribute("data-shell-open", "menu");
      btn.setAttribute("aria-label", t("shell.menu.open"));
      btn.textContent = "☰";

      // site-header: brand 앞 / community-top: left 앞
      const left = header.querySelector(".header-left, .community-top__left, .brand");
      if (left && left.parentElement === header) {
        header.insertBefore(btn, left);
        // brand를 header-left로 감싸지 않고 버튼만 앞에
      } else if (header.firstElementChild) {
        header.insertBefore(btn, header.firstElementChild);
      } else {
        header.appendChild(btn);
      }
    }

    /* 이벤트 위임 */
    document.addEventListener("click", (e) => {
      const openMenuBtn = e.target.closest('[data-shell-open="menu"]');
      if (openMenuBtn) {
        e.preventDefault();
        openMenu();
        return;
      }

      const closeTarget = e.target.closest("[data-shell-close]");
      if (closeTarget) {
        const what = closeTarget.getAttribute("data-shell-close");
        if (what === "menu") closeMenu();
        if (what === "dm") closeDm();
        return;
      }

      const alertBtn = e.target.closest("[data-shell-alert]");
      if (alertBtn) {
        e.preventDefault();
        WePlaNet.alert(alertBtn.getAttribute("data-shell-alert"));
        return;
      }

      const roomBtn = e.target.closest("[data-open-room]");
      if (roomBtn) {
        const name =
          roomBtn.querySelector(".dm-list-item__name")?.textContent?.trim() ||
          roomBtn.getAttribute("data-open-room");
        const expired = roomBtn.getAttribute("data-room-expired") === "true";
        const neverSubscribed = roomBtn.getAttribute("data-room-never-subscribed") === "true";
        showRoom(name, expired, neverSubscribed);
        return;
      }
    });

    document.getElementById("fabChat")?.addEventListener("click", () => {
      // 아티스트도 DM 모달을 연다.
      openDm();
    });
    document.getElementById("dmBackBtn")?.addEventListener("click", () => {
      // 아티스트는 뒤로가기가 패널 닫기
      if (isArtist) {
        closeDm();
      } else {
        showList();
      }
    });

    backdrop.addEventListener("click", () => {
      closeMenu();
      closeDm();
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        closeMenu();
        closeDm();
      }
    });

    // DM 전송 목업
    document.getElementById("dmComposer")?.addEventListener("submit", (e) => {
      e.preventDefault();
      const input = document.getElementById("dmInput");
      const text = input.value.trim();
      if (!text) return;
      const box = document.getElementById("dmMessages");
      const row = document.createElement("div");
      row.className = "dm-msg dm-msg--me";
      row.innerHTML = `<div class="dm-msg__bubble"></div><span class="dm-msg__time">${escapeHtml(t("shell.dm.justNow"))}</span>`;
      row.querySelector(".dm-msg__bubble").textContent = text;
      box.appendChild(row);
      input.value = "";
      box.scrollTop = box.scrollHeight;
    });

    // ?dm=1 이면 자동으로 연다 (관리자 제외).
    const params = new URLSearchParams(location.search);
    if (!isAdmin && params.get("dm") === "1") openDm();
    if (!isAdmin && params.get("dm") === "expired") {
      openDm();
      showRoom("YUMA", true);
    }
    if (params.get("menu") === "1") openMenu();

    // 스케줄 캘린더 CSS 와 global-icons 로드
    (function loadScheduleAssets() {
      if (!document.querySelector('link[href*="schedule-calendar.css"]')) {
        var link = document.createElement("link");
        link.rel = "stylesheet";
        var cssBase = document.body.getAttribute("data-base") || "/";
        link.href = cssBase.replace(/\/?$/, "/") + "css/calendar/schedule-calendar.css";
        document.head.appendChild(link);
      }
      if (document.querySelector('script[src*="global-icons.js"]')) return;
      var src = ((document.body.getAttribute("data-base") || "/") + "js/calendar/global-icons.js").replace("//js", "/js");
      var s = document.createElement("script");
      s.src = src;
      document.body.appendChild(s);
    })();

    // 커뮤니티 찾아보기 - 페이지 이동 없이 검색 모달을 띄운다 (없으면 만들어 넣음).
    function scriptUrl(name) {
      var current = document.querySelector('script[src*="shell.js"]');
      return current && current.src
        ? current.src.replace(/shell\.js(\?.*)?$/, name + "$1")
        : ((document.body.getAttribute("data-base") || "/") + "js/" + name).replace("//js", "/js");
    }

    function loadScriptOnce(name) {
      if (document.querySelector('script[src*="' + name + '"]')) return;
      var s = document.createElement("script");
      s.src = scriptUrl(name);
      document.body.appendChild(s);
    }

    function appendHtml(html) {
      var holder = document.createElement("div");
      holder.innerHTML = html.trim();
      document.body.appendChild(holder.firstElementChild);
    }

    function ensureExploreUi() {
      if (!document.getElementById("communitySearchModal")) {
        appendHtml(`
<div class="modal-backdrop" id="communitySearchModal">
  <div class="modal">
    <div class="modal__head">
      <strong class="modal__title">${escapeHtml(t("layout.header.searchTitle"))}</strong>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <input type="text" id="exploreKeyword" class="form-input" placeholder="${escapeHtml(t("main.search.placeholder"))}" style="margin-bottom:12px;" />
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:12px;">
      <div>
        <label class="text-xs text-muted" for="exploreGender">${escapeHtml(t("main.search.gender"))}</label>
        <select id="exploreGender" class="form-input">
          <option value="">${escapeHtml(t("main.search.all"))}</option>
          <option value="MALE">${escapeHtml(t("main.search.genderMale"))}</option>
          <option value="FEMALE">${escapeHtml(t("main.search.genderFemale"))}</option>
          <option value="MIXED">${escapeHtml(t("main.search.genderMixed"))}</option>
        </select>
      </div>
      <div>
        <label class="text-xs text-muted" for="exploreCategory">${escapeHtml(t("main.search.category"))}</label>
        <select id="exploreCategory" class="form-input">
          <option value="">${escapeHtml(t("main.search.all"))}</option>
          <option value="아이돌">${escapeHtml(t("main.search.categoryIdol"))}</option>
          <option value="배우">${escapeHtml(t("main.search.categoryActor"))}</option>
        </select>
      </div>
    </div>
    <button type="button" id="exploreSearchBtn" class="btn btn--primary btn--sm btn--block" style="margin-bottom:12px;">${escapeHtml(t("main.search.submit"))}</button>
    <div id="exploreResults" class="rising-grid" style="grid-template-columns:1fr;"></div>
  </div>
</div>`);
      }

      // 검색 결과에서 가입하려면 닉네임 모달도 필요하다.
      if (!document.getElementById("communityJoinModal")) {
        appendHtml(`
<div class="modal-backdrop" id="communityJoinModal">
  <div class="modal">
    <div class="modal__head">
      <strong class="modal__title">${escapeHtml(t("community.join.button"))}</strong>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <p class="mb-24">${t("community.join.modalPrompt").replace("{0}", escapeHtml(t("community.join.thisCommunity")))}</p>
    <div class="form-group" id="communityJoinNicknameGroup">
      <label class="form-label" for="communityJoinNicknameInput">${escapeHtml(t("community.join.nicknameLabel"))}</label>
      <input type="text" id="communityJoinNicknameInput" class="form-input" maxlength="10" placeholder="${escapeHtml(t("community.join.nicknamePlaceholder"))}" autocomplete="off" />
      <p class="form-error" id="communityJoinNicknameError"></p>
      <p class="text-xs text-muted" style="margin-top:8px;line-height:1.6;">
        ${escapeHtml(t("community.join.help"))}
      </p>
    </div>
    <button type="button" id="communityJoinSubmitBtn" class="btn btn--primary btn--block">${escapeHtml(t("community.join.submit"))}</button>
  </div>
</div>`);
      }

      loadScriptOnce("community-explore.js");
      loadScriptOnce("community-join.js");
    }

    document.addEventListener("click", function (e) {
      // 드로어에서 만든 모달은 여기서 직접 연다.
      if (e.target.closest("[data-community-search]")) {
        e.preventDefault();
        closeMenu();
        ensureExploreUi();
        var modal = document.getElementById("communitySearchModal");
        if (modal) modal.classList.add("is-open");
        return;
      }
      // 셸이 비동기로 그린 모달의 닫기·배경 클릭 처리
      var closeBtn = e.target.closest("#weplanet-shell [data-modal-close], #communitySearchModal [data-modal-close], #communityJoinModal [data-modal-close]");
      if (closeBtn) {
        var back = closeBtn.closest(".modal-backdrop");
        if (back) back.classList.remove("is-open");
        return;
      }
      if (e.target.id === "communitySearchModal" || e.target.id === "communityJoinModal"
          || (e.target.classList.contains("modal-backdrop") && e.target.closest("#weplanet-shell"))) {
        e.target.classList.remove("is-open");
      }
    });

    // dm-realtime.js 가 셸이 다 그려진 뒤 시작하도록 신호를 보낸다.
    window.WePlaNetShellReady = true;
    document.dispatchEvent(new Event("weplanet:shell-ready"));

    // dm-realtime.js 가 없는 화면이면 stomp.min.js 와 함께 불러온다.
    if (!isAdmin && !document.querySelector('script[src*="dm-realtime.js"]')) {
      if (window.Stomp) {
        loadScriptOnce("dm-realtime.js");
      } else {
        const stomp = document.createElement("script");
        stomp.src = "https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js";
        stomp.onload = () => loadScriptOnce("dm-realtime.js");
        document.body.appendChild(stomp);
      }
    }
  }

  // 문구를 받은 뒤 그린다 (실패해도 한국어로 진행).
  fetch("/api/i18n/shell", { headers: { Accept: "application/json" } })
    .then(function (res) { return res.ok ? res.json() : null; })
    .then(function (data) {
      if (data) I18N = Object.assign({}, DEFAULT_I18N, data);
    })
    .catch(function () { /* 네트워크 오류 시 한국어 기본값으로 진행 */ })
    .finally(init);
})();
