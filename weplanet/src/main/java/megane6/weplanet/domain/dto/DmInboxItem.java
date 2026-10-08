package megane6.weplanet.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** DM 인박스 한 줄 (화면에 필요한 값을 미리 계산해 담음). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmInboxItem {
    private Long artistId;
    private String artistNickname;
    private String groupName;         // 상대가 그룹 멤버면 그룹 이름, 솔로면 null
    private String lastMessage;       // 대화 이력이 없으면 null (추천 칸으로 분류)
    private LocalDateTime lastMessageTime;
    private boolean hasConversation;  // true면 "메시지" 칸, false면 "추천" 칸
    private boolean membershipExpired; // 멤버십 만료 시 DM 방에 만료 배너 표시
    private boolean neverSubscribed;   // 가입 이력이 없으면 가입 안내 문구 표시
}
