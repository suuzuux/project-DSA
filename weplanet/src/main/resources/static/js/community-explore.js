/**
 * ============================================================
 * WePlaNet – EXPLORE-02 커뮤니티 검색
 * ------------------------------------------------------------
 * index.html의 #communitySearchModal 안 요소들을 제어함.
 * 검색: GET /community/search (CommunityExploreController)
 * 가입/탈퇴 버튼은 EXPLORE-03에서 다시 추가할 예정.
 * ============================================================
 */
/**
 * ============================================================
 * WePlaNet – EXPLORE-02 커뮤니티 검색
 * ------------------------------------------------------------
 * 성별/솔로·그룹/인원수/국적/카테고리/데뷔일 필터 전부 포함.
 * ============================================================
 */
(function () {
  "use strict";

  const resultsEl = document.getElementById("exploreResults");
  const keywordEl = document.getElementById("exploreKeyword");
  const genderEl = document.getElementById("exploreGender");
  const memberCountEl = document.getElementById("exploreMemberCount");
  const nationalityEl = document.getElementById("exploreNationality");
  const categoryEl = document.getElementById("exploreCategory");
  const debutFromEl = document.getElementById("exploreDebutFrom");
  const debutToEl = document.getElementById("exploreDebutTo");
  const soloOnlyEl = document.getElementById("exploreSoloOnly");
  const searchBtn = document.getElementById("exploreSearchBtn");
  if (!resultsEl) return;

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderCard(a) {
    const soloBadge = a.solo ? `<span class="badge-verified">솔로</span>` : "";
    return `<div class="rising-card" style="cursor:default;">
      <div class="avatar avatar--lg">${escapeHtml(a.logo)}</div>
      <div class="rising-card__info">
        <strong>${escapeHtml(a.nickname)} ${soloBadge}</strong>
        <span>${escapeHtml(a.nationality)} · ${escapeHtml(a.category)}</span>
      </div>
    </div>`;
  }

  function buildParams() {
    const params = new URLSearchParams();
    if (keywordEl?.value) params.set("keyword", keywordEl.value);
    if (genderEl?.value) params.set("gender", genderEl.value);
    if (memberCountEl?.value) params.set("memberCount", memberCountEl.value);
    if (nationalityEl?.value) params.set("nationality", nationalityEl.value);
    if (categoryEl?.value) params.set("category", categoryEl.value);
    if (debutFromEl?.value) params.set("debutFrom", debutFromEl.value);
    if (debutToEl?.value) params.set("debutTo", debutToEl.value);
    if (soloOnlyEl?.checked) params.set("isSolo", "true");
    return params;
  }

  async function runSearch() {
    resultsEl.innerHTML = `<p class="text-muted">검색 중...</p>`;
    try {
      const res = await fetch("/community/search?" + buildParams().toString(), {
        headers: { "X-Requested-With": "fetch" },
      });
      const rows = await res.json();
      resultsEl.innerHTML = rows.length
        ? rows.map(renderCard).join("")
        : `<p class="text-muted">검색 결과가 없습니다.</p>`;
    } catch (err) {
      resultsEl.innerHTML = `<p class="text-muted">검색 중 오류가 발생했습니다.</p>`;
    }
  }

  let debounceTimer = null;
  keywordEl?.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(runSearch, 250);
  });

  [genderEl, memberCountEl, nationalityEl, categoryEl, debutFromEl, debutToEl, soloOnlyEl].forEach((el) => {
    el?.addEventListener("change", runSearch);
  });

  searchBtn?.addEventListener("click", runSearch);

  document.querySelectorAll('[data-modal-open="communitySearchModal"]').forEach((btn) => {
    btn.addEventListener("click", runSearch);
  });
})();