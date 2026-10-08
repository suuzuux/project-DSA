package megane6.weplanet.config;
import megane6.weplanet.service.main.ContentTranslationService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** AI 팬 DM 답장 같은 후속 작업을 웹소켓 요청 스레드와 분리하는 비동기 설정. */
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

	// 인증코드 메일 전용 풀 - 응답 시간으로 계정 존재 여부가 드러나지 않게 백그라운드로 보낸다.
	@Bean(name = "verificationMailExecutor")
	public Executor verificationMailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("verification-mail-");
		executor.setRejectedExecutionHandler((task, pool) ->
				org.slf4j.LoggerFactory.getLogger(AsyncConfig.class).warn("[인증 메일] 발송 대기열이 가득 차 메일 1통을 보내지 못했습니다."));
		executor.initialize();
		return executor;
	}

	// 팔로워 이메일 알림 전용 풀 - 발송량이 많을 수 있어 AI 채팅 풀과 분리했다.
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

	// 가입 완료·안내 메일 전용 풀 - 큐가 차면 로그만 남기고 버린다.
	@Bean(name = "accountMailExecutor")
	public Executor accountMailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("account-mail-");
		executor.setRejectedExecutionHandler((task, pool) ->
				org.slf4j.LoggerFactory.getLogger(AsyncConfig.class).warn("[안내 메일] 발송 대기열이 가득 차 메일 1통을 보내지 못했습니다."));
		executor.initialize();
		return executor;
	}

	// 메인 배너 AI 번역 전용 풀 - 큐가 차면 그 배너는 원문으로 보여준다.
	@Bean(name = "contentTranslationExecutor")
	public Executor contentTranslationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("content-translate-");
		executor.initialize();
		return executor;
	}
}
