/**
 * 공지 번역보기 - 게시글 "번역보기"와 같은 방식 (사이트 공지·커뮤니티 공지 상세 공용).
 * [data-notice-translate] 링크를 누르면 data-url 로 번역을 받아 제목·본문을 data-target 상자에 보여주고,
 * 다시 누르면 접는다. 번역에 실패했으면 다음에 누를 때 다시 시도한다. 문구는 링크의 data-label-* 속성으로 받는다(화면 언어).
 * 번역문은 textContent 로만 넣고, data-render="markdown" 이면(사이트 공지) 원문과 같은 Toast UI 뷰어로 본문을 그린다.
 */
(function () {
  "use strict";

  document.querySelectorAll("[data-notice-translate]").forEach(function (link) {
    var box = document.getElementById(link.dataset.target);
    if (!box) return;
    var busy = false;

    function renderBody(content) {
      var body = document.createElement("div");
      if (link.dataset.render === "markdown" && window.toastui) {
        body.className = "notice-viewer";
        body.style.whiteSpace = "normal";
        toastui.Editor.factory({ el: body, viewer: true, initialValue: content });
      } else {
        body.textContent = content;
      }
      return body;
    }

    link.addEventListener("click", function (e) {
      e.preventDefault();
      if (busy) return;
      if (box.dataset.loaded === "true") {
        var open = box.style.display !== "none";
        box.style.display = open ? "none" : "block";
        link.textContent = open ? link.dataset.labelShow : link.dataset.labelOriginal;
        return;
      }
      busy = true;
      link.textContent = link.dataset.labelLoading;
      fetch(link.dataset.url, { method: "POST", headers: { "X-Requested-With": "fetch" } })
        .then(function (res) { return res.json(); })
        .then(function (data) {
          box.textContent = "";
          if (data && data.success) {
            var title = document.createElement("strong");
            title.style.display = "block";
            title.style.marginBottom = "6px";
            title.textContent = data.title || "";
            box.appendChild(title);
            box.appendChild(renderBody(data.content || ""));
            box.dataset.loaded = "true";
            link.textContent = link.dataset.labelOriginal;
          } else {
            box.textContent = (data && data.message) || link.dataset.labelFailed;
            link.textContent = link.dataset.labelShow;
          }
          box.style.display = "block";
        })
        .catch(function () {
          box.textContent = link.dataset.labelFailed;
          box.style.display = "block";
          link.textContent = link.dataset.labelShow;
        })
        .finally(function () { busy = false; });
    });
  });
})();
