/**
 * ============================================================
 * WePlaNet – Global Icons (언어 / 알림 / 캘린더)
 * ------------------------------------------------------------
 * global_icon_shell_demo.jsx 를 기존 팬 셸(vanilla JS)에 이식한 모듈.
 *
 * 연동:
 *  - 언어(Context) 한 곳이 바뀌면 캘린더 일정·알림 문구가 같이 바뀐다.
 *  - 알림 중 스케줄 타입(라이브/티켓/콘서트)은 EVENTS 에서 파생되며,
 *    클릭하면 해당 일정 상세로 캘린더가 열린다.
 *  - 커뮤니티 미니캘린더·메인 주간 스케줄도 같은 EVENTS 를 쓴다.
 * ============================================================
 */
(function () {
  "use strict";

  var LANG_KEY = "weplanet.lang";
  var READ_KEY = "weplanet.noti.read";
  var COOKIE = "weplanet_lang";

  var LANGUAGES = [
    { code: "ko", label: "한국어" },
    { code: "en", label: "English" },
    { code: "ja", label: "日本語" },
    { code: "zh", label: "中文" },
    { code: "fr", label: "Français" },
    { code: "es", label: "Español" },
  ];

  var UI = {
    ko: {
      notificationsTitle: "알림",
      noNotifications: "새로운 알림이 없습니다.",
      calendarTitle: "Calendar",
      all: "전체",
      noEventsForDate: "등록된 일정이 없습니다.",
      ticketCta: "티켓팅 홈페이지로 이동",
      ticketImagePlaceholder: "티켓 이미지 첨부 영역",
      back: "목록",
      langChanged: function (label) { return "언어가 " + label + "(으)로 설정되었습니다"; },
      shopMoveTo: function (name) { return "이동: " + name + " 굿즈샵"; },
      myCommunities: "내 커뮤니티",
      noJoinedCommunities: "아직 가입한 커뮤니티가 없습니다.",
      login: "로그인하기",
      markAllRead: "모두 읽음",
      weekHint: "내가 가입한 모든 아티스트 커뮤니티의 스케줄 · 일자별 최대 4개",
    },
    en: {
      notificationsTitle: "Notifications",
      noNotifications: "No new notifications.",
      calendarTitle: "Calendar",
      all: "All",
      noEventsForDate: "No events on this date.",
      ticketCta: "Go to ticketing site",
      ticketImagePlaceholder: "Ticket image area",
      back: "Back",
      langChanged: function (label) { return "Language set to " + label; },
      shopMoveTo: function (name) { return "Opening: " + name + " shop"; },
      myCommunities: "My communities",
      noJoinedCommunities: "You have not joined any communities.",
      login: "Log in",
      markAllRead: "Mark all read",
      weekHint: "Schedules from every community you joined · up to 4 per day",
    },
    ja: {
      notificationsTitle: "通知",
      noNotifications: "新しい通知はありません。",
      calendarTitle: "Calendar",
      all: "すべて",
      noEventsForDate: "登録されたスケジュールはありません。",
      ticketCta: "チケットサイトへ移動",
      ticketImagePlaceholder: "チケット画像添付エリア",
      back: "一覧",
      langChanged: function (label) { return "言語が " + label + " に設定されました"; },
      shopMoveTo: function (name) { return "移動: " + name + " グッズショップ"; },
      myCommunities: "マイコミュニティ",
      noJoinedCommunities: "参加中のコミュニティはありません。",
      login: "ログイン",
      markAllRead: "すべて既読",
      weekHint: "参加中の全アーティストコミュニティのスケジュール · 1日最大4件",
    },
    zh: {
      notificationsTitle: "通知",
      noNotifications: "暂无新通知。",
      calendarTitle: "Calendar",
      all: "全部",
      noEventsForDate: "当天没有安排的日程。",
      ticketCta: "前往购票网站",
      ticketImagePlaceholder: "门票图片区域",
      back: "返回列表",
      langChanged: function (label) { return "语言已设置为 " + label; },
      shopMoveTo: function (name) { return "跳转至：" + name + " 官方商店"; },
      myCommunities: "我的社区",
      noJoinedCommunities: "暂无已加入的社区。",
      login: "登录",
      markAllRead: "全部已读",
      weekHint: "已加入的所有艺人社区日程 · 每天最多 4 条",
    },
    fr: {
      notificationsTitle: "Notifications",
      noNotifications: "Aucune nouvelle notification.",
      calendarTitle: "Calendar",
      all: "Tous",
      noEventsForDate: "Aucun événement ce jour-là.",
      ticketCta: "Aller au site de billetterie",
      back: "Retour",
      ticketImagePlaceholder: "Zone image du billet",
      langChanged: function (label) { return "Langue définie sur " + label; },
      shopMoveTo: function (name) { return "Redirection : boutique " + name; },
      myCommunities: "Mes communautés",
      noJoinedCommunities: "Vous n'avez rejoint aucune communauté.",
      login: "Se connecter",
      markAllRead: "Tout lu",
      weekHint: "Agendas de toutes vos communautés · 4 max. par jour",
    },
    es: {
      notificationsTitle: "Notificaciones",
      noNotifications: "No hay notificaciones nuevas.",
      calendarTitle: "Calendar",
      all: "Todas",
      noEventsForDate: "No hay eventos en esta fecha.",
      ticketCta: "Ir al sitio de venta de entradas",
      ticketImagePlaceholder: "Área de imagen del ticket",
      back: "Volver",
      langChanged: function (label) { return "Idioma configurado en " + label; },
      shopMoveTo: function (name) { return "Abriendo: tienda de " + name; },
      myCommunities: "Mis comunidades",
      noJoinedCommunities: "No te has unido a ninguna comunidad.",
      login: "Iniciar sesión",
      markAllRead: "Marcar leídas",
      weekHint: "Agenda de todas tus comunidades · máx. 4 por día",
    },
  };

  var MY_COMMUNITIES = [];

  var EVENT_TYPE_META = {
    tv_broadcast: {
      color: "#0f6b6b",
      icon: "📺",
      label: { ko: "TV/방송", en: "TV", ja: "TV/放送", zh: "电视/放送", fr: "TV", es: "TV" },
    },
    youtube: {
      color: "#e11d48",
      icon: "▶",
      label: { ko: "유튜브", en: "YouTube", ja: "YouTube", zh: "YouTube", fr: "YouTube", es: "YouTube" },
    },
    concert: {
      color: "#7c5cff",
      icon: "🎤",
      label: { ko: "콘서트", en: "Concert", ja: "コンサート", zh: "演唱会", fr: "Concert", es: "Concierto" },
    },
    radio: {
      color: "#1d6fd8",
      icon: "📻",
      label: { ko: "라디오", en: "Radio", ja: "ラジオ", zh: "电台", fr: "Radio", es: "Radio" },
    },
    awards: {
      color: "#c45c26",
      icon: "🏆",
      label: { ko: "시상식", en: "Awards", ja: "授賞式", zh: "颁奖", fr: "Cérémonie", es: "Premios" },
    },
    photo_magazine: {
      color: "#7c3aed",
      icon: "📷",
      label: { ko: "촬영/잡지", en: "Photo/Magazine", ja: "撮影/雑誌", zh: "拍摄/杂志", fr: "Photo/Magazine", es: "Foto/Revista" },
    },
    other: {
      color: "#5b5c6b",
      icon: "📌",
      label: { ko: "기타", en: "Other", ja: "その他", zh: "其他", fr: "Autre", es: "Otro" },
    },
    broadcast: {
      color: "#0f6b6b",
      icon: "📻",
      label: { ko: "방송", en: "Broadcast", ja: "放送", zh: "播出", fr: "Diffusion", es: "Emisión" },
    },
    live: {
      color: "#e11d48",
      icon: "🔴",
      label: { ko: "라이브", en: "Live", ja: "ライブ", zh: "直播", fr: "Live", es: "Live" },
    },
    ticket_open: {
      color: "#c45c26",
      icon: "🎫",
      label: { ko: "티켓오픈", en: "Ticket open", ja: "チケットオープン", zh: "开票", fr: "Billetterie", es: "Venta de entradas" },
    },
  };

  var WEEKDAYS = {
    ko: ["일", "월", "화", "수", "목", "금", "토"],
    en: ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"],
    ja: ["日", "月", "火", "水", "木", "金", "土"],
    zh: ["日", "一", "二", "三", "四", "五", "六"],
    fr: ["DIM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM"],
    es: ["DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"],
  };

  /* 스케줄 원본 — 에이전시 포털에서 등록한 일정을 /api/schedules 로 불러온다 */
  var EVENTS_BY_DATE = {};
  var POST_NOTIFICATIONS = [];

  var EXTRA_NOTIFICATIONS = [];

  var NOTI_ICON = {
    live_start: "🔴",
    ticket_d1: "🎫",
    ticket_h1: "🎫",
    post: "📝",
    media: "🎬",
    concert_d1: "📣",
    concert_day: "🎤",
    broadcast: "📻",
  };

  var NOTI_TITLE = {
    live_start: { ko: "라이브 시작", en: "Live started", ja: "ライブ開始", zh: "直播开始", fr: "Live commencé", es: "Live iniciado" },
    ticket_d1: { ko: "티켓팅 하루 전 알림", en: "Ticketing tomorrow", ja: "チケッティング前日通知", zh: "开票前一天提醒", fr: "Billetterie demain", es: "Venta de entradas mañana" },
    concert_day: { ko: "콘서트 당일 알림", en: "Concert day", ja: "コンサート当日通知", zh: "演出当天提醒", fr: "Jour du concert", es: "Día del concierto" },
    broadcast: { ko: "방송 알림", en: "On air", ja: "放送通知", zh: "播出提醒", fr: "Diffusion", es: "En antena" },
  };

  /* ---------- helpers ---------- */
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function communityName(id) {
    var found = MY_COMMUNITIES.filter(function (c) { return c.id === id; })[0];
    return found ? found.name : id;
  }

  function parseYmd(str) {
    var p = (str || "").split("-");
    return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  }

  function fmtDate(y, m, d) {
    return y + "-" + String(m + 1).padStart(2, "0") + "-" + String(d).padStart(2, "0");
  }

  function validLang(code) {
    return LANGUAGES.some(function (l) { return l.code === code; }) ? code : "ko";
  }

  function getLang() {
    return validLang(localStorage.getItem(LANG_KEY) || "ko");
  }

  function t() {
    return UI[getLang()] || UI.ko;
  }

  function tr(obj) {
    if (!obj) return "";
    if (typeof obj === "string") return obj;
    var lang = getLang();
    return obj[lang] || obj.ko || obj.en || "";
  }

  function weekDays() {
    return WEEKDAYS[getLang()] || WEEKDAYS.en;
  }

  function setCookie(code) {
    document.cookie = COOKIE + "=" + encodeURIComponent(code) + ";path=/;max-age=31536000;SameSite=Lax";
  }

  function persistLang(code) {
    localStorage.setItem(LANG_KEY, code);
    setCookie(code);
    document.documentElement.setAttribute("lang", code === "zh" ? "zh-CN" : code);
  }

  function loadReadIds() {
    try {
      var raw = JSON.parse(localStorage.getItem(READ_KEY) || "[]");
      return Array.isArray(raw) ? raw : [];
    } catch (e) {
      return [];
    }
  }

  function saveReadIds(ids) {
    localStorage.setItem(READ_KEY, JSON.stringify(ids));
  }

  function relativeTime(dateStr) {
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var d = parseYmd(dateStr);
    d.setHours(0, 0, 0, 0);
    var diff = Math.round((today - d) / 86400000);
    var table = {
      ko: { 0: "오늘", 1: "어제", n: function (n) { return n + "일 전"; }, future: function (n) { return n + "일 후"; } },
      en: { 0: "Today", 1: "Yesterday", n: function (n) { return n + " days ago"; }, future: function (n) { return "in " + n + " days"; } },
      ja: { 0: "今日", 1: "昨日", n: function (n) { return n + "日前"; }, future: function (n) { return n + "日後"; } },
      zh: { 0: "今天", 1: "昨天", n: function (n) { return n + "天前"; }, future: function (n) { return n + "天后"; } },
      fr: { 0: "Aujourd'hui", 1: "Hier", n: function (n) { return "il y a " + n + " j"; }, future: function (n) { return "dans " + n + " j"; } },
      es: { 0: "Hoy", 1: "Ayer", n: function (n) { return "hace " + n + " días"; }, future: function (n) { return "en " + n + " días"; } },
    };
    var pack = table[getLang()] || table.en;
    if (diff === 0) return pack[0];
    if (diff === 1) return pack[1];
    if (diff > 1) return pack.n(diff);
    return pack.future(-diff);
  }

  function findEventById(eventId) {
    var dates = Object.keys(EVENTS_BY_DATE);
    for (var i = 0; i < dates.length; i++) {
      var list = EVENTS_BY_DATE[dates[i]];
      for (var j = 0; j < list.length; j++) {
        if (list[j].id === eventId) return { date: dates[i], event: list[j] };
      }
    }
    return null;
  }

  function eventMessage(type, ev) {
    var title = tr(ev.title);
    var artist = communityName(ev.artist);
    var map = {
      live_start: {
        ko: artist + " 라이브가 시작됐어요. 지금 바로 참여해보세요! · " + title,
        en: artist + " just started a live. Join now! · " + title,
        ja: artist + "のライブが始まりました。今すぐ参加しましょう！ · " + title,
        zh: artist + " 开启了直播，快来参与吧！ · " + title,
        fr: artist + " vient de démarrer un live. Rejoignez maintenant ! · " + title,
        es: artist + " acaba de iniciar un live. ¡Únete ahora! · " + title,
      },
      ticket_d1: {
        ko: title + " 티켓 오픈이 " + ev.time + "에 진행됩니다. 알림을 켜두고 놓치지 마세요.",
        en: "Ticket opening for " + title + " is at " + ev.time + ". Keep notifications on so you don't miss it.",
        ja: title + " のチケットオープンは " + ev.time + " です。通知をオンにしてお見逃しなく。",
        zh: title + " 将于 " + ev.time + " 开票，请打开通知不要错过。",
        fr: "L'ouverture des billets pour " + title + " est à " + ev.time + ". Gardez les notifications activées.",
        es: "La venta de entradas para " + title + " es a las " + ev.time + ". Mantén las notificaciones activadas.",
      },
      concert_day: {
        ko: "오늘은 " + title + " 공연일입니다. 입장 시간과 좌석을 미리 확인하세요.",
        en: "Today is " + title + ". Check your entry time and seat in advance.",
        ja: "本日は " + title + " の公演日です。入場時間と座席を事前にご確認ください。",
        zh: "今天是 " + title + " 演出日，请提前确认入场时间与座位。",
        fr: "Aujourd'hui, c'est " + title + ". Vérifiez votre heure d'entrée et votre place.",
        es: "Hoy es " + title + ". Revisa con anticipación tu hora de entrada y asiento.",
      },
      broadcast: {
        ko: artist + " 방송 일정: " + title + " (" + ev.time + ")",
        en: artist + " on air: " + title + " (" + ev.time + ")",
        ja: artist + " 放送: " + title + " (" + ev.time + ")",
        zh: artist + " 播出：" + title + "（" + ev.time + "）",
        fr: artist + " à l'antenne : " + title + " (" + ev.time + ")",
        es: artist + " al aire: " + title + " (" + ev.time + ")",
      },
    };
    return map[type] || {};
  }

  function notificationsFromSchedule() {
    var items = [];
    var typeMap = {
      live: "live_start",
      ticket_open: "ticket_d1",
      concert: "concert_day",
      broadcast: "broadcast",
      tv_broadcast: "broadcast",
      youtube: "broadcast",
      radio: "broadcast",
      awards: "concert_day",
      photo_magazine: "broadcast",
      other: "broadcast",
    };
    Object.keys(EVENTS_BY_DATE).forEach(function (date) {
      EVENTS_BY_DATE[date].forEach(function (ev) {
        var nType = typeMap[ev.type];
        if (!nType) return;
        var eventDate = parseYmd(date);
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        eventDate.setHours(0, 0, 0, 0);
        var age = Math.round((today - eventDate) / 86400000);
        items.push({
          id: "ev-" + ev.id,
          type: nType,
          eventId: ev.id,
          artistId: String(ev.artist),
          artistName: communityName(ev.artist),
          artistLogo: artistLogo(communityName(ev.artist)),
          date: date,
          time: date,
          read: age > 7,
          category: (EVENT_TYPE_META[ev.type] || EVENT_TYPE_META.broadcast).label,
          title: ev.title,
          message: eventMessage(nType, ev),
          _sort: date + "T" + (ev.time || "00:00"),
        });
      });
    });
    return items.sort(function (a, b) { return a._sort < b._sort ? 1 : -1; });
  }

  function allNotifications() {
    var readIds = loadReadIds();
    var merged = POST_NOTIFICATIONS.concat(notificationsFromSchedule()).concat(EXTRA_NOTIFICATIONS);
    return merged.map(function (n) {
      var copy = Object.assign({}, n);
      if (readIds.indexOf(n.id) !== -1) copy.read = true;
      return copy;
    });
  }

  function artistLogo(name) {
    var value = String(name || "?").trim();
    return value.length >= 2 ? value.substring(0, 2).toUpperCase() : value.toUpperCase();
  }

  function isCommunityPage() {
    return !!document.querySelector(".community-top__name");
  }

  function isAuthenticatedPage() {
    var body = document.body;
    var value = body && body.getAttribute("data-authenticated");
    if (value !== null) return value === "true";
    return !!document.querySelector('form[action="/logout"], form[action$="/logout"]');
  }

  function notificationCommunityId() {
    return isCommunityPage() ? detectCommunityId() : state.notificationCommunity;
  }

  function notificationsForPanel() {
    var target = notificationCommunityId();
    return allNotifications().filter(function (notification) {
      return target === "all" || String(notification.artistId || notification.artist) === String(target);
    });
  }

  function notificationCategory(notification) {
    if (notification.category) return tr(notification.category);
    var fallback = {
      post: { ko: "게시글", en: "Post", ja: "投稿", zh: "帖子", fr: "Post", es: "Publicación" },
      media: { ko: "미디어", en: "Media", ja: "メディア", zh: "媒体", fr: "Média", es: "Medios" },
    };
    return tr(fallback[notification.type] || notification.type);
  }

  function apiUrlForPage(path) {
    var communityId = isCommunityPage() ? detectCommunityId() : "all";
    return communityId === "all" ? path : path + "?artistId=" + encodeURIComponent(communityId);
  }

  function detectCommunityId() {
    var nameEl = document.querySelector(".community-top__name");
    var attrId = nameEl && nameEl.getAttribute("data-artist-id");
    if (attrId) return String(attrId);
    var name = (nameEl && nameEl.textContent || "").trim();
    var found = MY_COMMUNITIES.filter(function (c) { return c.name === name; })[0];
    if (found) return found.id;
    var artists = window.__WEPLANET_ARTISTS__ || [];
    for (var i = 0; i < artists.length; i++) {
      if ((artists[i].nickname || "") === name) return String(artists[i].id);
    }
    return "all";
  }

  function communitiesFromPage() {
    var artists = window.__WEPLANET_ARTISTS__ || [];
    return artists.map(function (a) {
      return { id: String(a.id), name: a.nickname || a.name || ("Artist " + a.id) };
    });
  }

  function applySchedulePayload(data) {
    if (data && Array.isArray(data.communities)) {
      MY_COMMUNITIES = data.communities;
    } else {
      var fromPage = communitiesFromPage();
      if (fromPage.length) MY_COMMUNITIES = fromPage;
    }
    EVENTS_BY_DATE = (data && data.eventsByDate) ? data.eventsByDate : {};
    var now = new Date();
    state.cursor = new Date(now.getFullYear(), now.getMonth(), 1);
    state.weekStart = now;
    state.selected = fmtDate(now.getFullYear(), now.getMonth(), now.getDate());
    renderNotiPanel();
    updateNotiBadges();
    hydrateMiniCal();
    hydrateWeekGrid();
    if (state.calendarOpen) renderCalendar();
  }

  function loadSchedulesFromApi() {
    if (!isAuthenticatedPage()) {
      MY_COMMUNITIES = [];
      EVENTS_BY_DATE = {};
      hydrateMiniCal();
      hydrateWeekGrid();
      return Promise.resolve();
    }
    return fetch(apiUrlForPage("/api/schedules"), { headers: { Accept: "application/json" } })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        applySchedulePayload(data || {});
      })
      .catch(function () {
        applySchedulePayload({ communities: communitiesFromPage(), eventsByDate: {} });
      });
  }

  function loadPostNotifications() {
    if (!isAuthenticatedPage()) {
      POST_NOTIFICATIONS = [];
      return Promise.resolve();
    }
    return fetch(apiUrlForPage("/api/notifications"), { headers: { Accept: "application/json" } })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        POST_NOTIFICATIONS = data && Array.isArray(data.posts)
          ? data.posts.map(function (post) {
              var created = parseYmd(post.date);
              created.setHours(0, 0, 0, 0);
              var age = Math.round((today - created) / 86400000);
              return Object.assign({}, post, { read: age > 7 });
            })
          : [];
        renderNotiPanel();
        updateNotiBadges();
      })
      .catch(function () {
        POST_NOTIFICATIONS = [];
      });
  }

  function shopBase() {
    return document.body.getAttribute("data-base") || "";
  }

  /* ---------- state ---------- */
  var nowInit = new Date();
  var state = {
    calendarOpen: false,
    cursor: new Date(nowInit.getFullYear(), nowInit.getMonth(), 1),
    selected: fmtDate(nowInit.getFullYear(), nowInit.getMonth(), nowInit.getDate()),
    community: "all",
    notificationCommunity: "all",
    detail: null,
    expandedId: null,
    weekStart: nowInit,
  };

  /* ---------- toast ---------- */
  var toastTimer = null;
  function showToast(msg) {
    var el = document.getElementById("wpGlobalToast");
    if (!el) return;
    el.textContent = msg;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      el.hidden = true;
    }, 2200);
  }

  /* ---------- language ---------- */
  function setLang(code, opts) {
    code = validLang(code);
    persistLang(code);
    if (!(opts && opts.silent)) {
      var label = (LANGUAGES.filter(function (l) { return l.code === code; })[0] || {}).label || code;
      showToast((UI[code] || UI.ko).langChanged(label));
    }
    closeMenus();
    renderLangMenu();
    renderNotiPanel();
    updateNotiBadges();
    if (state.calendarOpen) renderCalendar();
    hydrateMiniCal();
    hydrateWeekGrid();
    syncSettingsSelect();
    document.dispatchEvent(new CustomEvent("weplanet:langchange", { detail: { lang: code } }));
  }

  /* ---------- inject root ---------- */
  function injectRoot() {
    if (document.getElementById("weplanet-global-icons")) return;
    var wrap = document.createElement("div");
    wrap.id = "weplanet-global-icons";
    wrap.innerHTML =
      '<div class="wp-cal-backdrop" id="wpCalBackdrop" hidden>' +
        '<div class="wp-cal-modal" id="wpCalModal" role="dialog" aria-modal="true" aria-labelledby="wpCalTitle"></div>' +
      "</div>" +
      '<div class="wp-toast" id="wpGlobalToast" hidden></div>';
    document.body.appendChild(wrap);

    document.getElementById("wpCalBackdrop").addEventListener("click", function (e) {
      // 배경만 닫기 — 모달 내부 클릭은 버블되어 날짜/일정 선택이 동작해야 함
      if (e.target.id === "wpCalBackdrop") closeCalendar();
    });
  }

  function closeMenus() {
    document.querySelectorAll(".wp-lang-menu, .wp-noti-panel").forEach(function (el) {
      el.hidden = true;
    });
    document.querySelectorAll(".wp-global-slot .icon-btn.is-open").forEach(function (btn) {
      btn.classList.remove("is-open");
    });
  }

  function wrapSlot(btn, kind) {
    if (btn.closest(".wp-global-slot")) return btn.closest(".wp-global-slot");
    var slot = document.createElement("div");
    slot.className = "wp-global-slot wp-global-slot--" + kind;
    btn.parentNode.insertBefore(slot, btn);
    slot.appendChild(btn);
    return slot;
  }

  function isLangBtn(btn) {
    if (!btn || btn.tagName === "A") return false;
    var label = (btn.getAttribute("aria-label") || "").trim();
    var text = (btn.textContent || "").trim();
    return label === "언어" || text.indexOf("🌐") !== -1;
  }

  function isNotiBtn(btn) {
    if (!btn || btn.tagName === "A") return false;
    var label = (btn.getAttribute("aria-label") || "").trim();
    var text = (btn.textContent || "").trim();
    return label === "알림" || text.indexOf("🔔") !== -1;
  }

  function isSearchBtn(btn) {
    if (!btn) return false;
    var label = (btn.getAttribute("aria-label") || "").trim();
    var title = (btn.getAttribute("title") || "").trim();
    return label.indexOf("검색") !== -1 || title.indexOf("검색") !== -1;
  }

  var HEADER_ICONS = {
    search: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>',
    notification: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>',
    language: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>',
  };

  function headerIconButtons() {
    return Array.prototype.slice.call(
      document.querySelectorAll(".header-actions .icon-btn, .community-top__right .icon-btn")
    );
  }

  function ensureHeaderIcons() {
    var actions = document.querySelector(".community-top__right") || document.querySelector(".header-actions");
    if (!actions) return;

    var icons = headerIconButtons();
    var searchBtn = icons.filter(isSearchBtn)[0] || null;
    var hasLang = icons.some(isLangBtn);
    var hasNoti = icons.some(isNotiBtn);

    var anchor = actions.querySelector("a.btn, form, span[sec\\:authorize]") || null;

    function insert(btn) {
      if (anchor && anchor.parentElement === actions) actions.insertBefore(btn, anchor);
      else actions.appendChild(btn);
    }

    if (!searchBtn) {
      searchBtn = document.createElement("a");
      searchBtn.className = "icon-btn";
      searchBtn.href = "/?openSearch=1";
      searchBtn.setAttribute("aria-label", "커뮤니티 검색");
      searchBtn.setAttribute("title", "커뮤니티 검색");
      searchBtn.innerHTML = HEADER_ICONS.search;
      insert(searchBtn);
    } else {
      searchBtn.innerHTML = HEADER_ICONS.search;
    }

    var langBtn = icons.filter(isLangBtn)[0] || null;
    if (!hasLang) {
      langBtn = document.createElement("button");
      langBtn.type = "button";
      langBtn.className = "icon-btn";
      langBtn.setAttribute("aria-label", "언어");
      langBtn.innerHTML = HEADER_ICONS.language;
      insert(langBtn);
    } else {
      langBtn.innerHTML = HEADER_ICONS.language;
    }
    var notiBtn = icons.filter(isNotiBtn)[0] || null;
    if (!hasNoti) {
      notiBtn = document.createElement("button");
      notiBtn.type = "button";
      notiBtn.className = "icon-btn icon-btn--badge";
      notiBtn.setAttribute("aria-label", "알림");
      notiBtn.innerHTML = HEADER_ICONS.notification;
      insert(notiBtn);
    } else {
      notiBtn.innerHTML = HEADER_ICONS.notification;
    }

    actions.insertBefore(searchBtn, actions.firstElementChild);
    actions.insertBefore(notiBtn, searchBtn.nextSibling);
    actions.insertBefore(langBtn, notiBtn.nextSibling);
  }

  function bindHeaderIcons() {
    headerIconButtons().forEach(function (btn) {
      if (isLangBtn(btn)) bindLangButton(btn);
      if (isNotiBtn(btn)) bindNotiButton(btn);
    });
  }

  function bindLangButton(btn) {
    if (btn.dataset.wpBound === "lang") return;
    btn.dataset.wpBound = "lang";
    btn.setAttribute("aria-label", "언어");
    btn.removeAttribute("onclick");
    var slot = wrapSlot(btn, "lang");
    if (!slot.querySelector(".wp-lang-menu")) {
      var menu = document.createElement("div");
      menu.className = "wp-lang-menu";
      menu.hidden = true;
      slot.appendChild(menu);
    }
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      var menuEl = slot.querySelector(".wp-lang-menu");
      var willOpen = menuEl.hidden;
      closeMenus();
      if (willOpen) {
        renderLangMenu();
        menuEl.hidden = false;
        btn.classList.add("is-open");
      }
    });
  }

  function bindNotiButton(btn) {
    if (btn.dataset.wpBound === "noti") return;
    btn.dataset.wpBound = "noti";
    btn.setAttribute("aria-label", "알림");
    btn.classList.add("icon-btn--badge", "wp-noti-bound");
    btn.removeAttribute("onclick");
    btn.onclick = null;
    var slot = wrapSlot(btn, "noti");
    if (!slot.querySelector(".wp-noti-count")) {
      var badge = document.createElement("span");
      badge.className = "wp-noti-count";
      slot.appendChild(badge);
    }
    if (!slot.querySelector(".wp-noti-panel")) {
      var panel = document.createElement("div");
      panel.className = "wp-noti-panel";
      panel.hidden = true;
      slot.appendChild(panel);
    }
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      var panelEl = slot.querySelector(".wp-noti-panel");
      var willOpen = panelEl.hidden;
      closeMenus();
      if (willOpen) {
        renderNotiPanel();
        panelEl.hidden = false;
        btn.classList.add("is-open");
      }
    });
  }

  function renderLangMenu() {
    var lang = getLang();
    document.querySelectorAll(".wp-lang-menu").forEach(function (menu) {
      menu.innerHTML = LANGUAGES.map(function (l) {
        return (
          '<button type="button" class="wp-lang-menu__item' + (l.code === lang ? " is-active" : "") + '" data-lang="' + esc(l.code) + '">' +
            "<span>" + esc(l.label) + "</span>" +
            (l.code === lang ? '<span class="wp-lang-menu__check">✔</span>' : "") +
          "</button>"
        );
      }).join("");
    });
  }

  function renderNotiPanel() {
    var ui = t();
    var guest = !isAuthenticatedPage();
    var list = notificationsForPanel();
    var filters = guest || isCommunityPage()
      ? ""
      : '<div class="wp-noti-filter" role="group" aria-label="커뮤니티 선택">' +
          [{ id: "all", name: ui.all }].concat(MY_COMMUNITIES).map(function (community) {
            return '<button type="button" class="wp-noti-filter__chip' +
              (state.notificationCommunity === community.id ? " is-active" : "") +
              '" data-noti-community="' + esc(community.id) + '">' +
              esc(community.name) +
            "</button>";
          }).join("") +
        "</div>";
    document.querySelectorAll(".wp-noti-panel").forEach(function (panel) {
      var body = guest
        ? loginPrompt()
        : list.length
        ? list.map(function (n) {
            var artistName = n.artistName || communityName(n.artistId || n.artist);
            var artistAvatar = n.artistLogo || artistLogo(artistName);
            var timeLabel = n.date ? relativeTime(n.date) : tr(n.time);
            var target = n.postUrl
              ? ' data-noti-url="' + esc(n.postUrl) + '"'
              : n.eventId
              ? ' data-noti-event="' + esc(n.eventId) + '"'
              : "";
            var artistLine = isCommunityPage()
              ? ""
              : '<span class="wp-noti-item__artist">' +
                  '<span class="avatar avatar--sm">' + esc(artistAvatar) + "</span>" +
                  "<span>" + esc(artistName) + "</span>" +
                "</span>";
            return (
              '<div class="wp-noti-item' + (n.read ? " is-read" : "") + '" data-noti-id="' + esc(n.id) + '">' +
                '<button type="button" class="wp-noti-item__head" data-noti-toggle="' + esc(n.id) + '"' + target + '>' +
                  artistLine +
                  '<span class="wp-noti-item__line">' +
                    '<span class="wp-noti-item__category">' + esc(notificationCategory(n)) + "</span>" +
                    '<strong class="wp-noti-item__title">' + esc(tr(n.title)) + "</strong>" +
                    (n.read ? "" : '<span class="wp-noti-item__dot"></span>') +
                  "</span>" +
                  '<span class="wp-noti-item__time">' + esc(timeLabel) + "</span>" +
                "</button>" +
              "</div>"
            );
          }).join("")
        : '<div class="wp-noti-empty">' + esc(ui.noNotifications) + "</div>";

      panel.innerHTML =
        '<div class="wp-noti-panel__head">' +
          "<strong>" + esc(ui.notificationsTitle) + "</strong>" +
          (guest ? "" : '<button type="button" class="wp-noti-panel__readall" data-noti-readall>' + esc(ui.markAllRead) + "</button>") +
        "</div>" +
        filters +
        '<div class="wp-noti-panel__list">' + body + "</div>";
    });
  }

  function updateNotiBadges() {
    var unread = allNotifications().filter(function (n) { return !n.read; }).length;
    document.querySelectorAll(".wp-noti-count").forEach(function (badge) {
      badge.textContent = unread > 9 ? "9+" : String(unread);
      badge.hidden = unread === 0;
    });
    document.querySelectorAll(".wp-noti-bound").forEach(function (btn) {
      btn.classList.toggle("has-unread", unread > 0);
    });
  }

  function markRead(id) {
    var ids = loadReadIds();
    if (ids.indexOf(id) === -1) {
      ids.push(id);
      saveReadIds(ids);
    }
  }

  function markAllRead() {
    saveReadIds(loadReadIds().concat(notificationsForPanel().map(function (n) { return n.id; })));
    renderNotiPanel();
    updateNotiBadges();
  }

  /* ---------- calendar ---------- */
  function getDayEvents(dateStr) {
    var all = EVENTS_BY_DATE[dateStr] || [];
    if (state.community === "all") return all;
    return all.filter(function (e) { return e.artist === state.community; });
  }

  function openCalendar(opts) {
    opts = opts || {};
    closeMenus();
    if (opts.eventId) {
      var found = findEventById(opts.eventId);
      if (found) {
        state.selected = found.date;
        state.cursor = parseYmd(found.date);
        state.detail = found.event;
        state.community = found.event.artist;
      }
    } else if (opts.date) {
      state.selected = opts.date;
      state.cursor = parseYmd(opts.date);
      state.detail = null;
      state.community = detectCommunityId();
    } else {
      state.community = detectCommunityId();
      state.detail = null;
    }
    state.calendarOpen = true;
    var backdrop = document.getElementById("wpCalBackdrop");
    backdrop.hidden = false;
    backdrop.classList.add("is-open");
    renderCalendar();
  }

  function closeCalendar() {
    state.calendarOpen = false;
    state.detail = null;
    var backdrop = document.getElementById("wpCalBackdrop");
    if (!backdrop) return;
    backdrop.hidden = true;
    backdrop.classList.remove("is-open");
  }

  function loginPrompt() {
    var ui = t();
    return '<div class="wp-auth-prompt">' +
      '<strong>' + esc(ui.noJoinedCommunities) + "</strong>" +
      '<a class="btn btn--primary btn--sm" href="/login">' + esc(ui.login) + "</a>" +
    "</div>";
  }

  function renderCalendar() {
    var modal = document.getElementById("wpCalModal");
    if (!modal) return;
    if (!isAuthenticatedPage()) {
      modal.innerHTML = loginPrompt();
      return;
    }
    var ui = t();
    if (state.detail) {
      modal.innerHTML = renderEventDetail(state.detail, state.selected);
      return;
    }
    var y = state.cursor.getFullYear();
    var m = state.cursor.getMonth();
    var firstDow = new Date(y, m, 1).getDay();
    var daysInMonth = new Date(y, m + 1, 0).getDate();
    var cells = [];
    var i;
    for (i = 0; i < firstDow; i++) cells.push(null);
    for (i = 1; i <= daysInMonth; i++) cells.push(i);
    var days = weekDays();
    var events = getDayEvents(state.selected);
    var selectedDow = parseYmd(state.selected).getDay();

    var chips = [{ id: "all", name: ui.all }].concat(MY_COMMUNITIES).map(function (c) {
      var active = state.community === c.id;
      return (
        '<button type="button" class="wp-cal-chip' + (active ? " is-active" : "") + '" data-cal-community="' + esc(c.id) + '">' +
          esc(c.name) +
        "</button>"
      );
    }).join("");

    var gridDays = days.map(function (w) {
      return '<div class="wp-cal-dow">' + esc(w) + "</div>";
    }).join("");

    var gridCells = cells.map(function (d, idx) {
      if (!d) return "<div></div>";
      var dateStr = fmtDate(y, m, d);
      var dayEvents = getDayEvents(dateStr);
      var selected = dateStr === state.selected;
      var dots = dayEvents.slice(0, 3).map(function (ev) {
        var meta = EVENT_TYPE_META[ev.type] || EVENT_TYPE_META.broadcast;
        return '<span class="wp-cal-dot" style="background:' + meta.color + '"></span>';
      }).join("");
      return (
        '<button type="button" class="wp-cal-day" data-cal-date="' + dateStr + '">' +
          '<span class="wp-cal-day__num' + (selected ? " is-selected" : "") + '">' + d + "</span>" +
          '<span class="wp-cal-day__dots">' + dots + "</span>" +
        "</button>"
      );
    }).join("");

    var list = events.length
      ? events.map(function (ev) {
          var meta = EVENT_TYPE_META[ev.type] || EVENT_TYPE_META.broadcast;
          return (
            '<button type="button" class="wp-cal-event" data-cal-event="' + esc(ev.id) + '">' +
              '<span class="wp-cal-dot" style="background:' + meta.color + '"></span>' +
              "<span>" + esc(tr(ev.title)) + "</span>" +
              "<span>›</span>" +
            "</button>"
          );
        }).join("")
      : '<div class="wp-cal-empty">' + esc(ui.noEventsForDate) + "</div>";

    modal.innerHTML =
      '<div class="wp-cal-head">' +
        '<strong id="wpCalTitle">' + esc(ui.calendarTitle) + "</strong>" +
        '<button type="button" class="icon-btn" data-cal-close aria-label="close">✕</button>' +
      "</div>" +
      '<div class="wp-cal-label">' + esc(ui.myCommunities) + "</div>" +
      '<div class="wp-cal-chips">' + chips + "</div>" +
      '<div class="wp-cal-nav">' +
        '<button type="button" class="icon-btn" data-cal-prev aria-label="prev">◀</button>' +
        "<span>" + y + " · " + String(m + 1).padStart(2, "0") + "</span>" +
        '<button type="button" class="icon-btn" data-cal-next aria-label="next">▶</button>' +
      "</div>" +
      '<div class="wp-cal-grid">' + gridDays + gridCells + "</div>" +
      '<div class="wp-cal-list">' +
        '<div class="wp-cal-list__date">' + esc(state.selected) + " (" + esc(days[selectedDow]) + ")</div>" +
        list +
      "</div>";
  }

  function renderEventDetail(event, date) {
    var ui = t();
    var meta = EVENT_TYPE_META[event.type] || EVENT_TYPE_META.broadcast;
    var ticket = event.hasTicketImage
      ? '<div class="wp-cal-ticket">' +
          "<div>🎫</div><div>" + esc(ui.ticketImagePlaceholder) + "</div>" +
        "</div>"
      : "";
    var link = event.link
      ? '<a class="btn btn--accent btn--block" href="' + esc(event.link) + '" target="_blank" rel="noreferrer">' +
          esc(ui.ticketCta) + " ↗</a>"
      : "";
    return (
      '<div class="wp-cal-head">' +
        '<button type="button" class="wp-cal-back" data-cal-back>◀ ' + esc(ui.back) + "</button>" +
        '<button type="button" class="icon-btn" data-cal-close aria-label="close">✕</button>' +
      "</div>" +
      '<span class="wp-cal-type" style="background:' + meta.color + "22;color:" + meta.color + '">' +
        esc(tr(meta.label)) +
      "</span>" +
      "<h3 class=\"wp-cal-detail-title\">" + esc(tr(event.title)) + "</h3>" +
      '<div class="wp-cal-detail-row">🕐 <span>' + esc(date) + " · " + esc(event.time) + "</span></div>" +
      '<div class="wp-cal-detail-row">📍 <span>' + esc(event.place) + "</span></div>" +
      '<div class="wp-cal-detail-row">🏷 <span>' + esc(communityName(event.artist)) + "</span></div>" +
      ticket +
      link
    );
  }

  function bindCalendarFab() {
    var fab = document.querySelector('.fab-stack [aria-label="캘린더"], .fab-stack [title="캘린더"], [data-shell-open="calendar"]');
    if (!fab || fab.dataset.wpBound === "cal") return;
    fab.dataset.wpBound = "cal";
    fab.removeAttribute("data-shell-alert");
    fab.setAttribute("data-shell-open", "calendar");
    fab.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      openCalendar();
    });
  }

  /* ---------- highlight mini-cal / home week grid (same EVENTS) ---------- */
  function hydrateMiniCal() {
    var grid = document.querySelector(".mini-cal__grid");
    if (!grid) return;
    var nav = document.querySelector(".mini-cal__nav span");
    var y = state.cursor.getFullYear();
    var m = state.cursor.getMonth();
    if (nav) nav.textContent = y + " - " + String(m + 1).padStart(2, "0");

    var firstDow = new Date(y, m, 1).getDay();
    var daysInMonth = new Date(y, m + 1, 0).getDate();
    var days = weekDays();
    var html = days.map(function (w) {
      return '<span class="dow">' + esc(w.charAt(0)) + "</span>";
    }).join("");
    var i;
    for (i = 0; i < firstDow; i++) html += '<span class="day"></span>';
    var community = detectCommunityId();
    for (i = 1; i <= daysInMonth; i++) {
      var dateStr = fmtDate(y, m, i);
      var evs = (EVENTS_BY_DATE[dateStr] || []).filter(function (e) {
        return community === "all" || e.artist === community;
      });
      var attendance = (window.__ARTIST_ATTENDANCE__ || {})[dateStr];
      var cls = "day";
      if (evs.length) cls += " has-event";
      if (attendance) cls += " has-paw";
      if (dateStr === state.selected) cls += " is-today";
      var pawHtml = attendance
        ? '<span class="paw-stamp" style="color:' + esc(attendance) + '" title="아티스트 출석">🐾</span>'
        : "";
      html +=
        '<button type="button" class="' + cls + '" data-open-date="' + dateStr + '">' +
          i + pawHtml +
        "</button>";
    }
    grid.innerHTML = html;

    var list = document.querySelector(".cal-event-list");
    if (list) {
      var shown = getFiltered(state.selected, community);
      var daysArr = weekDays();
      list.innerHTML = shown.length
        ? shown.map(function (ev) {
            return "<li><strong>" + esc(state.selected) + " (" + esc(daysArr[parseYmd(state.selected).getDay()]) +
              ")</strong><br />" + esc(tr(ev.title)) + "</li>";
          }).join("")
        : "<li>" + esc(t().noEventsForDate) + "</li>";
    }

    var widget = grid.closest(".widget");
    if (widget && !widget.dataset.wpCalBound) {
      widget.dataset.wpCalBound = "1";
      widget.addEventListener("click", function (e) {
        if (e.target.closest(".widget__title")) {
          openCalendar();
          return;
        }
        if (e.target.closest(".mini-cal__nav") && e.target.tagName === "BUTTON") {
          var buttons = widget.querySelectorAll(".mini-cal__nav button");
          if (e.target === buttons[0]) {
            state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() - 1, 1);
            hydrateMiniCal();
            return;
          }
          if (e.target === buttons[1]) {
            state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + 1, 1);
            hydrateMiniCal();
            return;
          }
        }
        var day = e.target.closest("[data-open-date]");
        if (day) {
          openCalendar({ date: day.getAttribute("data-open-date") });
        }
      });
    }
  }

  function getFiltered(dateStr, community) {
    var all = EVENTS_BY_DATE[dateStr] || [];
    if (!community || community === "all") return all;
    return all.filter(function (e) { return e.artist === community; });
  }

  function mondayOf(date) {
    var d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    var day = d.getDay();
    var diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    return d;
  }

  function hydrateWeekGrid() {
    var grid = document.querySelector(".week-grid");
    if (!grid) return;
    if (!isAuthenticatedPage()) {
      grid.innerHTML = loginPrompt();
      return;
    }
    var start = mondayOf(state.weekStart);
    var days = weekDays();
    var ui = t();
    var hint = document.querySelector(".week-grid") && document.querySelector(".week-grid").previousElementSibling;
    if (hint && hint.classList.contains("text-xs")) hint.textContent = ui.weekHint;

    var navStrong = document.querySelector(".week-nav strong");
    var end = new Date(start);
    end.setDate(start.getDate() + 6);
    if (navStrong) {
      navStrong.textContent =
        start.getMonth() + 1 + "/" + start.getDate() + " ~ " + (end.getMonth() + 1) + "/" + end.getDate();
    }

    var html = "";
    for (var i = 0; i < 7; i++) {
      var d = new Date(start);
      d.setDate(start.getDate() + i);
      var dateStr = fmtDate(d.getFullYear(), d.getMonth(), d.getDate());
      var evs = (EVENTS_BY_DATE[dateStr] || []).slice(0, 4);
      var yy = String(d.getFullYear()).slice(2);
      html +=
        '<div class="week-day" data-open-date="' + dateStr + '">' +
          '<div class="week-day__label">' + yy + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" +
            String(d.getDate()).padStart(2, "0") + " (" + esc(days[d.getDay()]) + ")</div>" +
          (evs.length
            ? evs.map(function (ev, idx) {
                return '<div class="week-event' + (idx % 2 ? " week-event--alt" : "") + '" data-open-event="' + esc(ev.id) + '">' +
                  esc(tr(ev.title)) + "</div>";
              }).join("")
            : "") +
        "</div>";
    }
    grid.innerHTML = html;

    if (!grid.dataset.wpWeekBound) {
      grid.dataset.wpWeekBound = "1";
      grid.addEventListener("click", function (e) {
        var evBtn = e.target.closest("[data-open-event]");
        if (evBtn) {
          openCalendar({ eventId: evBtn.getAttribute("data-open-event") });
          return;
        }
        var day = e.target.closest("[data-open-date]");
        if (day) openCalendar({ date: day.getAttribute("data-open-date") });
      });
    }

    var weekNav = document.querySelector(".week-nav");
    if (weekNav && !weekNav.dataset.wpWeekBound) {
      weekNav.dataset.wpWeekBound = "1";
      var buttons = weekNav.querySelectorAll("button");
      if (buttons[0]) {
        buttons[0].addEventListener("click", function () {
          var cur = mondayOf(state.weekStart);
          state.weekStart = new Date(cur.getFullYear(), cur.getMonth(), cur.getDate() - 7);
          hydrateWeekGrid();
        });
      }
      if (buttons[1]) {
        buttons[1].addEventListener("click", function () {
          var cur = mondayOf(state.weekStart);
          state.weekStart = new Date(cur.getFullYear(), cur.getMonth(), cur.getDate() + 7);
          hydrateWeekGrid();
        });
      }
    }
  }

  /* ---------- settings page ---------- */
  function syncSettingsSelect() {
    var sel = document.getElementById("wpServiceLang");
    if (!sel) return;
    sel.value = getLang();
  }

  function enhanceSettingsLang() {
    var sel = document.getElementById("wpServiceLang");
    if (!sel) {
      var block = Array.prototype.slice.call(document.querySelectorAll(".settings-block")).filter(function (b) {
        var h = b.querySelector(".settings-block__title");
        return h && /언어/.test(h.textContent || "");
      })[0];
      sel = block && block.querySelector("select");
      if (sel) sel.id = "wpServiceLang";
    }
    if (!sel) return;
    sel.innerHTML = LANGUAGES.map(function (l) {
      return '<option value="' + esc(l.code) + '">' + esc(l.label) + "</option>";
    }).join("");
    sel.value = getLang();
    sel.addEventListener("change", function () {
      setLang(sel.value);
    });
  }

  /* ---------- shop on community (toast, existing <a>는 그대로) ---------- */
  function bindShopButtons() {
    document.querySelectorAll('.community-top__right a[aria-label="Shop"], .community-top__right a[title="WePlaNet Shop"]').forEach(function (a) {
      if (a.dataset.wpShopBound) return;
      a.dataset.wpShopBound = "1";
      a.addEventListener("click", function () {
        var id = detectCommunityId();
        var name = id === "all" ? "WePlaNet" : communityName(id);
        showToast(t().shopMoveTo(name));
      });
    });
  }

  /* ---------- global clicks / keys ---------- */
  function onDocClick(e) {
    if (e.target.closest(".wp-global-slot")) {
      var langBtn = e.target.closest("[data-lang]");
      if (langBtn) {
        setLang(langBtn.getAttribute("data-lang"));
        return;
      }
      var notiCommunity = e.target.closest("[data-noti-community]");
      if (notiCommunity) {
        state.notificationCommunity = notiCommunity.getAttribute("data-noti-community");
        renderNotiPanel();
        updateNotiBadges();
        return;
      }
      var toggle = e.target.closest("[data-noti-toggle]");
      if (toggle) {
        var nid = toggle.getAttribute("data-noti-toggle");
        markRead(nid);
        var postUrl = toggle.getAttribute("data-noti-url");
        if (postUrl) {
          window.location.href = postUrl;
          return;
        }
        var eventId = toggle.getAttribute("data-noti-event");
        if (eventId) {
          openCalendar({ eventId: eventId });
          return;
        }
        renderNotiPanel();
        updateNotiBadges();
        return;
      }
      if (e.target.closest("[data-noti-readall]")) {
        markAllRead();
        return;
      }
      var openEv = e.target.closest("[data-open-event]");
      if (openEv) {
        openCalendar({ eventId: openEv.getAttribute("data-open-event") });
        return;
      }
      return;
    }

    if (e.target.closest("[data-cal-close]")) { closeCalendar(); return; }
    if (e.target.closest("[data-cal-back]")) { state.detail = null; renderCalendar(); return; }
    if (e.target.closest("[data-cal-prev]")) {
      state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() - 1, 1);
      renderCalendar();
      return;
    }
    if (e.target.closest("[data-cal-next]")) {
      state.cursor = new Date(state.cursor.getFullYear(), state.cursor.getMonth() + 1, 1);
      renderCalendar();
      return;
    }
    var chip = e.target.closest("[data-cal-community]");
    if (chip) {
      state.community = chip.getAttribute("data-cal-community");
      renderCalendar();
      return;
    }
    var day = e.target.closest("[data-cal-date]");
    if (day) {
      state.selected = day.getAttribute("data-cal-date");
      renderCalendar();
      return;
    }
    var evBtn = e.target.closest("[data-cal-event]");
    if (evBtn) {
      var found = findEventById(evBtn.getAttribute("data-cal-event"));
      if (found) {
        state.detail = found.event;
        state.selected = found.date;
        renderCalendar();
      }
      return;
    }

    if (!e.target.closest(".wp-global-slot")) closeMenus();
  }

  function init() {
    persistLang(getLang());
    injectRoot();
    ensureHeaderIcons();
    bindHeaderIcons();
    bindCalendarFab();
    bindShopButtons();
    renderLangMenu();
    renderNotiPanel();
    updateNotiBadges();
    hydrateMiniCal();
    hydrateWeekGrid();
    enhanceSettingsLang();
    loadSchedulesFromApi();
    loadPostNotifications();

    document.addEventListener("click", onDocClick);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        closeMenus();
        closeCalendar();
      }
    });

    var params = new URLSearchParams(location.search);
    if (params.get("calendar") === "1") openCalendar();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
