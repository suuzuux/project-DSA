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
  const nickname = body.getAttribute("data-nickname") || "";
  const artists = Array.isArray(window.__WEPLANET_ARTISTS__) ? window.__WEPLANET_ARTISTS__ : [];

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function communitiesBlockHtml() {
    const searchBtn = `<button type="button" class="icon-btn" data-shell-open="explore" aria-label="커뮤니티 탐색">🔍</button>`;

    if (!artists.length) {
      return isAuthenticated
        ? `<div class="drawer-menu__section-head">
    <p class="drawer-menu__section-title">커뮤니티 바로가기</p>
    ${searchBtn}
  </div>
  <div class="drawer-menu__communities">
    <p class="text-xs text-muted" style="padding:8px 0;">가입한 커뮤니티가 없습니다.</p>
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

    return `<div class="drawer-menu__section-head">
    <p class="drawer-menu__section-title">커뮤니티 바로가기</p>
    ${searchBtn}
  </div>
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
    <a href="${base}collection.html"><span class="nav-ico">📛</span> 나의 컬렉션</a>
    <a href="#" data-shell-alert="공지사항 (목업)"><span class="nav-ico">📢</span> 공지사항</a>
    <a href="${base}shop.html"><span class="nav-ico">🧺</span> Shop</a>
    <a href="${base}membership.html"><span class="nav-ico">🎫</span> 멤버십</a>
    <a href="${base}settings.html"><span class="nav-ico">⚙</span> 회원정보 및 설정</a>
  </nav>
</aside>

<!-- ========== 2. FAB ========== -->
<div class="fab-stack">
  <button type="button" class="fab fab--secondary" data-shell-alert="캘린더 (목업)" title="캘린더" aria-label="캘린더">📅</button>
  <button type="button" class="fab" id="fabChat" title="채팅 (DM)" aria-label="채팅 열기">✈</button>
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
      <button type="button" class="icon-btn" data-shell-alert="검색" aria-label="검색">🔍</button>
      <button type="button" class="icon-btn" data-shell-alert="더보기" aria-label="더보기">⋮</button>
    </div>

    <!-- 구독 만료 배너 (data-room에 따라 표시) -->
    <div class="dm-expired ${dmExpired ? "" : "hidden"}" id="dmExpiredBanner">
      <div class="dm-expired__icon">💙</div>
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
      <button type="button" class="icon-btn" data-shell-alert="이모지" aria-label="이모지">😊</button>
      <button type="submit" class="send-btn" aria-label="전송">✈</button>
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
      <div class="membership-hero__badge">🏅</div>
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
    <a class="btn btn--accent btn--block btn--lg" href="${base}membership.html">멤버십 가입하기</a>
  </div>
</div>

<!-- ========== 5. 멤버십 상세 모달 (P33) ========== -->
<div class="modal-backdrop" id="membershipDetailModal">
  <div class="modal">
    <div class="modal__head">
      <h2 class="modal__title">멤버십 상세 보기</h2>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <div class="membership-card-detail">
      <div class="membership-card-detail__row"><span>이름</span><strong>홍길동</strong></div>
      <div class="membership-card-detail__row"><span>멤버십 고유 번호</span><strong>WP-RZ-20251127</strong></div>
      <div class="membership-card-detail__row"><span>기간</span><strong>2025.11.27 ~ 2026.11.26 (KST)</strong></div>
    </div>
    <div class="settings-row"><span>성</span><span>홍</span></div>
    <div class="settings-row"><span>이름</span><span>길동</span></div>
    <div class="settings-row"><span>이메일</span><span>hong22@gmail.com</span></div>
    <div class="settings-row"><span>전화번호</span><span>010-2222-2222</span></div>
  </div>
</div>

<!-- ========== 6. 커뮤니티 탐색 패널 (EXPLORE-02) ========== -->
<div class="explore-panel" id="explorePanel" role="dialog" aria-label="커뮤니티 탐색" aria-hidden="true">
  <div class="dm-header">
    <strong class="dm-header__title">커뮤니티 찾기</strong>
    <button type="button" class="icon-btn" data-shell-close="explore" aria-label="닫기">∨</button>
  </div>
  <form class="explore-filters" id="exploreFilterForm">
    <input type="text" name="nickname" placeholder="아티스트명 검색" />
    <div class="explore-filters__row">
      <select name="gender">
        <option value="">그룹 성별 전체</option>
        <option value="BOY">보이그룹</option>
        <option value="GIRL">걸그룹</option>
        <option value="MIXED">혼성</option>
        <option value="SOLO">솔로</option>
      </select>
      <input type="text" name="nationality" placeholder="국적" />
    </div>
    <div class="explore-filters__row">
      <input type="text" name="category" placeholder="직업/카테고리" />
      <input type="number" name="minMembers" placeholder="최소 인원" min="0" />
      <input type="number" name="maxMembers" placeholder="최대 인원" min="0" />
    </div>
    <div class="explore-filters__row">
      <label class="text-xs text-muted">데뷔일 <input type="date" name="debutFrom" /> ~ <input type="date" name="debutTo" /></label>
    </div>
    <button type="submit" class="btn btn--primary btn--sm btn--block">검색</button>
  </form>
  <div class="explore-results" id="exploreResults">
    <p class="text-xs text-muted" style="padding:16px;">검색 조건을 입력하거나, 그냥 검색을 눌러 전체 목록을 확인하세요.</p>
  </div>
</div>

<!-- ========== 7. 커뮤니티 가입 + 프로필 생성 모달 (EXPLORE-03) ========== -->
<div class="modal-backdrop" id="communityJoinModal">
  <div class="modal">
    <div class="modal__head">
      <h2 class="modal__title" id="communityJoinTitle">커뮤니티 가입</h2>
      <button type="button" class="modal__close" data-modal-close>✕</button>
    </div>
    <div id="communityJoinStep">
      <p class="text-sm text-muted" style="padding:8px 0 16px;">가입 처리 중입니다...</p>
    </div>
    <form id="communityProfileForm" class="hidden" style="display:none;">
      <input type="hidden" name="artistId" />
      <div class="form-group">
        <label class="form-label">커뮤니티 닉네임</label>
        <input type="text" class="form-input" name="nickname" maxlength="10" data-count required />
        <div class="char-count"></div>
        <div class="form-error"></div>
      </div>
      <div class="form-group">
        <label class="form-label">소개글 (선택)</label>
        <textarea class="form-textarea" name="bio" maxlength="30" data-count></textarea>
        <div class="char-count"></div>
        <div class="form-error"></div>
      </div>
      <div style="display:flex;gap:8px;">
        <button type="button" class="btn btn--secondary btn--block" data-modal-close>취소</button>
        <button type="submit" class="btn btn--accent btn--block" id="communityProfileSubmitBtn">가입 완료하고 프로필 만들기</button>
      </div>
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

    const openExploreBtn = e.target.closest('[data-shell-open="explore"]');
    if (openExploreBtn) {
      e.preventDefault();
      openExplore();
      return;
    }

    const closeTarget = e.target.closest("[data-shell-close]");
    if (closeTarget) {
      const what = closeTarget.getAttribute("data-shell-close");
      if (what === "menu") closeMenu();
      if (what === "dm") closeDm();
      if (what === "explore") closeExplore();
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

    const exploreJoinBtn = e.target.closest("[data-explore-join]");
    if (exploreJoinBtn) {
      startCommunityJoin(Number(exploreJoinBtn.getAttribute("data-explore-join")), exploreJoinBtn.getAttribute("data-explore-name"));
      return;
    }

    // main.js의 data-modal-close는 페이지 로드 시점에 없던(shell.js가 나중에 주입한) 모달엔 안 먹히므로 여기서 직접 처리
    const modalCloseBtn = e.target.closest("[data-modal-close]");
    if (modalCloseBtn) {
      const modalBackdrop = modalCloseBtn.closest(".modal-backdrop");
      if (modalBackdrop) modalBackdrop.classList.remove("is-open");
      return;
    }
  });

  document.getElementById("fabChat")?.addEventListener("click", openDm);
  document.getElementById("dmBackBtn")?.addEventListener("click", showList);

  backdrop.addEventListener("click", () => {
    closeMenu();
    closeDm();
    closeExplore();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closeMenu();
      closeDm();
      closeExplore();
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

  /* ---------------------------------------------------------
   * 커뮤니티 탐색 (EXPLORE-02/03)
   * --------------------------------------------------------- */
  const explorePanel = document.getElementById("explorePanel");
  const exploreResults = document.getElementById("exploreResults");
  const exploreFilterForm = document.getElementById("exploreFilterForm");
  const communityJoinModal = document.getElementById("communityJoinModal");
  const communityJoinStep = document.getElementById("communityJoinStep");
  const communityProfileForm = document.getElementById("communityProfileForm");

  // 프로필 입력 폼이 "가입 흐름"으로 열렸는지 "편집 흐름"으로 열렸는지 구분하는 상태
  // (제출 성공 후 동작이 서로 다름: 가입 흐름은 홈으로 이동, 편집 흐름은 그 자리에서 위젯만 갱신)
  let profileFormContext = { mode: "join" };

  function openExplore() {
    explorePanel.classList.add("is-open");
    explorePanel.setAttribute("aria-hidden", "false");
  }
  function closeExplore() {
    explorePanel.classList.remove("is-open");
    explorePanel.setAttribute("aria-hidden", "true");
  }

  function renderExploreResults(list) {
    if (!list.length) {
      exploreResults.innerHTML = `<p class="text-xs text-muted" style="padding:16px;">조건에 맞는 커뮤니티가 없습니다.</p>`;
      return;
    }
    exploreResults.innerHTML = list
      .map((a) => {
        const meta = [a.genderLabel, a.nationality, a.category, a.memberCount ? `${a.memberCount}인` : null, a.debutDate]
          .filter(Boolean)
          .join(" · ");
        const actionHtml = a.joined
          ? `<span class="text-xs" style="color:var(--wp-success);font-weight:700;">✔️ 가입중</span>`
          : `<button type="button" class="btn btn--primary btn--sm" data-explore-join="${a.id}" data-explore-name="${escapeHtml(a.nickname)}">가입하기</button>`;
        return `<div class="dm-list-item" style="cursor:default;">
      <span class="avatar avatar--sm">${escapeHtml(a.logo || "?")}</span>
      <div class="dm-list-item__meta">
        <div class="dm-list-item__name">${escapeHtml(a.nickname)}</div>
        <div class="dm-list-item__preview">${escapeHtml(meta || "-")}</div>
      </div>
      ${actionHtml}
    </div>`;
      })
      .join("");
  }

  function runExploreSearch() {
    const params = new URLSearchParams(new FormData(exploreFilterForm));
    [...params.keys()].forEach((k) => {
      if (!params.get(k)) params.delete(k);
    });
    fetch(`${base}community/search?${params.toString()}`)
      .then((res) => res.json())
      .then(renderExploreResults)
      .catch(() => {
        exploreResults.innerHTML = `<p class="text-xs text-muted" style="padding:16px;">검색 중 오류가 발생했습니다.</p>`;
      });
  }

  exploreFilterForm?.addEventListener("submit", (e) => {
    e.preventDefault();
    runExploreSearch();
  });

  // "가입하기" 클릭 - DB에는 아무것도 안 쓰고, 곧바로 닉네임 입력 폼부터 보여줌
  // (실제 가입 + 프로필 저장은 이 폼을 제출하는 순간에만 일어남 -> 취소하면 아무 흔적도 안 남음)
  function startCommunityJoin(artistId, artistName) {
    profileFormContext = { mode: "join" };
    document.getElementById("communityJoinTitle").textContent = `${artistName || "커뮤니티"} 가입`;
    communityJoinStep.classList.add("hidden");
    communityProfileForm.classList.remove("hidden");
    communityProfileForm.style.display = "block";
    communityProfileForm.querySelector('[name="artistId"]').value = artistId;
    communityProfileForm.querySelector('[name="nickname"]').value = "";
    communityProfileForm.querySelector('[name="bio"]').value = "";
    communityJoinModal.classList.add("is-open");

    // 이전에 다른 커뮤니티에서 쓰던 닉네임/소개글이 있으면 조용히 채워줌 (없어도 폼은 이미 떠 있음)
    fetch(`${base}community/my-default-profile`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!data) return;
        const nicknameInput = communityProfileForm.querySelector('[name="nickname"]');
        const bioInput = communityProfileForm.querySelector('[name="bio"]');
        if (data.defaultNickname && !nicknameInput.value) nicknameInput.value = data.defaultNickname;
        if (data.defaultBio && !bioInput.value) bioInput.value = data.defaultBio;
      })
      .catch(() => {});
  }

  // 이미 가입된 커뮤니티에서 "프로필 만들기/편집" 버튼으로 바로 진입하는 경로
  // (가입 처리를 다시 안 하고, 프로필 입력 폼만 곧장 열어줌)
  function openProfileEditor(artistId, artistName, prefill, onSaved) {
    profileFormContext = { mode: "edit", onSaved };
    document.getElementById("communityJoinTitle").textContent = `${artistName || "커뮤니티"} 프로필 편집`;
    communityJoinStep.classList.add("hidden");
    communityProfileForm.classList.remove("hidden");
    communityProfileForm.style.display = "block";
    communityProfileForm.querySelector('[name="artistId"]').value = artistId;
    communityProfileForm.querySelector('[name="nickname"]').value = prefill?.nickname || "";
    communityProfileForm.querySelector('[name="bio"]').value = prefill?.bio || "";
    communityJoinModal.classList.add("is-open");
  }

  communityProfileForm?.addEventListener("submit", (e) => {
    e.preventDefault();
    const artistId = communityProfileForm.querySelector('[name="artistId"]').value;
    const nickname = communityProfileForm.querySelector('[name="nickname"]').value.trim();
    const bio = communityProfileForm.querySelector('[name="bio"]').value.trim();
    const context = profileFormContext || { mode: "join" };
    const submitBtn = document.getElementById("communityProfileSubmitBtn");

    function saveProfile() {
      return fetch(`${base}community/${artistId}/profile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nickname, bio }),
      }).then((res) => res.json().then((data) => ({ ok: res.ok, data })));
    }

    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = "처리 중...";
    }

    // join 모드는 제출하는 이 순간에만 "가입 -> 프로필 저장"을 순서대로 실행 (그 전엔 DB에 아무것도 안 씀)
    const flow = context.mode === "join"
      ? fetch(`${base}community/${artistId}/join`, { method: "POST" }).then((res) => {
          if (res.status === 401) {
            window.location.href = `${base}login`;
            return null;
          }
          return saveProfile();
        })
      : saveProfile();

    flow
      .then((result) => {
        if (!result) return;
        const { ok, data } = result;
        if (!ok) {
          alert(data.message || "입력값을 다시 확인해주세요.");
          return;
        }
        if (context.mode === "edit") {
          communityJoinModal.classList.remove("is-open");
          alert("프로필이 저장되었습니다.");
          if (typeof context.onSaved === "function") {
            fetch(`${base}community/${artistId}/profile`)
              .then((res) => res.json())
              .then(context.onSaved)
              .catch(() => {});
          }
          return;
        }
        alert("커뮤니티 가입이 완료되었습니다!");
        window.location.href = `${base}`;
      })
      .catch(() => alert("저장 중 오류가 발생했습니다."))
      .finally(() => {
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = "가입 완료하고 프로필 만들기";
        }
      });
  });

  /* ---------------------------------------------------------
   * 내 프로필 위젯 (커뮤니티 페이지 사이드바, EXPLORE-03)
   * --------------------------------------------------------- */
  const myProfileWidget = document.getElementById("myProfileWidget");

    function renderMyProfileWidget(data) {
      const widgetBody = document.getElementById("myProfileWidgetBody");
      if (!widgetBody || !myProfileWidget) return;
      const artistId = myProfileWidget.getAttribute("data-artist-id");

      if (!data.joined) {
        widgetBody.innerHTML = `<p class="text-xs text-muted">이 커뮤니티에 가입하면 프로필을 만들 수 있어요.</p>`;
        return;
      }

      // 카드/버튼 없이, 새 페이지로 이동하는 링크 한 줄만 보여줌 (마이페이지에서 조회/수정 다 처리)
      const linkText = data.hasProfile ? "내 프로필 수정 ›" : "내 프로필 만들기 ›";
      widgetBody.innerHTML = `<a href="${base}community/${artistId}/my-profile" class="my-profile-link">${linkText}</a>`;
    }
      if (myProfileWidget) {
        fetch(`${base}community/${myProfileWidget.getAttribute("data-artist-id")}/profile`)
          .then((res) => res.json())
          .then(renderMyProfileWidget)
          .catch(() => {
            document.getElementById("myProfileWidgetBody").innerHTML =
              `<p class="text-xs text-muted">프로필 정보를 불러오지 못했습니다.</p>`;
          });
      }

      // URL ?dm=1 이면 자동 오픈 (chat.html 데모용)
      const params = new URLSearchParams(location.search);
      if (params.get("dm") === "1") openDm();
      if (params.get("dm") === "expired") {
        openDm();
        showRoom("YUMA", true);
      }
      if (params.get("menu") === "1") openMenu();
    })();