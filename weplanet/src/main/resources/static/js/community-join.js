/** 커뮤니티 가입 모달 - [data-join-btn] 이면 어디서든 열린다 (이벤트 위임). */
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

  // 문구는 사용하는 시점에 WePlaNet.t 에서 찾는다 (없으면 한국어 기본값).
  const t = (key, fallback, args) =>
    (window.WePlaNet && window.WePlaNet.t) ? window.WePlaNet.t(key, fallback, args) : fallback;

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
    // 검색 모달이 있으면 닫는다.
    document.getElementById("communitySearchModal")?.classList.remove("is-open");
    modal.classList.add("is-open");
    nicknameInput?.focus();
  }

  // 다른 스크립트에서 열 수 있게 공개
  window.WePlaNetJoin = { open };

  document.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-join-btn]");
    if (!btn) return;
    e.preventDefault();
    open(btn.dataset.artistId, btn.dataset.artistName);
  });

  // 닉네임을 담아 가입 요청
  async function submitJoin() {
    const nickname = (nicknameInput?.value || "").trim();
    clearError();

    if (!nickname) {
      showError(t("error.community.nicknameRequired", "닉네임을 입력해주세요."));
      return;
    }
    if (nickname.length > 10) {
      showError(t("error.community.nicknameTooLong", "닉네임은 10자 이내로 입력해주세요."));
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

      let message = t("client.join.failed", "가입 중 오류가 발생했습니다.");
      try {
        const data = await res.json();
        if (data?.message) message = data.message;
      } catch (_) {
        // JSON 파싱 실패 시 기본 메시지 사용
      }
      showError(message);
    } catch (err) {
      showError(t("client.join.failed", "가입 중 오류가 발생했습니다."));
    } finally {
      submitBtn.disabled = false;
    }
  }

  submitBtn?.addEventListener("click", submitJoin);

  // Enter 로도 가입 (전송 중엔 버튼이 비활성이라 중복 요청 없음).
  nicknameInput?.addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
      e.preventDefault();
      if (!submitBtn || !submitBtn.disabled) submitJoin();
    }
  });
})();