/**
 * 커뮤니티 가입(닉네임 설정) 모달 - [data-join-btn] 이 붙은 버튼이면 어디서 눌러도 열린다
 * (검색 결과처럼 나중에 그려지는 버튼도 잡히도록 document 에서 위임 처리).
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

  // main.js 보다 먼저 로드되는 화면도 있어서, 문구를 꺼내는 시점(클릭 등)에 WePlaNet.t 를 찾는다. 없으면 한국어 기본값.
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

  // 닉네임 입력칸에서 Enter 를 눌러도 "가입하기"와 똑같이 동작한다 (모달에 form 이 없어서 직접 연결).
  // 전송 중에는 버튼이 disabled 라 Enter 를 연타해도 중복 요청이 나가지 않는다.
  nicknameInput?.addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
      e.preventDefault();
      if (!submitBtn || !submitBtn.disabled) submitJoin();
    }
  });
})();