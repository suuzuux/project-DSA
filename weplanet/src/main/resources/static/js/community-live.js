/**
 * 커뮤니티 Live 탭: 진행 중 방송 썸네일 클릭 시청 + 다시보기 재생.
 */
(function () {
    "use strict";

    const root = document.getElementById("community-live");
    if (!root) return;

    const artistId = Number(root.dataset.artistId);
    const userId = Number(root.dataset.userId);
    const featuredEl = document.getElementById("live-featured");
    const lobbyEl = document.getElementById("live-lobby");
    const onairEl = document.getElementById("live-onair");
    const player = document.getElementById("live-player");
    const unmuteBtn = document.getElementById("live-unmute-btn");
    const backBtn = document.getElementById("live-back-btn");
    const commentsEl = document.getElementById("live-comments");
    const commentForm = document.getElementById("live-comment-form");
    const commentInput = document.getElementById("live-comment-input");
    const replayModal = document.getElementById("live-replay-modal");
    const replayPlayer = document.getElementById("live-replay-player");
    const replayTitle = document.getElementById("live-replay-title");
    const replayClose = document.getElementById("live-replay-close");

    const ICE = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };
    const MAX_VISIBLE_COMMENTS = 100;

    let stompClient = null;
    let pc = null;
    let hostUserId = null;
    let watching = false;
    let pendingIce = [];
    let joined = false;
    let latestStatus = null;
    let pendingJoin = false;

    function sendJson(destination, body) {
        if (!stompClient || !stompClient.connected) return;
        stompClient.send(destination, {}, JSON.stringify(body));
    }

    function isLive(status) {
        return !!(status && (status.live || status.type === "LIVE"));
    }

    function isNearBottom(el) {
        return el.scrollHeight - el.scrollTop - el.clientHeight < 48;
    }

    function scrollCommentsToLatest(force) {
        if (!commentsEl) return;
        if (force || isNearBottom(commentsEl)) {
            commentsEl.scrollTop = commentsEl.scrollHeight;
        }
    }

    function trimOldComments() {
        while (commentsEl.children.length > MAX_VISIBLE_COMMENTS) {
            commentsEl.removeChild(commentsEl.firstChild);
        }
    }

    function randomArtistColor() {
        const colors = [
            "#e11d48", "#db2777", "#c026d3", "#7c3aed",
            "#2563eb", "#0891b2", "#059669", "#ca8a04",
            "#ea580c", "#dc2626", "#4f46e5", "#0d9488"
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    }

    function appendComment(comment) {
        if (!comment || !commentsEl) return;
        const stick = isNearBottom(commentsEl);
        const row = document.createElement("div");
        row.className = "live-comment";
        if (comment.id) {
            row.dataset.commentId = String(comment.id);
        }
        const reported = comment.reportedByMe === true || comment.reportedByMe === "true";
        const name = document.createElement("strong");
        name.textContent = comment.authorNickname || "익명";
        const isArtist = comment.fromArtist === true
            || comment.fromArtist === "true"
            || Number(comment.authorId) === artistId;
        if (isArtist && !reported) {
            name.className = "live-comment__name--artist";
            name.style.color = randomArtistColor();
        }
        const body = document.createElement("span");
        body.textContent = reported ? "신고접수된 댓글입니다" : (comment.content || "");
        row.appendChild(name);
        row.appendChild(body);
        const myId = root.dataset.userId ? Number(root.dataset.userId) : null;
        if (!reported && comment.id && myId && Number(comment.authorId) !== myId) {
            const reportBtn = document.createElement("button");
            reportBtn.type = "button";
            reportBtn.className = "live-comment__report";
            reportBtn.textContent = "신고";
            reportBtn.addEventListener("click", function () {
                reportLiveComment(comment.id, row);
            });
            row.appendChild(reportBtn);
        }
        if (reported) {
            markLiveCommentReported(row);
        }
        commentsEl.appendChild(row);
        trimOldComments();
        if (stick) {
            commentsEl.scrollTop = commentsEl.scrollHeight;
        }
    }

    function markLiveCommentReported(row) {
        if (!row) return;
        row.classList.add("live-comment--reported");
        const body = row.querySelector("span");
        if (body) {
            body.textContent = "신고접수된 댓글입니다";
        }
        const name = row.querySelector("strong");
        if (name) {
            name.classList.remove("live-comment__name--artist");
            name.style.color = "";
        }
        const reportBtn = row.querySelector(".live-comment__report");
        if (reportBtn) {
            reportBtn.remove();
        }
    }

    async function reportLiveComment(commentId, row) {
        if (!commentId) return;
        const reason = window.prompt("신고 사유를 선택하세요.\nSPAM / ABUSE / SEXUAL / ETC", "ABUSE");
        if (!reason) return;
        const normalized = String(reason).trim().toUpperCase();
        if (!["SPAM", "ABUSE", "SEXUAL", "ETC"].includes(normalized)) {
            window.alert("신고 사유는 SPAM, ABUSE, SEXUAL, ETC 중 하나여야 합니다.");
            return;
        }
        try {
            const res = await fetch(
                "/api/community/" + artistId + "/live/comments/" + commentId + "/report?reason=" + encodeURIComponent(normalized),
                {
                    method: "POST",
                    headers: { "X-Requested-With": "fetch" }
                }
            );
            const data = await res.json().catch(function () { return {}; });
            if (!res.ok || data.success === false) {
                window.alert((data && data.message) || "신고에 실패했습니다.");
                return;
            }
            markLiveCommentReported(row);
        } catch (err) {
            console.warn("[LIVE] 채팅 신고 실패", err);
            window.alert("신고에 실패했습니다.");
        }
    }

    function showLobby() {
        if (lobbyEl) lobbyEl.hidden = false;
        onairEl.hidden = true;
        unmuteBtn.hidden = true;
        watching = false;
    }

    function showFeatured() {
        if (featuredEl) featuredEl.hidden = false;
        showLobby();
    }

    function hideLiveUi() {
        if (featuredEl) featuredEl.hidden = true;
        if (lobbyEl) lobbyEl.hidden = false;
        onairEl.hidden = true;
        unmuteBtn.hidden = true;
        watching = false;
        hostUserId = null;
        joined = false;
        commentsEl.innerHTML = "";
        if (pc) {
            pc.close();
            pc = null;
        }
        pendingIce = [];
        player.srcObject = null;
    }

    function showOnAir() {
        if (lobbyEl) lobbyEl.hidden = true;
        onairEl.hidden = false;
        watching = true;
    }

    async function loadComments() {
        try {
            const res = await fetch("/api/community/" + artistId + "/live/comments", {
                headers: { "X-Requested-With": "fetch" }
            });
            const data = await res.json();
            if (!res.ok || data.success === false) return;
            commentsEl.innerHTML = "";
            (data.comments || []).forEach(appendComment);
            scrollCommentsToLatest(true);
        } catch (err) {
            console.warn("[LIVE] 댓글 불러오기 실패", err);
        }
    }

    function sendLeave() {
        if (joined) {
            sendJson("/app/live.leave", { artistId: artistId });
            joined = false;
        }
    }

    function closePeer() {
        sendLeave();
        if (pc) {
            pc.close();
            pc = null;
        }
        pendingIce = [];
        player.srcObject = null;
    }

    function returnToLobby() {
        closePeer();
        commentsEl.innerHTML = "";
        unmuteBtn.hidden = true;
        if (isLive(latestStatus)) {
            showFeatured();
        } else {
            hideLiveUi();
        }
    }

    function flushIce() {
        if (!pc || !pc.remoteDescription) return;
        const queued = pendingIce;
        pendingIce = [];
        queued.forEach(function (candidate) {
            pc.addIceCandidate(candidate).catch(function (err) {
                console.warn("[LIVE] ICE 추가 실패", err);
            });
        });
    }

    function ensurePeer() {
        if (pc) return pc;
        pc = new RTCPeerConnection(ICE);
        pc.onicecandidate = function (event) {
            if (!event.candidate || !hostUserId) return;
            sendJson("/app/live.signal", {
                artistId: artistId,
                toUserId: hostUserId,
                type: "candidate",
                candidate: event.candidate.candidate,
                sdpMid: event.candidate.sdpMid,
                sdpMLineIndex: event.candidate.sdpMLineIndex
            });
        };
        pc.ontrack = function (event) {
            if (event.streams && event.streams[0]) {
                player.srcObject = event.streams[0];
            } else {
                const stream = player.srcObject || new MediaStream();
                stream.addTrack(event.track);
                player.srcObject = stream;
            }
            const playPromise = player.play();
            if (playPromise && playPromise.catch) {
                playPromise.catch(function () {
                    player.muted = true;
                    unmuteBtn.hidden = false;
                    player.play().catch(function () {});
                });
            }
        };
        return pc;
    }

    async function handleSignal(message) {
        if (!message || !watching) return;
        if (message.type === "offer" && message.sdp) {
            if (message.fromUserId) {
                hostUserId = message.fromUserId;
            }
            const peer = ensurePeer();
            await peer.setRemoteDescription({ type: "offer", sdp: message.sdp });
            flushIce();
            const answer = await peer.createAnswer();
            await peer.setLocalDescription(answer);
            sendJson("/app/live.signal", {
                artistId: artistId,
                toUserId: hostUserId,
                type: "answer",
                sdp: peer.localDescription.sdp
            });
            return;
        }
        if (message.type === "candidate" && message.candidate) {
            const candidate = {
                candidate: message.candidate,
                sdpMid: message.sdpMid,
                sdpMLineIndex: message.sdpMLineIndex
            };
            if (!pc || !pc.remoteDescription) {
                pendingIce.push(candidate);
                return;
            }
            await pc.addIceCandidate(candidate);
        }
    }

    function joinLive(status) {
        latestStatus = status;
        hostUserId = status.hostUserId || hostUserId;
        showOnAir();
        if (joined) {
            return;
        }
        loadComments();
        sendJson("/app/live.join", { artistId: artistId });
        joined = true;
    }

    function applyStatus(status) {
        latestStatus = status;
        if (isLive(status)) {
            if (watching || pendingJoin) {
                pendingJoin = false;
                joinLive(status);
            } else {
                showFeatured();
            }
            return;
        }
        pendingJoin = false;
        closePeer();
        hideLiveUi();
    }

    function subscribeTopics() {
        stompClient.subscribe("/topic/live." + artistId + ".status", function (frame) {
            applyStatus(JSON.parse(frame.body));
        });
        stompClient.subscribe("/topic/live." + artistId + ".peer." + userId, function (frame) {
            handleSignal(JSON.parse(frame.body)).catch(function (err) {
                console.warn("[LIVE] 시그널 처리 실패", err);
            });
        });
        stompClient.subscribe("/topic/live." + artistId + ".comments", function (frame) {
            appendComment(JSON.parse(frame.body));
        });
        stompClient.subscribe("/topic/live.error." + userId, function (frame) {
            const body = JSON.parse(frame.body);
            if (body && body.message) {
                console.warn("[LIVE]", body.message);
            }
        });
    }

    function wsChatUrl() {
        const proto = location.protocol === "https:" ? "wss:" : "ws:";
        return proto + "//" + location.host + "/ws-chat";
    }

    function connectAndStart() {
        const socket = new WebSocket(wsChatUrl());
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.connect({}, function () {
            subscribeTopics();
            fetch("/api/community/" + artistId + "/live/status", {
                headers: { "X-Requested-With": "fetch" }
            })
                .then(function (res) { return res.json(); })
                .then(applyStatus)
                .catch(function (err) {
                    console.error("[LIVE] 상태 조회 실패", err);
                    hideLiveUi();
                });
        }, function (err) {
            console.error("[LIVE] 웹소켓 연결 실패", err);
        });
    }

    function requestWatch() {
        pendingJoin = true;
        if (isLive(latestStatus) && stompClient && stompClient.connected) {
            pendingJoin = false;
            joinLive(latestStatus);
        }
    }

    function openReplay(src, title) {
        if (!replayModal || !replayPlayer || !src) return;
        replayTitle.textContent = title || "다시보기";
        replayPlayer.src = src;
        replayModal.hidden = false;
        replayPlayer.play().catch(function () {});
    }

    function closeReplay() {
        if (!replayModal || !replayPlayer) return;
        replayPlayer.pause();
        replayPlayer.removeAttribute("src");
        replayPlayer.load();
        replayModal.hidden = true;
    }

    if (featuredEl) {
        featuredEl.addEventListener("click", requestWatch);
    }

    if (backBtn) {
        backBtn.addEventListener("click", returnToLobby);
    }

    unmuteBtn.addEventListener("click", function () {
        player.muted = false;
        player.play().catch(function () {});
        unmuteBtn.hidden = true;
    });

    commentForm.addEventListener("submit", function (event) {
        event.preventDefault();
        const content = commentInput.value.trim();
        if (!content || !watching) return;
        sendJson("/app/live.comment", { artistId: artistId, content: content });
        commentInput.value = "";
    });

    root.querySelectorAll(".live-replay-card").forEach(function (card) {
        card.addEventListener("click", function () {
            openReplay(card.getAttribute("data-src"), card.getAttribute("data-title"));
        });
    });

    if (replayClose) {
        replayClose.addEventListener("click", closeReplay);
    }
    if (replayModal) {
        replayModal.addEventListener("click", function (event) {
            if (event.target === replayModal) closeReplay();
        });
    }

    window.addEventListener("beforeunload", function () {
        sendLeave();
        if (pc) pc.close();
    });

    if (!userId) {
        hideLiveUi();
        return;
    }
    connectAndStart();
})();
