/** DM 플로팅 위젯에 ChatController 데이터와 웹소켓을 연결한다 (shell.js 뒤에 로드). */
(function () {
    "use strict";

    const body = document.body;
    if (body.getAttribute("data-shell") !== "fan") return;

    // data-fan-id 는 로그인했을 때만 채워진다 (로그인한 내 계정 id).
    const fanIdRaw = body.getAttribute("data-fan-id");
    const fanId = fanIdRaw ? Number(fanIdRaw) : null;

    // 아티스트는 인박스 대신 자기 방으로 바로 들어간다.
    const roleName = body.getAttribute("data-role") || "";
    const isArtist = roleName === "ROLE_ARTIST" || roleName === "ROLE_ARTIST_MEMBER";

    // 아티스트 방 번호 (그룹 멤버는 서버에 활동 여부를 확인)
    let artistRoomId = roleName === "ROLE_ARTIST" ? fanId : null;

    function withArtistRoomId(callback) {
        if (artistRoomId) {
            callback(artistRoomId);
            return;
        }
        fetch("/chat/my-artist-room")
            .then(function (res) {
                return res.json();
            })
            .then(function (data) {
                artistRoomId = data.artistId;
                if (artistRoomId) {
                    callback(artistRoomId);
                }
            });
    }

    // 관리자는 DM 을 쓰지 않는다.
    if (roleName === "ROLE_ADMIN") return;

    // 문구는 WePlaNet.t 에서 꺼낸다 (없으면 한국어 기본값).
    const t = (key, ko) => (window.WePlaNet && typeof window.WePlaNet.t === "function")
        ? window.WePlaNet.t(key, ko)
        : ko;

    let stompClient = null;
    let currentArtistId = null; // 지금 열린 방의 상대 아티스트 id
    let subscriptions = [];

    function unsubscribeAll() {
        subscriptions.forEach(function (sub) {
            sub.unsubscribe();
        });
        subscriptions = [];
    }

    function wsChatUrl() {
        const proto = location.protocol === "https:" ? "wss:" : "ws:";
        return proto + "//" + location.host + "/ws-chat";
    }

    // 연결 중 요청은 기다렸다가 같은 소켓으로 처리한다 (소켓 중복 방지).
    let socketWaiters = null;

    function ensureSocket(callback) {
        if (stompClient && stompClient.connected) {
            callback();
            return;
        }
        if (socketWaiters) {
            socketWaiters.push(callback);
            return;
        }
        socketWaiters = [callback];
        const socket = new WebSocket(wsChatUrl());
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // 웹소켓 디버그 로그 끄기
        stompClient.connect({}, function () {
            const waiters = socketWaiters;
            socketWaiters = null;
            waiters.forEach(function (fn) {
                fn();
            });
        }, function (err) {
            socketWaiters = null;
            console.error('[DM] 웹소켓 연결 실패', err);
        });
    }

    function timeLabel(iso) {
        if (!iso) return "";
        return (iso.split("T")[1] || "").slice(0, 5);
    }

    // 인박스 한 줄 버튼 생성 (shell.js 와 같은 클래스 구조라 기존 클릭 처리가 그대로 동작).
    function buildItem(item) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "dm-list-item";
        btn.setAttribute("data-open-room", item.artistId);
        btn.setAttribute("data-artist-id", item.artistId);
        btn.setAttribute("data-room-expired", item.membershipExpired ? "true" : "false");
        btn.setAttribute("data-room-never-subscribed", item.neverSubscribed ? "true" : "false");

        const avatarWrap = document.createElement("div");
        avatarWrap.className = "dm-list-item__avatar";
        const avatar = document.createElement("div");
        avatar.className = "avatar";
        avatar.textContent = item.artistNickname ? item.artistNickname.slice(0, 2) : "?";
        avatarWrap.appendChild(avatar);

        const meta = document.createElement("div");
        meta.className = "dm-list-item__meta";
        const name = document.createElement("div");
        name.className = "dm-list-item__name";
        const nameText = document.createElement("span");
        // 그룹 멤버면 그룹 이름을 함께 보여준다.
        nameText.textContent = (item.artistNickname || "") + (item.groupName ? " · " + item.groupName : "");
        const badge = document.createElement("span");
        badge.className = "badge-verified";
        badge.textContent = "✓";
        name.appendChild(nameText);
        name.appendChild(badge);
        const preview = document.createElement("div");
        preview.className = item.hasConversation ? "dm-list-item__preview" : "dm-list-item__group";
        preview.textContent = item.hasConversation ? (item.lastMessage || "") : "";
        meta.appendChild(name);
        meta.appendChild(preview);

        const time = document.createElement("span");
        time.className = "dm-list-item__time";
        time.textContent = item.hasConversation ? timeLabel(item.lastMessageTime) : "";

        btn.appendChild(avatarWrap);
        btn.appendChild(meta);
        btn.appendChild(time);
        return btn;
    }

    // 서버 인박스로 목업 목록을 교체한다 (팬 전용).
    function renderInbox(items) {
        const dmBody = document.querySelector("#dmListView .dm-body");
        if (!dmBody) return;

        const withHistory = items.filter(function (i) {
            return i.hasConversation;
        });
        const withoutHistory = items.filter(function (i) {
            return !i.hasConversation;
        });

        const promo = dmBody.querySelector(".dm-promo"); // 구독 혜택 배너는 유지
        dmBody.innerHTML = "";
        if (promo) dmBody.appendChild(promo);

        const msgLabel = document.createElement("p");
        msgLabel.className = "dm-section-label";
        msgLabel.textContent = t("shell.dm.messages", "메시지");
        dmBody.appendChild(msgLabel);

        if (withHistory.length === 0) {
            const empty = document.createElement("p");
            empty.className = "text-xs text-muted";
            empty.style.padding = "0 16px";
            empty.textContent = t("client.dm.noConversation", "아직 나눈 대화가 없어요.");
            dmBody.appendChild(empty);
        } else {
            withHistory.forEach(function (item) {
                dmBody.appendChild(buildItem(item));
            });
        }

        const recLabel = document.createElement("p");
        recLabel.className = "dm-section-label";
        recLabel.textContent = t("shell.dm.recommend", "추천");
        dmBody.appendChild(recLabel);
        withoutHistory.forEach(function (item) {
            dmBody.appendChild(buildItem(item));
        });

        renderUnreadBadges(); // 방별 안 읽은 개수 다시 표시
    }

    function loadInbox() {
        if (!fanId) {
            const dmBody = document.querySelector("#dmListView .dm-body");
            if (dmBody) {
                // 화면 문구를 받은 뒤 그린다.
                const i18nReady = (window.WePlaNet && window.WePlaNet.i18nReady) || Promise.resolve();
                i18nReady.then(function () {
                    dmBody.innerHTML = '<p class="text-xs text-muted" style="padding:16px 4px;"></p>';
                    dmBody.firstChild.textContent = t("client.dm.loginRequired", "로그인 후 이용할 수 있어요.");
                });
            }
            return;
        }
        fetch("/chat/inbox?fanId=" + fanId)
            .then(function (res) {
                return res.json();
            })
            .then(renderInbox);
    }

    // 말풍선 그리기 (내 메시지는 오른쪽)
    function appendBubble(container, data) {
        const isMe = data.senderId === fanId;
        const row = document.createElement("div");
        row.className = "dm-msg" + (isMe ? " dm-msg--me" : "");

        if (!isMe) {
            const avatar = document.createElement("div");
            avatar.className = "avatar avatar--sm";
            avatar.textContent = (data.senderNickname || "?").slice(0, 2);
            row.appendChild(avatar);

            const wrap = document.createElement("div");
            wrap.className = "dm-msg__content"; // 말풍선이 meta 줄 너비로 늘어나지 않게
            const meta = document.createElement("div");
            meta.className = "dm-msg__meta";
            // 아티스트 방송 방에서는 팬마다 닉네임만 보여준다.
            if (!isArtist) {
                const tag = document.createElement("span");
                tag.className = "artist-tag";
                tag.textContent = "ARTIST";
                meta.appendChild(tag);
                meta.appendChild(document.createTextNode(" " + (data.senderNickname || "")));
            } else {
                meta.appendChild(document.createTextNode(data.senderNickname || ""));
            }

            const bubble = document.createElement("div");
            bubble.className = "dm-msg__bubble";
            bubble.textContent = data.content;

            wrap.appendChild(meta);
            wrap.appendChild(bubble);
            row.appendChild(wrap);
        } else {
            const bubble = document.createElement("div");
            bubble.className = "dm-msg__bubble";
            bubble.textContent = data.content;
            row.appendChild(bubble);
        }

        const timeEl = document.createElement("span");
        timeEl.className = "dm-msg__time";
        timeEl.textContent = timeLabel(data.createdAt);
        row.appendChild(timeEl);

        container.appendChild(row);
        container.scrollTop = container.scrollHeight;
    }

    // 금칙어·한도 경고 배너 (3.5초 후 숨김)
    let warningTimer = null;

    function showWarning(message) {
        const banner = document.getElementById("dmWarningBanner");
        if (!banner) return;

        banner.textContent = t("client.dm.warningPrefix", "[경고] ") + message;
        banner.classList.remove("hidden");

        clearTimeout(warningTimer);
        warningTimer = setTimeout(function () {
            banner.classList.add("hidden");
        }, 3500);
    }

    // 오늘 남은 전송 횟수 표시
    function updateQuota(remaining) {
        const wrap = document.getElementById("dmQuota");
        const countEl = document.getElementById("dmQuotaCount");
        if (!wrap || !countEl) return;

        if (remaining === undefined || remaining === null) {
            wrap.hidden = true;
            return;
        }

        countEl.textContent = remaining;
        wrap.hidden = false;
        // 다 쓰면 색을 바꾼다.
        wrap.classList.toggle("is-empty", Number(remaining) <= 0);
    }

    // [팬] 1:1 DM 방 열기 - 지난 대화를 불러오고 실시간 수신을 구독한다.
    function openRealRoom(artistId) {
        if (!fanId) {
            window.location.href = "/login";
            return;
        }
        currentArtistId = Number(artistId);

        fetch("/chat/room-data?artistId=" + currentArtistId + "&fanId=" + fanId)
            .then(function (res) {
                return res.json();
            })
            .then(function (data) {
                const messages = document.getElementById("dmMessages");
                if (messages) {
                    messages.innerHTML = "";
                    data.messages.forEach(function (m) {
                        appendBubble(messages, m);
                    });
                }

                // 서버가 판단한 만료 여부로 배너를 맞춘다.
                const banner = document.getElementById("dmExpiredBanner");
                if (banner) {
                    banner.classList.toggle("hidden", !data.membershipExpired);
                    // 가입 이력이 없으면 가입 안내 문구로 바꾼다.
                    banner.classList.toggle("is-never-subscribed", !!data.neverSubscribed);
                }

                // 멤버십 만료 시 입력창을 숨긴다.
                const composerEl = document.getElementById("dmComposer");
                if (composerEl) {
                    composerEl.style.display = data.membershipExpired ? "none" : "";
                }

                // 오늘 남은 전송 횟수
                updateQuota(data.remaining);

                // 방을 열면 메시지를 모두 읽음 처리한다.
                markRoomRead(currentArtistId);

                unsubscribeAll();
                ensureSocket(function () {
                    const personalTopic = "/topic/chat." + currentArtistId + ".fan." + fanId;
                    const errorTopic = "/topic/chat.error." + fanId;
                    // 아티스트 방송 채널도 구독한다.
                    const broadcastTopic = "/topic/chat." + currentArtistId;

                    subscriptions.push(stompClient.subscribe(personalTopic, function (frame) {
                        const payload = JSON.parse(frame.body);
                        appendBubble(messages, payload);
                        // 정상 저장되면 남은 횟수가 함께 온다.
                        if (payload.remaining !== undefined) {
                            updateQuota(payload.remaining);
                        }
                    }));

                    subscriptions.push(stompClient.subscribe(broadcastTopic, function (frame) {
                        appendBubble(messages, JSON.parse(frame.body));
                    }));

                    subscriptions.push(stompClient.subscribe(errorTopic, function (frame) {
                        // 경고는 화면 안 배너로 보여준다.
                        const warning = JSON.parse(frame.body);
                        showWarning(warning.message);
                        // 한도 초과일 때만 남은 횟수를 0으로 표시한다 (금칙어·멤버십 경고는 차감 없음).
                        if (warning.reason === "DAILY_LIMIT") {
                            updateQuota(0);
                        }
                    }));
                });
            });
    }

    // [아티스트] DM 버튼을 누르면 바로 자기 팬 DM 방으로 들어간다.
    function openArtistBroadcastRoom() {
        const dmRoomName = document.getElementById("dmRoomName");
        if (dmRoomName) dmRoomName.textContent = t("shell.dm.fanDm", "팬 DM");

        // 아티스트 방에는 "ARTIST · DM" 표시와 인증뱃지를 숨긴다.
        const titleWrap = dmRoomName ? dmRoomName.parentElement : null;
        if (titleWrap) {
            const badge = titleWrap.querySelector(".badge-verified");
            const sub = titleWrap.querySelector("small");
            if (badge) badge.classList.add("hidden");
            if (sub) sub.classList.add("hidden");
        }

        // 아티스트에게는 멤버십 만료 배너가 없다.
        const banner = document.getElementById("dmExpiredBanner");
        if (banner) banner.classList.add("hidden");
        const composerEl = document.getElementById("dmComposer");
        if (composerEl) composerEl.style.display = "";
        updateQuota(null); // 전송 한도도 팬 전용이라 표시하지 않음

        withArtistRoomId(function (roomId) {
            fetch("/chat/room-data/artist?artistId=" + roomId)
                .then(function (res) {
                    return res.json();
                })
                .then(function (data) {
                    const messages = document.getElementById("dmMessages");
                    if (messages) {
                        messages.innerHTML = "";
                        data.messages.forEach(function (m) {
                            appendBubble(messages, m);
                        });
                    }

                    unsubscribeAll();
                    ensureSocket(function () {
                        // 우리 커뮤니티 방송 채널 (방 번호 = 커뮤니티 id)
                        const broadcastTopic = "/topic/chat." + roomId;
                        // 팬 메시지 중 30%만 오는 채널 (도배 방지)
                        const artistFeedTopic = "/topic/chat." + roomId + ".artistFeed";
                        // 경고는 보낸 사람 본인 채널로 온다.
                        const errorTopic = "/topic/chat.error." + fanId;

                        subscriptions.push(stompClient.subscribe(broadcastTopic, function (frame) {
                            appendBubble(messages, JSON.parse(frame.body));
                        }));

                        subscriptions.push(stompClient.subscribe(artistFeedTopic, function (frame) {
                            appendBubble(messages, JSON.parse(frame.body));
                        }));

                        subscriptions.push(stompClient.subscribe(errorTopic, function (frame) {
                            showWarning(JSON.parse(frame.body).message);
                        }));
                    });
                });
        });
    }

    document.addEventListener("click", function (e) {
        // DM 목록 버튼의 data-open-room 으로만 방을 연다 (body 의 data-artist-id 와 구분).
        const roomBtn = e.target.closest("[data-open-room]");
        if (roomBtn && !isArtist) {
            openRealRoom(roomBtn.getAttribute("data-open-room"));
        }

        if (e.target.closest("#fabChat")) {
            if (isArtist) {
                openArtistBroadcastRoom(); // 위젯을 열 때마다 최신 이력으로 갱신
            } else {
                loadInbox(); // 위젯을 열 때마다 최신 목록으로 갱신
            }
        }
    });

    // [팬] 안 읽은 DM 숫자 - 서버는 최근 7일 방 주인 메시지 시각만 주고, 방별 읽음 위치는 localStorage 에 저장한다.
    const LAST_READ_KEY = "weplanet.dm.lastRead." + fanId;
    let serverSkew = 0;   // 서버 시각 - 브라우저 시각(ms)
    let unreadTimes = {}; // 방별 방 주인 메시지 시각 (서버 기준 ms)
    let liveUnread = {};  // 방별 실시간으로 새로 받은 개수
    let notifySubs = [];  // DM 방 구독과 따로 관리

    function loadLastRead() {
        try {
            return JSON.parse(localStorage.getItem(LAST_READ_KEY)) || {};
        } catch (e) {
            return {}; // 저장소를 못 쓰면 매번 처음처럼 동작
        }
    }

    function saveLastRead(map) {
        try {
            localStorage.setItem(LAST_READ_KEY, JSON.stringify(map));
        } catch (e) {
            // 저장 실패는 무시
        }
    }

    function serverNow() {
        return Date.now() + serverSkew;
    }

    function unreadCount(artistId) {
        if (!(artistId in unreadTimes)) return 0;
        const lastRead = loadLastRead()[artistId] || 0;
        const fromHistory = unreadTimes[artistId].filter(function (time) {
            return time > lastRead;
        }).length;
        return fromHistory + (liveUnread[artistId] || 0);
    }

    function countLabel(n) {
        return n > 99 ? "99+" : String(n);
    }

    function renderUnreadBadges() {
        let total = 0;
        Object.keys(unreadTimes).forEach(function (id) {
            total += unreadCount(id);
        });

        const fab = document.getElementById("fabChat");
        if (fab) {
            if (!fab.dataset.baseLabel) fab.dataset.baseLabel = fab.getAttribute("aria-label") || "";
            let badge = fab.querySelector(".fab__badge");
            if (total > 0) {
                if (!badge) {
                    badge = document.createElement("span");
                    badge.className = "fab__badge";
                    badge.setAttribute("aria-hidden", "true");
                    fab.appendChild(badge);
                }
                badge.textContent = countLabel(total);
                fab.setAttribute("aria-label", t("client.dm.unreadLabel", "읽지 않은 DM {0}개").replace("{0}", total));
            } else {
                if (badge) badge.remove();
                fab.setAttribute("aria-label", fab.dataset.baseLabel);
            }
        }

        // DM 목록이 열려 있으면 방별 개수도 표시
        document.querySelectorAll("#dmListView .dm-list-item[data-open-room]").forEach(function (item) {
            const n = unreadCount(item.getAttribute("data-open-room"));
            let pill = item.querySelector(".dm-list-item__unread");
            if (n > 0) {
                if (!pill) {
                    pill = document.createElement("span");
                    pill.className = "dm-list-item__unread";
                    item.appendChild(pill);
                }
                pill.textContent = countLabel(n);
            } else if (pill) {
                pill.remove();
            }
        });
    }

    function markRoomRead(artistId) {
        if (!artistId || !(String(artistId) in unreadTimes)) return;
        const map = loadLastRead();
        map[artistId] = serverNow();
        saveLastRead(map);
        liveUnread[artistId] = 0;
        renderUnreadBadges();
    }

    // 지금 그 방 대화창을 보고 있는지
    function isRoomOnScreen(artistId) {
        const panel = document.getElementById("dmPanel");
        const room = document.getElementById("dmRoomView");
        return !!panel && panel.classList.contains("is-open")
            && !!room && room.classList.contains("is-active")
            && Number(currentArtistId) === Number(artistId);
    }

    function startUnreadWatcher() {
        fetch("/chat/unread-source")
            .then(function (res) {
                return res.json();
            })
            .then(function (data) {
                serverSkew = (data.serverNow || Date.now()) - Date.now();
                const lastRead = loadLastRead();
                let changed = false;
                unreadTimes = {};
                (data.rooms || []).forEach(function (room) {
                    unreadTimes[room.artistId] = room.times || [];
                    // 처음 보는 방은 지금을 기준점으로 잡는다.
                    if (!(room.artistId in lastRead)) {
                        lastRead[room.artistId] = serverNow();
                        changed = true;
                    }
                });
                if (changed) saveLastRead(lastRead);
                renderUnreadBadges();

                const roomIds = Object.keys(unreadTimes);
                if (roomIds.length === 0) return;
                ensureSocket(function () {
                    notifySubs.forEach(function (sub) {
                        sub.unsubscribe();
                    });
                    notifySubs = [];
                    roomIds.forEach(function (roomId) {
                        const onMessage = function (frame) {
                            const payload = JSON.parse(frame.body);
                            // 방 주인이 직접 보낸 메시지만 센다.
                            if (Number(payload.senderId) !== Number(roomId)) return;
                            if (isRoomOnScreen(roomId)) {
                                markRoomRead(roomId);
                                return;
                            }
                            liveUnread[roomId] = (liveUnread[roomId] || 0) + 1;
                            renderUnreadBadges();
                        };
                        // 방송 + 나에게 온 개인 메시지
                        notifySubs.push(stompClient.subscribe("/topic/chat." + roomId, onMessage));
                        notifySubs.push(stompClient.subscribe("/topic/chat." + roomId + ".fan." + fanId, onMessage));
                    });
                });
            })
            .catch(function () {
                // 알림 숫자 실패는 DM 사용에 영향을 주지 않게 무시한다.
            });
    }

    // 셸이 비동기로 다 그려진 뒤(weplanet:shell-ready)에 폼 교체와 인박스 로딩을 시작한다.
    function whenShellReady(callback) {
        if (window.WePlaNetShellReady) {
            callback();
        } else {
            document.addEventListener("weplanet:shell-ready", callback, { once: true });
        }
    }

    whenShellReady(function () {
        // 셸의 목업 전송 폼을 복제·교체해 이벤트를 떼고 실제 전송 로직을 붙인다.
        const oldComposer = document.getElementById("dmComposer");
        if (oldComposer) {
            const newComposer = oldComposer.cloneNode(true);
            oldComposer.parentNode.replaceChild(newComposer, oldComposer);

            newComposer.addEventListener("submit", function (e) {
                e.preventDefault();
                if (!fanId) {
                    window.location.href = "/login";
                    return;
                }
                const input = document.getElementById("dmInput");
                const text = input.value.trim();
                if (!text) return;

                if (isArtist) {
                    // fanId 가 null 이면 아티스트 DM (방 번호는 커뮤니티 id)
                    if (!artistRoomId) return;
                    ensureSocket(function () {
                        stompClient.send("/app/chat.send", {}, JSON.stringify({
                            artistId: artistRoomId,
                            fanId: null,
                            senderId: fanId,
                            content: text
                        }));
                    });
                } else {
                    if (!currentArtistId) return;
                    ensureSocket(function () {
                        stompClient.send("/app/chat.send", {}, JSON.stringify({
                            artistId: currentArtistId,
                            fanId: fanId,
                            senderId: fanId,
                            content: text
                        }));
                    });
                }
                input.value = "";
            });
        }

        if (!isArtist) {
            loadInbox();
        }

        // 안 읽은 DM 숫자는 로그인한 팬에게만
        if (fanId && roleName === "ROLE_FAN") {
            startUnreadWatcher();
        }
    });
})();
