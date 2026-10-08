/** 공지 번역보기 - data-url 로 번역을 받아 펼치고 접는다 (textContent 사용, 사이트 공지는 Toast UI 뷰어로 렌더). */
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
