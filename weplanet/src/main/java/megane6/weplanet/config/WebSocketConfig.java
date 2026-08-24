package megane6.weplanet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 채팅을 위한 웹소켓(WebSocket) 연결 설정.
 * <p>
 * 지금까지 만든 게시판 기능은 "브라우저가 서버에게 물어보면(요청), 서버가 한 번 답해주고 끝(응답)"
 * 나는 방식이었음(HTTP 요청-응답). 근데 실시간 채팅은 이 방식으로는 안 됨 -
 * 상대방이 메시지를 보냈을 때, 내가 딱히 물어보지도 않았는데 서버가 "지금 새 메시지 왔어!"라고
 * 먼저 알려줘야 하기 때문임.
 * <p>
 * 웹소켓은 브라우저와 서버 사이에 "계속 연결된 통로"를 하나 뚫어두는 기술이고,
 * STOMP는 그 통로 위에서 "누가 어떤 채널을 구독하고, 어떤 채널로 메시지를 보내는지"를
 * 정리해주는 규칙(프로토콜)임. 이 클래스는 그 통로와 규칙을 설정하는 부분.
 */
@Configuration
@EnableWebSocketMessageBroker // "이 서버는 웹소켓 실시간 메시지 기능을 쓸 거다"라고 스프링에게 알려주는 표시
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 브라우저가 "여기로 웹소켓 연결을 맺어줘"라고 처음 접속하는 주소를 정함.
    // 브라우저 쪽 자바스크립트에서 new SockJS('/ws-chat') 이라고 쓰는 부분과 짝이 맞아야 함.
    // withSockJS() : 혹시 웹소켓을 지원 안 하는 낡은 환경이어도, 비슷하게 흉내 내서 동작하게 해주는 안전장치
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat").withSockJS();
    }

    // 채널 주소의 접두사(맨 앞부분) 규칙을 정함
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/topic"으로 시작하는 채널 : 서버 -> 브라우저 방향 (브라우저가 구독해서 실시간으로 받아보는 채널)
        // 예) /topic/chat.2  (2번 아티스트의 방송 채널)
        registry.enableSimpleBroker("/topic");

        // "/app"으로 시작하는 채널 : 브라우저 -> 서버 방향 (브라우저가 메시지를 보낼 때 쓰는 채널)
        // 예) /app/chat.send  (ChatController의 @MessageMapping("/chat.send")와 연결됨)
        registry.setApplicationDestinationPrefixes("/app");
    }

}
