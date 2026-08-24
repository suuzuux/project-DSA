package megane6.weplanet.domain.dto;

import lombok.Data;

/**
 * 브라우저(자바스크립트)가 웹소켓으로 보내는 메시지 하나를 그대로 옮겨 담는 그릇(DTO).
 * <p>
 * DTO(Data Transfer Object)란: 화면과 서버가 데이터를 주고받을 때 쓰는 "포장 상자" 같은 클래스.
 * ChatMessage(엔티티, DB 테이블과 직접 연결됨)를 그대로 쓰지 않고 이렇게 별도로 만든 이유:
 * 브라우저는 아직 실제 User, ChatMessage 객체를 모르고 "숫자 id들과 글자"만 보낼 수 있기 때문에,
 * 딱 그 모양(id, id, id, 문자열)에 맞춘 단순한 클래스를 하나 만들어서 받는 것.
 */
@Data
public class ChatMessageRequest {
    private Long artistId;
    private Long fanId;      // null이면 아티스트가 전체 팬에게 보내는 방송(공지) 메시지
    private Long senderId;
    private String content;
}
