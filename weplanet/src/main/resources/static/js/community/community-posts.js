/** 커뮤니티 게시판 - 정렬(fetch)과 글쓰기 모달 */
(function () {
  "use strict";

  const boardRoot = document.getElementById("communityPostBoard");
  if (!boardRoot) return;

  const artistId = boardRoot.dataset.artistId;
  const boardTab = boardRoot.dataset.boardTab;
  // 목록 주소는 영문 주소를 우선 쓴다.
  const listBase = boardRoot.dataset.listBase || ("/community/" + artistId + "/" + boardTab);

  // 문구는 WePlaNet.t 에서 꺼낸다 (없으면 한국어 기본값).
  const t = function (key, fallback, args) {
    return (window.WePlaNet && window.WePlaNet.t) ? window.WePlaNet.t(key, fallback, args) : fallback;
  };

  function loadList(url, pushHistory) {
    fetch(url, { headers: { "X-Requested-With": "fetch" } })
      .then(function (response) {
        if (!response.ok) throw new Error(t("community.profile.listLoadFailed", "목록을 불러오지 못했습니다."));
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
        updateScrollTopVisibility();
      });
  }

  // 다음 10개를 받아 목록 뒤에 붙인다 (이벤트 위임이라 재바인딩 불필요).
  document.addEventListener("click", function (e) {
    const moreButton = e.target.closest("[data-view-more]");
    if (!moreButton) return;

    e.preventDefault();
    const url = moreButton.dataset.moreUrl;
    if (!url || moreButton.disabled) return;

    moreButton.disabled = true;
    moreButton.textContent = t("community.common.loading", "불러오는 중…");
    fetch(url, { headers: { "X-Requested-With": "fetch" } })
      .then(function (response) {
        if (!response.ok) throw new Error(t("client.posts.moreFailed", "다음 게시글을 불러오지 못했습니다."));
        return response.text();
      })
      .then(function (html) {
        const doc = new DOMParser().parseFromString(html, "text/html");
        const incomingArea = doc.getElementById("postListArea");
        const currentArea = document.getElementById("postListArea");
        const currentFeed = currentArea ? currentArea.querySelector(".feed") : null;
        const incomingFeed = incomingArea ? incomingArea.querySelector(".feed") : null;
        if (!currentArea || !currentFeed || !incomingArea || !incomingFeed) {
          throw new Error(t("client.posts.badResponse", "게시글 응답 형식이 올바르지 않습니다."));
        }

        incomingFeed.querySelectorAll(".post-card").forEach(function (card) {
          currentFeed.appendChild(card);
        });

        const oldMore = currentArea.querySelector(".post-list-more");
        const nextMore = incomingArea.querySelector(".post-list-more");
        if (oldMore) {
          if (nextMore) oldMore.replaceWith(nextMore);
          else oldMore.remove();
        }
      })
      .catch(function () {
        moreButton.disabled = false;
        moreButton.textContent = t("community.common.more", "더보기") + " ";
        const arrow = document.createElement("span");
        arrow.setAttribute("aria-hidden", "true");
        arrow.textContent = "∨";
        moreButton.appendChild(arrow);
      });
  });

  const scrollTopButton = document.getElementById("postScrollTop");

  function updateScrollTopVisibility() {
    if (!scrollTopButton) return;
    scrollTopButton.hidden = window.scrollY < 500;
  }

  if (scrollTopButton) {
    window.addEventListener("scroll", updateScrollTopVisibility, { passive: true });
    scrollTopButton.addEventListener("click", function () {
      window.scrollTo({ top: 0, behavior: "smooth" });
    });
    updateScrollTopVisibility();
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

  // Toast UI Editor (실제 전송값은 hidden textarea)
  const editorEl = document.getElementById("writePostEditor");
  let postEditor = null;
  // 에디터 라벨이 생성 시점에 정해지므로 i18n 응답 후에 만든다.
  const i18nReady = (window.WePlaNet && window.WePlaNet.i18nReady) || Promise.resolve();
  i18nReady.then(initPostEditor);

  function initPostEditor() {
    if (!editorEl || !window.toastui) return;
    postEditor = new toastui.Editor({
      el: editorEl,
      height: "260px",
      initialEditType: "wysiwyg",
      previewStyle: "vertical",
      placeholder: t("client.posts.editorPlaceholder", "포스트를 남겨보세요 …"),
      // 이미지 버튼은 base64 로 본문에 들어가 글자 수 제한을 넘기므로 뺀다 (파일 첨부 사용).
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

    // 모드 전환 탭 라벨을 알기 쉬운 문구로 바꾼다.
    relabelEditorModeTabs(editorEl);
  }

  function relabelEditorModeTabs(root) {
    const labels = {
      Markdown: {
        text: t("client.editor.markdown", "마크다운"),
        title: t("client.editor.markdownTitle", "# 제목, **굵게** 같은 기호를 직접 입력하는 모드"),
      },
      WYSIWYG: {
        text: t("client.editor.wysiwyg", "간편 편집"),
        title: t("client.editor.wysiwygTitle", "위 툴바 버튼으로 서식을 지정하는 모드 (기호를 직접 쓰지 않아도 됨)"),
      },
    };

    // 탭이 늦게 붙어 다음 프레임에 한 번 더 시도한다.
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

  // 본문이 있어야 등록 가능
  function refreshSubmitState() {
    if (!submitBtn) return;
    const titleOk = !titleEl || titleEl.value.trim().length > 0; // 제목칸이 없으면 통과
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

  // 링크 아이콘 - 입력창 표시·숨김 (숨길 때 값도 비움)
  if (linkToggleBtn && linkRow) {
    linkToggleBtn.addEventListener("click", function () {
      const willShow = linkRow.style.display === "none";
      linkRow.style.display = willShow ? "block" : "none";
      linkToggleBtn.setAttribute("aria-pressed", willShow ? "true" : "false");
      if (!willShow && linkUrlEl) linkUrlEl.value = "";
      if (willShow && linkUrlEl) linkUrlEl.focus();
    });
  }

  // Hide from Artists 토글 - class·aria·hidden 값을 이 핸들러에서 직접 관리한다.
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
          errorEl.textContent = t("error.post.tooManyAttachments", "첨부파일은 최대 10개까지 등록할 수 있습니다.");
          errorEl.style.display = "block";
        }
        filesEl.value = "";
        if (fileCountEl) fileCountEl.textContent = "";
      } else {
        if (errorEl) errorEl.style.display = "none";
        if (fileCountEl) {
          fileCountEl.textContent = filesEl.files.length > 0
            ? t("client.posts.filesSelected", "{0}개 선택됨", [filesEl.files.length])
            : "";
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
            loadList(listBase + "?sort=latest&page=0", true);
          });
        }
        if (contentType.indexOf("application/json") !== -1) {
          return response.json().then(function (data) {
            // 막힌 경우 모달과 작성 내용을 유지하고 경고창으로 알린다.
            WePlaNet.alert(data.message || t("client.posts.submitFailed", "등록에 실패했습니다."));
            submitBtn.disabled = false;
          });
        }
        // 서버 오류 응답이면 목록을 다시 불러와 등록 여부를 확인한다.
        writeModal.classList.remove("is-open");
        resetWriteModal();
        loadList(listBase + "?sort=latest&page=0", true);
      })
      .catch(function () {
        if (errorEl) {
          errorEl.textContent = t("client.posts.submitFailedCheckList", "등록에 실패했습니다. 목록을 확인해주세요.");
          errorEl.style.display = "block";
        }
        submitBtn.disabled = false;
        loadList(listBase + "?sort=latest&page=0", false);
      });
  });
})();
