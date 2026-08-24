package megane6.weplanet.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DM 인박스(와이어프레임 13번) 한 줄을 표현하는 자료 상자.
 * 화면에는 이 값들만 뿌려주면 되도록, 필요한 정보만 미리 다 계산해서 담아둠.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmInboxItem {
    private Long artistId;
    private String artistNickname;
    private String lastMessage;       // 대화 이력이 없으면 null (화면에서 "추천" 칸으로 분류됨)
    private LocalDateTime lastMessageTime;
    private boolean hasConversation;  // true면 "메시지" 칸, false면 "추천" 칸
    private boolean membershipExpired; // 와이어프레임 19번: 멤버십이 만료됐으면 DM 방에 만료 배너를 보여줌
}
