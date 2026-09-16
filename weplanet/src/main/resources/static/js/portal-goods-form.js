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
    options: initial.options && typeof initial.options === "object" ? JSON.parse(JSON.stringify(initial.options)) : {},
  };

  function isSelected(code) {
    return state.categories.indexOf(code) >= 0;
  }

  function toggleCategory(code) {
    var idx = state.categories.indexOf(code);
    if (idx >= 0) {
      state.categories.splice(idx, 1);
      delete state.options[code];
    } else {
      state.categories.push(code);
      if (!state.options[code]) state.options[code] = {};
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

  function ensureList(cat, key) {
    if (!state.options[cat]) state.options[cat] = {};
    if (!state.options[cat][key]) state.options[cat][key] = [];
    return state.options[cat][key];
  }

  function toggleListValue(cat, key, value) {
    var list = ensureList(cat, key);
    var i = list.indexOf(value);
    if (i >= 0) list.splice(i, 1);
    else list.push(value);
    syncHidden();
    renderOptionPanels();
  }

  function setScalar(cat, key, value) {
    if (!state.options[cat]) state.options[cat] = {};
    state.options[cat][key] = value ? [String(value)] : [];
    syncHidden();
  }

  function chipButtons(cat, key, values, labels) {
    var wrap = document.createElement("div");
    wrap.className = "goods-option-chips";
    var list = ensureList(cat, key);
    values.forEach(function (val, idx) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "goods-option-chip";
      btn.textContent = labels ? labels[idx] : val;
      if (list.indexOf(val) >= 0) btn.classList.add("is-selected");
      btn.addEventListener("click", function () {
        toggleListValue(cat, key, val);
      });
      wrap.appendChild(btn);
    });
    return wrap;
  }

  function renderOptionPanels() {
    optionsRoot.innerHTML = "";
    state.categories.forEach(function (cat) {
      var panel = document.createElement("div");
      panel.className = "goods-option-panel";
      panel.setAttribute("data-cat-panel", cat);
      var title = document.createElement("h3");
      title.className = "goods-option-panel__title";
      title.textContent = labelFor(cat);
      panel.appendChild(title);

      if (cat === "CLOTHING") {
        panel.appendChild(chipButtons(cat, "sizes", CLOTHING_SIZES, null));
      } else if (cat === "SHOES") {
        panel.appendChild(chipButtons(cat, "mm", SHOE_MM, SHOE_MM.map(function (v) { return v + "mm"; })));
      } else if (cat === "BAG") {
        ["width", "height", "depth"].forEach(function (dim) {
          var row = document.createElement("label");
          row.className = "goods-dim-row";
          var dimLabel = dim === "width" ? "가로(cm)" : dim === "height" ? "높이(cm)" : "세로(cm)";
          row.textContent = dimLabel + " ";
          var input = document.createElement("input");
          input.type = "number";
          input.min = "0";
          input.step = "0.1";
          input.className = "form-input goods-dim-input";
          var cur = state.options[cat] && state.options[cat][dim] ? state.options[cat][dim][0] : "";
          input.value = cur || "";
          input.addEventListener("input", function () {
            setScalar(cat, dim, input.value);
          });
          row.appendChild(input);
          panel.appendChild(row);
        });
      } else if (cat === "ACCESSORY" || cat === "OTHER") {
        var hint = document.createElement("p");
        hint.className = "text-xs text-muted";
        hint.textContent = "옵션 입력은 선택 사항입니다.";
        panel.appendChild(hint);
        var note = document.createElement("textarea");
        note.className = "form-input";
        note.rows = 2;
        note.placeholder = "예: 색상, 재질 등 (선택)";
        var noteVal = state.options[cat] && state.options[cat].note ? state.options[cat].note[0] : "";
        note.value = noteVal || "";
        note.addEventListener("input", function () {
          setScalar(cat, "note", note.value);
        });
        panel.appendChild(note);
      }

      optionsRoot.appendChild(panel);
    });
  }

  function labelFor(code) {
    var btn = chipRoot.querySelector('[data-cat-chip="' + code + '"]');
    return btn ? btn.textContent.trim() : code;
  }

  function syncHidden() {
    hiddenJson.value = JSON.stringify({
      categories: state.categories,
      options: state.options,
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
