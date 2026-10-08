package megane6.weplanet.controller.chat;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.chat.AiFanChatService;
import megane6.weplanet.service.chat.ChatFilterService;
import megane6.weplanet.service.chat.ChatMessageService;
import megane6.weplanet.service.chat.ChatQuotaService;
import org.springframework.context.MessageSource;
import megane6.weplanet.service.community.CommunityArtistResolver;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 채팅 화면(HTTP)과 실시간 메시지(@MessageMapping, STOMP)를 처리하는 컨트롤러. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    // 웹소켓 구독자에게 메시지를 보내는 도구.
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatFilterService chatFilterService;
    private final ChatQuotaService chatQuotaService;
    private final AiFanChatService aiFanChatService;
    private final MessageSource messageSource;
    private final megane6.weplanet.i18n.Messages messages;
    private final CommunityArtistResolver communityArtistResolver;

    // 웹소켓 요청엔 세션 로케일이 없어 보낸 사람의 선호 언어로 문구를 만든다.
    private String chatMsg(String code, User forUser) {
        Locale locale = PreferredLocaleResolver.toLocale(forUser.getPreferredLanguage());
        return messageSource.getMessage(code, null, locale);
    }

    // 금칙어 관리 화면(HTTP 요청) 결과 문구 - 요청 로케일 기준
    private String msg(String code) {
        return messageSource.getMessage(code, null,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());
    }

    // 유저 조회 공통 메서드 (label 은 로그용 대상 이름).
    private User getUserOrThrow(Long userId, String label) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("error.community.userNotFound"));
    }

    // 관리자가 아니면 예외.
    private void requireAdmin(User requester) {
        if (requester.getRole() != Role.ADMIN) {
            throw new IllegalStateException("error.admin.adminOnly");
        }
    }

    // 로그인 사용자 조회 (비로그인이면 /login 으로 이동).
    private User requireLoginUser(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AuthenticationRequiredException();
        }
        return getUserOrThrow(principal.getId(), "로그인 사용자");
    }

    /** 웹소켓 채널(destination)로 payload 를 보내는 공통 메서드. */
    private void broadcast(String destination, Map<String, Object> payload) {
        messagingTemplate.convertAndSend(destination, (Object) payload);
    }

    // 팬 채팅방 화면 - 팬 개인 채널과 아티스트 방송 채널을 구독한다.
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

    // 아티스트 채팅방 화면 - 방송 채널과 팬 메시지 추천 피드를 구독한다.
    @GetMapping("/chat/room/artist")
    public String artistRoom(
            @RequestParam Long artistId,
            Model model
    ) {
        User artist = getUserOrThrow(artistId, "아티스트");

        // 새로고침해도 대화가 남도록 지난 대화 이력을 함께 내려준다.
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

    /** DM 인박스 목록 - 대화한 아티스트와 추천 아티스트 (플로팅 위젯이 호출). */
    @GetMapping("/chat/inbox")
    @ResponseBody
    public List<Map<String, Object>> inbox(@RequestParam Long fanId) {
        User fan = getUserOrThrow(fanId, "팬");

        List<DmInboxItem> items = chatMessageService.getInboxForFan(fan);

        return items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("artistId", item.getArtistId());
            map.put("artistNickname", item.getArtistNickname());
            map.put("groupName", item.getGroupName());
            map.put("hasConversation", item.isHasConversation());
            map.put("lastMessage", item.getLastMessage());
            map.put("lastMessageTime", item.getLastMessageTime() != null ? item.getLastMessageTime().toString() : null);
            map.put("membershipExpired", item.isMembershipExpired());
            map.put("neverSubscribed", item.isNeverSubscribed());
            return map;
        }).toList();
    }

    /** 안 읽은 DM 개수 계산용 - 최근 7일 방 주인 메시지 시각과 서버 시각을 내려준다. */
    @GetMapping("/chat/unread-source")
    @ResponseBody
    public Map<String, Object> unreadSource(@AuthenticationPrincipal AuthenticatedUser principal) {
        Map<String, Object> result = new HashMap<>();
        result.put("serverNow", System.currentTimeMillis());
        if (principal == null || !"ROLE_FAN".equals(principal.getRoleName())) {
            result.put("rooms", List.of());
            return result;
        }
        User fan = getUserOrThrow(principal.getId(), "팬");
        ZoneId zone = ZoneId.systemDefault();
        List<Map<String, Object>> rooms = chatMessageService
                .getRecentOwnerMessageTimes(fan, LocalDateTime.now().minusDays(7))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> room = new HashMap<>();
                    room.put("artistId", entry.getKey());
                    room.put("times", entry.getValue().stream()
                            .map(time -> time.atZone(zone).toInstant().toEpochMilli())
                            .toList());
                    return room;
                }).toList();
        result.put("rooms", rooms);
        return result;
    }

    /** 아티스트 채팅방 데이터 (DM 모달에서 쓰는 JSON 버전). */
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

    /** /app/chat.send 로 온 메시지를 검사·저장한 뒤 관련 채널로 보낸다. */
    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request, Authentication authentication) {

        // 빈 메시지는 무시한다.
        if (request.getContent() == null || request.getContent().isBlank()) {
            return;
        }

        // 비로그인 메시지는 무시한다.
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser me)) {
            log.warn("비로그인 상태로 채팅 전송 시도 - 무시함 (artistId={})", request.getArtistId());
            return;
        }

        // 다른 사람 명의로 보내는 요청은 거부한다.
        if (!me.getId().equals(request.getSenderId())) {
            log.warn("senderId 위조 시도 감지: 로그인한 사용자={}, 요청 senderId={}", me.getId(), request.getSenderId());
            return;
        }

        // 경고 문구 언어는 보낸 사람 기준이라 sender 를 먼저 조회한다.
        User sender = getUserOrThrow(request.getSenderId(), "보낸 사람");

        // 금칙어가 있으면 저장·전송하지 않고 보낸 사람에게만 경고한다.
        if (chatFilterService.containsBannedWord(request.getContent())) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", chatMsg("chat.warning.bannedWord", sender));
            // 경고 종류 (화면은 DAILY_LIMIT 일 때만 남은 횟수를 0으로 표시).
            warning.put("reason", "BANNED_WORD");

            // 보낸 사람만 구독하는 채널이라 본인에게만 경고가 간다.
            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        // artistId 는 DM 방 주인 - 솔로 아티스트 본인 또는 그룹 멤버 한 명.
        User artist = getUserOrThrow(request.getArtistId(), "DM 방 주인");
        if (!communityArtistResolver.isDmRoomOwner(artist)) {
            log.warn("DM 방 주인이 아닌 계정으로 전송 시도: artistId={}", artist.getId());
            return;
        }
        User fan = request.getFanId() != null
                ? getUserOrThrow(request.getFanId(), "팬")
                : null;

        // 팬이 직접 보낸 메시지인지 (아티스트 답장에는 팬 전용 제약을 걸지 않음).
        boolean sentByFan = fan != null && sender.getId().equals(fan.getId());
        
        // 팬이 아니면 DM 방 주인 본인만 보낼 수 있다.
        if (!sentByFan && !sender.getId().equals(artist.getId())) {
            log.warn("아티스트가 아닌 계정의 아티스트 채널 전송 시도: senderId={}, artistId={}",
                    sender.getId(), artist.getId());
            return;
        }

        // 멤버십이 없거나 만료된 팬은 DM 을 보낼 수 없다.
        if (sentByFan && chatMessageService.isMembershipExpired(fan, artist)) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", chatMsg("chat.warning.membershipRequired", sender));
            warning.put("reason", "MEMBERSHIP_REQUIRED");

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        // 팬 메시지만 하루 전송 한도를 확인한다.
        if (sentByFan && !chatQuotaService.tryConsume(fan, artist)) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("error", true);
            warning.put("message", chatMsg("chat.warning.dailyLimitExceeded", sender));
            warning.put("reason", "DAILY_LIMIT");
            warning.put("remaining", 0);

            broadcast("/topic/chat.error." + request.getSenderId(), warning);

            return;
        }

        // 팬 메시지는 30% 확률로만 아티스트 화면에 노출하고, 그 값을 DB 에 저장해 이력과 맞춘다.
        boolean visibleToArtist = (fan == null) || (Math.random() < 0.3);

        ChatMessage saved = chatMessageService.saveMessage(artist, fan, sender, request.getContent(), visibleToArtist);

        // 엔티티 대신 화면에 필요한 값만 담아 보낸다 (민감 정보 노출 방지).
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", sender.getId());
        payload.put("senderNickname", sender.getNickname());
        payload.put("fanId", fan != null ? fan.getId() : null);
        payload.put("content", saved.getContent());
        payload.put("createdAt", saved.getCreatedAt().toString());

        if (fan != null) {
            payload.put("remaining", chatQuotaService.getRemaining(fan, artist));
        }

        // 방송인지 개인 메시지인지에 따라 채널이 다르다.
        if (fan == null) {
            // 아티스트가 보낸 메시지 - 아티스트 채널을 구독한 모든 팬에게 전달
            broadcast("/topic/chat." + artist.getId(), payload);

            // 아티스트 본인이 DM을 보낸 경우에만 가상 팬 5명이 백그라운드에서 답장한다
            if (sender.getId().equals(artist.getId())) {
                aiFanChatService.replyToArtistDm(artist.getId(), saved.getContent());
            }
        } else {
            // 팬이 보낸 개인 메시지 - 그 팬 개인 채널(본인+아티스트만 구독)에는 무조건 전달됨
            broadcast("/topic/chat." + artist.getId() + ".fan." + fan.getId(), payload);

            // 위에서 정해둔 노출 여부에 따라, 아티스트가 보는 "추천 피드" 채널에도 추가로 보냄
            if (visibleToArtist) {
                broadcast("/topic/chat." + artist.getId() + ".artistFeed", payload);
            }
        }
    }

    /** DM 방 데이터 - 지난 대화와 오늘 남은 전송 횟수. */
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
        // 한 번도 가입하지 않은 팬이면 가입 안내 문구를 보여준다.
        result.put("neverSubscribed", chatMessageService.isNeverSubscribed(fan, artist));
        return result;
    }

    // 금칙어 관리 화면 (관리자만).
    @GetMapping("/chat/admin/keywords")
    public String keywordList(@AuthenticationPrincipal AuthenticatedUser principal,
                             Model model) {
        requireAdmin(requireLoginUser(principal));
        
        model.addAttribute("keywords", chatFilterService.getAllKeywords());
        
        return "chat/keywordManage";
    }
    
    // 금칙어 등록
    @PostMapping("/chat/admin/keywords")
    public String addKeyword(
            @RequestParam String keyword,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request,
            @RequestHeader(
                    value = "X-Requested-With",
                    required = false
            ) String requestedWith,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        requireAdmin(requireLoginUser(principal));
        
        return handleKeywordMutation(
                () -> chatFilterService.addKeyword(
                        keyword,
                        principal.getId(),
                        request.getRemoteAddr()
                ),
                msg("chat.keyword.added"),
                requestedWith,
                model,
                redirectAttributes
        );
    }
    
    // 금칙어 수정
    @PostMapping("/chat/admin/keywords/{id}/update")
    public String updateKeyword(
            @PathVariable Long id,
            @RequestParam String keyword,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request,
            @RequestHeader(
                    value = "X-Requested-With",
                    required = false
            ) String requestedWith,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        requireAdmin(requireLoginUser(principal));
        
        return handleKeywordMutation(
                () -> chatFilterService.updateKeyword(
                        id,
                        keyword,
                        principal.getId(),
                        request.getRemoteAddr()
                ),
                msg("chat.keyword.updated"),
                requestedWith,
                model,
                redirectAttributes
        );
    }
    
    // 금칙어 삭제
    @PostMapping("/chat/admin/keywords/{id}/delete")
    public String deleteKeyword(@PathVariable Long id,
                                @AuthenticationPrincipal AuthenticatedUser principal,
                                HttpServletRequest request,
                                @RequestHeader(
                                        value = "X-Requested-With",
                                        required = false
                                ) String requestedWith,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        requireAdmin(requireLoginUser(principal));
        
        return handleKeywordMutation(
                () -> chatFilterService.deleteKeyword(
                        id, principal.getId(), request.getRemoteAddr()),
                msg("chat.keyword.deleted"),
                requestedWith,
                model,
                redirectAttributes);
    }
    
    // 등록·수정·삭제 결과를 AJAX 또는 일반 요청에 맞게 반환
    private String handleKeywordMutation(Runnable action,
                                         String successMessage,
                                         String requestedWith,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        String errorMessage = null;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            // 값을 들고 다니는 예외도 {0} 까지 번역한다.
            errorMessage = messages.resolve(e);
        }
        
        if ("fetch".equals(requestedWith)) {
            model.addAttribute(
                    "keywords",
                    chatFilterService.getAllKeywords()
            );
            
            if (errorMessage == null) {
                model.addAttribute(
                        "keywordMessage",
                        successMessage
                );
            } else {
                model.addAttribute(
                        "keywordError",
                        errorMessage
                );
            }
            
            return "chat/keywordManage :: keywordListFragment";
        }
        
        if (errorMessage == null) {
            redirectAttributes.addFlashAttribute("keywordMessage", successMessage);
        } else {
            redirectAttributes.addFlashAttribute("keywordError", errorMessage);
        }
        
        return "redirect:/chat/admin/keywords";
    }
    
    // 아티스트 쪽 계정의 내 DM 방 번호 (솔로·멤버 모두 본인 id, 방 주인이 아니면 null).
    @GetMapping("/chat/my-artist-room")
    @ResponseBody
    public Map<String, Object> myArtistRoom(@AuthenticationPrincipal AuthenticatedUser principal) {
        User me = requireLoginUser(principal);

        // Map.of 는 null 을 못 넣어 HashMap 을 쓴다.
        Map<String, Object> result = new HashMap<>();
        result.put("artistId", communityArtistResolver.isDmRoomOwner(me) ? me.getId() : null);
        return result;
    }

    // 시연용 AI 팬 메시지 생성 버튼 (fetch 호출).
    @PostMapping("/chat/room/artist/ai-fan")
    @ResponseBody
    public Map<String, Object> generateAiFan(
            @RequestParam Long artistId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        // Gemini 호출·저장이 일어나므로 채팅방 주인 아티스트만 호출할 수 있다.
        User requester = requireLoginUser(principal);
        if (!requester.getId().equals(artistId) || !communityArtistResolver.isDmRoomOwner(requester)) {
            throw new IllegalStateException("error.chat.ownRoomOnly");
        }

        getUserOrThrow(artistId, "아티스트");
        aiFanChatService.replyToArtistDm(artistId, null);

        return Map.of("success", true);
    }
}
