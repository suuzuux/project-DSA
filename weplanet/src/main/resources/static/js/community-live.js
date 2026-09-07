/**
 * 커뮤니티 Live 탭: 아티스트 방송을 WebRTC로 시청하고 실시간 댓글을 주고받는다.
 */
(function () {
    "use strict";

    const root = document.getElementById("community-live");
    if (!root) return;

    const artistId = Number(root.dataset.artistId);
    const userId = Number(root.dataset.userId);
    const emptyEl = document.getElementById("live-empty");
    const onairEl = document.getElementById("live-onair");
    const player = document.getElementById("live-player");
    const unmuteBtn = document.getElementById("live-unmute-btn");
    const commentsEl = document.getElementById("live-comments");
    const commentForm = document.getElementById("live-comment-form");
    const commentInput = document.getElementById("live-comment-input");

    const ICE = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };

    let stompClient = null;
    let pc = null;
    let hostUserId = null;
    let watching = false;
    let pendingIce = [];
    let joined = false;

    function sendJson(destination, body) {
        if (!stompClient || !stompClient.connected) return;
        stompClient.send(destination, {}, JSON.stringify(body));
    }

    function appendComment(comment) {
        if (!comment) return;
        const row = document.createElement("div");
        row.className = "live-comment";
        const name = document.createElement("strong");
        name.textContent = comment.authorNickname || "익명";
        const body = document.createElement("span");
        body.textContent = comment.content || "";
        row.appendChild(name);
        row.appendChild(body);
        commentsEl.appendChild(row);
        commentsEl.scrollTop = commentsEl.scrollHeight;
    }

    function showEmpty() {
        emptyEl.hidden = false;
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
        emptyEl.hidden = true;
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
        hostUserId = status.hostUserId || hostUserId;
        showOnAir();
        if (joined) {
            return;
        }
        loadComments();
        sendJson("/app/live.join", { artistId: artistId });
        joined = true;
    }

    function subscribeTopics() {
        stompClient.subscribe("/topic/live." + artistId + ".status", function (frame) {
            const body = JSON.parse(frame.body);
            if (body.live || body.type === "LIVE") {
                joinLive(body);
            } else {
                closePeer();
                showEmpty();
            }
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
                .then(function (status) {
                    if (status && status.live) {
                        joinLive(status);
                    } else {
                        showEmpty();
                    }
                })
                .catch(function (err) {
                    console.error("[LIVE] 상태 조회 실패", err);
                    showEmpty();
                });
        }, function (err) {
            console.error("[LIVE] 웹소켓 연결 실패", err);
        });
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

    window.addEventListener("beforeunload", function () {
        sendLeave();
        if (pc) pc.close();
    });

    if (!userId) {
        showEmpty();
        return;
    }
    connectAndStart();
})();
