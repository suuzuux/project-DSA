/** 커뮤니티 검색 모달 (가입 버튼은 community-join.js, ?openSearch=1 이면 자동 열림). */
(function () {
  "use strict";

  const resultsEl = document.getElementById("exploreResults");
  const keywordEl = document.getElementById("exploreKeyword");
  const genderEl = document.getElementById("exploreGender");
  const categoryEl = document.getElementById("exploreCategory");
  const searchBtn = document.getElementById("exploreSearchBtn");
  if (!resultsEl) return;

  // 가입한 커뮤니티 id 집합 (문자열)
  const joinedArtistIds = new Set(
    (window.__WEPLANET_ARTISTS__ || []).map((a) => String(a.id))
  );

  // 문구는 WePlaNet.t 에서 꺼낸다 (없으면 한국어 기본값).
  const t = (key, fallback, args) =>
    (window.WePlaNet && window.WePlaNet.t) ? window.WePlaNet.t(key, fallback, args) : fallback;

  // DB 에 한국어로 저장된 카테고리는 표시할 때만 번역한다.
  const CATEGORY_KEYS = { "아이돌": "main.search.categoryIdol", "배우": "main.search.categoryActor" };
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

    // 가입했거나 본인 커뮤니티면 가입 버튼을 달지 않는다.
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
      // i18n 응답을 기다린 뒤 검색 결과를 그린다.
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

  // 검색 버튼을 눌렀을 때만 검색
  searchBtn?.addEventListener("click", runSearch);

  // Enter 로도 검색
  keywordEl?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      runSearch();
    }
  });

  // 모달을 열 때 전체 목록을 보여준다.
  document.querySelectorAll('[data-modal-open="communitySearchModal"]').forEach((btn) => {
    btn.addEventListener("click", runSearch);
  });

  // ?openSearch=1 이면 검색 모달을 자동으로 연다.
  const urlParams = new URLSearchParams(location.search);
  if (urlParams.get("openSearch") === "1") {
    document.getElementById("communitySearchModal")?.classList.add("is-open");
    runSearch();
  }
})();