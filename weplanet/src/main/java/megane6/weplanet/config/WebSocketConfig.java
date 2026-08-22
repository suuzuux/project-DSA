package megane6.weplanet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 브라우저가 웹소켓 연결을 맺으러 오는 주소
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat").withSockJS();
    }

    // 채널 접두사 규칙 정의
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 -> 클라이언트 (구독하는 채널)
        registry.enableSimpleBroker("/topic");
        // 클라이언트 -> 서버 (메세지 보내는 채널)
        registry.setApplicationDestinationPrefixes("/app");
    }

}
