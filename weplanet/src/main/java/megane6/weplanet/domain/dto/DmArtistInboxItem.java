package megane6.weplanet.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 아티스트가 받은 DM함의 팬 한 줄 (추천·멤버십 만료 개념 없음). */
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
