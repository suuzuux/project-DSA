package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.AiFanChatService;
import megane6.weplanet.service.ChatFilterService;
import megane6.weplanet.service.ChatMessageService;
import megane6.weplanet.service.ChatQuotaService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 실시간 채팅(CHAT) 관련 화면과 메시지 처리를 담당하는 컨트롤러.
 * <p>
 * 이 컨트롤러는 두 가지 종류의 메서드가 섞여 있음.
 * ① @GetMapping/@PostMapping 메서드들 : 지금까지 배운 것과 똑같은 일반 HTTP 요청/응답
 * (채팅방 화면 보여주기, 금칙어 관리 화면 등)
 * ② @MessageMapping 메서드(send) : 일반 HTTP가 아니라, 웹소켓(WebSocketConfig 참고)을 통해
 * 실시간으로 오가는 메시지를 처리하는 부분. 브라우저가 fetch()가 아니라
 * stompClient.send(...)로 보낸 메시지가 여기로 들어옴.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    // 실시간으로 연결된 브라우저들에게 메시지를 "방송"할 때 쓰는 도구
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatFilterService chatFilterService;
    private final ChatQuotaService chatQuotaService;
    private final AiFanChatService aiFanChatService;

    // 유저 조회 공통 헬퍼 - label은 에러 메시지에 쓸 대상 이름 ("아티스트", "팬" 등)
    private User getUserOrThrow(Long userId, String label) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(label + "를 찾을 수 없습니다."));
    }

    // 관리자 권한 체크 공통 헬퍼 - 관리자가 아니면 예외
    private void requireAdmin(User requester) {
        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("관리자만 접근할 수 있습니다.");
        }
    }

    // 로그인한 실제 사용자를 꺼냄 - 비로그인이면 GlobalExceptionHandler가 /login으로 보내줌
    private User requireLoginUser(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AuthenticationRequiredException();
        }
        return getUserOrThrow(principal.getId(), "로그인 사용자");
    }

    /**
     * 웹소켓으로 연결된 브라우저들에게 실시간 메시지를 보내는 공통 헬퍼.
     * <p>
     * destination : 어느 채널로 보낼지 (예: "/topic/chat.2" - 2번 아티스트 채널을 구독 중인 모두에게 감)
     * payload : 보낼 내용물(누가, 무슨 말을, 언제 했는지 등을 담은 자료 상자)
     */
    private void broadcast(String destination, Map<String, Object> payload) {
        messagingTemplate.convertAndSend(destination, (Object) payload);
    }

    // 팬 전용 채팅방 화면 (CHAT-02) - 이 팬의 개인 채널 + 아티스트 방송 채널을 화면에서 구독하게 됨
    @GetMapping("/chat/room/fan")
    public String fanRoom(
            @RequestParam Long artistId,
            @RequestParam Long fanId,
            Model model
    ) {
        User artist = getUserOrThrow(artistId, "아티스트");
        User fan = getUserOrThrow(fanId, "팬");

        model.addAttribute("artistId", artistId);
        model.addAttribute("fanId", fanId);
        model.addAttribute("remaining", chatQuotaService.getRemaining(fan, artist));

        return "chat/fanChatRoom";
    }

    // 아티스트 전용 채팅방 화면 (CHAT-02) - 방송 채널 + 팬 메시지 중 랜덤으로 추려진 피드만 구독하게 됨
    @GetMapping("/chat/room/artist")
    public String artistRoom(
            @RequestParam Long artistId,
            Model model
    ) {
        User artist = getUserOrThrow(artistId, "아티스트");

        // 지난 대화 이력 - 예전엔 웹소켓 구독만 하고 이력을 안 내려줘서,
        // 새로고침하면 그전까지 오간 메시지가 전부 사라져 보였음
        List<Map<String, Object>> history = chatMessageService.getArtistRoomHistory(artist).stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("senderId", m.getSender().getId());
                    map.put("senderNickname", m.getSender().getNickname());
                    map.put("content", m.getContent());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return (Map<String, Object>) map;
                }).toList();

        model.addAttribute("artistId", artistId);
        model.addAttribute("history", history);
        return "chat/artistChatRoom";
    }

    /**
     * DM 인박스 목록 (와이어프레임 13번) - 이 팬이 대화 나눈 아티스트들 + 아직 대화 안 나눈 아티스트("추천").
     * 메인 페이지 우측 하단 플로팅 위젯(shell.js)이 열릴 때 이 API를 호출해서 실제 데이터로 채움.
     */
    @GetMapping("/chat/inbox")
    @ResponseBody
    public List<Map<String, Object>> inbox(@RequestParam Long fanId) {
        User fan = getUserOrThrow(fanId, "팬");

        List<DmInboxItem> items = chatMessageService.getInboxForFan(fan);

        return items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("artistId", item.getArtistId());
            map.put("artistNickname", item.getArtistNickname());
            map.put("hasConversation", item.isHasConversation());
            map.put("lastMessage", item.getLastMessage());
            map.put("lastMessageTime", item.getLastMessageTime() != null ? item.getLastMessageTime().toString() : null);
            map.put("membershipExpired", item.isMembershipExpired());
            return map;
        }).toList();
    }

    /**
     * 아티스트 자신의 채팅방(1대다 방송) 데이터를 JSON으로 내려줌 - DM 모달 안에서 쓰기 위함.
     * 화면(chat/artistChatRoom.html)과 같은 데이터(getArtistRoomHistory)를 쓰지만,
     * 페이지 이동 없이 모달에서 fetch로 채워야 해서 JSON 버전을 따로 둠.
     */
    @GetMapping("/chat/room-data/artist")
    @ResponseBody
    public Map<String, Object> artistRoomData(@RequestParam Long artistId) {
        User artist = getUserOrThrow(artistId, "아티스트");

        List<Map<String, Object>> messages = chatMessageService.getArtistRoomHistory(artist).stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("senderId", m.getSender().getId());
                    map.put("senderNickname", m.getSender().getNickname());
                    map.put("content", m.getContent());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return (Map<String, Object>) map;
                }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        return result;
    }

    /**
     * 브라우저가 웹소켓의 "/app/chat.send" 채널로 보낸 메시지를 처리함.
     * <p>
     *
     * @MessageMapping("/chat.send") : @PostMapping과 비슷한 역할이지만, HTTP 요청이 아니라
     * 웹소켓으로 온 메시지를 받을 때 씀. WebSocketConfig에서 "/app"을 접두사로 정해뒀기 때문에,
     * 실제로는 "/app/chat.send"로 온 메시지가 이 메서드로 연결됨.
     * <p>
     * 이 메서드는 return 값이 없음(void). 일반 컨트롤러처럼 "화면을 보여주는" 게 목적이 아니라,
     * 메시지를 검사하고 저장한 뒤 broadcast(...)로 관련된 사람들에게 실시간으로 뿌려주는 게 목적이기 때문.
     */
    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request, Authentication authentication) {

        // 빈 메시지나 잘못된 요청은 조용히 무시 (금칙어 검사에서 content가 null이면 NPE 나는 것 방지)
        if (request.getContent() == null || request.getContent().isBlank()) {
            return;
        }

        // 비로그인 상태로 온 메시지는 무시함 (예전엔 프론트가 fanId를 못 구하면 1번으로 기본값 처리해서,
        // 로그인 안 해도 1번 유저 명의로 메시지가 보내지는 문제가 있었음)
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser me)) {
            log.warn("비로그인 상태로 채팅 전송 시도 - 무시함 (artistId={})", request.getArtistId());
            return;
        }

        // 로그인한 사람과 요청에 담긴 senderId가 다르면(다른 사람 명의로 보내려는 시도) 거부
        if (!me.getId().equals(request.getSenderId())) {
            log.warn("senderId 위조 시도 감지: 로그인한 사용자={}, 요청 senderId={}", me.getId(), request.getSenderId());
            return;
        }

        // CHAT-03 : 금칙어가 포함되어 있으면 저장/방송하지 않고, 보낸 사람 본인에게만 경고를 돌려줌
        if (chatFilterService.containsBannedWord(request.getContent())) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "부적절한 언어가 포함되어 전송이 제한되었습니다.");

            // "/topic/chat.error.보낸사람ID" 채널은 그 사람만 구독하고 있으므로, 본인에게만 경고가 도착함
            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        User artist = getUserOrThrow(request.getArtistId(), "아티스트");
        User sender = getUserOrThrow(request.getSenderId(), "보낸 사람");
        User fan = request.getFanId() != null
                ? getUserOrThrow(request.getFanId(), "팬")
                : null;

        // 이 메시지가 "팬 본인이 보낸 것"인지 여부. 아티스트가 특정 팬과의 DM 방에서 답장을 보낼 때도
        // fan 필드는 채워져 있지만(어느 팬과의 대화인지 구분용), 실제로 보낸 사람은 아티스트이므로
        // 팬 전용 제약(멤버십/하루 한도)을 걸면 안 됨. 그동안 fan != null만 보고 체크해서,
        // 아티스트가 멤버십 만료된 팬에게 답장하거나, 그 팬의 한도를 대신 소진시켜버리는 문제가 있었음
        boolean sentByFan = fan != null && sender.getId().equals(fan.getId());

        // 와이어프레임 19번: 멤버십이 없거나 만료된 팬은 DM을 보낼 수 없음.
        // 그동안 프론트(dm-realtime.js)에서 입력창만 숨기고 서버 검증이 없어서,
        // 웹소켓으로 직접 쏘면 미가입자도 전송이 됐음
        if (sentByFan && chatMessageService.isMembershipExpired(fan, artist)) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "멤버십에 가입해야 DM을 보낼 수 있습니다.");

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        // CHAT-05 : 팬이 보낸 메시지인 경우에만 하루 전송 한도를 체크함 (아티스트 방송/답장은 한도 없음)
        if (sentByFan && !chatQuotaService.tryConsume(fan, artist)) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", "오늘 보낼 수 있는 메시지 횟수를 다 사용했습니다. 내일 다시 채워집니다.");

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        // CHAT-02 비대칭 수신 : 팬 메시지는 30% 확률로만 아티스트 화면에 노출됨(도배 방지).
        // 실시간 전송과 새로고침 후 히스토리가 어긋나지 않도록, 여기서 한 번 정한 값을
        // 그대로 DB에 저장해두고 아티스트 화면 히스토리도 이 값을 기준으로 걸러냄
        boolean visibleToArtist = (fan == null) || (Math.random() < 0.3);

        ChatMessage saved = chatMessageService.saveMessage(artist, fan, sender, request.getContent(), visibleToArtist);

        // 엔티티(ChatMessage)를 그대로 방송하지 않고, 화면에 필요한 값만 뽑아서 새 자료 상자(payload)에 담아 보냄
        // (User 엔티티 안에는 비밀번호 등 민감한 정보가 들어있어서, 그걸 그대로 브라우저에 보내면 안 되기 때문)
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("fanId", fan != null ? fan.getId() : null);
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        if (fan != null) {
            payload.put("remaining", chatQuotaService.getRemaining(fan, artist));
        }

        // CHAT-02 비대칭 수신 : 방송이냐 개인 메시지냐에 따라 어느 채널로 보낼지가 달라짐
        if (fan == null) {
            // 아티스트가 보낸 방송(공지) 메시지 - 아티스트 채널을 구독한 모든 팬에게 전달
            broadcast("/topic/chat." + artist.getId(), payload);
        } else {
            // 팬이 보낸 개인 메시지 - 그 팬 개인 채널(본인+아티스트만 구독)에는 무조건 전달됨
            broadcast("/topic/chat." + artist.getId() + ".fan." + fan.getId(), payload);

            // 위에서 정해둔 노출 여부에 따라, 아티스트가 보는 "추천 피드" 채널에도 추가로 보냄
            if (visibleToArtist) {
                broadcast("/topic/chat." + artist.getId() + ".artistFeed", payload);
            }
        }
    }

    /**
     * DM 방 하나를 열 때 필요한 데이터(지난 대화 이력 + 오늘 남은 전송 횟수)를 한 번에 내려줌.
     * 플로팅 위젯(shell.js)이 DM 목록에서 아티스트를 클릭하면 이 API로 방 데이터를 채운 뒤 화면을 그림.
     */
    @GetMapping("/chat/room-data")
    @ResponseBody
    public Map<String, Object> roomData(@RequestParam Long artistId, @RequestParam Long fanId) {
        User artist = getUserOrThrow(artistId, "아티스트");
        User fan = getUserOrThrow(fanId, "팬");

        List<Map<String, Object>> messages = chatMessageService.getConversation(artist, fan).stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("senderId", m.getSender().getId());
                    map.put("senderNickname", m.getSender().getNickname());
                    map.put("content", m.getContent());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return (Map<String, Object>) map;
                }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("artistNickname", artist.getNickname());
        result.put("remaining", chatQuotaService.getRemaining(fan, artist));
        result.put("messages", messages);
        result.put("membershipExpired", chatMessageService.isMembershipExpired(fan, artist));
        return result;
    }

    // 금칙어 관리 화면 (CHAT-04) - 관리자만 접근 가능
    // 예전엔 testUserId 파라미터로 관리자 여부를 판단해서, ?testUserId=3 만 붙이면
    // 로그인하지 않은 사람도 금칙어를 등록/삭제할 수 있었음 -> 실제 로그인 계정 기준으로 변경
    @GetMapping("/chat/admin/keywords")
    public String keywordList(
            @AuthenticationPrincipal AuthenticatedUser principal,
            Model model
    ) {
        requireAdmin(requireLoginUser(principal));

        model.addAttribute("keywords", chatFilterService.getAllKeywords());

        return "chat/keywordManage";
    }

    // 금칙어 등록 - fetch로 온 요청이면 목록 부분(fragment)만 새로 그려서 페이지 새로고침 없이 갱신
    @PostMapping("/chat/admin/keywords")
    public String addKeyword(
            @RequestParam String keyword,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        requireAdmin(requireLoginUser(principal));

        chatFilterService.addKeyword(keyword);

        if ("fetch".equals(requestedWith)) {
            model.addAttribute("keywords", chatFilterService.getAllKeywords());
            return "chat/keywordManage :: keywordListFragment";
        }

        return "redirect:/chat/admin/keywords";
    }

    // 금칙어 삭제 - 등록과 같은 방식
    @PostMapping("/chat/admin/keywords/{id}/delete")
    public String deleteKeyword(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model
    ) {
        requireAdmin(requireLoginUser(principal));

        chatFilterService.deleteKeyword(id);

        if ("fetch".equals(requestedWith)) {
            model.addAttribute("keywords", chatFilterService.getAllKeywords());
            return "chat/keywordManage :: keywordListFragment";
        }

        return "redirect:/chat/admin/keywords";
    }

    // AI 팬 메시지 생성 (CHAT-06, 선택 기능/시연용) - 실제 팬이 아니라
    // 채팅방이 한산할 때 화면을 채워 보여주기 위한 가짜 메시지. 이건 웹소켓이 아니라 일반 fetch로 호출됨
    @PostMapping("/chat/room/artist/ai-fan")
    @ResponseBody
    public Map<String, Object> generateAiFan(
            @RequestParam Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        // 이 엔드포인트는 호출될 때마다 Gemini API가 실제로 돌고 chat_message에 저장까지 됨.
        // 그동안 인증 확인이 없어서 비로그인 상태로 반복 호출하면 API 한도를 소진시킬 수 있었음.
        // 채팅방을 쓰는 아티스트 본인만 호출할 수 있도록 제한함
        User requester = requireLoginUser(principal);
        if (!requester.getId().equals(artistId)) {
            throw new IllegalStateException("본인 채팅방에서만 사용할 수 있습니다.");
        }

        User artist = getUserOrThrow(artistId, "아티스트");
        User aiFan = userRepository.findByUsername("aifan_bot")
                .orElseThrow(() -> new IllegalStateException("AI 팬 계정(username=aifan_bot)이 없습니다."));

        String content = aiFanChatService.generateFanMessage();

        ChatMessage saved = chatMessageService.saveMessage(artist, aiFan, aiFan, content);

        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", aiFan.getId());
        payload.put("senderNickname", aiFan.getNickname());
        payload.put("fanId", aiFan.getId());
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        // fetch로 온 요청이지만, 결과는 요청 보낸 사람에게 직접 응답하는 대신
        // 웹소켓 채널로 방송해서 화면에 실시간으로 나타나게 함 (채팅 메시지들과 같은 방식으로 보이도록)
        broadcast("/topic/chat." + artist.getId() + ".artistFeed", payload);

        return Map.of("success", true);
    }
}
