/**
 * 아티스트 포털 라이브 호스트: 카메라 프리뷰 + WebRTC 송출 + 실시간 댓글.
 */
(function () {
    "use strict";

    const root = document.getElementById("portal-live");
    if (!root) return;

    const artistId = Number(root.dataset.artistId);
    const hostId = Number(root.dataset.hostId);
    const preview = document.getElementById("live-preview");
    const startBtn = document.getElementById("live-start-btn");
    const endBtn = document.getElementById("live-end-btn");
    const badge = document.getElementById("live-badge");
    const cameraError = document.getElementById("live-camera-error");
    const saveStatus = document.getElementById("live-save-status");
    const viewerCountEl = document.getElementById("live-viewer-count");
    const commentsEl = document.getElementById("live-comments");
    const commentForm = document.getElementById("live-comment-form");
    const commentInput = document.getElementById("live-comment-input");

    const ICE = { iceServers: [{ urls: "stun:stun.l.google.com:19302" }] };
    const FETCH_HEADERS = { "X-Requested-With": "fetch", "Content-Type": "application/json" };

    let localStream = null;
    let stompClient = null;
    let socketReady = false;
    let isLive = root.dataset.live === "true";
    const peers = new Map();
    const pendingIce = new Map();
    const viewers = new Set();
    let mediaRecorder = null;
    const recordedChunks = [];

    function refreshControls() {
        startBtn.disabled = isLive || !localStream || !socketReady;
        endBtn.disabled = !isLive;
        badge.hidden = !isLive;
        root.dataset.live = isLive ? "true" : "false";
        if (!isLive) {
            viewers.clear();
            updateViewerCount();
        }
    }

    function setLiveUi(live) {
        isLive = live;
        refreshControls();
    }

    function updateViewerCount() {
        viewerCountEl.textContent = "시청자 " + viewers.size + "명";
    }

    function showCameraError(message) {
        cameraError.hidden = !message;
        cameraError.textContent = message || "";
    }

    function showSaveStatus(message) {
        if (!saveStatus) return;
        saveStatus.hidden = !message;
        saveStatus.textContent = message || "";
    }

    function recorderMimeType() {
        const types = [
            "video/webm;codecs=vp8,opus",
            "video/webm;codecs=vp9,opus",
            "video/webm",
            "video/mp4"
        ];
        if (typeof MediaRecorder === "undefined") return "";
        for (let i = 0; i < types.length; i++) {
            if (MediaRecorder.isTypeSupported(types[i])) {
                return types[i];
            }
        }
        return "";
    }

    function startRecording() {
        recordedChunks.length = 0;
        mediaRecorder = null;
        if (!localStream || typeof MediaRecorder === "undefined") {
            return;
        }
        const mime = recorderMimeType();
        try {
            mediaRecorder = mime
                ? new MediaRecorder(localStream, { mimeType: mime, videoBitsPerSecond: 800000 })
                : new MediaRecorder(localStream);
        } catch (err) {
            console.warn("[LIVE] 녹화 시작 실패", err);
            mediaRecorder = null;
            return;
        }
        mediaRecorder.ondataavailable = function (event) {
            if (event.data && event.data.size > 0) {
                recordedChunks.push(event.data);
            }
        };
        try {
            mediaRecorder.start(1000);
        } catch (err) {
            console.warn("[LIVE] 녹화 start 실패", err);
            mediaRecorder = null;
        }
    }

    function stopRecording() {
        return new Promise(function (resolve) {
            if (!mediaRecorder || mediaRecorder.state === "inactive") {
                resolve(null);
                return;
            }
            const recorder = mediaRecorder;
            recorder.onstop = function () {
                const type = (recorder.mimeType || "video/webm").split(";")[0];
                const blob = recordedChunks.length
                    ? new Blob(recordedChunks, { type: type })
                    : null;
                mediaRecorder = null;
                recordedChunks.length = 0;
                resolve(blob && blob.size > 0 ? blob : null);
            };
            try {
                recorder.stop();
            } catch (err) {
                console.warn("[LIVE] 녹화 종료 실패", err);
                mediaRecorder = null;
                resolve(null);
            }
        });
    }

    function uploadReplay(blob) {
        if (!blob) {
            showSaveStatus("다시보기 영상이 없어 저장하지 못했습니다.");
            return Promise.resolve();
        }
        const type = (blob.type || "video/webm").split(";")[0];
        const ext = type.indexOf("mp4") >= 0 ? "mp4" : "webm";
        const form = new FormData();
        form.append("file", blob, "live-replay." + ext);
        showSaveStatus("다시보기를 Live 탭에 저장하는 중...");
        return fetch("/api/portal/live/replay", {
            method: "POST",
            headers: { "X-Requested-With": "fetch" },
            body: form
        }).then(parseResponse).then(function () {
            showSaveStatus("다시보기가 Live 탭에 저장되었습니다.");
        });
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
        if (!comment) return;
        const stick = commentsEl.scrollHeight - commentsEl.scrollTop - commentsEl.clientHeight < 48;
        const row = document.createElement("div");
        row.className = "live-comment";
        if (comment.id) {
            row.dataset.commentId = String(comment.id);
        }
        const reported = comment.reportedByMe === true || comment.reportedByMe === "true";
        const name = document.createElement("strong");
        name.textContent = comment.authorNickname || "익명";
        if (!reported && (comment.fromArtist || Number(comment.authorId) === artistId)) {
            name.className = "live-comment__name--artist";
            name.style.color = randomArtistColor();
        }
        const body = document.createElement("span");
        body.textContent = reported ? "신고접수된 댓글입니다" : (comment.content || "");
        row.appendChild(name);
        row.appendChild(body);
        if (!reported && comment.id && hostId && Number(comment.authorId) !== hostId) {
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
        while (commentsEl.children.length > 100) {
            commentsEl.removeChild(commentsEl.firstChild);
        }
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

    function sendJson(destination, body) {
        if (!stompClient || !stompClient.connected) return;
        stompClient.send(destination, {}, JSON.stringify(body));
    }

    function sendSignal(toUserId, payload) {
        sendJson("/app/live.signal", Object.assign({
            artistId: artistId,
            toUserId: toUserId
        }, payload));
    }

    function closePeer(viewerId) {
        const pc = peers.get(viewerId);
        if (pc) {
            pc.close();
            peers.delete(viewerId);
        }
        pendingIce.delete(viewerId);
        viewers.delete(viewerId);
        updateViewerCount();
    }

    function closeAllPeers() {
        Array.from(peers.keys()).forEach(closePeer);
    }

    function flushIce(viewerId, pc) {
        const queued = pendingIce.get(viewerId) || [];
        pendingIce.delete(viewerId);
        queued.forEach(function (candidate) {
            pc.addIceCandidate(candidate).catch(function (err) {
                console.warn("[LIVE] ICE 추가 실패", err);
            });
        });
    }

    function createPeer(viewerId) {
        closePeer(viewerId);
        const pc = new RTCPeerConnection(ICE);
        peers.set(viewerId, pc);
        viewers.add(viewerId);
        updateViewerCount();

        if (localStream) {
            localStream.getTracks().forEach(function (track) {
                pc.addTrack(track, localStream);
            });
        }

        pc.onicecandidate = function (event) {
            if (!event.candidate) return;
            sendSignal(viewerId, {
                type: "candidate",
                candidate: event.candidate.candidate,
                sdpMid: event.candidate.sdpMid,
                sdpMLineIndex: event.candidate.sdpMLineIndex
            });
        };

        pc.onconnectionstatechange = function () {
            if (pc.connectionState === "failed" || pc.connectionState === "closed" || pc.connectionState === "disconnected") {
                if (pc.connectionState !== "disconnected") {
                    closePeer(viewerId);
                }
            }
        };

        pc.createOffer().then(function (offer) {
            return pc.setLocalDescription(offer);
        }).then(function () {
            sendSignal(viewerId, { type: "offer", sdp: pc.localDescription.sdp });
        }).catch(function (err) {
            console.error("[LIVE] offer 생성 실패", err);
            closePeer(viewerId);
        });

        return pc;
    }

    async function handlePeerMessage(message) {
        const fromUserId = message.fromUserId;
        if (!fromUserId) return;
        const pc = peers.get(fromUserId);
        if (!pc) return;

        if (message.type === "answer" && message.sdp) {
            await pc.setRemoteDescription({ type: "answer", sdp: message.sdp });
            flushIce(fromUserId, pc);
            return;
        }
        if (message.type === "candidate" && message.candidate) {
            const candidate = {
                candidate: message.candidate,
                sdpMid: message.sdpMid,
                sdpMLineIndex: message.sdpMLineIndex
            };
            if (!pc.remoteDescription) {
                const queued = pendingIce.get(fromUserId) || [];
                queued.push(candidate);
                pendingIce.set(fromUserId, queued);
                return;
            }
            await pc.addIceCandidate(candidate);
        }
    }

    function subscribeTopics() {
        stompClient.subscribe("/topic/live." + artistId + ".host", function (frame) {
            const body = JSON.parse(frame.body);
            if (body.type === "join" && body.viewerId && isLive) {
                createPeer(body.viewerId);
            } else if (body.type === "leave" && body.viewerId) {
                closePeer(body.viewerId);
            }
        });
        stompClient.subscribe("/topic/live." + artistId + ".peer." + hostId, function (frame) {
            handlePeerMessage(JSON.parse(frame.body)).catch(function (err) {
                console.warn("[LIVE] 시그널 처리 실패", err);
            });
        });
        stompClient.subscribe("/topic/live." + artistId + ".comments", function (frame) {
            appendComment(JSON.parse(frame.body));
        });
        stompClient.subscribe("/topic/live.error." + hostId, function (frame) {
            const body = JSON.parse(frame.body);
            if (body && body.message) {
                showCameraError(body.message);
            }
        });
        if (isLive) {
            sendJson("/app/live.host", { artistId: artistId });
            loadComments();
        }
    }

    function ensureSocket(callback) {
        if (stompClient && stompClient.connected) {
            callback();
            return;
        }
        const proto = location.protocol === "https:" ? "wss:" : "ws:";
        const socket = new WebSocket(proto + "//" + location.host + "/ws-chat");
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.connect({}, function () {
            socketReady = true;
            subscribeTopics();
            refreshControls();
            callback();
        }, function (err) {
            socketReady = false;
            refreshControls();
            console.error("[LIVE] 웹소켓 연결 실패", err);
        });
    }

    async function parseResponse(res) {
        const data = await res.json().catch(function () { return {}; });
        if (!res.ok || data.success === false) {
            throw new Error(data.message || "요청에 실패했습니다.");
        }
        return data;
    }

    async function loadComments() {
        try {
            const res = await fetch("/api/community/" + artistId + "/live/comments", {
                headers: { "X-Requested-With": "fetch" }
            });
            const data = await parseResponse(res);
            commentsEl.innerHTML = "";
            (data.comments || []).forEach(appendComment);
        } catch (err) {
            console.warn("[LIVE] 댓글 불러오기 실패", err);
        }
    }

    async function startCamera() {
        try {
            localStream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: "user" },
                audio: true
            });
            preview.srcObject = localStream;
            showCameraError("");
            refreshControls();
        } catch (err) {
            console.error("[LIVE] 카메라 접근 실패", err);
            showCameraError("카메라 또는 마이크를 사용할 수 없습니다. 브라우저 권한을 확인해주세요.");
            refreshControls();
        }
    }

    startBtn.addEventListener("click", function () {
        if (!localStream) {
            showCameraError("카메라를 먼저 켜주세요.");
            return;
        }
        startBtn.disabled = true;
        ensureSocket(function () {
            fetch("/api/portal/live/start", { method: "POST", headers: FETCH_HEADERS })
                .then(parseResponse)
                .then(function () {
                    setLiveUi(true);
                    sendJson("/app/live.host", { artistId: artistId });
                    loadComments();
                    showCameraError("");
                    showSaveStatus("");
                    startRecording();
                })
                .catch(function (err) {
                    setLiveUi(isLive);
                    showCameraError(err.message);
                });
        });
    });

    endBtn.addEventListener("click", function () {
        endBtn.disabled = true;
        stopRecording().then(function (blob) {
            return fetch("/api/portal/live/end", { method: "POST", headers: FETCH_HEADERS })
                .then(parseResponse)
                .then(function () {
                    closeAllPeers();
                    setLiveUi(false);
                    return uploadReplay(blob);
                });
        }).catch(function (err) {
            setLiveUi(isLive);
            showCameraError(err.message);
            showSaveStatus("");
        });
    });

    commentForm.addEventListener("submit", function (event) {
        event.preventDefault();
        const content = commentInput.value.trim();
        if (!content || !isLive) return;
        sendJson("/app/live.comment", { artistId: artistId, content: content });
        commentInput.value = "";
    });

    window.addEventListener("beforeunload", function () {
        if (isLive) {
            navigator.sendBeacon("/api/portal/live/end");
        }
        closeAllPeers();
        if (localStream) {
            localStream.getTracks().forEach(function (track) { track.stop(); });
        }
    });

    setLiveUi(isLive);
    startCamera();
    ensureSocket(function () {});
})();
