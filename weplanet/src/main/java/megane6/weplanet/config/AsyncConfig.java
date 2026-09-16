package megane6.weplanet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 팬 DM 답장처럼 "보낸 직후 백그라운드에서 이어서 할 일"을 웹소켓 요청 스레드와 분리한다.
 * 아티스트 메시지가 화면에 먼저 뜨고, 팬 답장은 조금 뒤에 차례로 도착하게 하기 위함.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "aiFanExecutor")
	public Executor aiFanExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("ai-fan-");
		executor.initialize();
		return executor;
	}

	// [이벤트·혜택 알림] 게시글/공지/라이브 시작 시 팔로워에게 이메일을 보내는 작업 전용 풀.
	// 팔로워 수가 많으면 순차 발송(JavaMailSender는 건당 SMTP 호출이라 순차 처리)이 오래 걸릴 수 있어서
	// aiFanExecutor(AI 채팅용, 큐 20)와는 분리했다. 큐를 더 넉넉하게 잡아둠.
	@Bean(name = "communityNotifyExecutor")
	public Executor communityNotifyExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("community-notify-");
		executor.initialize();
		return executor;
	}
}
