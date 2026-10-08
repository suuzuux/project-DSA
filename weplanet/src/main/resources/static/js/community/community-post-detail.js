/** 커뮤니티 게시글 상세 - 좋아요, 댓글, 번역 */
(function () {
  "use strict";

  const postId = document.body.dataset.postId;
  if (!postId) return;

  // 문구는 WePlaNet.t 에서 꺼낸다 (없으면 한국어 기본값).
  const t = function (key, fallback, args) {
    return (window.WePlaNet && window.WePlaNet.t) ? window.WePlaNet.t(key, fallback, args) : fallback;
  };

  function focusCommentInput() {
    if (window.location.hash !== "#commentContentInput") return;
    const input = document.getElementById("commentContentInput");
    if (!input) return;
    input.scrollIntoView({ behavior: "smooth", block: "center" });
    window.setTimeout(function () {
      input.focus({ preventScroll: true });
    }, 250);
  }

  focusCommentInput();
  window.addEventListener("hashchange", focusCommentInput);

  function markCommentReported(commentId) {
    const text = document.getElementById("commentText-" + commentId);
    const item = text ? text.closest(".comment-item") : null;
    if (item) {
      item.classList.add("comment-item--reported");
    }
    if (text) {
      text.textContent = t("community.comment.reported", "신고접수된 댓글입니다");
    }
    const reportRow = document.getElementById("commentReportRow-" + commentId);
    if (reportRow) {
      reportRow.style.display = "none";
      reportRow.remove();
    }
    if (item) {
      const actions = item.querySelector(".flex-center");
      if (actions) actions.remove();
      const editRow = document.getElementById("commentEditRow-" + commentId);
      if (editRow) editRow.remove();
      const translated = document.getElementById("commentTranslated-" + commentId);
      if (translated) translated.remove();
    }
  }

  const likeButton = document.getElementById("likeButton");
  if (likeButton) {
    likeButton.addEventListener("click", function () {
      fetch("/posts/detail/" + postId + "/like", { method: "POST" })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          document.getElementById("likeCount").textContent = String(data.likeCount);
          likeButton.classList.toggle("is-liked", !!data.liked);
        });
    });
  }

  const bookmarkButton = document.getElementById("bookmarkButton");
  if (bookmarkButton) {
    bookmarkButton.addEventListener("click", function () {
      const btn = this;
      fetch("/posts/detail/" + postId + "/bookmark", { method: "POST" })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          document.getElementById("bookmarkIconFilled").style.display = data.bookmarked ? "" : "none";
          document.getElementById("bookmarkIconOutline").style.display = data.bookmarked ? "none" : "";
          btn.classList.toggle("is-active", data.bookmarked);
        });
    });
  }

  const translateLink = document.getElementById("postTranslateLink");
  if (translateLink) {
    translateLink.addEventListener("click", function () {
      const link = this;
      const textArea = document.getElementById("postTranslatedText");

      if (textArea.style.display !== "none") {
        textArea.style.display = "none";
        link.textContent = t("community.translate.show", "번역보기");
        return;
      }

      link.textContent = t("client.translate.loading", "번역 중...");
      fetch("/posts/detail/" + postId + "/translate", { method: "POST" })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          textArea.textContent = data.translated;
          textArea.style.display = "block";
          link.textContent = t("client.translate.original", "원문보기");
        });
    });
  }

  // 게시글 신고 - 토글로 신고 사유 선택 행을 열고 닫음
  const postReportToggle = document.getElementById("postReportToggle");
  const postReportRow = document.getElementById("postReportRow");
  const postReportForm = document.getElementById("postReportForm");
  if (postReportToggle && postReportRow) {
    postReportToggle.addEventListener("click", function () {
      postReportRow.style.display = postReportRow.style.display === "none" ? "block" : "none";
    });
  }
  if (postReportForm) {
    postReportForm.addEventListener("submit", function (e) {
      e.preventDefault();
      const msg = document.getElementById("postReportMsg");
      fetch(postReportForm.action, {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
        body: new URLSearchParams(new FormData(postReportForm)),
      })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          if (msg) msg.textContent = data.message || t("client.report.submitted", "신고가 접수되었습니다.");
        })
        .catch(function () {
          if (msg) msg.textContent = t("client.report.failed", "신고 접수에 실패했습니다.");
        });
    });
  }

  // 게시글 수정 모달 (Toast UI Editor)
  const editEditorEl = document.getElementById("editPostEditor");
  const editContentEl = document.getElementById("editPostContent");
  const editTitleEl = document.getElementById("editPostTitle");
  const editSubmitBtn = document.getElementById("editPostSubmit");
  let editPostEditor = null;
  if (editEditorEl && editContentEl && window.toastui) {
    editPostEditor = new toastui.Editor({
      el: editEditorEl,
      height: "260px",
      initialEditType: "wysiwyg",
      previewStyle: "vertical",
      initialValue: editContentEl.value,
      toolbarItems: [
        ["heading", "bold", "italic", "strike"],
        ["hr", "quote"],
        ["ul", "ol", "task", "indent", "outdent"],
        ["table", "link"],
        ["code", "codeblock"],
      ],
    });
    function refreshEditState() {
      const text = editPostEditor.getMarkdown();
      editContentEl.value = text;
      const counter = document.getElementById("editPostCharCount");
      if (counter) counter.textContent = text.length + " / 1000";
      if (editSubmitBtn) {
        const titleOk = !editTitleEl || editTitleEl.value.trim().length > 0; // 제목칸이 없으면 통과
        editSubmitBtn.disabled = !titleOk || text.trim().length === 0 || text.length > 1000;
      }
    }
    editPostEditor.on("change", refreshEditState);
    if (editTitleEl) editTitleEl.addEventListener("input", refreshEditState);
    refreshEditState();
  }

  // 수정 저장은 fetch 로 보내 금칙어 등은 경고창으로 알린다.
  const editPostForm = document.getElementById("editPostForm");
  if (editPostForm) {
    editPostForm.addEventListener("submit", function (e) {
      e.preventDefault();
      if (editPostEditor) editContentEl.value = editPostEditor.getMarkdown();
      if (editSubmitBtn) editSubmitBtn.disabled = true;
      fetch(editPostForm.action, {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
        body: new URLSearchParams(new FormData(editPostForm)),
      })
        .then(function (response) {
          if (response.ok) {
            // 저장 성공 시 새로고침
            window.location.reload();
            return;
          }
          return response.json().then(function (data) {
            WePlaNet.alert(data.message || t("client.request.failed", "요청에 실패했습니다."));
            if (editSubmitBtn) editSubmitBtn.disabled = false;
          });
        })
        .catch(function () {
          WePlaNet.alert(t("client.request.failed", "요청에 실패했습니다."));
          if (editSubmitBtn) editSubmitBtn.disabled = false;
        });
    });
  }

  const summarizeButton = document.getElementById("summarizeButton");
  if (summarizeButton) {
    summarizeButton.addEventListener("click", function () {
      const area = document.getElementById("summaryArea");
      // 번역 문구는 textContent 로 넣는다.
      area.innerHTML = "<p></p>";
      area.querySelector("p").textContent = t("client.summary.loading", "AI가 요약을 만들고 있어요...");

      fetch("/posts/detail/" + postId + "/summarize", {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
      })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          area.innerHTML = '<hr><h4></h4><p id="summaryText"></p>';
          area.querySelector("h4").textContent = t("client.summary.title", "AI 요약");
          document.getElementById("summaryText").textContent = data.summary;
        })
        .catch(function () {
          area.innerHTML = '<p class="text-xs" style="color:var(--wp-danger, #d33);"></p>';
          area.querySelector("p").textContent = t("client.summary.failed", "요약에 실패했습니다.");
        });
    });
  }

  document.addEventListener("click", function (e) {
    if (e.target.classList.contains("comment-report-toggle")) {
      const commentId = e.target.getAttribute("data-comment-id");
      const row = document.getElementById("commentReportRow-" + commentId);
      if (row) row.style.display = row.style.display === "none" ? "flex" : "none";
      return;
    }
    if (e.target.classList.contains("comment-edit-toggle")) {
      const commentId = e.target.getAttribute("data-comment-id");
      const row = document.getElementById("commentEditRow-" + commentId);
      if (row) row.style.display = row.style.display === "none" ? "flex" : "none";
      return;
    }
    // 답글 입력칸 열고 닫기
    if (e.target.classList.contains("comment-reply-toggle")) {
      const commentId = e.target.getAttribute("data-comment-id");
      const row = document.getElementById("commentReplyRow-" + commentId);
      if (row) {
        const opened = row.style.display !== "none";
        row.style.display = opened ? "none" : "block";
        if (!opened) {
          const input = row.querySelector("input[name='content']");
          if (input) input.focus();
        }
      }
      return;
    }
    if (!e.target.classList.contains("comment-translate-link")) return;

    const link = e.target;
    const commentId = link.getAttribute("data-comment-id");
    const textArea = document.getElementById("commentTranslated-" + commentId);

    if (textArea.style.display !== "none") {
      textArea.style.display = "none";
      link.textContent = t("community.translate.show", "번역보기");
      return;
    }

    link.textContent = t("client.translate.loading", "번역 중...");
    fetch("/posts/detail/" + postId + "/comment/" + commentId + "/translate", { method: "POST" })
      .then(function (response) {
        return response.json();
      })
      .then(function (data) {
        textArea.textContent = data.translated;
        textArea.style.display = "block";
        link.textContent = t("client.translate.original", "원문보기");
      });
  });

  const cancelBtn = document.getElementById("commentCancelBtn");
  if (cancelBtn) {
    cancelBtn.addEventListener("click", function () {
      const input = document.getElementById("commentContentInput");
      if (input) input.value = "";
    });
  }

  document.addEventListener("submit", function (e) {
    const form = e.target;
    if (form.classList.contains("comment-report-form")) {
      e.preventDefault();
      const commentId = form.getAttribute("data-comment-id");
      const msg = document.getElementById("commentReportMsg-" + commentId);
      fetch(form.action, {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
        body: new URLSearchParams(new FormData(form)),
      })
        .then(function (response) {
          return response.json().then(function (data) {
            return { ok: response.ok, data: data };
          });
        })
        .then(function (result) {
          if (!result.ok || result.data.success === false) {
            if (msg) msg.textContent = (result.data && result.data.message) || t("client.report.failed", "신고 접수에 실패했습니다.");
            return;
          }
          markCommentReported(commentId);
        })
        .catch(function () {
          if (msg) msg.textContent = t("client.report.failed", "신고 접수에 실패했습니다.");
        });
      return;
    }

    if (
      form.id !== "commentForm" &&
      !form.classList.contains("comment-delete-form") &&
      !form.classList.contains("comment-edit-form") &&
      !form.classList.contains("comment-reply-form")
    ) {
      return;
    }

    // 빈 답글은 보내지 않는다.
    if (form.classList.contains("comment-reply-form")) {
      const replyInput = form.querySelector("input[name='content']");
      if (replyInput && !replyInput.value.trim()) {
        e.preventDefault();
        return;
      }
    }

    e.preventDefault();
    fetch(form.action, {
      method: "POST",
      headers: { "X-Requested-With": "fetch" },
      body: new URLSearchParams(new FormData(form)),
    })
      .then(function (response) {
        if (!response.ok) {
          return response.json().then(function (data) {
            WePlaNet.alert(data.message || t("client.request.failed", "요청에 실패했습니다."));
          });
        }
        return response.text().then(function (html) {
          const section = document.getElementById("commentSection");
          if (section) section.outerHTML = html;
        });
      });
  });
})();
