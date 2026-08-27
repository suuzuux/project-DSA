/**
 * ============================================================
 * WePlaNet – Shell (메뉴 / 채팅 / 멤버십) Injector & Controllers
 * ------------------------------------------------------------
 * body에 data-shell="fan" 이 있으면 공통 오버레이를 삽입한다.
 *
 * data-base       : 상대경로 prefix (예: "" | "../" | "../../")
 * data-dm-expired : "true" 이면 DM 방에 구독 만료 배너 표시 (P19)
 *
 * 사용 예)
 *   <body data-shell="fan" data-base="../">
 *   <script src="../js/main.js"></script>
 *   <script src="../js/shell.js"></script>
 * ============================================================
 */
(function () {
  "use strict";

  const body = document.body;
  if (body.getAttribute("data-shell") !== "fan") return;

  const base = body.getAttribute("data-base") || "";
  const dmExpired = body.getAttribute("data-dm-expired") === "true";
  const isAuthenticated = body.getAttribute("data-authenticated") === "true";
  // 관리자에게만 드로어 메뉴에 "금칙어 관리" 항목을 보여주기 위함.
  // (각 화면 <body>에서 data-role="ROLE_ADMIN" 형태로 내려줌. 없으면 빈 문자열)
  const roleName = body.getAttribute("data-role") || "";
  const isAdmin = roleName === "ROLE_ADMIN";
  const nickname = body.getAttribute("data-nickname") || "";
  const artists = Array.isArray(window.__WEPLANET_ARTISTS__) ? window.__WEPLANET_ARTISTS__ : [];

  // 이모지 대신 쓰는 line-icon 모음 (24x24, currentColor). 이모지는 폰트/OS마다 그림체가 달라져서
  // "AI가 대충 넣은 느낌"이 났는데, 선 아이콘으로 통일하면 한 톤으로 정리됨.
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
    if (!artists.length) {
      return isAuthenticated
        ? `<p class="drawer-menu__section-title">커뮤니티 바로가기</p>
  <div class="drawer-menu__communities">
    <p class="text-xs text-muted" style="padding:8px 0;line-height:1.5;">
      아직 가입한 커뮤니티가 없어요.<br />좋아하는 아티스트 커뮤니티에 가입하고 팬으로 참여해보세요!
    </p>
       <a href="${base}?openSearch=1" style="color:var(--wp-brand);font-weight:600;font-size:var(--wp-fs-xs);">커뮤니티 찾아보기 ›</a>
  </div>`
        : "";
    }

    const links = artists
      .map((a) => {
        const logo = escapeHtml(a.logo || "?");
        const name = escapeHtml(a.nickname || "아티스트");
        return `<a href="${base}community/${a.id}"><span class="avatar avatar--sm">${logo}</span> ${name}</a>`;
      })
      .join("");

    return `<p class="drawer-menu__section-title">커뮤니티 바로가기</p>
  <div class="drawer-menu__communities">${links}</div>`;
  }

  /* ---------------------------------------------------------
   * HTML 템플릿
   * --------------------------------------------------------- */
  function shellHTML() {
    const greetBlock = isAuthenticated && nickname
      ? `<p class="drawer-menu__greet"><em>${escapeHtml(nickname)}</em> 님,<br />좋은 하루예요.</p>`
      : "";

    const communitiesBlock = communitiesBlockHtml();

    // 금칙어 관리(CHAT-04)는 관리자 전용 화면이라, ADMIN 계정으로 로그인했을 때만 메뉴에 노출함.
    // (예전엔 메뉴가 없어서 /chat/admin/keywords 주소를 직접 쳐야만 들어갈 수 있었음)
    const adminBlock = isAdmin
      ? `<a href="${base}chat/admin/keywords"><span class="nav-ico">${ICONS.shield}</span> 금칙어 관리</a>`
      : "";

    return `
<!-- ========== Shell Backdrop ========== -->
<div class="shell-backdrop" id="shellBackdrop" hidden></div>

<!-- ========== 1. 좌측 메뉴 드로어 (P13/18/19) ========== -->
<aside class="drawer-menu" id="drawerMenu" aria-label="메인 메뉴" aria-hidden="true">
  <div class="drawer-menu__top">
    <strong>메뉴</strong>
    <button type="button" class="icon-btn" data-shell-close="menu" aria-label="메뉴 닫기">✕</button>
  </div>
  ${greetBlock}
  ${communitiesBlock}

  <nav class="drawer-menu__nav">
    <a href="${base}collection.html"><span class="nav-ico">${ICONS.collection}</span> 나의 컬렉션</a>
    <a href="#" data-shell-alert="공지사항 (목업)"><span class="nav-ico">${ICONS.notice}</span> 공지사항</a>
    <a href="${base}shop.html"><span class="nav-ico">${ICONS.shop}</span> Shop</a>
    <a href="${base}membership.html"><span class="nav-ico">${ICONS.award}</span> 멤버십</a>
    <a href="${base}settings.html"><span class="nav-ico">${ICONS.settings}</span> 회원정보 및 설정</a>
    ${adminBlock}
  </nav>
</aside>

<!-- ========== 2. FAB ========== -->
<div class="fab-stack">
  <button type="button" class="fab fab--secondary" data-shell-alert="캘린더 (목업)" title="캘린더" aria-label="캘린더">${ICONS.calendar}</button>
  <button type="button" class="fab" id="fabChat" title="채팅 (DM)" aria-label="채팅 열기">${ICONS.send}</button>
</div>

<!-- ========== 3. DM 패널 ========== -->
<div class="dm-panel" id="dmPanel" role="dialog" aria-label="DM 채팅" aria-hidden="true">

  <!-- 3-A. DM 목록 (P13) -->
  <div class="dm-list-view is-active" id="dmListView">
    <div class="dm-header">
      <strong class="dm-header__title">WePlaNet DM</strong>
      <button type="button" class="icon-btn" data-shell-alert="더보기" aria-label="더보기">⋯</button>
      <button type="button" class="icon-btn" data-shell-alert="친구 추가" aria-label="친구">＋</button>
      <button type="button" class="icon-btn" data-shell-close="dm" aria-label="닫기">∨</button>
    </div>
    <div class="dm-body">
      <div class="dm-promo">
        <strong>구독 혜택 안내</strong>
        <a href="${base}membership.html">DM 100% 활용방법 ›</a>
      </div>
      <p class="dm-section-label">메시지</p>
      <p class="text-xs text-muted" style="padding:16px 4px;">아직 메시지가 없습니다.</p>
      <p class="dm-section-label">추천</p>
      <p class="text-xs text-muted" style="padding:16px 4px;">추천 아티스트가 없습니다.</p>
    </div>
  </div>

  <!-- 3-B. DM 채팅방 (P18 / P19) -->
  <div class="dm-room" id="dmRoomView">
    <div class="dm-header">
      <button type="button" class="icon-btn" id="dmBackBtn" aria-label="목록으로">‹</button>
      <div class="dm-header__title">
        <span id="dmRoomName">DM</span> <span class="badge-verified">✓</span>
        <small>ARTIST · DM</small>
      </div>
      <button type="button" class="icon-btn" data-shell-alert="검색" aria-label="검색">${ICONS.search}</button>
      <button type="button" class="icon-btn" data-shell-alert="더보기" aria-label="더보기">⋮</button>
    </div>

    <!-- 구독 만료 배너 (data-room에 따라 표시) -->
    <div class="dm-expired ${dmExpired ? "" : "hidden"}" id="dmExpiredBanner">
      <div class="dm-expired__icon">${ICONS.heart}</div>
      <div class="dm-expired__text">
        <strong>DM 구독 만료</strong>
        <span>다시 구독하고 새로운 메시지를 받아보세요.</span>
      </div>
      <a class="dm-expired__cta" href="${base}membership.html">구독하기 ›</a>
    </div>

    <div class="dm-messages" id="dmMessages">
      <p class="text-xs text-muted" style="padding:24px 8px;text-align:center;">대화를 시작해보세요.</p>
    </div>

    <form class="dm-composer" id="dmComposer">
      <button type="button" class="icon-btn" data-shell-alert="첨부" aria-label="첨부">＋</button>
      <input type="text" placeholder="메시지 입력" autocomplete="off" id="dmInput" />
      <button type="submit" class="send-btn" aria-label="전송">${ICONS.send}</button>
    </form>
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
      <h1 style="font-size:18px;">아티스트 팬클럽 공식 멤버십에 가입하고,<br />특별한 멤버십 혜택을 누려보세요.</h1>
    </div>
    <ul class="membership-benefits">
      <li>멤버십 전용 아티스트 공식 상품 구매 기회</li>
      <li>WePlaNet Shop 내 아티스트 콘텐츠 구매 관련 혜택</li>
      <li>공연 시 선예매, 추첨제 참여 기회</li>
      <li>WePlaNet 내 멤버십 전용 독점 콘텐츠</li>
      <li>멤버와 DM(1:1 채팅) 구독권 혜택</li>
    </ul>
    <p class="membership-price">₩ 30,000 / 년<small>VAT 포함</small></p>
    <form id="membershipJoinForm" method="post">
      <button type="submit" class="btn btn--accent btn--block btn--lg">멤버십 가입하기</button>
    </form>
  </div>
</div>

<!-- ========== 5. 멤버십 상세 모달 (P33) - 실데이터는 클릭 시 fetch로 채움 ========== -->
<div class="modal-backdrop" id="membershipDetailModal">
  <div class="modal">
    <div class="modal__head">
      <h2 class="modal__title">멤버십 상세 보기</h2>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <div class="membership-card-detail">
      <div class="membership-card-detail__row"><span>이름</span><strong id="membershipDetailName">-</strong></div>
      <div class="membership-card-detail__row"><span>멤버십 고유 번호</span><strong id="membershipDetailNo">-</strong></div>
      <div class="membership-card-detail__row"><span>기간</span><strong id="membershipDetailPeriod">-</strong></div>
    </div>
    <!-- [머지 충돌 해결] 이메일/전화번호는 양쪽 동일. 해지 폼은 HEAD에만 있고
         cancelMembership 엔드포인트가 유지되므로 HEAD 유지 -->
    <div class="settings-row"><span>이메일</span><span id="membershipDetailEmail">-</span></div>
    <div class="settings-row"><span>전화번호</span><span id="membershipDetailPhone">-</span></div>
    <form id="membershipCancelForm" method="post" style="margin-top:16px;"
          onsubmit="return confirm('멤버십을 해지할까요? DM 등 멤버십 전용 혜택을 더 이상 이용할 수 없습니다.');">
      <button type="submit" class="btn btn--ghost btn--block" style="color:var(--wp-danger, #d33);">멤버십 해지</button>
    </form>
  </div>
</div>
`;
  }

  /* ---------------------------------------------------------
   * Inject
   * --------------------------------------------------------- */
  const wrap = document.createElement("div");
  wrap.id = "weplanet-shell";
  wrap.innerHTML = shellHTML();
  document.body.appendChild(wrap);

  // 헤더에 햄버거가 없으면 brand 앞에 삽입
  ensureMenuToggle();

  // 멤버십 가입 모달(P27)의 실제 가입 폼 action을 현재 커뮤니티 아티스트로 채움
  // (모달 자체는 페이지 공통 삽입이라 서버 쪽 artist.id()를 직접 못 씀 - URL에서 뽑아옴)
  const membershipJoinForm = document.getElementById("membershipJoinForm");
  if (membershipJoinForm) {
    const artistMatch = location.pathname.match(/^\/community\/(\d+)/);
    if (artistMatch) {
      membershipJoinForm.action = "/community/" + artistMatch[1] + "/membership/join";
    }
  }

  // [머지 충돌 해결] 해지 엔드포인트를 유지했으므로 HEAD 유지
  // 멤버십 해지 폼도 같은 방식으로 action 채움
  const membershipCancelForm = document.getElementById("membershipCancelForm");
  if (membershipCancelForm) {
    const artistMatch = location.pathname.match(/^\/community\/(\d+)/);
    if (artistMatch) {
      membershipCancelForm.action = "/community/" + artistMatch[1] + "/membership/cancel";
    }
  }

  // 멤버십 상세 모달(P33) - "Membership 상세보기" 버튼을 누른 시점에 실제 가입일/만료일/연락처를 받아와 채움.
  // (예전엔 홍길동/2025.11.27 같은 고정값이 항상 떠 있었음 - 백엔드(/membership/detail)는 이미 실데이터를
  //  내려주고 있었는데 프론트에서 그걸 부르는 코드가 없었던 것)
  document.addEventListener("click", (e) => {
    if (!e.target.closest('[data-modal-open="membershipDetailModal"]')) return;
    const artistMatch = location.pathname.match(/^\/community\/(\d+)/);
    if (!artistMatch) return;
    fetch("/community/" + artistMatch[1] + "/membership/detail")
      .then((res) => res.json())
      .then((data) => {
        document.getElementById("membershipDetailName").textContent = data.name || "-";
        document.getElementById("membershipDetailNo").textContent = data.membershipNo || "-";
        document.getElementById("membershipDetailPeriod").textContent = data.period || "-";
        document.getElementById("membershipDetailEmail").textContent = data.email || "-";
        document.getElementById("membershipDetailPhone").textContent = data.phone || "-";
      });
  });

  /* ---------------------------------------------------------
   * Controllers
   * --------------------------------------------------------- */
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
    showList();
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

  function showRoom(name, expired) {
    dmRoomName.textContent = name;
    dmExpiredBanner.classList.toggle("hidden", !expired && !dmExpired);
    if (expired || dmExpired) dmExpiredBanner.classList.remove("hidden");
    else dmExpiredBanner.classList.add("hidden");
    dmListView.classList.remove("is-active");
    dmRoomView.classList.add("is-active");
  }

  function ensureMenuToggle() {
    // 이미 data-shell-open="menu" 버튼이 있으면 스킵
    if (document.querySelector('[data-shell-open="menu"]')) return;

    const header = document.querySelector(".site-header, .community-top");
    if (!header) return;

    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "menu-toggle";
    btn.setAttribute("data-shell-open", "menu");
    btn.setAttribute("aria-label", "메뉴 열기");
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
      alert(alertBtn.getAttribute("data-shell-alert"));
      return;
    }

    const roomBtn = e.target.closest("[data-open-room]");
    if (roomBtn) {
      const name =
        roomBtn.querySelector(".dm-list-item__name")?.textContent?.trim() ||
        roomBtn.getAttribute("data-open-room");
      const expired = roomBtn.getAttribute("data-room-expired") === "true";
      showRoom(name, expired);
      return;
    }
  });

  document.getElementById("fabChat")?.addEventListener("click", openDm);
  document.getElementById("dmBackBtn")?.addEventListener("click", showList);

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
    row.innerHTML = `<div class="dm-msg__bubble"></div><span class="dm-msg__time">방금</span>`;
    row.querySelector(".dm-msg__bubble").textContent = text;
    box.appendChild(row);
    input.value = "";
    box.scrollTop = box.scrollHeight;
  });

  // URL ?dm=1 이면 자동 오픈 (chat.html 데모용)
  const params = new URLSearchParams(location.search);
  if (params.get("dm") === "1") openDm();
  if (params.get("dm") === "expired") {
    openDm();
    showRoom("YUMA", true);
  }
  if (params.get("menu") === "1") openMenu();
})();