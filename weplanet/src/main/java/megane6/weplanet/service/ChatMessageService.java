package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ChatMessageRepository;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.community.CommunityArtistResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    // 채팅 메시지 저장 - fan이 null이면 아티스트가 전체 팬에게 보낸 메시지
    @Transactional
    public ChatMessage saveMessage(User artist, User fan, User sender, String content) {
        return saveMessage(artist, fan, sender, content, true);
    }

    // visibleToArtist: 팬이 보낸 메시지가 아티스트 화면(추천 피드)에 노출될지 여부.
    // 실시간 전송과 새로고침 후 히스토리가 서로 달라지지 않도록, 전송 시점에 정한 값을 그대로 저장함
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

    /**
     * DM 인박스(와이어프레임 13번) - 이 팬이 대화 나눈 상대들을 최근 대화순으로,
     * 그리고 아직 대화를 안 나눈 나머지 상대들은 "추천"으로 뒤에 이어붙여서 돌려줌.
     * 멤버별 DM: 상대는 솔로 아티스트 본인 또는 그룹의 멤버 한 명 (그룹 계정 자체와는 대화하지 않음)
     */
    @Transactional(readOnly = true)
    public List<DmInboxItem> getInboxForFan(User fan) {
        // 지금 DM 방을 열 수 있는 상대 전체. 여기 없는 상대(예전 그룹 단위 방, 탈퇴한 멤버 등)는 인박스에 안 보여줌
        Map<Long, DmRoomOwner> owners = dmRoomOwners();

        // findByFanOrderByCreatedAtDesc : 이미 최신순으로 정렬해서 가져오므로,
        // 상대별로 처음 만나는 메시지가 곧 "그 상대와의 마지막 메시지"가 됨
        List<ChatMessage> messages = chatMessageRepository.findByFanOrderByCreatedAtDesc(fan);

        // LinkedHashMap : 순서를 기억하는 Map. 먼저 등장한(=가장 최근 대화한) 상대가 앞쪽에 오도록 유지해줌
        Map<Long, DmInboxItem> conversations = new LinkedHashMap<>();
        for (ChatMessage message : messages) {
            Long roomId = message.getArtist().getId();
            DmRoomOwner owner = owners.get(roomId);
            if (owner == null || conversations.containsKey(roomId)) {
                continue; // 방 주인이 아니거나, 이미 그 상대의 최신 메시지를 찾았으면 건너뜀
            }
            conversations.put(roomId, inboxItem(fan, owner)
                    .lastMessage(message.getContent())
                    .lastMessageTime(message.getCreatedAt())
                    .hasConversation(true)
                    .build());
        }

        List<DmInboxItem> result = new ArrayList<>(conversations.values());

        // 아직 대화 이력이 없는 나머지 상대들도 "추천" 칸에 보여주기 위해 뒤에 이어붙임
        for (DmRoomOwner owner : owners.values()) {
            if (!conversations.containsKey(owner.user().getId())) {
                result.add(inboxItem(fan, owner)
                        .hasConversation(false)
                        .build());
            }
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

    // DM 방 주인 한 명. group 이 null 이면 솔로 아티스트 본인, 아니면 그 그룹의 멤버
    private record DmRoomOwner(User user, User group) {
        User community() {
            return group != null ? group : user;
        }
    }

    // 솔로 아티스트는 본인이, 활동 멤버가 있는 그룹은 멤버 한 명 한 명이 방 주인 (CommunityArtistResolver.isDmRoomOwner 와 같은 규칙)
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

    // DM 방을 열었을 때 지난 대화 이력을 보여주기 위한 조회.
    // 1:1 메시지뿐 아니라 아티스트가 전체 팬에게 보낸 방송(fan IS NULL)도 함께 가져옴
    public List<ChatMessage> getConversation(User artist, User fan) {
        return chatMessageRepository.findConversationWithBroadcast(artist, fan);
    }

    // 아티스트 채팅방 화면용 - 이 아티스트 방의 메시지를 시간순으로.
    // 단, 팬 메시지는 "노출 대상으로 뽑힌 것(visibleToArtist)"만 보여줌 -
    // 전부 보여주면 실시간에서 걸러낸 의미가 없어지고 새로고침만 하면 도배가 다 보이게 됨
    public List<ChatMessage> getArtistRoomHistory(User artist) {
        return chatMessageRepository.findByArtistOrderByCreatedAtAsc(artist).stream()
                .filter(ChatMessage::isVisibleToArtist)
                .toList();
    }

    // 와이어프레임 19번: 이 팬의 이 아티스트 멤버십이 만료됐는지(=DM 입력창을 막아야 하는지) 확인.
    // 처음엔 "가입한 적 자체가 없으면 만료가 아니다"로 처리했었는데, 이러면 한 번도 가입 안 한 사람도
    // 입력창이 그대로 보여서 DM을 보낼 수 있었음(와이어프레임엔 아예 대화창이 없어야 함). 그래서
    // "멤버십 기록이 없는 것"도 "만료된 것"과 똑같이 취급하도록 수정함 - 둘 다 지금 활성 멤버십이 없다는 점은 같음
    // 멤버별 DM: roomOwner 는 멤버일 수 있지만 멤버십은 그룹(커뮤니티) 단위라서 소속 그룹으로 찾아가서 확인한다
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

    // DM 배너 문구 구분용 - 위 isMembershipExpired 는 "가입 안 함"과 "만료"를 똑같이 막지만,
    // 한 번도 가입 안 한 팬에게 "구독 만료"라고 보여주는 건 맞지 않아서 가입 안내 문구를 따로 보여줌.
    // 해지(MembershipService.cancel)하면 membership 줄이 지워지므로, 가입 이력(membership_period)까지 확인함
    // 멤버별 DM도 가입은 그룹(커뮤니티) 단위이므로 roomOwner가 멤버면 소속 그룹 이력을 확인한다.
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
