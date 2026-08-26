/**
 * 커뮤니티 Fan 게시글 상세 – 좋아요, 댓글, 번역
 */
(function () {
  "use strict";

  const postId = document.body.dataset.postId;
  if (!postId) return;

  const likeButton = document.getElementById("likeButton");
  if (likeButton) {
    likeButton.addEventListener("click", function () {
      fetch("/posts/detail/" + postId + "/like", { method: "POST" })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          document.getElementById("likeCount").textContent = data.likeCount > 0 ? data.likeCount : "";
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
        link.textContent = "번역보기";
        return;
      }

      link.textContent = "번역 중...";
      fetch("/posts/detail/" + postId + "/translate", { method: "POST" })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          textArea.textContent = data.translated;
          textArea.style.display = "block";
          link.textContent = "원문보기";
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
          if (msg) msg.textContent = data.message || "신고가 접수되었습니다.";
        })
        .catch(function () {
          if (msg) msg.textContent = "신고 접수에 실패했습니다.";
        });
    });
  }

  // 게시글 수정 모달 - Toast UI Editor, 기존 내용을 hidden textarea 값으로 초기화
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
        const titleOk = editTitleEl && editTitleEl.value.trim().length > 0;
        editSubmitBtn.disabled = !titleOk || text.trim().length === 0 || text.length > 1000;
      }
    }
    editPostEditor.on("change", refreshEditState);
    if (editTitleEl) editTitleEl.addEventListener("input", refreshEditState);
    refreshEditState();
  }

  const summarizeButton = document.getElementById("summarizeButton");
  if (summarizeButton) {
    summarizeButton.addEventListener("click", function () {
      const area = document.getElementById("summaryArea");
      area.innerHTML = "<p>AI가 요약을 만들고 있어요...</p>";

      fetch("/posts/detail/" + postId + "/summarize", {
        method: "POST",
        headers: { "X-Requested-With": "fetch" },
      })
        .then(function (response) {
          return response.json();
        })
        .then(function (data) {
          area.innerHTML = '<hr><h4>AI 요약</h4><p id="summaryText"></p>';
          document.getElementById("summaryText").textContent = data.summary;
        })
        .catch(function () {
          area.innerHTML = '<p class="text-xs" style="color:var(--wp-danger, #d33);">요약에 실패했습니다.</p>';
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
    if (!e.target.classList.contains("comment-translate-link")) return;

    const link = e.target;
    const commentId = link.getAttribute("data-comment-id");
    const textArea = document.getElementById("commentTranslated-" + commentId);

    if (textArea.style.display !== "none") {
      textArea.style.display = "none";
      link.textContent = "번역보기";
      return;
    }

    link.textContent = "번역 중...";
    fetch("/posts/detail/" + postId + "/comment/" + commentId + "/translate", { method: "POST" })
      .then(function (response) {
        return response.json();
      })
      .then(function (data) {
        textArea.textContent = data.translated;
        textArea.style.display = "block";
        link.textContent = "원문보기";
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
          return response.json();
        })
        .then(function (data) {
          if (msg) msg.textContent = data.message || "신고가 접수되었습니다.";
        })
        .catch(function () {
          if (msg) msg.textContent = "신고 접수에 실패했습니다.";
        });
      return;
    }

    if (form.id !== "commentForm" && !form.classList.contains("comment-delete-form") && !form.classList.contains("comment-edit-form")) return;

    e.preventDefault();
    fetch(form.action, {
      method: "POST",
      headers: { "X-Requested-With": "fetch" },
      body: new URLSearchParams(new FormData(form)),
    })
      .then(function (response) {
        if (!response.ok) {
          return response.json().then(function (data) {
            alert(data.message || "요청에 실패했습니다.");
          });
        }
        return response.text().then(function (html) {
          const section = document.getElementById("commentSection");
          if (section) section.outerHTML = html;
        });
      });
  });
})();
