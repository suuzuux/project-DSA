/**
 * ============================================================
 * WePlaNet – DM 플로팅 위젯 실데이터 연결 (김화평, CHAT 담당)
 * ------------------------------------------------------------
 * shell.js(위석현님)가 그려주는 DM 위젯 화면(목업 데이터)에,
 * 실제 백엔드(ChatController)의 데이터와 웹소켓을 연결해주는 스크립트.
 * shell.js가 먼저 화면을 그려놓은 다음에 이 스크립트가 실행되어야 하므로,
 * html에서는 반드시 shell.js보다 나중에 불러와야 함.
 * ============================================================
 */
(function () {
    "use strict";

    const body = document.body;
    if (body.getAttribute("data-shell") !== "fan") return;

    // data-fan-id는 실제 로그인한 사람일 때만 서버가 채워줌 (비로그인이면 아예 속성 자체가 없음).
    // 예전엔 없으면 1번으로 기본값 처리해서, 비로그인 상태로도 1번 계정 명의로 DM이 보내지는 문제가 있었음
    const fanIdRaw = body.getAttribute("data-fan-id");
    const fanId = fanIdRaw ? Number(fanIdRaw) : null;

    let stompClient = null;
    let currentArtistId = null;
    let subscriptions = [];

    function unsubscribeAll() {
        subscriptions.forEach(function (sub) {
            sub.unsubscribe();
        });
        subscriptions = [];
    }

    function ensureSocket(callback) {
        if (stompClient && stompClient.connected) {
            callback();
            return;
        }
        const socket = new SockJS("/ws-chat");
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // 콘솔에 웹소켓 로그가 너무 많이 찍히는 걸 막음
        stompClient.connect({}, function () {
            callback();
        }, function (err) {
            console.error('[DM] 웹소켓 연결 실패', err);
        });
    }

    function timeLabel(iso) {
        if (!iso) return "";
        return (iso.split("T")[1] || "").slice(0, 5);
    }

    // 인박스 한 줄(아티스트 하나)을 실제 버튼 엘리먼트로 만듦.
    // shell.js가 기대하는 것과 같은 클래스 구조(.dm-list-item, .dm-list-item__name 등)를 그대로 맞춰서 만듦
    // → shell.js의 기존 클릭 핸들러(방 전환/헤더 표시)가 그대로 이 버튼에도 작동함
    function buildItem(item) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "dm-list-item";
        btn.setAttribute("data-open-room", item.artistId);
        btn.setAttribute("data-artist-id", item.artistId);
        btn.setAttribute("data-room-expired", item.membershipExpired ? "true" : "false");

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
        name.textContent = item.artistNickname || "";
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

    // 서버에서 실제 인박스 목록을 받아와서, shell.js가 그려둔 목업 목록을 통째로 실데이터로 갈아끼움
    function renderInbox(items) {
        const dmBody = document.querySelector("#dmListView .dm-body");
        if (!dmBody) return;

        const withHistory = items.filter(function (i) {
            return i.hasConversation;
        });
        const withoutHistory = items.filter(function (i) {
            return !i.hasConversation;
        });

        const promo = dmBody.querySelector(".dm-promo"); // 상단 "구독 혜택 안내" 배너는 그대로 유지
        dmBody.innerHTML = "";
        if (promo) dmBody.appendChild(promo);

        const msgLabel = document.createElement("p");
        msgLabel.className = "dm-section-label";
        msgLabel.textContent = "메시지";
        dmBody.appendChild(msgLabel);

        if (withHistory.length === 0) {
            const empty = document.createElement("p");
            empty.className = "text-xs text-muted";
            empty.style.padding = "0 16px";
            empty.textContent = "아직 나눈 대화가 없어요.";
            dmBody.appendChild(empty);
        } else {
            withHistory.forEach(function (item) {
                dmBody.appendChild(buildItem(item));
            });
        }

        const recLabel = document.createElement("p");
        recLabel.className = "dm-section-label";
        recLabel.textContent = "추천";
        dmBody.appendChild(recLabel);
        withoutHistory.forEach(function (item) {
            dmBody.appendChild(buildItem(item));
        });
    }

    function loadInbox() {
        if (!fanId) {
            const dmBody = document.querySelector("#dmListView .dm-body");
            if (dmBody) {
                dmBody.innerHTML = '<p class="text-xs text-muted" style="padding:16px 4px;">로그인 후 이용할 수 있어요.</p>';
            }
            return;
        }
        fetch("/chat/inbox?fanId=" + fanId)
            .then(function (res) {
                return res.json();
            })
            .then(renderInbox);
    }

    // 메시지 하나를 대화창에 말풍선으로 그림 (내가 보낸 건 오른쪽, 아티스트가 보낸 건 왼쪽 - shell.js CSS 클래스 그대로 사용)
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
            const meta = document.createElement("div");
            meta.className = "dm-msg__meta";
            const tag = document.createElement("span");
            tag.className = "artist-tag";
            tag.textContent = "ARTIST";
            meta.appendChild(tag);
            meta.appendChild(document.createTextNode(" " + (data.senderNickname || "")));

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

    // 아티스트 하나를 골라서 방을 열 때: 지난 대화 이력을 불러오고, 실시간 수신을 새로 구독함
    // (화면 전환/헤더 표시는 shell.js가 이미 처리해줌 - 여기선 메시지 데이터만 채움)
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

                // 와이어프레임 19번: 서버가 최종 판단한 만료 여부로 배너를 확실하게 맞춰줌
                // (shell.js가 클릭 시점에 이미 한 번 처리해주지만, 서버 응답이 더 정확한 최신 값이라 덮어씀)
                const banner = document.getElementById("dmExpiredBanner");
                if (banner) {
                    banner.classList.toggle("hidden", !data.membershipExpired);
                }

                // 와이어프레임 19번: 멤버십 만료 시엔 입력창 자체가 없어야 함 (배너만 있고 메시지는 못 보냄)
                const composerEl = document.getElementById("dmComposer");
                if (composerEl) {
                    composerEl.style.display = data.membershipExpired ? "none" : "";
                }

                unsubscribeAll();
                ensureSocket(function () {
                    const personalTopic = "/topic/chat." + currentArtistId + ".fan." + fanId;
                    const errorTopic = "/topic/chat.error." + fanId;

                    subscriptions.push(stompClient.subscribe(personalTopic, function (frame) {
                        appendBubble(messages, JSON.parse(frame.body));
                    }));

                    subscriptions.push(stompClient.subscribe(errorTopic, function (frame) {
                        alert(JSON.parse(frame.body).message);
                    }));
                });
            });
    }

    document.addEventListener("click", function (e) {
        const roomBtn = e.target.closest("[data-artist-id]");
        if (roomBtn) {
            openRealRoom(roomBtn.getAttribute("data-artist-id"));
        }

        if (e.target.closest("#fabChat")) {
            loadInbox(); // 위젯을 열 때마다 최신 목록으로 새로고침
        }
    });

    // shell.js가 미리 만들어둔 "메시지 보내기" 폼은 실제 전송 없이 화면에만 붙이는 목업 코드라서,
    // 노드를 통째로 복제해서 갈아끼우는 방식으로 그 목업 이벤트를 떼어내고 실제 전송 로직을 새로 닮
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
            if (!text || !currentArtistId) return;

            ensureSocket(function () {
                stompClient.send("/app/chat.send", {}, JSON.stringify({
                    artistId: currentArtistId,
                    fanId: fanId,
                    senderId: fanId,
                    content: text
                }));
            });
            input.value = "";
        });
    }

    loadInbox();
})();
