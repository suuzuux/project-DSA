/** 공통 UI 동작 - 배너 캐러셀, 모달, 탭·토글, 약관 전체동의, 회원가입 검증. */

(function () {
  "use strict";

  // 유틸
  const qs = (sel, root = document) => root.querySelector(sel);
  const qsa = (sel, root = document) => [...root.querySelectorAll(sel)];

  // JS 문구 (/api/i18n/client) - WePlaNet.t(key, 한국어 기본값, 인자) 로 꺼내 쓴다.
  let clientMessages = null;

  function formatMessage(template, args) {
    let result = String(template == null ? "" : template);
    (args || []).forEach((arg, idx) => {
      result = result.split("{" + idx + "}").join(String(arg == null ? "" : arg));
    });
    return result;
  }

  const i18nReady = fetch("/api/i18n/client", { headers: { Accept: "application/json" } })
    .then((res) => (res.ok ? res.json() : null))
    .then((data) => {
      clientMessages = data || {};
      return clientMessages;
    })
    .catch(() => {
      clientMessages = {};
      return clientMessages;
    });

  function t(key, fallback, args) {
    const template = (clientMessages && clientMessages[key] != null) ? clientMessages[key] : fallback;
    return formatMessage(template == null ? key : template, args);
  }

  // 라이트·다크 테마 (선택값은 localStorage, 없으면 OS 설정).
  const THEME_KEY = "weplanet-theme";

  // 로그인 전 화면은 항상 밝게 둔다 (선택값은 유지).
  const forceLightPage = document.body.classList.contains("auth-page");

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", forceLightPage ? "light" : theme);
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch (e) {
      // 저장이 막혀도 화면 전환은 된다.
    }
  }

  function initialTheme() {
    try {
      const saved = localStorage.getItem(THEME_KEY);
      if (saved === "dark" || saved === "light") return saved;
    } catch (e) {
      // OS 설정 사용
    }
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "dark"
      : "light";
  }

  applyTheme(initialTheme());

  document.addEventListener("click", function (e) {
    const toggle = e.target.closest("[data-theme-toggle]");
    if (!toggle) return;
    const isDark = document.documentElement.getAttribute("data-theme") === "dark";
    applyTheme(isDark ? "light" : "dark");
  });

  // 공용 다이얼로그 - WePlaNet.alert / confirm / toast (브라우저 기본 창 대체).
  function ensureDialogRoot() {
    let root = document.getElementById("wpDialogRoot");
    if (root) return root;

    root = document.createElement("div");
    root.id = "wpDialogRoot";
    root.className = "wp-dialog-backdrop";
    root.hidden = true;
    root.innerHTML =
      '<div class="wp-dialog" role="dialog" aria-modal="true" aria-labelledby="wpDialogMsg">' +
      '  <p class="wp-dialog__msg" id="wpDialogMsg"></p>' +
      '  <div class="wp-dialog__actions">' +
      '    <button type="button" class="btn btn--soft" data-wp-dialog="cancel"></button>' +
      '    <button type="button" class="btn btn--primary" data-wp-dialog="ok"></button>' +
      "  </div>" +
      "</div>";
    document.body.appendChild(root);
    return root;
  }

  function openDialog(message, withCancel) {
    const root = ensureDialogRoot();
    const msgEl = root.querySelector(".wp-dialog__msg");
    const okBtn = root.querySelector('[data-wp-dialog="ok"]');
    const cancelBtn = root.querySelector('[data-wp-dialog="cancel"]');

    msgEl.textContent = message;
    // 버튼 문구는 열 때마다 채운다 (i18n 응답 이후 반영).
    okBtn.textContent = t("common.confirm", "확인");
    cancelBtn.textContent = t("common.cancel", "취소");
    cancelBtn.hidden = !withCancel;
    root.hidden = false;
    okBtn.focus();

    return new Promise((resolve) => {
      function close(result) {
        root.hidden = true;
        okBtn.removeEventListener("click", onOk);
        cancelBtn.removeEventListener("click", onCancel);
        document.removeEventListener("keydown", onKey);
        resolve(result);
      }
      function onOk() { close(true); }
      function onCancel() { close(false); }
      function onKey(e) {
        if (e.key === "Escape") close(false);
        if (e.key === "Enter") close(true);
      }

      okBtn.addEventListener("click", onOk);
      cancelBtn.addEventListener("click", onCancel);
      document.addEventListener("keydown", onKey);
    });
  }

  function ensureToastRoot() {
    let root = document.getElementById("wpToastRoot");
    if (root) return root;
    root = document.createElement("div");
    root.id = "wpToastRoot";
    root.className = "wp-toast-root";
    document.body.appendChild(root);
    return root;
  }

  window.WePlaNet = window.WePlaNet || {};
  window.WePlaNet.t = t;
  window.WePlaNet.i18nReady = i18nReady;
  window.WePlaNet.alert = function (message) {
    return openDialog(message, false);
  };
  window.WePlaNet.confirm = function (message) {
    return openDialog(message, true);
  };
  window.WePlaNet.toast = function (message) {
    const root = ensureToastRoot();
    const item = document.createElement("div");
    item.className = "wp-toast";
    item.textContent = message;
    root.appendChild(item);
    setTimeout(function () {
      item.classList.add("is-out");
      setTimeout(function () { item.remove(); }, 250);
    }, 2600);
  };

  /** onsubmit 확인창 대체 - 제출을 막고 확인을 누르면 다시 제출한다. */
  window.WePlaNet.confirmSubmit = function (form, message) {
    if (form.dataset.wpConfirmed === "1") {
      form.dataset.wpConfirmed = "";
      return true;
    }
    window.WePlaNet.confirm(message).then(function (ok) {
      if (ok) {
        form.dataset.wpConfirmed = "1";
        if (typeof form.requestSubmit === "function") {
          form.requestSubmit();
        } else {
          form.submit();
        }
      }
    });
    return false;
  };

  /** data-modal-open / data-modal-close 모달 제어 */
  function initModals() {
    qsa("[data-modal-open]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-modal-open");
        const modal = document.getElementById(id);
        if (modal) modal.classList.add("is-open");
      });
    });

    qsa("[data-modal-close]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const backdrop = btn.closest(".modal-backdrop");
        if (backdrop) backdrop.classList.remove("is-open");
      });
    });

    // 배경 클릭 시 닫기
    qsa(".modal-backdrop").forEach((backdrop) => {
      backdrop.addEventListener("click", (e) => {
        if (e.target === backdrop) backdrop.classList.remove("is-open");
      });
    });

    // ESC
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        qsa(".modal-backdrop.is-open").forEach((m) => m.classList.remove("is-open"));
      }
    });
  }

  /** 배너 캐러셀 (.banner[data-carousel]) */
  function initCarousels() {
    qsa("[data-carousel]").forEach((root) => {
      const track = qs(".banner__track", root);
      const slides = qsa(".banner__slide", root);
      const dotsWrap = qs(".banner__dots", root);
      if (!track || slides.length === 0) return;

      let index = 0;

      // 도트 생성
      if (dotsWrap) {
        dotsWrap.innerHTML = slides
          .map((_, i) => `<button type="button" class="banner__dot${i === 0 ? " is-active" : ""}" data-i="${i}"></button>`)
          .join("");
        // aria-label 은 i18n 응답 후 채운다.
        i18nReady.then(() => {
          qsa(".banner__dot", dotsWrap).forEach((dot, i) => {
            dot.setAttribute("aria-label", t("client.carousel.slide", "슬라이드 {0}", [i + 1]));
          });
        });
      }

      const go = (i) => {
        index = (i + slides.length) % slides.length;
        track.style.transform = `translateX(-${index * 100}%)`;
        qsa(".banner__dot", root).forEach((d, di) => {
          d.classList.toggle("is-active", di === index);
        });
      };

      qs("[data-carousel-prev]", root)?.addEventListener("click", () => go(index - 1));
      qs("[data-carousel-next]", root)?.addEventListener("click", () => go(index + 1));
      dotsWrap?.addEventListener("click", (e) => {
        const t = e.target.closest("[data-i]");
        if (t) go(Number(t.dataset.i));
      });

      // 자동 재생 (호버 시 정지)
      let timer = setInterval(() => go(index + 1), 5000);
      root.addEventListener("mouseenter", () => clearInterval(timer));
      root.addEventListener("mouseleave", () => {
        timer = setInterval(() => go(index + 1), 5000);
      });
    });
  }

  /** 탭 ([data-tabs] 안 is-active 토글 + 패널 전환) */
  function initTabs() {
    qsa("[data-tabs]").forEach((root) => {
      root.addEventListener("click", (e) => {
        const tab = e.target.closest("[data-tab]");
        if (!tab || !root.contains(tab)) return;

        const name = tab.getAttribute("data-tab");
        qsa("[data-tab]", root).forEach((t) => t.classList.toggle("is-active", t === tab));
        qsa("[data-tab-panel]", root).forEach((p) => {
          p.classList.toggle("hidden", p.getAttribute("data-tab-panel") !== name);
        });
      });
    });
  }

  /** 토글 스위치 (.toggle 클릭 시 is-on) */
  function initToggles() {
    qsa(".toggle").forEach((el) => {
      el.setAttribute("role", "switch");
      el.setAttribute("aria-checked", el.classList.contains("is-on"));
      el.addEventListener("click", () => {
        el.classList.toggle("is-on");
        el.setAttribute("aria-checked", el.classList.contains("is-on"));
      });
    });
  }

  /** 약관 전체동의 동기화 */
  function initAgreeAll() {
    const all = qs("#agreeAll");
    if (!all) return;
    const items = qsa("[data-agree-item]");

    all.addEventListener("change", () => {
      items.forEach((c) => {
        c.checked = all.checked;
      });
    });

    items.forEach((c) => {
      c.addEventListener("change", () => {
        all.checked = items.every((i) => i.checked);
      });
    });
  }

  /** 회원가입 클라이언트 검증 (통과 시 POST /signup) */
  function initSignupValidation() {
    const form = qs("#signupForm");
    if (!form) return;

    // 메시지는 검증 시점에 꺼낸다 (서버 문구 키 재사용).
    const rules = {
      username: {
        test: (v) => /^[a-zA-Z0-9]{4,20}$/.test(v),
        get msg() { return t("signup.validation.usernamePattern", "아이디는 영문/숫자 4~20자로 입력해주세요."); },
      },
      password: {
        test: (v) => /^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$/.test(v),
        get msg() { return t("signup.validation.passwordPattern", "비밀번호는 영문/숫자 포함 8~20자로 입력해주세요."); },
      },
      passwordConfirm: {
        test: (v) => v === (qs("#password")?.value || ""),
        get msg() { return t("signup.error.passwordMismatch", "비밀번호가 일치하지 않습니다."); },
      },
      realName: {
        test: (v) => v.trim().length > 0,
        get msg() { return t("signup.validation.realNameRequired", "이름을 입력해주세요."); },
      },
      email: {
        test: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v),
        get msg() { return t("signup.validation.emailFormat", "올바른 이메일 형식으로 입력해주세요."); },
      },
      nickname: {
        // 서버 닉네임 규칙과 같은 2~15자
        test: (v) => v.trim() === "" || (v.length >= 2 && v.length <= 15),
        get msg() { return t("client.signup.nicknameLength", "닉네임은 2~15자로 입력해주세요."); },
      },
    };

    const validateField = (name) => {
      const input = qs(`[name="${name}"]`, form);
      const group = input?.closest(".form-group");
      if (!input || !group || !rules[name]) return true;
      const ok = rules[name].test(input.value.trim());
      group.classList.toggle("is-invalid", !ok);
      const err = qs(".form-error", group);
      if (err) err.textContent = ok ? "" : rules[name].msg;
      return ok;
    };

    Object.keys(rules).forEach((name) => {
      qs(`[name="${name}"]`, form)?.addEventListener("blur", () => validateField(name));
    });

    form.addEventListener("submit", (e) => {
      const ok = Object.keys(rules).every(validateField);
      const requiredAgrees = qsa("[data-agree-required]");
      const agreeOk = requiredAgrees.every((c) => c.checked);
      if (!agreeOk) {
        e.preventDefault();
        window.WePlaNet.alert(t("client.signup.agreeRequired", "필수 약관에 동의해 주세요."));
        return;
      }
      if (!ok) {
        e.preventDefault();
      }
    });
  }

  /** data-mock-submit 폼 (기본 제출 막고 안내 또는 이동) */
  function initMockForms() {
    qsa("form[data-mock-submit]").forEach((form) => {
      form.addEventListener("submit", (e) => {
        e.preventDefault();
        const go = form.getAttribute("data-mock-submit");
        if (go && go !== "true") {
          window.location.href = go;
        } else {
          window.WePlaNet.alert(t("client.mockSubmit", "목업 화면입니다. 실제 서버 전송은 하지 않습니다."));
        }
      });
    });
  }

  /** 글자 수 카운터 (textarea[data-count] + .char-count) */
  function initCharCounters() {
    qsa("[data-count]").forEach((el) => {
      const max = Number(el.getAttribute("maxlength") || el.dataset.count || 0);
      const counter = el.parentElement?.querySelector(".char-count");
      const update = () => {
        if (counter) counter.textContent = `${el.value.length}${max ? ` / ${max}` : ""}`;
      };
      el.addEventListener("input", update);
      update();
    });
  }

  /** 좋아요 토글 (목업 카운트) */
  function initLikeButtons() {
    qsa("[data-like]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const countEl = btn.querySelector("[data-like-count]");
        if (!countEl) {
          btn.classList.toggle("is-liked");
          return;
        }
        let n = parseInt(countEl.textContent.replace(/[^\d]/g, ""), 10) || 0;
        const liked = btn.classList.toggle("is-liked");
        n = liked ? n + 1 : Math.max(0, n - 1);
        countEl.textContent = n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k`.replace(".0", "") : String(n);
      });
    });
  }

  // 초기화
  document.addEventListener("DOMContentLoaded", () => {
    initModals();
    initCarousels();
    initTabs();
    initToggles();
    initAgreeAll();
    initSignupValidation();
    initMockForms();
    initCharCounters();
    initLikeButtons();
  });
})();
