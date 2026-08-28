/**
 * ============================================================
 * WePlaNet – EXPLORE-03 커뮤니티 가입(닉네임 설정) 모달
 * ------------------------------------------------------------
 * 검색 결과의 "가입" 버튼, 커뮤니티 페이지의 "커뮤니티 가입하기" 버튼이
 * 모두 이 파일 하나를 공유한다. [data-join-btn] 이 붙은 요소면 어디서 눌러도 열린다.
 * (검색 결과처럼 나중에 그려지는 요소도 잡히도록 document 레벨에서 위임 처리)
 * 모달 마크업은 community/fragments/layout.html 의 joinModal 조각 하나뿐이다.
 * ============================================================
 */
(function () {
  "use strict";

  const modal = document.getElementById("communityJoinModal");
  if (!modal) return;

  const artistNameEl = document.getElementById("communityJoinArtistName");
  const nicknameInput = document.getElementById("communityJoinNicknameInput");
  const nicknameGroup = document.getElementById("communityJoinNicknameGroup");
  const nicknameError = document.getElementById("communityJoinNicknameError");
  const submitBtn = document.getElementById("communityJoinSubmitBtn");
  let selectedArtistId = null;

  function clearError() {
    nicknameGroup?.classList.remove("is-invalid");
    if (nicknameError) nicknameError.textContent = "";
  }

  function showError(message) {
    nicknameGroup?.classList.add("is-invalid");
    if (nicknameError) nicknameError.textContent = message;
    nicknameInput?.focus();
  }

  function open(artistId, artistName) {
    if (document.body.dataset.authenticated !== "true") {
      window.location.href = "/login";
      return;
    }
    selectedArtistId = artistId;
    if (artistNameEl) artistNameEl.textContent = `『${artistName}』`;
    if (nicknameInput) nicknameInput.value = "";
    clearError();
    // 검색 모달에서 넘어온 경우 뒤에 겹쳐 보이지 않게 닫아줌 (커뮤니티 페이지엔 없으므로 무시됨)
    document.getElementById("communitySearchModal")?.classList.remove("is-open");
    modal.classList.add("is-open");
    nicknameInput?.focus();
  }

  // 다른 스크립트(community-explore.js 등)에서도 열 수 있도록 공개
  window.WePlaNetJoin = { open };

  document.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-join-btn]");
    if (!btn) return;
    e.preventDefault();
    open(btn.dataset.artistId, btn.dataset.artistName);
  });

  // "가입하기" → 닉네임만 담아 실제 /community/{artistId}/join 호출
  submitBtn?.addEventListener("click", async () => {
    const nickname = (nicknameInput?.value || "").trim();
    clearError();

    if (!nickname) {
      showError("닉네임을 입력해주세요.");
      return;
    }
    if (nickname.length > 10) {
      showError("닉네임은 10자 이내로 입력해주세요.");
      return;
    }

    submitBtn.disabled = true;
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
        modal.classList.remove("is-open");
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
      showError(message);
    } catch (err) {
      showError("가입 중 오류가 발생했습니다.");
    } finally {
      submitBtn.disabled = false;
    }
  });
})();