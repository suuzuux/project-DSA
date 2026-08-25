/**
 * ============================================================
 * WePlaNet – Common Interactions (Mock)
 * ------------------------------------------------------------
 * 실제 API 연동 없이 UI 동작만 시연한다.
 * - 배너 캐러셀
 * - 모달 open/close
 * - 탭 / 토글
 * - 약관 전체동의
 * - 회원가입 간단 유효성 (시각 피드백)
 * - 폼 submit 기본 방지 + 페이지 이동 안내
 * ============================================================
 */

(function () {
  "use strict";

  /* ---------------------------------------------------------
   * 유틸
   * --------------------------------------------------------- */
  const qs = (sel, root = document) => root.querySelector(sel);
  const qsa = (sel, root = document) => [...root.querySelectorAll(sel)];

  /**
   * data-modal-open / data-modal-close 로 모달 제어
   * 예) <button data-modal-open="membershipModal">
   */
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

  /**
   * 배너 캐러셀 (.banner[data-carousel])
   */
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
          .map((_, i) => `<button type="button" class="banner__dot${i === 0 ? " is-active" : ""}" data-i="${i}" aria-label="슬라이드 ${i + 1}"></button>`)
          .join("");
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

  /**
   * 탭: [data-tabs] 안에서 .is-active 토글 + 패널 전환
   * HTML 예)
   * <div data-tabs>
   *   <button data-tab="a" class="is-active">A</button>
   *   <div data-tab-panel="a">...</div>
   * </div>
   */
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

  /**
   * 토글 스위치 (.toggle) – 클릭 시 is-on
   */
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

  /**
   * 약관: #agreeAll 체크 시 하위 전부 동기화
   */
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

  /**
   * 회원가입 클라이언트 유효성
   * - 통과 시 실제 서버(POST /signup)로 제출
   */
  function initSignupValidation() {
    const form = qs("#signupForm");
    if (!form) return;

    const rules = {
      username: {
        test: (v) => /^[a-zA-Z0-9]{4,20}$/.test(v),
        msg: "아이디는 영문/숫자 4~20자로 입력해주세요.",
      },
      password: {
        test: (v) => /^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$/.test(v),
        msg: "비밀번호는 영문/숫자 포함 8~20자로 입력해주세요.",
      },
      passwordConfirm: {
        test: (v) => v === (qs("#password")?.value || ""),
        msg: "비밀번호가 일치하지 않습니다.",
      },
      realName: {
        test: (v) => v.trim().length > 0,
        msg: "이름을 입력해주세요.",
      },
      email: {
        test: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v),
        msg: "올바른 이메일 형식으로 입력해주세요.",
      },
      nickname: {
        test: (v) => v.trim() === "" || (v.length >= 3 && v.length <= 10),
        msg: "닉네임은 3~10자로 입력해주세요.",
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
        alert("필수 약관에 동의해 주세요.");
        return;
      }
      if (!ok) {
        e.preventDefault();
      }
    });
  }

  /**
   * data-mock-submit 폼: 기본 submit 막고 alert 또는 이동
   */
  function initMockForms() {
    qsa("form[data-mock-submit]").forEach((form) => {
      form.addEventListener("submit", (e) => {
        e.preventDefault();
        const go = form.getAttribute("data-mock-submit");
        if (go && go !== "true") {
          window.location.href = go;
        } else {
          alert("목업 화면입니다. 실제 서버 전송은 하지 않습니다.");
        }
      });
    });
  }

  /**
   * 글자 수 카운터: textarea[data-count] + .char-count
   */
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

  /**
   * 좋아요 토글 (목업 카운트 ±1)
   */
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

  /* ---------------------------------------------------------
   * Init
   * --------------------------------------------------------- */
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
