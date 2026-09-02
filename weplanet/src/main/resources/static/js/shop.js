(function () {
  "use strict";

  function showShopToast(message, durationMs) {
    var duration = durationMs || 1000;
    var el = document.getElementById("shopToast");
    if (!el) {
      el = document.createElement("div");
      el.id = "shopToast";
      el.className = "shop-toast";
      el.setAttribute("role", "status");
      el.hidden = true;
      document.body.appendChild(el);
    }
    el.textContent = message;
    el.hidden = false;
    el.classList.add("is-visible");
    clearTimeout(el._hideTimer);
    el._hideTimer = setTimeout(function () {
      el.classList.remove("is-visible");
      el.hidden = true;
    }, duration);
  }

  function updateCartBadge(count) {
    document.querySelectorAll('a[href*="/shop/cart"]').forEach(function (link) {
      var badge = link.querySelector(".shop-cart-badge");
      if (count > 0) {
        if (!badge) {
          badge = document.createElement("span");
          badge.className = "shop-cart-badge";
          link.appendChild(badge);
        }
        badge.textContent = String(count);
        badge.hidden = false;
      } else if (badge) {
        badge.hidden = true;
      }
    });
  }

  var form = document.getElementById("addToCartForm");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn) submitBtn.disabled = true;

      fetch(form.action, {
        method: "POST",
        body: new FormData(form),
        headers: { Accept: "application/json" },
      })
        .then(function (res) {
          return res.ok ? res.json() : null;
        })
        .then(function (data) {
          if (data && data.ok) {
            showShopToast(data.message || "장바구니에 담았습니다.", 1000);
            if (typeof data.cartCount === "number") {
              updateCartBadge(data.cartCount);
            }
            return;
          }
          showShopToast("장바구니에 담지 못했습니다.", 1000);
        })
        .catch(function () {
          showShopToast("장바구니에 담지 못했습니다.", 1000);
        })
        .finally(function () {
          if (submitBtn) submitBtn.disabled = false;
        });
    });
  }

  window.WePlaNetShop = {
    showToast: showShopToast,
    updateCartBadge: updateCartBadge,
  };
})();
