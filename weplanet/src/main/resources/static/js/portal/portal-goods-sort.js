(function () {
  "use strict";
  var tbody = document.getElementById("goodsSortBody");
  if (!tbody) return;

  var reorderUrl = tbody.getAttribute("data-reorder-url");
  var statusEl = document.getElementById("goodsSortStatus");
  var dragRow = null;

  // 페이지가 넘겨준 문구 (없으면 한국어 기본값)
  var I18N = window.PORTAL_I18N || {};
  function t(key, ko) {
    return I18N[key] != null ? I18N[key] : ko;
  }

  function setStatus(msg, isError) {
    if (!statusEl) return;
    statusEl.textContent = msg || "";
    statusEl.style.color = isError ? "#c45c26" : "#1f7a52";
  }

  function orderedIds() {
    return Array.prototype.map.call(
      tbody.querySelectorAll("tr[data-goods-id]"),
      function (tr) {
        return tr.getAttribute("data-goods-id");
      }
    );
  }

  function saveOrder() {
    if (!reorderUrl) return;
    var body = new FormData();
    orderedIds().forEach(function (id) {
      body.append("orderedIds", id);
    });
    setStatus(t("portal.goods.sortSaving", "순서 저장 중…"), false);
    fetch(reorderUrl, { method: "POST", body: body, headers: { Accept: "application/json" } })
      .then(function (res) {
        return res.json();
      })
      .then(function (data) {
        if (data && data.ok) {
          setStatus(t("portal.goods.sortSaved", "노출 순서가 저장되었습니다."), false);
        } else {
          setStatus((data && data.message) || t("common.error.saveFailed", "저장에 실패했습니다."), true);
        }
      })
      .catch(function () {
        setStatus(t("common.error.saveFailed", "저장에 실패했습니다."), true);
      });
  }

  tbody.querySelectorAll("tr[data-goods-id]").forEach(function (row) {
    row.setAttribute("draggable", "true");
    row.addEventListener("dragstart", function (e) {
      dragRow = row;
      row.classList.add("is-dragging");
      e.dataTransfer.effectAllowed = "move";
    });
    row.addEventListener("dragend", function () {
      row.classList.remove("is-dragging");
      dragRow = null;
    });
    row.addEventListener("dragover", function (e) {
      e.preventDefault();
      if (!dragRow || dragRow === row) return;
      var rect = row.getBoundingClientRect();
      var after = e.clientY > rect.top + rect.height / 2;
      if (after) {
        row.after(dragRow);
      } else {
        row.before(dragRow);
      }
    });
    row.addEventListener("drop", function (e) {
      e.preventDefault();
      saveOrder();
    });
  });
})();
