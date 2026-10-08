/** 메인 배너 번역 이어받기 - pending 이면 /api/main-banners 로 번역문을 받아 글자만 교체한다 (최대 MAX_TRIES, 원문 제목으로 대조). */
(function () {
  "use strict";

  var MAX_TRIES = 3;
  var RETRY_MS = 3000;
  var root = document.querySelector('[data-banners-pending="true"]');
  if (!root) return;
  var tries = 0;

  function apply(banners) {
    root.querySelectorAll(".banner__slide--promo").forEach(function (slide) {
      var title = slide.querySelector(".banner-promo__title");
      if (!title) return;
      var shown = title.textContent;
      banners.forEach(function (banner) {
        if (banner.originalTitle !== shown || banner.title === shown) return;
        title.textContent = banner.title;
        var body = slide.querySelector(".banner-promo__body");
        if (body && banner.body) body.textContent = banner.body;
      });
    });
  }

  function load() {
    tries++;
    fetch("/api/main-banners", { headers: { "X-Requested-With": "fetch" } })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (!data || !data.banners) return;
        apply(data.banners);
        if (data.pending && tries < MAX_TRIES) setTimeout(load, RETRY_MS);
      })
      .catch(function () { /* 번역을 못 받으면 원문 그대로 둔다 */ });
  }

  load();
})();
