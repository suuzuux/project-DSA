/**
 * ============================================================
 * WePlaNet – CSRF 토큰 자동 첨부 (FIX-02)
 * ------------------------------------------------------------
 * 서버(SecurityConfig)는 POST/PUT/PATCH/DELETE 요청마다 CSRF 토큰을 요구한다.
 * 토큰은 서버가 XSRF-TOKEN 쿠키로 내려주는데, 브라우저가 쿠키를 자동으로 붙이는 것만으로는
 * 검사를 통과할 수 없고 "쿠키 값을 읽어서 X-XSRF-TOKEN 헤더에 직접 넣어야" 통과한다.
 * (다른 사이트는 우리 쿠키를 읽을 수 없어서 이 헤더를 만들지 못한다 = CSRF 방어의 원리)
 *
 * 이 파일은 window.fetch 를 감싸서, 우리 서버로 가는 데이터 변경 요청에 그 헤더를 자동으로 넣는다.
 * 그래서 각 화면의 fetch 코드는 고칠 필요가 없다. fetch 를 쓰는 다른 스크립트보다 먼저 불러올 것.
 * JS 로 만든 POST 폼(th:action 이 없는 폼)에는 제출 직전에 _csrf 숨은 필드를 넣어 준다.
 *
 *   <script th:src="@{/js/csrf.js}"></script>
 *
 * - GET/HEAD/OPTIONS 는 서버가 검사하지 않으므로 손대지 않는다 (토큰이 불필요하게 퍼지지 않게)
 * - 다른 사이트(토스 등)로 가는 요청에는 절대 넣지 않는다
 * - Thymeleaf 폼(th:action)은 숨은 _csrf 필드가 자동으로 들어가므로 이 파일과 상관없다
 * ============================================================
 */
(function () {
  "use strict";

  // 같은 페이지에서 두 번 불러와도 fetch 를 두 겹으로 감싸지 않게 한다
  if (window.__weplanetCsrfInstalled) return;
  window.__weplanetCsrfInstalled = true;

  var COOKIE_NAME = "XSRF-TOKEN";
  var HEADER_NAME = "X-XSRF-TOKEN";
  var SAFE_METHODS = ["GET", "HEAD", "OPTIONS", "TRACE"];

  // 쿠키는 요청할 때마다 새로 읽는다 (로그인/로그아웃하면 서버가 토큰을 바꾸기 때문)
  function readToken() {
    var cookies = document.cookie ? document.cookie.split("; ") : [];
    for (var i = 0; i < cookies.length; i++) {
      var eq = cookies[i].indexOf("=");
      if (cookies[i].substring(0, eq) === COOKIE_NAME) {
        return decodeURIComponent(cookies[i].substring(eq + 1));
      }
    }
    return null;
  }

  function isSameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return false;
    }
  }

  var originalFetch = window.fetch;
  if (typeof originalFetch !== "function") return;

  window.fetch = function (input, init) {
    var isRequestObject = typeof Request !== "undefined" && input instanceof Request;
    var url = isRequestObject ? input.url : String(input);
    var method = ((init && init.method) || (isRequestObject ? input.method : "GET")).toUpperCase();

    if (SAFE_METHODS.indexOf(method) !== -1 || !isSameOrigin(url)) {
      return originalFetch.call(this, input, init);
    }

    var token = readToken();
    if (!token) {
      return originalFetch.call(this, input, init);
    }

    // 원래 넘긴 헤더(객체/Headers/배열 어떤 모양이든)를 유지한 채 토큰만 더한다
    var headers = new Headers((init && init.headers) || (isRequestObject ? input.headers : undefined));
    if (!headers.has(HEADER_NAME)) {
      headers.set(HEADER_NAME, token);
    }
    var nextInit = Object.assign({}, init, { headers: headers });
    return originalFetch.call(this, input, nextInit);
  };

  // JS 가 직접 만들어 넣은 POST 폼(예: shell.js 의 멤버십 가입/해지)은 Thymeleaf 가 토큰을 못 넣어 준다.
  // 그런 폼이 제출될 때 _csrf 숨은 필드를 대신 넣는다. 이미 토큰 필드가 있는 폼(th:action)은 건드리지 않는다.
  // (확인창 뒤에 form.submit() 으로 다시 내보내는 경우도 첫 제출 때 넣어 둔 필드가 그대로 남아 있다)
  var PARAM_NAME = "_csrf";
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if ((form.getAttribute("method") || "get").toUpperCase() !== "POST") return;
    // form.action 은 폼 안에 name="action" 인 입력이 있으면 그 요소가 나오므로 속성 값을 직접 읽는다
    if (!isSameOrigin(form.getAttribute("action") || window.location.href) || form.querySelector('input[name="' + PARAM_NAME + '"]')) return;

    var token = readToken();
    if (!token) return;
    var input = document.createElement("input");
    input.type = "hidden";
    input.name = PARAM_NAME;
    input.value = token;
    form.appendChild(input);
  }, true);
})();
