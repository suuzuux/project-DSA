package megane6.weplanet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
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

    /**
     * 브라우저 -> 서버 방향으로 들어오는 웹소켓 메시지를 가로채서, 메시지 전송(SEND)은
     * 로그인한 사용자만 할 수 있도록 막는 부분.
     * <p>
     * ChatController.send() 안에서도 로그인 여부를 확인하고 있지만, 그건 "컨트롤러까지 들어온 뒤"의 검사임.
     * 여기서 미리 걸러주면 비로그인 메시지는 아예 컨트롤러에 도달하지 못함(이중 방어).
     * <p>
     * SEND만 막고 CONNECT/SUBSCRIBE는 열어둔 이유 : 구독은 아티스트 채팅방 화면처럼
     * 로그인 없이 보기만 하는 경우가 있어서, 지금 단계에서 막으면 기존 화면이 깨질 수 있음.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null
                        && SimpMessageType.MESSAGE.equals(accessor.getMessageType())
                        && accessor.getUser() == null) {
                    throw new IllegalStateException("로그인이 필요한 요청입니다.");
                }

                return message;
            }
        });
    }

}
