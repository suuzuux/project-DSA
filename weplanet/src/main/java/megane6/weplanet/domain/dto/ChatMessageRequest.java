package megane6.weplanet.domain.dto;

import lombok.Data;

// 브라우저가 웹소켓으로 보내는 메시지 하나를 그대로 옮겨 담는 그릇
@Data
public class ChatMessageRequest {
    private Long artistId;
    private Long fanId;      // 없으면 방송(전체 공지) 메시지
    private Long senderId;
    private String content;
}