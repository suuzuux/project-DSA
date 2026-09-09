/**
 * 커뮤니티 목록/하이라이트 – 게시글 하트 좋아요 (상세 진입 없이 토글)
 */
(function () {
  "use strict";

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-post-like]");
    if (!btn) return;

    e.preventDefault();
    e.stopPropagation();

    if (document.body.getAttribute("data-authenticated") !== "true") {
      window.location.href = "/login";
      return;
    }

    var postId = btn.getAttribute("data-post-like");
    if (!postId || btn.dataset.liking === "1") return;
    btn.dataset.liking = "1";

    fetch("/posts/detail/" + postId + "/like", { method: "POST" })
      .then(function (response) {
        if (response.status === 401 || response.status === 403) {
          window.location.href = "/login";
          return null;
        }
        return response.ok ? response.json() : null;
      })
      .then(function (data) {
        if (!data) return;
        btn.classList.toggle("is-liked", !!data.liked);
        var countEl = btn.querySelector("[data-like-count]");
        if (countEl) {
          countEl.textContent = data.likeCount > 0 ? String(data.likeCount) : "";
        }
      })
      .finally(function () {
        btn.dataset.liking = "0";
      });
  });
})();
