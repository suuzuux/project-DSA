/**
 * ============================================================
 * WePlaNet – EXPLORE-02 커뮤니티 검색 / EXPLORE-03 가입(닉네임 설정) UI
 * ------------------------------------------------------------
 * 필터는 검색어 / 성별 / 직업/카테고리(아이돌·배우) 세 가지만 제공.
 * 검색은 "검색" 버튼을 눌렀을 때(또는 키워드칸에서 Enter)만 실행됨.
 * 검색 결과 각 줄: 이름/아바타 영역을 누르면 해당 커뮤니티 페이지로 이동,
 * 오른쪽 "가입" 버튼을 누르면 확인 단계 없이 바로 닉네임 입력 모달이 뜸.
 * "가입하기"를 누르면 실제 /community/{artistId}/join 을 nickname만 담아 호출한다.
 * 드로어 메뉴의 "커뮤니티 찾아보기"(?openSearch=1)로 들어오면 검색 모달이 자동으로 열림.
 * 아바타/소개글 편집은 이번 범위에 포함하지 않음 (PROFILE-01에서 별도 진행 예정).
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

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderCard(a) {
    const soloBadge = a.solo ? `<span class="badge-solo">솔로</span>` : "";
    return `<div class="rising-card" style="justify-content:space-between;">
      <a href="/community/${a.artistId}" class="flex-center" style="gap:12px;flex:1;min-width:0;">
        <div class="avatar avatar--lg">${escapeHtml(a.logo)}</div>
        <div class="rising-card__info">
          <strong>${escapeHtml(a.nickname)} ${soloBadge}</strong>
          <span>${escapeHtml(a.nationality)} · ${escapeHtml(a.category)}</span>
        </div>
      </a>
      <button type="button" class="btn btn--primary btn--sm" data-join-btn
              data-artist-id="${a.artistId}" data-artist-name="${escapeHtml(a.nickname)}">가입</button>
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

  /* ---------------------------------------------------------
   * EXPLORE-03: 검색 결과의 "가입" 버튼 → 확인 단계 없이 바로 닉네임 입력 → 실제 가입
   * --------------------------------------------------------- */
  const joinModal = document.getElementById("communityJoinModal");
  const joinArtistName = document.getElementById("communityJoinArtistName");
  const joinNicknameInput = document.getElementById("communityJoinNicknameInput");
  const joinNicknameGroup = document.getElementById("communityJoinNicknameGroup");
  const joinNicknameError = document.getElementById("communityJoinNicknameError");
  const joinSubmitBtn = document.getElementById("communityJoinSubmitBtn");
  let selectedArtistId = null;

  function resetJoinNicknameError() {
    joinNicknameGroup?.classList.remove("is-invalid");
    if (joinNicknameError) joinNicknameError.textContent = "";
  }

  function openJoinModal(artistId, artistName) {
    if (document.body.dataset.authenticated !== "true") {
      window.location.href = "/login";
      return;
    }
    selectedArtistId = artistId;
    if (joinArtistName) joinArtistName.textContent = `『${artistName}』`;
    if (joinNicknameInput) joinNicknameInput.value = "";
    resetJoinNicknameError();
    document.getElementById("communitySearchModal")?.classList.remove("is-open");
    joinModal?.classList.add("is-open");
    joinNicknameInput?.focus();
  }

  // 검색 결과 줄의 "가입" 버튼 클릭 → 확인 단계 없이 바로 닉네임 입력 모달
  resultsEl.addEventListener("click", (e) => {
    const joinBtn = e.target.closest("[data-join-btn]");
    if (!joinBtn) return;
    e.preventDefault();
    openJoinModal(joinBtn.dataset.artistId, joinBtn.dataset.artistName);
  });

  // "가입하기" → 닉네임만 담아 실제 /community/{artistId}/join 호출
  joinSubmitBtn?.addEventListener("click", async () => {
    const nickname = (joinNicknameInput?.value || "").trim();
    resetJoinNicknameError();

    if (!nickname) {
      joinNicknameGroup?.classList.add("is-invalid");
      if (joinNicknameError) joinNicknameError.textContent = "닉네임을 입력해주세요.";
      joinNicknameInput?.focus();
      return;
    }
    if (nickname.length > 10) {
      joinNicknameGroup?.classList.add("is-invalid");
      if (joinNicknameError) joinNicknameError.textContent = "닉네임은 10자 이내로 입력해주세요.";
      joinNicknameInput?.focus();
      return;
    }

    joinSubmitBtn.disabled = true;
    try {
      const formData = new FormData();
      formData.set("nickname", nickname);

      const res = await fetch(`/community/${selectedArtistId}/join`, {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
        body: formData,
      });

      if (res.status === 401) {
        window.location.href = "/login";
        return;
      }

      if (res.ok) {
        joinModal?.classList.remove("is-open");
        window.location.reload();
        return;
      }

      let message = "가입 중 오류가 발생했습니다.";
      try {
        const data = await res.json();
        if (data?.message) message = data.message;
      } catch (_) {
        // JSON 파싱 실패 시 기본 메시지 사용
      }
      joinNicknameGroup?.classList.add("is-invalid");
      if (joinNicknameError) joinNicknameError.textContent = message;
    } catch (err) {
      joinNicknameGroup?.classList.add("is-invalid");
      if (joinNicknameError) joinNicknameError.textContent = "가입 중 오류가 발생했습니다.";
    } finally {
      joinSubmitBtn.disabled = false;
    }
  });
})();