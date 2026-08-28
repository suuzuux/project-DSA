/**
 * ============================================================
 * WePlaNet – EXPLORE-02/05 커뮤니티 검색 및 목록 조회
 * ------------------------------------------------------------
 * 필터는 검색어 / 성별 / 직업·카테고리(아이돌·배우) 세 가지만 제공.
 * 검색은 "검색" 버튼을 눌렀을 때(또는 키워드칸에서 Enter)만 실행됨.
 * 검색 결과 각 줄: 이름/아바타 영역을 누르면 해당 커뮤니티 페이지로 이동,
 * 오른쪽 "가입" 버튼(data-join-btn)은 community-join.js 가 받아서 닉네임 모달을 띄운다.
 * 이미 가입한 커뮤니티는 버튼 대신 비활성 "✓ 가입중"을 그린다.
 *   - 가입 여부는 드로어 "커뮤니티 바로가기"용으로 이미 내려와 있는
 *     window.__WEPLANET_ARTISTS__(=joinedArtists) 를 재사용한다. 검색 API는 손대지 않음.
 *   - 가입에 성공하면 페이지를 새로고침하므로 이 목록도 항상 최신 상태다.
 * 드로어 메뉴의 "커뮤니티 찾아보기"(?openSearch=1)로 들어오면 검색 모달이 자동으로 열림.
 * ============================================================
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

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderCard(a) {
    const soloBadge = a.solo ? `<span class="badge-solo">솔로</span>` : "";
    const joined = joinedArtistIds.has(String(a.artistId));

    // 가입한 커뮤니티는 data-join-btn 을 붙이지 않는다 -> 닉네임 모달이 열리지 않음
    const actionHtml = joined
      ? `<button type="button" class="btn btn--ghost btn--sm" disabled
                 style="opacity:.7;cursor:default;">✓ 가입중</button>`
      : `<button type="button" class="btn btn--primary btn--sm" data-join-btn
                 data-artist-id="${a.artistId}" data-artist-name="${escapeHtml(a.nickname)}">가입</button>`;

    return `<div class="rising-card" style="justify-content:space-between;">
      <a href="/community/${a.artistId}" class="flex-center" style="gap:12px;flex:1;min-width:0;">
        <div class="avatar avatar--lg">${escapeHtml(a.logo)}</div>
        <div class="rising-card__info">
          <strong>${escapeHtml(a.nickname)} ${soloBadge}</strong>
          <span>${escapeHtml(a.nationality)} · ${escapeHtml(a.category)}</span>
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