(function () {
  "use strict";

  var form = document.getElementById("goodsForm");
  var chipRoot = document.getElementById("goodsCategoryChips");
  var optionsRoot = document.getElementById("goodsCategoryOptions");
  var hiddenJson = document.getElementById("categoryOptionsJson");
  if (!form || !chipRoot || !optionsRoot || !hiddenJson) return;

  var initial = {};
  try {
    initial = JSON.parse(hiddenJson.value || "{}");
  } catch (e) {
    initial = {};
  }

  var CLOTHING_SIZES = ["XS", "S", "M", "L", "XL", "XXL"];
  var SHOE_MM = [];
  for (var mm = 220; mm <= 300; mm += 5) {
    SHOE_MM.push(String(mm));
  }

  var state = {
    categories: Array.isArray(initial.categories) ? initial.categories.slice() : [],
    stocks: initial.stocks && typeof initial.stocks === "object" ? JSON.parse(JSON.stringify(initial.stocks)) : {},
    attributes: initial.attributes && typeof initial.attributes === "object"
      ? JSON.parse(JSON.stringify(initial.attributes))
      : {},
  };

  function isSelected(code) {
    return state.categories.indexOf(code) >= 0;
  }

  function toggleCategory(code) {
    var idx = state.categories.indexOf(code);
    if (idx >= 0) {
      state.categories.splice(idx, 1);
      if (code === "CLOTHING") delete state.stocks.CLOTHING;
      if (code === "SHOES") delete state.stocks.SHOES;
      delete state.attributes[code];
    } else {
      state.categories.push(code);
      if (code === "CLOTHING" && !state.stocks.CLOTHING) state.stocks.CLOTHING = {};
      if (code === "SHOES" && !state.stocks.SHOES) state.stocks.SHOES = {};
    }
    renderChips();
    renderOptionPanels();
    syncHidden();
  }

  function renderChips() {
    chipRoot.querySelectorAll("[data-cat-chip]").forEach(function (btn) {
      var code = btn.getAttribute("data-cat-chip");
      var on = isSelected(code);
      btn.classList.toggle("is-selected", on);
      btn.setAttribute("aria-pressed", on ? "true" : "false");
    });
  }

  function sizeStockMap(key) {
    var raw = state.stocks[key];
    return raw && typeof raw === "object" ? raw : {};
  }

  function needsDefaultStock() {
    if (state.categories.length === 0) return true;
    return state.categories.some(function (c) {
      return c !== "CLOTHING" && c !== "SHOES";
    });
  }

  function selectableStockPanel(key, title, values, suffix) {
    var panel = document.createElement("div");
    panel.className = "goods-option-panel";
    var h = document.createElement("h3");
    h.className = "goods-option-panel__title";
    h.textContent = title;
    panel.appendChild(h);
    var hint = document.createElement("p");
    hint.className = "text-xs text-muted";
    hint.textContent = "취급할 항목만 선택하면 재고 입력란이 생깁니다. 선택하지 않은 항목은 구매 화면에도 안 나옵니다.";
    panel.appendChild(hint);

    var grid = document.createElement("div");
    grid.className = "goods-size-stock-grid";
    var map = sizeStockMap(key);

    values.forEach(function (val) {
      var selected = Object.prototype.hasOwnProperty.call(map, val);
      var row = document.createElement("label");
      row.className = "goods-size-stock-item" + (selected ? "" : " is-off");

      var check = document.createElement("input");
      check.type = "checkbox";
      check.checked = selected;

      var label = document.createElement("span");
      label.textContent = val + (suffix || "");

      var stock = document.createElement("input");
      stock.type = "number";
      stock.min = "0";
      stock.placeholder = "재고";
      stock.className = "form-input goods-stock-input";
      stock.value = selected ? String(map[val]) : "";
      stock.disabled = !selected;

      check.addEventListener("change", function () {
        var next = sizeStockMap(key);
        if (check.checked) {
          next[val] = stock.value === "" ? 0 : Number(stock.value);
          stock.disabled = false;
          row.classList.remove("is-off");
        } else {
          delete next[val];
          stock.value = "";
          stock.disabled = true;
          row.classList.add("is-off");
        }
        state.stocks[key] = next;
        syncHidden();
      });
      stock.addEventListener("input", function () {
        if (!check.checked) return;
        var next = sizeStockMap(key);
        next[val] = stock.value === "" ? 0 : Number(stock.value);
        state.stocks[key] = next;
        syncHidden();
      });

      row.appendChild(check);
      row.appendChild(label);
      row.appendChild(stock);
      grid.appendChild(row);
    });
    panel.appendChild(grid);
    return panel;
  }

  function defaultStockPanel() {
    var panel = document.createElement("div");
    panel.className = "goods-option-panel";
    var h = document.createElement("h3");
    h.className = "goods-option-panel__title";
    h.textContent = "굿즈 단위 재고";
    panel.appendChild(h);
    var hint = document.createElement("p");
    hint.className = "text-xs text-muted";
    hint.textContent = "사이즈가 없는 카테고리(가방/악세서리/기타) 또는 카테고리 미선택 시 사용합니다.";
    panel.appendChild(hint);
    var input = document.createElement("input");
    input.type = "number";
    input.min = "0";
    input.className = "form-input";
    input.style.maxWidth = "160px";
    input.value = state.stocks.DEFAULT != null ? String(state.stocks.DEFAULT) : "0";
    input.addEventListener("input", function () {
      state.stocks.DEFAULT = input.value === "" ? 0 : Number(input.value);
      syncHidden();
    });
    panel.appendChild(input);
    return panel;
  }

  function bagPanel() {
    var panel = document.createElement("div");
    panel.className = "goods-option-panel";
    var h = document.createElement("h3");
    h.className = "goods-option-panel__title";
    h.textContent = "가방 치수 (cm)";
    panel.appendChild(h);
    var bag = state.attributes.BAG || {};
    ["width", "height", "depth"].forEach(function (dim) {
      var row = document.createElement("label");
      row.className = "goods-dim-row";
      row.textContent = (dim === "width" ? "가로" : dim === "height" ? "높이" : "세로") + " ";
      var input = document.createElement("input");
      input.type = "number";
      input.min = "0";
      input.step = "0.1";
      input.className = "form-input goods-dim-input";
      input.value = bag[dim] || "";
      input.addEventListener("input", function () {
        state.attributes.BAG = state.attributes.BAG || {};
        state.attributes.BAG[dim] = input.value;
        syncHidden();
      });
      row.appendChild(input);
      panel.appendChild(row);
    });
    return panel;
  }

  function notePanel(code, title) {
    var panel = document.createElement("div");
    panel.className = "goods-option-panel";
    var h = document.createElement("h3");
    h.className = "goods-option-panel__title";
    h.textContent = title;
    panel.appendChild(h);
    var note = document.createElement("textarea");
    note.className = "form-input";
    note.rows = 2;
    note.placeholder = "예: 색상, 재질 등 (선택)";
    note.value = (state.attributes[code] && state.attributes[code].note) || "";
    note.addEventListener("input", function () {
      state.attributes[code] = state.attributes[code] || {};
      state.attributes[code].note = note.value;
      syncHidden();
    });
    panel.appendChild(note);
    return panel;
  }

  function renderOptionPanels() {
    optionsRoot.innerHTML = "";
    if (state.categories.indexOf("CLOTHING") >= 0) {
      optionsRoot.appendChild(selectableStockPanel("CLOTHING", "의류 사이즈 · 옵션별 재고", CLOTHING_SIZES, ""));
    }
    if (state.categories.indexOf("SHOES") >= 0) {
      optionsRoot.appendChild(selectableStockPanel("SHOES", "신발 치수(mm) · 옵션별 재고", SHOE_MM, "mm"));
    }
    if (state.categories.indexOf("BAG") >= 0) {
      optionsRoot.appendChild(bagPanel());
    }
    if (state.categories.indexOf("ACCESSORY") >= 0) {
      optionsRoot.appendChild(notePanel("ACCESSORY", "악세서리 메모 (선택)"));
    }
    if (state.categories.indexOf("OTHER") >= 0) {
      optionsRoot.appendChild(notePanel("OTHER", "기타 메모 (선택)"));
    }
    if (needsDefaultStock()) {
      optionsRoot.appendChild(defaultStockPanel());
    }
  }

  function syncHidden() {
    if (!needsDefaultStock()) {
      delete state.stocks.DEFAULT;
    }
    hiddenJson.value = JSON.stringify({
      categories: state.categories,
      stocks: state.stocks,
      attributes: state.attributes,
    });
  }

  chipRoot.querySelectorAll("[data-cat-chip]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      toggleCategory(btn.getAttribute("data-cat-chip"));
    });
  });

  renderChips();
  renderOptionPanels();
  syncHidden();

  form.addEventListener("submit", function () {
    syncHidden();
  });
})();
