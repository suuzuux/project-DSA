package megane6.weplanet.controller.chat;

import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.MembershipOrder;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.artist.ArtistAccountProfileRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.chat.ChatMessageService;
import megane6.weplanet.service.chat.ChatQuotaService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

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
    private final ChatController controller = new ChatController(chats, users, null, null,
            mock(ChatQuotaService.class), null, null, null, resolver, profiles);
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

    private static User user(Long id, Role role, String nickname) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.getNickname()).thenReturn(nickname);
        return user;
    }
}
