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
	// 아이디·비밀번호 찾기 / 휴면 해제 인증코드 메일 전용 풀 (VerificationMailAsyncSender).
	// 메일을 요청 처리 중에 보내면 "계정이 있을 때만" 응답이 1~2초 늦어져서 계정 존재 여부가 드러나므로 백그라운드로 보낸다.
	// 큐가 가득 차면 버리고 로그만 남긴다 - 요청 스레드에서 대신 보내면(CallerRuns) 다시 응답 시간 차이가 생기기 때문.
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
