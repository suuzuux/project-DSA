/**
 * 커뮤니티 검색 모달 - 검색어/성별/카테고리로 찾고, 결과의 "가입" 버튼은 community-join.js 가 받는다.
 * 이미 가입한 커뮤니티는 "✓ 가입중", 본인 커뮤니티는 "내 커뮤니티"로 표시한다 (?openSearch=1 이면 자동으로 열림).
 */
(function () {
  "use strict";

  const resultsEl = document.getElementById("exploreResults");
  const keywordEl = document.getElementById("exploreKeyword");
  const genderEl = document.getElementById("exploreGender");
  const categoryEl = document.getElementById("exploreCategory");
  const searchBtn = document.getElementById("exploreSearchBtn");
  if (!resultsEl) return;

  // 내가 가입한 커뮤니티 id 집합 (숫자/문자 섞임 방지를 위해 문자열로 통일)
  const joinedArtistIds = new Set(
    (window.__WEPLANET_ARTISTS__ || []).map((a) => String(a.id))
  );

  // 문구는 main.js 의 WePlaNet.t(/api/i18n/client)에서 꺼낸다. 없으면 한국어 기본값.
  const t = (key, fallback, args) =>
    (window.WePlaNet && window.WePlaNet.t) ? window.WePlaNet.t(key, fallback, args) : fallback;

  // 카테고리는 DB에 한국어 값(아이돌/솔로가수/배우)으로 저장돼 있어서(검색 필터 값도 같은 문자열) 표시할 때만 번역한다.
  // 선택지에 있는 값 외의 직접 입력한 카테고리는 저장된 그대로 보여준다.
  const CATEGORY_KEYS = { "아이돌": "main.search.categoryIdol", "솔로가수": "main.search.categorySolo", "배우": "main.search.categoryActor" };
  const categoryLabel = (category) =>
    CATEGORY_KEYS[category] ? t(CATEGORY_KEYS[category], category) : (category || "");

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderCard(a) {
    const soloBadge = a.solo ? `<span class="badge-solo">${escapeHtml(t("client.explore.solo", "솔로"))}</span>` : "";
    const joined = joinedArtistIds.has(String(a.artistId));

    // 가입한 커뮤니티와 본인 커뮤니티(a.own - 아티스트/그룹 멤버)에는 data-join-btn 을 붙이지 않는다 (가입 모달이 안 열림)
    const actionHtml = a.own
      ? `<button type="button" class="btn btn--ghost btn--sm" disabled
                 style="opacity:.7;cursor:default;">${escapeHtml(t("client.explore.mine", "내 커뮤니티"))}</button>`
      : joined
      ? `<button type="button" class="btn btn--ghost btn--sm" disabled
                 style="opacity:.7;cursor:default;">${escapeHtml(t("client.explore.joined", "✓ 가입중"))}</button>`
      : `<button type="button" class="btn btn--primary btn--sm" data-join-btn
                 data-artist-id="${a.artistId}" data-artist-name="${escapeHtml(a.nickname)}">${escapeHtml(t("client.explore.join", "가입"))}</button>`;

    return `<div class="rising-card" style="justify-content:space-between;">
      <a href="/community/${a.artistId}" class="flex-center" style="gap:12px;flex:1;min-width:0;">
        <div class="avatar avatar--lg">${escapeHtml(a.logo)}</div>
        <div class="rising-card__info">
          <strong>${escapeHtml(a.nickname)} ${soloBadge}</strong>
          <span>${escapeHtml(a.nationality)} · ${escapeHtml(categoryLabel(a.category))}</span>
        </div>
      </a>
      ${actionHtml}
    </div>`;
  }

  function buildParams() {
    const params = new URLSearchParams();
    if (keywordEl?.value) params.set("keyword", keywordEl.value);
    if (genderEl?.value) params.set("gender", genderEl.value);
    if (categoryEl?.value) params.set("category", categoryEl.value);
    return params;
  }

  async function runSearch() {
    resultsEl.innerHTML = `<p class="text-muted">${escapeHtml(t("client.explore.searching", "검색 중..."))}</p>`;
    try {
      // ?openSearch=1 로 페이지 로드 직후 바로 검색할 때도 번역 문구로 그리도록 i18n 응답을 기다린다
      if (window.WePlaNet && window.WePlaNet.i18nReady) await window.WePlaNet.i18nReady;
      const res = await fetch("/community/search?" + buildParams().toString(), {
        headers: { "X-Requested-With": "fetch" },
      });
      const rows = await res.json();
      resultsEl.innerHTML = rows.length
        ? rows.map(renderCard).join("")
        : `<p class="text-muted">${escapeHtml(t("client.explore.empty", "검색 결과가 없습니다."))}</p>`;
    } catch (err) {
      resultsEl.innerHTML = `<p class="text-muted">${escapeHtml(t("client.explore.error", "검색 중 오류가 발생했습니다."))}</p>`;
    }
  }

  // "검색" 버튼을 눌렀을 때만 검색 실행 (필터 변경/입력 중에는 실행 안 함)
  searchBtn?.addEventListener("click", runSearch);

  // 키워드 입력창에서 Enter로도 검색 버튼과 동일하게 동작하도록
  keywordEl?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      runSearch();
    }
  });

  // 검색 모달을 여는 순간에는 필터 없는 전체 목록을 한 번 보여줌
  document.querySelectorAll('[data-modal-open="communitySearchModal"]').forEach((btn) => {
    btn.addEventListener("click", runSearch);
  });

  // 드로어 메뉴 "커뮤니티 찾아보기"(?openSearch=1)로 들어온 경우 - 검색 모달을 자동으로 열고 전체 목록을 바로 보여줌
  const urlParams = new URLSearchParams(location.search);
  if (urlParams.get("openSearch") === "1") {
    document.getElementById("communitySearchModal")?.classList.add("is-open");
    runSearch();
  }
})();