/**
 * <head> 에서 바로 실행돼 첫 화면부터 저장된 테마로 그린다.
 * (main.js 는 body 끝에서 돌기 때문에, 그 전까지 다크 사용자에게 밝은 화면이 번쩍인다)
 * 키와 판단 규칙은 main.js 의 THEME_KEY / initialTheme() 과 같아야 한다.
 */
(function () {
  var theme = "light";
  try {
    var saved = localStorage.getItem("weplanet-theme");
    if (saved === "dark" || saved === "light") {
      theme = saved;
    } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
      theme = "dark";
    }
  } catch (e) {
    /* 저장소 접근이 막혀도 기본값으로 그린다 */
  }
  document.documentElement.setAttribute("data-theme", theme);
})();
