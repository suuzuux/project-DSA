package megane6.weplanet.domain.dto;

import lombok.Data;

/** 브라우저가 웹소켓으로 보내는 채팅 메시지 DTO (id 와 내용만 받음). */
@Data
public class ChatMessageRequest {
    private Long artistId;
    private Long fanId;      // null이면 아티스트가 전체 팬에게 보내는 방송(공지) 메시지
    private Long senderId;
    private String content;
}
