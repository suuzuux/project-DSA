/**
 * 메인 배너 번역 이어받기 - 메인 화면을 그릴 때 AI 번역이 늦어 원문으로 둔 배너가 있으면(data-banners-pending="true")
 * /api/main-banners 로 번역문을 받아 제목·본문 글자만 바꿔 끼운다. 언어를 바꾼 직후에도 새로고침 없이 번역이 보이게.
 * 그래도 아직 번역 중이면(pending) 잠시 뒤 다시 물어본다 (최대 MAX_TRIES 번).
 * 화면의 배너는 원문 제목(originalTitle)으로 맞춰 본다 - 그 사이 노출 배너가 바뀌었으면 그 배너는 건너뛴다.
 */
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
    // 총공 슬라이드 제목 (번역된 이벤트 제목 + 언어 파일 문장 틀을 서버가 조립해 준 문장)
  function applyHashtag(text) {
    if (!text) return;
    var title = root.querySelector(".hashtag-home-slide .banner__title");
    if (title && title.textContent !== text) title.textContent = text;
  }

  function load() {
    tries++;
    fetch("/api/main-banners", { headers: { "X-Requested-With": "fetch" } })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (!data || !data.banners) return;
        apply(data.banners);
        applyHashtag(data.hashtagTitle);
        if (data.pending && tries < MAX_TRIES) setTimeout(load, RETRY_MS);
      })
      .catch(function () { /* 번역을 못 받으면 원문 그대로 둔다 */ });
  }

  load();
})();
