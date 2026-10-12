package megane6.weplanet.controller.chat;

import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.dto.ChatMessageRequest;
import megane6.weplanet.repository.artist.ArtistAccountProfileRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.chat.ChatMessageService;
import megane6.weplanet.service.chat.ChatQuotaService;
import megane6.weplanet.service.chat.ChatFilterService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.domain.dto.community.CommunityAuthorView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    private final ChatMessageService chats = mock(ChatMessageService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CommunityArtistResolver resolver = mock(CommunityArtistResolver.class);
    private final ArtistAccountProfileRepository profiles = mock(ArtistAccountProfileRepository.class);
    private final CommunityJoinService communityProfiles = mock(CommunityJoinService.class);
    private final ChatController controller = new ChatController(chats, users, null, null,
            mock(ChatQuotaService.class), null, null, null, resolver, profiles, communityProfiles);
    private final User fan = user(1L, Role.FAN, "팬");
    private final User member = user(11L, Role.ARTIST_MEMBER, "시온");
    private final User group = user(10L, Role.ARTIST, "NOVA");
    private final AuthenticatedUser principal = AuthenticatedUser.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(fan));
        when(users.findById(11L)).thenReturn(Optional.of(member));
        when(users.findById(10L)).thenReturn(Optional.of(group));
        when(resolver.isDmRoomOwner(member)).thenReturn(true);
        when(resolver.ownCommunityId(member)).thenReturn(10L);
        when(profiles.findByUser_Id(11L)).thenReturn(Optional.empty());
    }

    @Test
    void neverSubscribedMemberRoomLinksToGroupWithoutExposingMessages() {
        when(chats.isNeverSubscribed(fan, member)).thenReturn(true);
        when(chats.isMembershipExpired(fan, member)).thenReturn(true);

        Map<String, Object> result = controller.roomData(11L, 1L, principal);

        assertEquals(10L, result.get("communityId"));
        assertEquals("NOVA", result.get("communityName"));
        assertEquals("시온", result.get("artistNickname"));
        assertEquals(MembershipOrder.YEARLY_PRICE, result.get("membershipYearlyPrice"));
        assertEquals(true, result.get("neverSubscribed"));
        assertEquals(List.of(), result.get("messages"));
        verify(chats, never()).getConversation(any(), any());
    }

    @Test
    void cancelledOrExpiredFanKeepsConversationAndReceivesExpiredState() {
        ChatMessage message = ChatMessage.builder().artist(member).fan(fan).sender(fan)
                .content("이전 대화").createdAt(LocalDateTime.of(2026, 9, 16, 12, 0)).build();
        when(chats.isNeverSubscribed(fan, member)).thenReturn(false);
        when(chats.isMembershipExpired(fan, member)).thenReturn(true);
        when(chats.getConversation(member, fan)).thenReturn(List.of(message));

        Map<String, Object> result = controller.roomData(11L, 1L, principal);

        assertEquals(false, result.get("neverSubscribed"));
        assertEquals(true, result.get("membershipExpired"));
        assertEquals("이전 대화", ((Map<?, ?>) ((List<?>) result.get("messages")).getFirst()).get("content"));
    }

    @Test
    void anotherFansConversationCannotBeRequested() {
        assertThrows(AccessDeniedException.class, () -> controller.roomData(11L, 2L, principal));
        verifyNoInteractions(chats);
    }

    @Test
    void historyUsesNicknameOfRoomOwnersCommunity() {
        User solo = user(20L, Role.ARTIST, "솔로");
        when(users.findById(20L)).thenReturn(Optional.of(solo));
        when(resolver.isDmRoomOwner(solo)).thenReturn(true);
        when(resolver.ownCommunityId(solo)).thenReturn(20L);
        when(chats.getConversation(member, fan)).thenReturn(List.of(message(member, fan)));
        when(chats.getConversation(solo, fan)).thenReturn(List.of(message(solo, fan)));
        when(communityProfiles.authorViewsByAuthorIdKey(List.of(fan), 10L))
                .thenReturn(Map.of("1", new CommunityAuthorView("@@", null)));
        when(communityProfiles.authorViewsByAuthorIdKey(List.of(fan), 20L))
                .thenReturn(Map.of("1", new CommunityAuthorView("##", null)));

        assertEquals("@@", senderNickname(controller.roomData(11L, 1L, principal)));
        assertEquals("##", senderNickname(controller.roomData(20L, 1L, principal)));
        when(chats.getArtistRoomHistory(member)).thenReturn(List.of(message(member, fan)));
        assertEquals("@@", senderNickname(controller.artistRoomData(11L)));
    }

    @Test
    void realtimeFanMessageUsesGroupsCommunityNickname() {
        SimpMessagingTemplate websocket = mock(SimpMessagingTemplate.class);
        ChatQuotaService quota = mock(ChatQuotaService.class);
        ChatController realtime = new ChatController(chats, users, websocket, mock(ChatFilterService.class),
                quota, null, null, null, resolver, profiles, communityProfiles);
        ChatMessageRequest request = new ChatMessageRequest();
        request.setArtistId(11L);
        request.setFanId(1L);
        request.setSenderId(1L);
        request.setContent("안녕");
        when(quota.tryConsume(fan, member)).thenReturn(true);
        when(chats.saveMessage(eq(member), eq(fan), eq(fan), eq("안녕"), anyBoolean()))
                .thenReturn(message(member, fan));
        when(communityProfiles.displayNickname(fan, 10L)).thenReturn("@@");

        realtime.send(request, UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(websocket).convertAndSend(eq("/topic/chat.11.fan.1"), payload.capture());
        assertEquals("@@", ((Map<?, ?>) payload.getValue()).get("senderNickname"));
    }

    @Test
    void keywordPageUsesTenPageNumberButtonsForLargeList() {
        ChatFilterService filters = mock(ChatFilterService.class);
        ChatController keywords = new ChatController(chats, users, null, filters,
                null, null, null, null, resolver, profiles, communityProfiles);
        User admin = user(999L, Role.ADMIN, "관리자");
        when(users.findById(999L)).thenReturn(Optional.of(admin));
        when(filters.getAllKeywords()).thenReturn(java.util.stream.IntStream.range(0, 4860)
                .mapToObj(n -> megane6.weplanet.domain.entity.FilterKeyword.builder()
                        .id((long)n).keyword("단어" + n).build()).toList());
        var model = new org.springframework.ui.ExtendedModelMap();
        assertEquals("chat/keywordManage", keywords.keywordList(
                AuthenticatedUser.builder().id(999L).build(), model));
        var navigation = (megane6.weplanet.controller.admin.AdminPagination.Navigation) model.get("keywordsPagination");
        assertEquals(10, navigation.links().size());
        assertEquals(324, navigation.totalPages());
        assertEquals(15, ((List<?>) model.get("keywords")).size());
    }

    private ChatMessage message(User artist, User sender) {
        return ChatMessage.builder().artist(artist).fan(fan).sender(sender)
                .content("안녕").createdAt(LocalDateTime.now()).build();
    }

    private Object senderNickname(Map<String, Object> room) {
        return ((Map<?, ?>) ((List<?>) room.get("messages")).getFirst()).get("senderNickname");
    }

    private static User user(Long id, Role role, String nickname) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getNickname()).thenReturn(nickname);
        return user;
    }
}
