(function () {
  "use strict";

  var ADD_URL = "/shop/cart/add";
  var isCartPage = document.querySelector("[data-shop-cart-page]");

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

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function initCartQtySteppers(root) {
    (root || document).querySelectorAll(".cart-qty-stepper").forEach(function (stepper) {
      var form = stepper.closest(".cart-qty-form");
      if (!form) return;
      var min = parseInt(stepper.getAttribute("data-qty-min"), 10) || 1;
      var max = parseInt(stepper.getAttribute("data-qty-max"), 10);
      if (!max || max < 1) max = 999999;
      var input = form.querySelector('input[name="quantity"]');
      if (!input) return;
      var current = parseInt(input.value, 10) || min;
      var minus = stepper.querySelector('[data-qty-delta="-1"]');
      var plus = stepper.querySelector('[data-qty-delta="1"]');
      if (minus) minus.disabled = current <= min;
      if (plus) plus.disabled = current >= max;
    });
  }

  function buildCartRowHtml(item) {
    var product = item.product || {};
    var membershipTag = product.membershipOnly
      ? '<span class="tag tag--member">멤버십</span> '
      : "";
    var productUrl = "/shop/products/" + encodeURIComponent(product.id || "");

    return (
      '<tr data-product-id="' +
      escapeHtml(product.id || "") +
      '">' +
      '<td class="cart-table__product">' +
      '<div class="cart-product">' +
      '<div class="cart-product__thumb"></div>' +
      '<div class="cart-product__info">' +
      '<a class="cart-product__title" href="' +
      productUrl +
      '">' +
      escapeHtml(product.title || "") +
      "</a>" +
      '<p class="text-xs text-muted cart-product__meta">' +
      membershipTag +
      escapeHtml(product.artistName || "") +
      " · " +
      escapeHtml(product.categoryLabel || "") +
      "</p>" +
      "</div></div></td>" +
      '<td class="cart-table__qty">' +
      '<form action="/shop/cart/' +
      item.itemId +
      '/update" method="post" class="cart-qty-form">' +
      '<div class="cart-qty-stepper" data-qty-min="1" data-qty-max="' +
      (product.stockQuantity > 0 ? product.stockQuantity : 999999) +
      '">' +
      '<button type="button" class="cart-qty-stepper__btn" data-qty-delta="-1" aria-label="수량 줄이기">−</button>' +
      '<output class="cart-qty-stepper__value">' +
      item.quantity +
      "</output>" +
      '<input type="hidden" name="quantity" value="' +
      item.quantity +
      '" />' +
      '<button type="button" class="cart-qty-stepper__btn" data-qty-delta="1" aria-label="수량 늘리기">+</button>' +
      "</div></form></td>" +
      '<td class="cart-table__price"><strong>' +
      escapeHtml(item.formattedLineTotal || "") +
      "</strong></td>" +
      '<td class="cart-table__action">' +
      '<form action="/shop/cart/' +
      item.itemId +
      '/remove" method="post" class="cart-remove-form">' +
      '<button type="submit" class="cart-remove-btn" aria-label="삭제" title="삭제">' +
      '<span class="cart-remove-btn__icon" aria-hidden="true">🗑</span>' +
      "</button></form></td></tr>"
    );
  }

  function highlightCartRow(productId) {
    if (!productId) return;
    var tbody = document.getElementById("shopCartTableBody");
    if (!tbody) return;
    var row = tbody.querySelector('[data-product-id="' + CSS.escape(productId) + '"]');
    if (!row) return;

    row.classList.remove("cart-row--flash");
    void row.offsetWidth;
    row.classList.add("cart-row--flash");

    var list = document.querySelector(".shop-cart-list");
    if (list) {
      var rowTop = row.offsetTop;
      var rowBottom = rowTop + row.offsetHeight;
      var viewTop = list.scrollTop;
      var viewBottom = viewTop + list.clientHeight;
      if (rowTop < viewTop || rowBottom > viewBottom) {
        list.scrollTo({
          top: Math.max(0, rowTop - list.clientHeight / 2 + row.offsetHeight / 2),
          behavior: "smooth",
        });
      }
    }
  }

  function updateCartPage(cart, addedProductId) {
    if (!isCartPage || !cart) return;

    var layout = document.getElementById("shopCartLayout");
    var empty = document.getElementById("shopCartEmpty");
    var tbody = document.getElementById("shopCartTableBody");
    var subtotalEl = document.getElementById("shopCartSubtotal");
    var shippingEl = document.getElementById("shopCartShipping");
    var totalEl = document.getElementById("shopCartTotal");

    if (!cart.items || cart.items.length === 0) {
      if (layout) layout.classList.add("is-hidden");
      if (empty) empty.classList.remove("is-hidden");
      return;
    }

    if (layout) layout.classList.remove("is-hidden");
    if (empty) empty.classList.add("is-hidden");

    if (tbody) {
      tbody.innerHTML = cart.items.map(buildCartRowHtml).join("");
      initCartQtySteppers(tbody);
    }

    if (subtotalEl) subtotalEl.textContent = cart.formattedSubtotal || "";
    if (shippingEl) shippingEl.textContent = cart.formattedShippingFee || "";
    if (totalEl) totalEl.textContent = cart.formattedTotal || "";

    highlightCartRow(addedProductId);
  }

  function addProductToCart(productId, quantity, triggerBtn) {
    if (!productId) {
      return Promise.resolve(false);
    }
    if (triggerBtn) triggerBtn.disabled = true;
    var body = new FormData();
    body.append("productId", productId);
    body.append("quantity", String(quantity || 1));

    return fetch(ADD_URL, {
      method: "POST",
      body: body,
      headers: { Accept: "application/json" },
    })
      .then(function (res) {
        return res.json().catch(function () {
          return null;
        });
      })
      .then(function (data) {
        if (data && data.ok) {
          showShopToast(data.message || "장바구니에 담았습니다.", 1000);
          if (typeof data.cartCount === "number") updateCartBadge(data.cartCount);
          if (data.cart) updateCartPage(data.cart, data.addedProductId || productId);
          return true;
        }
        showShopToast((data && data.message) || "담기에 실패했습니다.", 1800);
        return false;
      })
      .catch(function () {
        showShopToast("장바구니에 담지 못했습니다.", 1000);
        return false;
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  function openShopPayment(prepared) {
    if (!window.WePlaNetToss) {
      return Promise.reject(new Error("결제 모듈을 불러오지 못했습니다."));
    }
    return WePlaNetToss.openVirtualAccount(
      prepared,
      window.location.origin + "/payments/shop/success",
      window.location.origin + "/payments/shop/fail"
    );
  }

  /** 바로 구매 — 토스 가상계좌 결제창 */
  function buyNowProduct(form, triggerBtn) {
    if (!form) {
      return Promise.resolve(false);
    }
    if (triggerBtn) triggerBtn.disabled = true;
    return fetch(form.action, {
      method: "POST",
      body: new FormData(form),
      headers: {
        Accept: "application/json",
        "X-Requested-With": "XMLHttpRequest",
      },
    })
      .then(function (res) {
        if (res.status === 401) {
          window.location.href = "/login";
          return null;
        }
        return res.json().catch(function () {
          return null;
        });
      })
      .then(function (data) {
        if (!data) {
          return false;
        }
        if (!data.success) {
          showShopToast(data.message || "구매에 실패했습니다.", 1800);
          return false;
        }
        return openShopPayment(data).then(function () {
          return true;
        });
      })
      .catch(function (error) {
        if (error && error.code === "USER_CANCEL") {
          showShopToast("결제를 취소했어요.", 1800);
        } else {
          showShopToast((error && error.message) || "구매에 실패했습니다.", 1800);
        }
        return false;
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  function checkoutCart(form, triggerBtn) {
    if (!form) return;
    if (triggerBtn) triggerBtn.disabled = true;
    var fields = { idempotencyKey: window.WePlaNetToss ? WePlaNetToss.createIdempotencyKey() : "" };
    var request = window.WePlaNetToss
      ? WePlaNetToss.postForm("/shop/payments/prepare-cart", fields)
      : Promise.reject(new Error("결제 모듈을 불러오지 못했습니다."));
    request
      .then(function (prepared) {
        return openShopPayment(prepared);
      })
      .catch(function (error) {
        if (error && error.code === "USER_CANCEL") {
          showShopToast("결제를 취소했어요.", 1800);
        } else {
          showShopToast((error && error.message) || "결제 준비에 실패했습니다.", 1800);
        }
      })
      .finally(function () {
        if (triggerBtn) triggerBtn.disabled = false;
      });
  }

  var form = document.getElementById("addToCartForm");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var productIdInput = form.querySelector('input[name="productId"]');
      var qtySelect = document.getElementById("qty");
      var productId = productIdInput && productIdInput.value;
      var quantity = qtySelect ? parseInt(qtySelect.value, 10) || 1 : 1;
      var submitBtn = form.querySelector('button[type="submit"]');
      addProductToCart(productId, quantity, submitBtn);
    });
  }

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-shop-add-cart]");
    if (!btn || btn.disabled) return;
    e.preventDefault();
    e.stopPropagation();
    addProductToCart(btn.getAttribute("data-product-id"), 1, btn);
  });

  document.addEventListener("click", function (e) {
    var stepBtn = e.target.closest("[data-qty-delta]");
    if (!stepBtn) return;
    e.preventDefault();
    var stepper = stepBtn.closest(".cart-qty-stepper");
    var form = stepBtn.closest(".cart-qty-form");
    if (!stepper || !form) return;

    var min = parseInt(stepper.getAttribute("data-qty-min"), 10) || 1;
    var max = parseInt(stepper.getAttribute("data-qty-max"), 10);
    if (!max || max < 1) max = 999999;
    var delta = parseInt(stepBtn.getAttribute("data-qty-delta"), 10) || 0;
    var input = form.querySelector('input[name="quantity"]');
    var output = stepper.querySelector(".cart-qty-stepper__value");
    if (!input) return;

    var next = (parseInt(input.value, 10) || min) + delta;
    if (next < min || next > max) return;

    input.value = String(next);
    if (output) output.textContent = String(next);
    form.submit();
  });

  initCartQtySteppers();

  var checkoutForm = document.querySelector('form[action="/shop/cart/checkout"], form[action$="/shop/cart/checkout"]');
  if (checkoutForm) {
    checkoutForm.addEventListener("submit", function (e) {
      e.preventDefault();
      checkoutCart(checkoutForm, checkoutForm.querySelector('button[type="submit"]'));
    });
  }

  window.WePlaNetShop = {
    showToast: showShopToast,
    updateCartBadge: updateCartBadge,
    updateCartPage: updateCartPage,
    addProductToCart: addProductToCart,
    buyNowProduct: buyNowProduct,
  };
})();
