/**
 * 최고관리자 > 이벤트 > 해시태그 총공 > 등록/수정 폼
 *  - 아티스트 검색: 입력이 잠깐 멈추면 서버에 검색 요청 → 결과의 "추가" → 참여 아티스트 목록에 한 줄 추가
 *  - 줄마다 hidden artistIds + hashtags 입력칸이 있어서, 그대로 제출하면 서버가 List 로 받는다
 *  - 시작일~종료일이 며칠인지 바로 보여주고, 3~7일을 벗어나면 경고
 * 검색 결과는 innerHTML 대신 textContent 로 채운다 (이름에 <script> 같은 글자가 있어도 실행되지 않게)
 */
(function () {
  "use strict";

  const form = document.getElementById("hashtagEventForm");
  if (!form) return;

  const searchUrl = window.__HASHTAG_ARTIST_SEARCH_URL__ || "/admin/events/hashtag/artists";
  const searchInput = document.getElementById("artistSearch");
  const resultList = document.getElementById("artistResults");
  const targetList = document.getElementById("targetList");
  const rowTemplate = document.getElementById("targetRowTemplate");
  const targetCount = document.getElementById("targetCount");
  const targetEmpty = document.getElementById("targetEmpty");
  const startInput = document.getElementById("startDate");
  const endInput = document.getElementById("endDate");
  const periodHint = document.getElementById("periodHint");
  const minDays = Number(form.dataset.minDays || 3);
  const maxDays = Number(form.dataset.maxDays || 7);

  // 화면 언어 문구 - hashtag-event-form.html 이 window.__HASHTAG_FORM_MSG__ 에 넣어 준다. 없으면 한국어 기본값
  const MSG = window.__HASHTAG_FORM_MSG__ || {};
  function t(key, ko, args) {
    let text = MSG[key] != null ? MSG[key] : ko;
    (args || []).forEach(function (value, i) {
      text = text.split("{" + i + "}").join(String(value));
    });
    return text;
  }

  let searchTimer = null;
  let lastResults = [];

  // 이미 참여 목록에 들어간 아티스트 id 들 (input 값이라 문자열)
  function selectedIds() {
    return Array.from(targetList.querySelectorAll("input[name='artistIds']")).map(function (input) {
      return input.value;
    });
  }

  function refreshCount() {
    const count = targetList.children.length;
    targetCount.textContent = t("teams", "{0}팀", [count]);
    targetEmpty.hidden = count > 0;
  }

  // 프로필 사진이 있으면 사진, 없으면 이름 첫 글자
  function fillAvatar(el, artist) {
    el.textContent = "";
    if (artist.profileImg) {
      const img = document.createElement("img");
      img.src = artist.profileImg;
      img.alt = "";
      el.appendChild(img);
    } else {
      el.textContent = (artist.name || "?").charAt(0);
    }
  }

  function addTarget(artist) {
    if (selectedIds().includes(String(artist.artistId))) return;

    // <template> 안의 한 줄 틀을 복사해서 값만 채운다
    const row = rowTemplate.content.firstElementChild.cloneNode(true);
    fillAvatar(row.querySelector(".hashtag-avatar"), artist);
    row.querySelector("strong").textContent = artist.name;
    row.querySelector(".text-muted").textContent = artist.agencyName || t("noAgency", "소속사 미등록");
    row.querySelector("input[name='artistIds']").value = artist.artistId;

    const hashtagInput = row.querySelector("input[name='hashtags']");
    hashtagInput.placeholder = "#" + t("hashtagPrefix", "문구") + "_" + (artist.nameEn || artist.name).replace(/\s+/g, "");

    targetList.appendChild(row);
    hashtagInput.focus();

    refreshCount();
    renderResults(lastResults); // 방금 추가한 아티스트를 "추가됨"으로 다시 그림
  }

  function renderResults(artists) {
    lastResults = artists;
    resultList.textContent = "";

    if (!searchInput.value.trim()) {
      resultList.hidden = true;
      return;
    }

    if (artists.length === 0) {
      const empty = document.createElement("li");
      empty.className = "hashtag-search-results__empty";
      empty.textContent = t("noResults", "검색 결과가 없습니다.");
      resultList.appendChild(empty);
      resultList.hidden = false;
      return;
    }

    const selected = selectedIds();
    artists.forEach(function (artist) {
      const item = document.createElement("li");

      const avatar = document.createElement("span");
      avatar.className = "hashtag-avatar";
      fillAvatar(avatar, artist);

      const info = document.createElement("span");
      info.className = "hashtag-search-results__name";
      const name = document.createElement("strong");
      name.textContent = artist.name + (artist.nameEn ? " (" + artist.nameEn + ")" : "");
      const agency = document.createElement("span");
      agency.className = "text-xs text-muted";
      agency.textContent = artist.agencyName || t("noAgency", "소속사 미등록");
      info.append(name, agency);

      const already = selected.includes(String(artist.artistId));
      const button = document.createElement("button");
      button.type = "button";
      button.className = "btn btn--ghost btn--sm";
      button.textContent = already ? t("added", "추가됨") : t("add", "추가");
      button.disabled = already;
      button.addEventListener("click", function () {
        addTarget(artist);
      });

      item.append(avatar, info, button);
      resultList.appendChild(item);
    });
    resultList.hidden = false;
  }

  function search() {
    const keyword = searchInput.value.trim();
    if (!keyword) {
      renderResults([]);
      return;
    }

    fetch(searchUrl + "?keyword=" + encodeURIComponent(keyword))
      .then(function (res) {
        return res.ok ? res.json() : [];
      })
      .then(renderResults)
      .catch(function () {
        renderResults([]);
      });
  }

  // 한 글자 칠 때마다 요청하지 않도록, 입력이 250ms 멈추면 그때 검색 (디바운스)
  searchInput.addEventListener("input", function () {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(search, 250);
  });

  // 검색창에서 엔터를 쳐도 폼이 제출되지 않게
  searchInput.addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
      e.preventDefault();
      search();
    }
  });

  // "빼기" - 서버가 그려준 줄도, JS로 추가한 줄도 목록 하나에 걸어둔 리스너로 처리 (이벤트 위임)
  targetList.addEventListener("click", function (e) {
    const button = e.target.closest("[data-remove-target]");
    if (!button) return;

    button.closest(".hashtag-target-row").remove();
    refreshCount();
    renderResults(lastResults);
  });

  function refreshPeriodHint() {
    if (!startInput.value || !endInput.value) {
      periodHint.textContent = "";
      return;
    }

    const start = new Date(startInput.value);
    const end = new Date(endInput.value);
    const days = Math.round((end - start) / 86400000) + 1; // 86400000ms = 하루, 양 끝 날짜 포함
    const ok = days >= minDays && days <= maxDays;

    periodHint.textContent = ok
      ? t("periodOk", "총 {0}일 동안 진행돼요.", [days])
      : t("periodInvalid", "기간은 {0}~{1}일이어야 해요. (현재 {2}일)", [minDays, maxDays, days]);
    periodHint.style.color = ok ? "" : "#c45c26";
  }

  startInput.addEventListener("change", refreshPeriodHint);
  endInput.addEventListener("change", refreshPeriodHint);
  refreshPeriodHint();
  refreshCount();
})();