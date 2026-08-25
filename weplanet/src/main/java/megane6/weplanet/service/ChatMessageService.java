package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.DmInboxItem;
import megane6.weplanet.domain.entity.ChatMessage;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ChatMessageRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    // 채팅 메시지 저장 - fan이 null이면 아티스트가 전체 팬에게 보낸 방송 메시지
    public ChatMessage saveMessage(User artist, User fan, User sender, String content) {
        ChatMessage message = ChatMessage.builder()
                .artist(artist)
                .fan(fan)
                .sender(sender)
                .content(content)
                .build();

        return chatMessageRepository.save(message);
    }

    /**
     * DM 인박스(와이어프레임 13번) - 이 팬이 대화 나눈 아티스트들을 최근 대화순으로,
     * 그리고 아직 대화를 안 나눈 나머지 아티스트들은 "추천"으로 뒤에 이어붙여서 돌려줌.
     */
    public List<DmInboxItem> getInboxForFan(User fan) {
        // findByFanOrderByCreatedAtDesc : 이미 최신순으로 정렬해서 가져오므로,
        // 아티스트별로 처음 만나는 메시지가 곧 "그 아티스트와의 마지막 메시지"가 됨
        List<ChatMessage> messages = chatMessageRepository.findByFanOrderByCreatedAtDesc(fan);

        // LinkedHashMap : 순서를 기억하는 Map. 먼저 등장한(=가장 최근 대화한) 아티스트가 앞쪽에 오도록 유지해줌
        Map<Long, DmInboxItem> conversations = new LinkedHashMap<>();
        for (ChatMessage message : messages) {
            // 예전 테스트 중 실수로 팬 계정 id가 artist 자리에 잘못 들어간 지저분한 데이터가 섞여 있을 수 있어서,
            // 진짜 ARTIST 역할인 경우만 인박스에 보여줌
            if (message.getArtist().getRole() != Role.ARTIST) {
                continue;
            }

            Long artistId = message.getArtist().getId();
            if (conversations.containsKey(artistId)) {
                continue; // 이미 그 아티스트의 최신 메시지를 찾았으면, 더 예전 메시지는 건너뜀
            }
            conversations.put(artistId, DmInboxItem.builder()
                    .artistId(artistId)
                    .artistNickname(message.getArtist().getNickname())
                    .lastMessage(message.getContent())
                    .lastMessageTime(message.getCreatedAt())
                    .hasConversation(true)
                    .membershipExpired(isMembershipExpired(fan, message.getArtist()))
                    .build());
        }

        List<DmInboxItem> result = new ArrayList<>(conversations.values());

        // 아직 대화 이력이 없는 나머지 아티스트들도 "추천" 칸에 보여주기 위해 뒤에 이어붙임
        List<User> allArtists = userRepository.findByRole(Role.ARTIST);
        for (User artist : allArtists) {
            if (!conversations.containsKey(artist.getId())) {
                result.add(DmInboxItem.builder()
                        .artistId(artist.getId())
                        .artistNickname(artist.getNickname())
                        .hasConversation(false)
                        .build());
            }
        }

        return result;
    }

    // DM 방을 열었을 때 지난 대화 이력을 보여주기 위한 조회
    public List<ChatMessage> getConversation(User artist, User fan) {
        return chatMessageRepository.findByArtistAndFanOrderByCreatedAtAsc(artist, fan);
    }

    // 와이어프레임 19번: 이 팬의 이 아티스트 멤버십이 만료됐는지 확인.
    // 멤버십 기록 자체가 없으면(한 번도 가입한 적 없으면) "만료됨"으로 취급하지 않음 - 애초에 구독한 적이 없는 것과
    // "구독했다가 끝난 것"은 다른 의미라, 배너는 실제로 만료된 경우에만 보여주는 게 맞다고 판단함
    public boolean isMembershipExpired(User fan, User artist) {
        return membershipRepository.findByFanAndArtist(fan, artist)
                .map(Membership::isExpired)
                .orElse(false);
    }
}