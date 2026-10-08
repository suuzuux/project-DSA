package megane6.weplanet.service.chat;
import megane6.weplanet.service.membership.MembershipService;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.chat.ChatMessageRepository;
import megane6.weplanet.repository.artist.GroupMemberRepository;
import megane6.weplanet.repository.membership.MembershipPeriodRepository;
import megane6.weplanet.repository.membership.MembershipRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.community.CommunityArtistResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPeriodRepository membershipPeriodRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CommunityArtistResolver communityArtistResolver;

    // 채팅 메시지 저장 (fan 이 null 이면 방송)
    @Transactional
    public ChatMessage saveMessage(User artist, User fan, User sender, String content) {
        return saveMessage(artist, fan, sender, content, true);
    }

    // visibleToArtist 는 전송 시 정한 노출 여부 (이력과 일치하도록 저장).
    @Transactional
    public ChatMessage saveMessage(User artist, User fan, User sender, String content, boolean visibleToArtist) {
        ChatMessage message = ChatMessage.builder()
                .artist(artist)
                .fan(fan)
                .sender(sender)
                .content(content)
                .visibleToArtist(visibleToArtist)
                .build();

        return chatMessageRepository.save(message);
    }

    /** DM 인박스 - 대화한 상대(최근순) 뒤에 나머지를 추천으로 붙인다 (상대는 솔로 본인 또는 멤버). */
    @Transactional(readOnly = true)
    public List<DmInboxItem> getInboxForFan(User fan) {
        // 지금 DM 방을 열 수 있는 상대 전체
        Map<Long, DmRoomOwner> owners = dmRoomOwners();

        // 최신순이라 상대별 첫 메시지가 마지막 메시지다.
        List<ChatMessage> messages = chatMessageRepository.findByFanOrderByCreatedAtDesc(fan);

        // 먼저 나온(최근 대화) 상대 순서를 유지한다.
        Map<Long, DmInboxItem> conversations = new LinkedHashMap<>();
        for (ChatMessage message : messages) {
            Long roomId = message.getArtist().getId();
            DmRoomOwner owner = owners.get(roomId);
            if (owner == null || conversations.containsKey(roomId)) {
                continue; // 방 주인이 아니거나 이미 찾은 상대면 건너뛴다.
            }
            conversations.put(roomId, inboxItem(fan, owner)
                    .lastMessage(message.getContent())
                    .lastMessageTime(message.getCreatedAt())
                    .hasConversation(true)
                    .build());
        }

        List<DmInboxItem> result = new ArrayList<>(conversations.values());

        // 대화 이력이 없는 상대는 추천으로 붙인다.
        for (DmRoomOwner owner : owners.values()) {
            if (!conversations.containsKey(owner.user().getId())) {
                result.add(inboxItem(fan, owner)
                        .hasConversation(false)
                        .build());
            }
        }

        return result;
    }

    /** 안 읽은 DM 계산용 - 멤버십이 유효한 방별로 방 주인 메시지 시각 (읽음 위치는 브라우저가 기억). */
    @Transactional(readOnly = true)
    public Map<Long, List<LocalDateTime>> getRecentOwnerMessageTimes(User fan, LocalDateTime since) {
        Map<Long, Boolean> activeByCommunity = new HashMap<>();
        Map<Long, List<LocalDateTime>> result = new LinkedHashMap<>();
        for (DmRoomOwner owner : dmRoomOwners().values()) {
            User community = owner.community();
            boolean active = activeByCommunity.computeIfAbsent(community.getId(),
                    id -> !isCommunityMembershipExpired(fan, community));
            if (active) {
                result.put(owner.user().getId(), new ArrayList<>());
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        for (ChatMessage message : chatMessageRepository.findOwnerMessagesSince(result.keySet(), fan, since)) {
            result.get(message.getArtist().getId()).add(message.getCreatedAt());
        }
        return result;
    }

    private DmInboxItem.DmInboxItemBuilder inboxItem(User fan, DmRoomOwner owner) {
        return DmInboxItem.builder()
                .artistId(owner.user().getId())
                .artistNickname(owner.user().getNickname())
                .groupName(owner.group() != null ? owner.group().getNickname() : null)
                .membershipExpired(isCommunityMembershipExpired(fan, owner.community()))
                .neverSubscribed(isCommunityNeverSubscribed(fan, owner.community()));
    }

    // DM 방 주인 (group 이 null 이면 솔로 본인, 아니면 그 그룹 멤버)
    private record DmRoomOwner(User user, User group) {
        User community() {
            return group != null ? group : user;
        }
    }

    // 솔로는 본인, 활동 멤버가 있는 그룹은 멤버 각자가 방 주인
    private Map<Long, DmRoomOwner> dmRoomOwners() {
        List<User> artists = userRepository.findByRole(Role.ARTIST);
        if (artists.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<GroupMember>> membersByGroupId = groupMemberRepository
                .findByGroupIdInAndLeftAtIsNullOrderByIdAsc(artists.stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId));

        Map<Long, DmRoomOwner> owners = new LinkedHashMap<>();
        for (User artist : artists) {
            List<GroupMember> members = membersByGroupId.getOrDefault(artist.getId(), List.of());
            if (members.isEmpty()) {
                owners.put(artist.getId(), new DmRoomOwner(artist, null));
                continue;
            }
            for (GroupMember member : members) {
                owners.put(member.getMember().getId(), new DmRoomOwner(member.getMember(), artist));
            }
        }
        return owners;
    }

    // DM 방 이력 - 1:1 메시지와 방송을 함께 조회한다.
    public List<ChatMessage> getConversation(User artist, User fan) {
        return chatMessageRepository.findConversationWithBroadcast(artist, fan);
    }

    // 아티스트 채팅방 이력 (팬 메시지는 노출 대상만).
    public List<ChatMessage> getArtistRoomHistory(User artist) {
        return chatMessageRepository.findByArtistOrderByCreatedAtAsc(artist).stream()
                .filter(ChatMessage::isVisibleToArtist)
                .toList();
    }

    // 활성 멤버십이 없으면(기록 없음 포함) 만료로 본다 (멤버는 소속 그룹 기준).
    public boolean isMembershipExpired(User fan, User roomOwner) {
        Long communityId = communityArtistResolver.ownCommunityId(roomOwner);
        if (communityId == null) {
            return true;
        }
        User community = communityId.equals(roomOwner.getId())
                ? roomOwner
                : userRepository.findById(communityId).orElse(null);
        return isCommunityMembershipExpired(fan, community);
    }

    private boolean isCommunityMembershipExpired(User fan, User community) {
        if (community == null) {
            return true;
        }
        return membershipRepository.findByFanAndArtist(fan, community)
                .map(Membership::isExpired)
                .orElse(true);
    }

    // 가입 이력 자체가 없는지 (만료 대신 가입 안내 문구용, 멤버는 소속 그룹 기준).
    public boolean isNeverSubscribed(User fan, User roomOwner) {
        Long communityId = communityArtistResolver.ownCommunityId(roomOwner);
        if (communityId == null) {
            return true;
        }
        User community = communityId.equals(roomOwner.getId())
                ? roomOwner
                : userRepository.findById(communityId).orElse(null);
        return isCommunityNeverSubscribed(fan, community);
    }

    private boolean isCommunityNeverSubscribed(User fan, User community) {
        if (community == null) {
            return true;
        }
        return membershipRepository.findByFanAndArtist(fan, community).isEmpty()
                && !membershipPeriodRepository.existsByFanIdAndArtistId(fan.getId(), community.getId());
    }
}
