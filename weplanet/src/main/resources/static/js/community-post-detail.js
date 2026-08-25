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

  document.addEventListener("click", function (e) {
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
    if (form.id !== "commentForm" && !form.classList.contains("comment-delete-form")) return;

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
