(function () {
  "use strict";

  document.querySelectorAll(".board-select").forEach(enhance);

  function enhance(wrap) {
    if (wrap.dataset.enhanced) return;
    var select = wrap.querySelector("select");
    if (!select) return;
    wrap.dataset.enhanced = "1";
    select.classList.add("board-select__native");
    select.setAttribute("tabindex", "-1");
    select.setAttribute("aria-hidden", "true");

    var trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "board-select__trigger";
    trigger.setAttribute("aria-haspopup", "listbox");
    trigger.setAttribute("aria-expanded", "false");
    trigger.setAttribute("aria-label", select.getAttribute("aria-label") || "정렬");

    var valueEl = document.createElement("span");
    valueEl.className = "board-select__value";
    trigger.appendChild(valueEl);

    var menu = document.createElement("ul");
    menu.className = "board-select__menu";
    menu.setAttribute("role", "listbox");
    menu.hidden = true;

    Array.prototype.forEach.call(select.options, function (opt, index) {
      var li = document.createElement("li");
      li.setAttribute("role", "option");
      li.setAttribute("data-index", String(index));
      li.textContent = (opt.textContent || "").trim();
      li.addEventListener("click", function (e) {
        e.preventDefault();
        e.stopPropagation();
        if (select.selectedIndex !== index) {
          select.selectedIndex = index;
          select.dispatchEvent(new Event("change", { bubbles: true }));
        }
        close();
        render();
      });
      menu.appendChild(li);
    });

    function selectedLabel() {
      var opt = select.options[select.selectedIndex];
      return opt ? (opt.textContent || "").trim() : "";
    }

    function render() {
      valueEl.textContent = selectedLabel();
      menu.querySelectorAll("[role=option]").forEach(function (li) {
        var on = Number(li.getAttribute("data-index")) === select.selectedIndex;
        li.classList.toggle("is-active", on);
        li.setAttribute("aria-selected", on ? "true" : "false");
      });
    }

    function open() {
      menu.hidden = false;
      wrap.classList.add("is-open");
      trigger.setAttribute("aria-expanded", "true");
    }

    function close() {
      menu.hidden = true;
      wrap.classList.remove("is-open");
      trigger.setAttribute("aria-expanded", "false");
    }

    trigger.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      if (menu.hidden) open();
      else close();
    });

    document.addEventListener("click", function (e) {
      if (!wrap.contains(e.target)) close();
    });

    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") close();
    });

    wrap.appendChild(trigger);
    wrap.appendChild(menu);
    render();
  }
})();
