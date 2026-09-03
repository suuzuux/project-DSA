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

  // Toast UI Editor - writePostContent(hidden textarea)가 실제 폼 전송값, 화면엔 이 에디터가 보임.
  // (예전엔 writePostContent가 그냥 평범한 textarea라 마크다운 리치 편집이 안 됐음)
  const editorEl = document.getElementById("writePostEditor");
  let postEditor = null;
  if (editorEl && window.toastui) {
    postEditor = new toastui.Editor({
      el: editorEl,
      height: "260px",
      initialEditType: "wysiwyg",
      previewStyle: "vertical",
      placeholder: "포스트를 남겨보세요 …",
      // 이미지 버튼 제외: 기본 동작이 base64로 통째로 마크다운에 박아넣어서 1000자 제한을 훌쩍 넘겨버림.
      // 진짜 이미지 첨부는 아래 별도 파일 첨부 버튼(writePostFiles, 실제 업로드) 쓰면 됨
      toolbarItems: [
        ["heading", "bold", "italic", "strike"],
        ["hr", "quote"],
        ["ul", "ol", "task", "indent", "outdent"],
        ["table", "link"],
        ["code", "codeblock"],
      ],
    });
    postEditor.on("change", function () {
      const text = postEditor.getMarkdown();
      contentEl.value = text;
      refreshSubmitState();
      const counter = writeModal.querySelector(".char-count");
      if (counter) counter.textContent = text.length + " / 1000";
    });

    // 하단 모드 전환 탭 라벨을 알아보기 쉬운 한글로 교체.
    // Toast UI 기본값은 "Markdown" / "WYSIWYG"이라 처음 보는 사람은 뭔지 알기 어려움.
    // (WYSIWYG = 툴바 버튼으로 꾸미는 모드, Markdown = #, ** 같은 기호를 직접 쓰는 모드)
    relabelEditorModeTabs(editorEl);
  }

  function relabelEditorModeTabs(root) {
    const labels = {
      Markdown: { text: "마크다운", title: "# 제목, **굵게** 같은 기호를 직접 입력하는 모드" },
      WYSIWYG: { text: "간편 편집", title: "위 툴바 버튼으로 서식을 지정하는 모드 (기호를 직접 쓰지 않아도 됨)" },
    };

    // 에디터가 그려진 직후에 탭이 붙기 때문에 다음 프레임에 한 번 더 시도함
    function apply() {
      const tabs = root.querySelectorAll(".toastui-editor-mode-switch .tab-item");
      if (!tabs.length) return false;
      tabs.forEach(function (tab) {
        const key = tab.textContent.trim();
        const label = labels[key];
        if (label) {
          tab.textContent = label.text;
          tab.title = label.title;
        }
      });
      return true;
    }

    if (!apply()) {
      requestAnimationFrame(apply);
    }
  }

  // 제목 + 본문 둘 다 있어야 등록 가능
  function refreshSubmitState() {
    if (!submitBtn) return;
    const titleOk = !titleEl || titleEl.value.trim().length > 0; // 제목칸을 없앴으므로 요소가 없으면 통과
    const text = contentEl.value;
    const contentOk = text.trim().length > 0 && text.length <= 1000;
    submitBtn.disabled = !(titleOk && contentOk);
  }
  if (titleEl) {
    titleEl.addEventListener("input", refreshSubmitState);
  }

  function resetWriteModal() {
    writeForm.reset();
    if (postEditor) postEditor.setMarkdown("");
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
