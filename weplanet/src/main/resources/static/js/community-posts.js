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

  function resetWriteModal() {
    writeForm.reset();
    if (errorEl) errorEl.style.display = "none";
    if (fileCountEl) fileCountEl.textContent = "";
    if (submitBtn) submitBtn.disabled = true;
    const counter = writeModal.querySelector(".char-count");
    if (counter) counter.textContent = "0 / 1000";
  }

  document.querySelectorAll('[data-modal-open="writePostModal"]').forEach(function (btn) {
    btn.addEventListener("click", resetWriteModal);
  });

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
        const contentType = response.headers.get("content-type") || "";
        if (response.ok) {
          return response.text().then(function (html) {
            const area = document.getElementById("postListArea");
            if (area && html && html.indexOf("postListArea") !== -1) {
              area.outerHTML = html;
            }
            writeModal.classList.remove("is-open");
            resetWriteModal();
            loadList(listBase + "?sort=latest", true);
          });
        }
        if (contentType.indexOf("application/json") !== -1) {
          return response.json().then(function (data) {
            if (errorEl) {
              errorEl.textContent = data.message || "등록에 실패했습니다.";
              errorEl.style.display = "block";
            }
            submitBtn.disabled = false;
          });
        }
        // 서버 오류 HTML 등이면 목록만 다시 불러와 실제 등록 여부 확인
        writeModal.classList.remove("is-open");
        resetWriteModal();
        loadList(listBase + "?sort=latest", true);
      })
      .catch(function () {
        if (errorEl) {
          errorEl.textContent = "등록에 실패했습니다. 목록을 확인해주세요.";
          errorEl.style.display = "block";
        }
        submitBtn.disabled = false;
        loadList(listBase + "?sort=latest", false);
      });
  });
})();
