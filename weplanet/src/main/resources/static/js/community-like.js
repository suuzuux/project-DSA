/**
 * 커뮤니티 목록/하이라이트 – 게시글·미디어 하트 좋아요 (상세 진입 없이 토글)
 */
(function () {
  "use strict";

  function toggleLike(btn, url) {
    if (document.body.getAttribute("data-authenticated") !== "true") {
      window.location.href = "/login";
      return;
    }
    if (!url || btn.dataset.liking === "1") return;
    btn.dataset.liking = "1";

    fetch(url, { method: "POST" })
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
  }

  document.addEventListener("click", function (e) {
    var postBtn = e.target.closest("[data-post-like]");
    if (postBtn) {
      e.preventDefault();
      e.stopPropagation();
      toggleLike(postBtn, "/posts/detail/" + postBtn.getAttribute("data-post-like") + "/like");
      return;
    }

    var mediaBtn = e.target.closest("[data-media-like]");
    if (mediaBtn) {
      e.preventDefault();
      e.stopPropagation();
      toggleLike(mediaBtn, "/board/media/" + mediaBtn.getAttribute("data-media-like") + "/like");
    }
  });
})();
