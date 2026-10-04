package megane6.weplanet.service;

import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ChatMessageRepository;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.community.CommunityArtistResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 멤버별 DM: 그룹은 멤버 한 명 한 명이 DM 상대, 멤버십은 그룹 단위로 확인한다
class ChatMessageServiceTest {

	private static final Long FAN = 1L;
	private static final Long GROUP = 10L;
	private static final Long MEMBER_A = 11L;
	private static final Long MEMBER_B = 12L;
	private static final Long SOLO = 20L;

	private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
	private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
	private final CommunityArtistResolver resolver = new CommunityArtistResolver(groupMemberRepository);
	private final ChatMessageService service = new ChatMessageService(
			chatMessageRepository, userRepository, membershipRepository, groupMemberRepository, resolver);

	private final User fan = user(FAN, Role.FAN, "fan");
	private final User group = user(GROUP, Role.ARTIST, "GROUP");
	private final User memberA = user(MEMBER_A, Role.ARTIST_MEMBER, "A");
	private final User memberB = user(MEMBER_B, Role.ARTIST_MEMBER, "B");
	private final User solo = user(SOLO, Role.ARTIST, "SOLO");

	// 인박스: 그룹 계정 대신 멤버들이 각자 한 줄씩, 솔로는 본인 그대로. 예전 그룹 단위 방 메시지는 안 보인다
	@Test
	void inboxListsGroupMembersInsteadOfGroupAccount() {
		when(userRepository.findByRole(Role.ARTIST)).thenReturn(List.of(group, solo));
		when(groupMemberRepository.findByGroupIdInAndLeftAtIsNullOrderByIdAsc(any())).thenReturn(List.of(
				GroupMember.join(GROUP, memberA),
				GroupMember.join(GROUP, memberB)));
		when(chatMessageRepository.findByFanOrderByCreatedAtDesc(fan)).thenReturn(List.of(
				message(memberB, "B와의 대화"),
				message(group, "예전 그룹 단위 방")));

		List<DmInboxItem> inbox = service.getInboxForFan(fan);

		assertEquals(List.of(MEMBER_B, MEMBER_A, SOLO), inbox.stream().map(DmInboxItem::getArtistId).toList());
		assertTrue(inbox.get(0).isHasConversation());
		assertEquals("B와의 대화", inbox.get(0).getLastMessage());
		assertEquals("GROUP", inbox.get(0).getGroupName());
		assertFalse(inbox.get(1).isHasConversation());
		assertNull(inbox.get(2).getGroupName());
	}

	// 멤버와의 DM 도 멤버십은 소속 그룹 기준으로 확인한다 (멤버 id 로 찾으면 기록이 없어 항상 만료로 막힘)
	@Test
	void membershipForMemberRoomIsCheckedAgainstGroup() {
		when(groupMemberRepository.findByMember_IdAndLeftAtIsNull(MEMBER_A))
				.thenReturn(Optional.of(GroupMember.join(GROUP, memberA)));
		when(userRepository.findById(GROUP)).thenReturn(Optional.of(group));
		when(membershipRepository.findByFanAndArtist(fan, group)).thenReturn(Optional.of(
				Membership.builder().expiresAt(LocalDateTime.now().plusDays(1)).build()));

		assertFalse(service.isMembershipExpired(fan, memberA));
	}

	// 방 주인 규칙: 솔로/활동 멤버는 방 주인, 활동 멤버가 있는 그룹 계정과 팬은 아니다
	@Test
	void dmRoomOwnerRules() {
		when(groupMemberRepository.countByGroupIdAndLeftAtIsNull(GROUP)).thenReturn(2L);
		when(groupMemberRepository.countByGroupIdAndLeftAtIsNull(SOLO)).thenReturn(0L);
		when(groupMemberRepository.findByMember_IdAndLeftAtIsNull(MEMBER_A))
				.thenReturn(Optional.of(GroupMember.join(GROUP, memberA)));
		when(groupMemberRepository.findByMember_IdAndLeftAtIsNull(MEMBER_B)).thenReturn(Optional.empty());

		assertTrue(resolver.isDmRoomOwner(solo));
		assertTrue(resolver.isDmRoomOwner(memberA));
		assertFalse(resolver.isDmRoomOwner(memberB)); // 탈퇴한 멤버
		assertFalse(resolver.isDmRoomOwner(group));
		assertFalse(resolver.isDmRoomOwner(fan));
	}

	private ChatMessage message(User room, String content) {
		return ChatMessage.builder()
				.artist(room)
				.fan(fan)
				.sender(fan)
				.content(content)
				.createdAt(LocalDateTime.now())
				.build();
	}

	private static User user(Long id, Role role, String nickname) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getRole()).thenReturn(role);
		when(user.getNickname()).thenReturn(nickname);
		return user;
	}
}
