/**
 * ============================================================
 * WePlaNet – DM 플로팅 위젯 실데이터 연결 (김화평, CHAT 담당)
 * ------------------------------------------------------------
 * shell.js(위석현님)가 그려주는 DM 위젯 화면(목업 데이터)에,
 * 실제 백엔드(ChatController)의 데이터와 웹소켓을 연결해주는 스크립트.
 * shell.js가 먼저 화면을 그려놓은 다음에 이 스크립트가 실행되어야 하므로,
 * html에서는 반드시 shell.js보다 나중에 불러와야 함.
 *
 * 팬 쪽은 "아티스트별 1:1 DM 인박스" (CHAT-02 이하 그대로),
 * 아티스트 쪽은 "자신의 방송 채팅방 1개" (CHAT-02 비대칭 수신, 팬 메시지는 30%만 노출) - 이 둘은
 * 서로 다른 모델이라서 아티스트는 인박스 목록 없이 DM 버튼을 누르면 바로 자신의 방으로 들어감.
 * ============================================================
 */
(function () {
    "use strict";

    const body = document.body;
    if (body.getAttribute("data-shell") !== "fan") return;

    // data-fan-id는 실제 로그인한 사람일 때만 서버가 채워줌 (비로그인이면 아예 속성 자체가 없음).
    // 예전엔 없으면 1번으로 기본값 처리해서, 비로그인 상태로도 1번 계정 명의로 DM이 보내지는 문제가 있었음
    // 이름은 fanId지만 실제로는 "로그인한 내 계정 id"임 - 아티스트로 로그인했을 때도 이 값이 곧 내 artistId가 됨
    const fanIdRaw = body.getAttribute("data-fan-id");
    const fanId = fanIdRaw ? Number(fanIdRaw) : null;

    // 아티스트 계정으로 로그인했는지 - 아티스트는 팬용 DM 인박스 대신
    // 자기 자신의 방송 채팅방 하나로 바로 들어가야 하므로 분기가 필요함
    const roleName = body.getAttribute("data-role") || "";
    const isArtist = roleName === "ROLE_ARTIST";

    // 관리자는 DM을 주고받을 일이 없는 계정이라(shell.js가 채팅 버튼 자체를 안 그림) 이 스크립트도 아예 동작 안 함
    if (roleName === "ROLE_ADMIN") return;

    let stompClient = null;
    let currentArtistId = null; // 팬 화면에서 지금 열려있는 방의 상대 아티스트 id
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
        const nameText = document.createElement("span");
        nameText.textContent = item.artistNickname || "";
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

    // 서버에서 실제 인박스 목록을 받아와서, shell.js가 그려둔 목업 목록을 통째로 실데이터로 갈아끼움 (팬 전용)
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

    // 메시지 하나를 대화창에 말풍선으로 그림 (내가 보낸 건 오른쪽, 상대가 보낸 건 왼쪽 - shell.js CSS 클래스 그대로 사용)
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
            wrap.className = "dm-msg__content"; // meta 줄 너비에 맞춰 말풍선까지 늘어나지 않도록(내용물 크기로 감싸기 위함)
            const meta = document.createElement("div");
            meta.className = "dm-msg__meta";
            // 아티스트 자신의 방송 채팅방에서는 상대가 여러 팬이라 "ARTIST" 태그가 아니라
            // 각자의 닉네임만 보여주는 게 맞음 (팬 화면에서는 상대가 항상 그 아티스트 한 명이라 태그 유지)
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

    // 금칙어/한도 초과 경고를 화면 안 배너로 보여줌 (3.5초 뒤 자동 숨김)
    let warningTimer = null;

    function showWarning(message) {
        const banner = document.getElementById("dmWarningBanner");
        if (!banner) return;

        banner.textContent = "[경고] " + message;
        banner.classList.remove("hidden");

        clearTimeout(warningTimer);
        warningTimer = setTimeout(function () {
            banner.classList.add("hidden");
        }, 3500);
    }

    // 오늘 남은 전송 횟수를 입력창 아래에 표시
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
        // 다 쓰면 눈에 띄게 색을 바꿔줌
        wrap.classList.toggle("is-empty", Number(remaining) <= 0);
    }

    // [팬 전용] 아티스트 하나를 골라서 1:1 DM 방을 열 때: 지난 대화 이력을 불러오고, 실시간 수신을 새로 구독함
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

                // 오늘 남은 전송 횟수 표시 (CHAT-05)
                updateQuota(data.remaining);

                unsubscribeAll();
                ensureSocket(function () {
                    const personalTopic = "/topic/chat." + currentArtistId + ".fan." + fanId;
                    const errorTopic = "/topic/chat.error." + fanId;
                    // 아티스트가 보내는 방송(공지) 채널. 서버(ChatController.send)는 fanId가 null인
                    // 메시지를 "/topic/chat.{artistId}" 로 뿌리는데, 그동안 팬 쪽에서 이 채널을 구독하지
                    // 않아서 아티스트가 보낸 메시지가 팬 화면에 아예 안 보였음
                    const broadcastTopic = "/topic/chat." + currentArtistId;

                    subscriptions.push(stompClient.subscribe(personalTopic, function (frame) {
                        const payload = JSON.parse(frame.body);
                        appendBubble(messages, payload);
                        // 내가 보낸 게 정상 저장되면 서버가 남은 횟수를 같이 내려줌
                        if (payload.remaining !== undefined) {
                            updateQuota(payload.remaining);
                        }
                    }));

                    subscriptions.push(stompClient.subscribe(broadcastTopic, function (frame) {
                        appendBubble(messages, JSON.parse(frame.body));
                    }));

                    subscriptions.push(stompClient.subscribe(errorTopic, function (frame) {
                        // 예전엔 브라우저 기본 alert을 띄웠는데, 팬 채팅방 화면(fanChatRoom.html)은
                        // 화면 안 배너를 쓰고 있어서 방식이 서로 달랐음 -> 배너로 통일
                        showWarning(JSON.parse(frame.body).message);
                        // 한도 초과로 거부된 경우라면 남은 횟수는 0
                        updateQuota(0);
                    }));
                });
            });
    }

    // [아티스트 전용] DM 버튼을 누르면 목록 없이 바로 자신의 방송 채팅방으로 들어감.
    // 팬 개개인과의 1:1 방이 아니라 방 1개(artistId=자기 자신)뿐이라, openRealRoom과는 별도로 다룸.
    function openArtistBroadcastRoom() {
        const dmRoomName = document.getElementById("dmRoomName");
        if (dmRoomName) dmRoomName.textContent = "내 채팅방";

        // "ARTIST · DM" 서브텍스트와 인증뱃지는 "팬이 특정 아티스트와 대화 중"일 때 의미가 있는 표시라
        // 아티스트 자신의 방송 채팅방에는 어울리지 않으므로 숨김
        const titleWrap = dmRoomName ? dmRoomName.parentElement : null;
        if (titleWrap) {
            const badge = titleWrap.querySelector(".badge-verified");
            const sub = titleWrap.querySelector("small");
            if (badge) badge.classList.add("hidden");
            if (sub) sub.classList.add("hidden");
        }

        // 아티스트에게는 "멤버십 만료" 개념이 없음(그건 팬이 이 아티스트를 구독했는지에 대한 제약) - 항상 숨김
        const banner = document.getElementById("dmExpiredBanner");
        if (banner) banner.classList.add("hidden");
        const composerEl = document.getElementById("dmComposer");
        if (composerEl) composerEl.style.display = "";
        updateQuota(null); // 하루 전송 한도도 팬 전용 제약이라 표시 안 함

        fetch("/chat/room-data/artist?artistId=" + fanId)
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
                    // 내가 방송한 메시지가 그대로 나에게도 되돌아오는 채널
                    const broadcastTopic = "/topic/chat." + fanId;
                    // 팬들이 보낸 메시지 중 30%만 도착하는 채널 (CHAT-02 비대칭 수신 - 도배 방지)
                    const artistFeedTopic = "/topic/chat." + fanId + ".artistFeed";
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
    }

    document.addEventListener("click", function (e) {
        // DM 목록 버튼(buildItem)에만 붙는 data-open-room을 기준으로 잡음 (팬 전용 - 아티스트는 목록 자체가 없음).
        // 예전엔 [data-artist-id]로 찾았는데, fan/artist/post-detail 화면은 <body>에도 그 속성이 있어서
        // 페이지 아무 곳이나 클릭할 때마다 openRealRoom이 실행되고 /chat/room-data가 호출됐음
        const roomBtn = e.target.closest("[data-open-room]");
        if (roomBtn && !isArtist) {
            openRealRoom(roomBtn.getAttribute("data-open-room"));
        }

        if (e.target.closest("#fabChat")) {
            if (isArtist) {
                openArtistBroadcastRoom(); // 위젯을 열 때마다 최신 이력으로 새로고침
            } else {
                loadInbox(); // 위젯을 열 때마다 최신 목록으로 새로고침
            }
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
            if (!text) return;

            if (isArtist) {
                // fanId를 null로 보내면 서버(ChatController.send)가 "이건 방송 메시지구나"라고 판단함
                if (!fanId) return;
                ensureSocket(function () {
                    stompClient.send("/app/chat.send", {}, JSON.stringify({
                        artistId: fanId,
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
})();
