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

/** 실시간 채팅용 WebSocket(STOMP) 설정. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 웹소켓 최초 연결 주소 (프론트는 wss://{host}/ws-chat 으로 연결).
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 터널(HTTPS) Origin 도 허용하고, 네이티브 WebSocket 과 SockJS 폴백을 함께 연다.
        registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic : 서버 → 브라우저 구독 채널 (예: /topic/chat.2).
        registry.enableSimpleBroker("/topic");

        // /app : 브라우저 → 서버 전송 채널 (예: /app/chat.send → @MessageMapping).
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** 웹소켓 SEND 는 로그인 사용자만 허용한다 (구독은 비로그인 화면이 있어 열어 둠). */
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
