/**
 * 커뮤니티 Fan / Artist 게시판 – 정렬(fetch) 및 포스트 작성 모달
 */
(function () {
  "use strict";

  const boardRoot = document.getElementById("communityPostBoard");
  if (!boardRoot) return;

  const artistId = boardRoot.dataset.artistId;
  const boardTab = boardRoot.dataset.boardTab;
  const listBase = "/community/" + artistId + "/" + boardTab;

  function loadList(url, pushHistory) {
    fetch(url, { headers: { "X-Requested-With": "fetch" } })
      .then(function (response) {
        return response.text();
      })
      .then(function (html) {
        const area = document.getElementById("postListArea");
        if (!area) return;
        area.outerHTML = html;

        const sortValue = new URL(url, window.location.origin).searchParams.get("sort") || "latest";
        document.querySelectorAll(".sort-link").forEach(function (link) {
          const isActive = new URL(link.href, window.location.origin).searchParams.get("sort") === sortValue;
          link.classList.toggle("is-active", isActive);
        });

        if (pushHistory) {
          history.pushState({}, "", url);
        }
      });
  }

  document.querySelectorAll(".sort-link").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      loadList(this.getAttribute("href"), true);
    });
  });

  window.addEventListener("popstate", function () {
    loadList(window.location.href, false);
  });

  const writeModal = document.getElementById("writePostModal");
  const writeForm = document.getElementById("writePostForm");
  if (!writeModal || !writeForm) return;

  const contentEl = document.getElementById("writePostContent");
  const titleEl = document.getElementById("writePostTitle");
  const submitBtn = document.getElementById("writePostSubmit");
  const errorEl = document.getElementById("writePostError");
  const filesEl = document.getElementById("writePostFiles");
  const fileCountEl = document.getElementById("writePostFileCount");
  const linkRow = document.getElementById("writePostLinkRow");
  const linkToggleBtn = document.getElementById("writePostLinkToggleBtn");
  const linkUrlEl = document.getElementById("writePostLinkUrl");
  const hideToggleBtn = document.getElementById("writePostHideToggle");
  const hiddenFromArtistEl = document.getElementById("writePostHiddenFromArtist");

  function resetWriteModal() {
    writeForm.reset();
    if (errorEl) errorEl.style.display = "none";
    if (fileCountEl) fileCountEl.textContent = "";
    if (submitBtn) submitBtn.disabled = true;
    const counter = writeModal.querySelector(".char-count");
    if (counter) counter.textContent = "0 / 1000";
    if (linkRow) linkRow.style.display = "none";
    if (linkToggleBtn) linkToggleBtn.setAttribute("aria-pressed", "false");
    if (linkUrlEl) linkUrlEl.value = "";
    if (hideToggleBtn) {
      hideToggleBtn.classList.remove("is-on");
      hideToggleBtn.setAttribute("aria-checked", "false");
    }
    if (hiddenFromArtistEl) hiddenFromArtistEl.value = "false";
  }

  document.querySelectorAll('[data-modal-open="writePostModal"]').forEach(function (btn) {
    btn.addEventListener("click", resetWriteModal);
  });

  // 🔗 링크 아이콘 - 누르면 링크 입력창이 나타나고, 다시 누르면 숨기면서 값도 비움
  if (linkToggleBtn && linkRow) {
    linkToggleBtn.addEventListener("click", function () {
      const willShow = linkRow.style.display === "none";
      linkRow.style.display = willShow ? "block" : "none";
      linkToggleBtn.setAttribute("aria-pressed", willShow ? "true" : "false");
      if (!willShow && linkUrlEl) linkUrlEl.value = "";
      if (willShow && linkUrlEl) linkUrlEl.focus();
    });
  }

  // "Hide from Artists" 토글 - class/aria/hidden input 값을 모두 이 핸들러 하나에서 직접 관리함.
  // (참고: 이 프로젝트의 전역 .toggle 자동 바인딩(main.js의 initToggles)에 기대지 않고 독립적으로 동작하도록 작성함 -
  //  전역 바인딩 타이밍에 의존하면 페이지에 따라 초기화가 안 된 상태로 남는 경우가 있었음)
  if (hideToggleBtn && hiddenFromArtistEl) {
    hideToggleBtn.addEventListener("click", function () {
      const nowOn = hiddenFromArtistEl.value !== "true";
      hiddenFromArtistEl.value = nowOn ? "true" : "false";
      hideToggleBtn.classList.toggle("is-on", nowOn);
      hideToggleBtn.setAttribute("aria-checked", nowOn ? "true" : "false");
    });
  }

  if (contentEl && submitBtn) {
    contentEl.addEventListener("input", function () {
      const text = contentEl.value;
      submitBtn.disabled = text.trim().length === 0 || text.length > 1000;
    });
  }

  if (filesEl) {
    filesEl.addEventListener("change", function () {
      if (filesEl.files.length > 10) {
        if (errorEl) {
          errorEl.textContent = "첨부파일은 최대 10개까지 등록할 수 있습니다.";
          errorEl.style.display = "block";
        }
        filesEl.value = "";
        if (fileCountEl) fileCountEl.textContent = "";
      } else {
        if (errorEl) errorEl.style.display = "none";
        if (fileCountEl) {
          fileCountEl.textContent = filesEl.files.length > 0 ? filesEl.files.length + "개 선택됨" : "";
        }
      }
    });
  }

  writeForm.addEventListener("submit", function (e) {
    e.preventDefault();

    const content = contentEl.value.trim();
    titleEl.value = content.length > 30 ? content.slice(0, 30) + "…" : content;
    submitBtn.disabled = true;

    fetch(writeForm.action, {
      method: "POST",
      headers: { "X-Requested-With": "fetch" },
      body: new FormData(writeForm),
    })
      .then(function (response) {
        if (response.ok) {
          return response.text().then(function (html) {
            const area = document.getElementById("postListArea");
            if (area) area.outerHTML = html;
            writeModal.classList.remove("is-open");
            resetWriteModal();
            loadList(listBase + "?sort=latest", true);
          });
        }
        return response.json().then(function (data) {
          if (errorEl) {
            errorEl.textContent = data.message || "등록에 실패했습니다.";
            errorEl.style.display = "block";
          }
          submitBtn.disabled = false;
        });
      })
      .catch(function () {
        if (errorEl) {
          errorEl.textContent = "등록에 실패했습니다.";
          errorEl.style.display = "block";
        }
        submitBtn.disabled = false;
      });
  });
})();
