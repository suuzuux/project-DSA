/**
 * ============================================================
 * WePlaNet – EXPLORE-02 커뮤니티 검색 / EXPLORE-03 가입 확인 UI
 * ------------------------------------------------------------
 * 성별/솔로·그룹/인원수/국적/카테고리/데뷔일 필터 전부 포함.
 * 검색은 "검색" 버튼을 눌렀을 때(또는 키워드칸에서 Enter)만 실행됨.
 * 검색 결과 카드를 클릭하면 "가입하시겠습니까?" 예/아니오 모달이 열림.
 * "예"를 누르면 실제 가입 처리는 하지 않고 communityJoinConfirmed
 * 커스텀 이벤트만 쏨 - 실제 가입 API 연결은 화평님 파트에서 이 이벤트를
 * 받아서 처리하면 됨. 예)
 *   document.addEventListener("communityJoinConfirmed", (e) => {
 *     const artistId = e.detail.artistId;
 *     // fetch(`/community/${artistId}/join`, { method: "POST", ... })
 *   });
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
    const soloBadge = a.solo ? `<span class="badge-solo">솔로</span>` : "";
    return `<div class="rising-card" style="cursor:pointer;" data-artist-id="${a.artistId}" data-artist-name="${escapeHtml(a.nickname)}">
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

  /* ---------------------------------------------------------
   * EXPLORE-03: 검색 결과 카드 클릭 → 가입 확인(예/아니오)
   * --------------------------------------------------------- */
  const joinModal = document.getElementById("communityJoinModal");
  const joinConfirmText = document.getElementById("communityJoinConfirmText");
  const joinYesBtn = document.getElementById("communityJoinYesBtn");
  let selectedArtistId = null;

  // 검색 결과 카드 클릭 → 가입 확인 모달 오픈
  resultsEl.addEventListener("click", (e) => {
    const card = e.target.closest(".rising-card[data-artist-id]");
    if (!card || !joinModal) return;

    if (document.body.dataset.authenticated !== "true") {
      window.location.href = "/login";
      return;
    }

    selectedArtistId = card.dataset.artistId;
    joinConfirmText.textContent = `『${card.dataset.artistName}』 커뮤니티에 가입하시겠습니까?`;
    document.getElementById("communitySearchModal")?.classList.remove("is-open");
    joinModal.classList.add("is-open");
  });

  // "예" → 모달만 닫고, 실제 가입 처리는 이 이벤트를 받는 쪽(화평님 파트)에서 진행
  joinYesBtn?.addEventListener("click", () => {
    joinModal.classList.remove("is-open");
    document.dispatchEvent(new CustomEvent("communityJoinConfirmed", {
      detail: { artistId: selectedArtistId },
    }));
  });
})();