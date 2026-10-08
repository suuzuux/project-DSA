package megane6.weplanet.config;
import megane6.weplanet.service.main.ContentTranslationService;

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

	// 아이디·비밀번호 찾기 / 휴면 해제 인증코드 메일 전용 풀 (VerificationMailAsyncSender).
	// 응답 시간 차이로 계정 존재 여부가 드러나지 않게 백그라운드로 보내고, 큐가 차면 같은 이유로 버리고 로그만 남긴다.
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

	// [이벤트·혜택 알림] 게시글/공지/라이브 시작 시 팔로워에게 이메일을 보내는 작업 전용 풀.
	// 팔로워가 많으면 순차 발송이 오래 걸릴 수 있어 aiFanExecutor(AI 채팅용)와 분리하고 큐를 넉넉하게 잡았다.
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

	// 가입 완료 / 커뮤니티 가입 유도 / 광고성 동의 확인 메일 전용 풀 (AccountMailListener) - 인증코드 메일이 밀리지 않게 분리.
	// 큐가 차면 그 메일은 보내지 않고 로그만 남긴다 (가입은 이미 끝났으므로 화면에 오류를 내지 않음).
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

	// 메인 배너 AI 번역 전용 풀 (ContentTranslationService) - 배너 여러 장을 동시에 번역해 메인 화면이 오래 기다리지 않게 한다.
	// 큐가 차면 그 배너는 이번에는 원문으로 보여준다 (호출부가 거절을 받아 처리).
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
