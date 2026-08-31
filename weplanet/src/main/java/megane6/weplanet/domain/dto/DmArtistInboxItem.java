package megane6.weplanet.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 아티스트가 받은 DM함(팬 목록) 한 줄을 표현하는 자료 상자.
 * 팬 인박스(DmInboxItem)와 달리 "추천"이나 "멤버십 만료" 개념이 없음 -
 * 아티스트는 자신에게 메시지를 보낸 적 있는 팬만 보면 되고, 멤버십은 팬 쪽 제약이라 여기선 신경 안 씀.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmArtistInboxItem {
    private Long fanId;
    private String fanNickname;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}
