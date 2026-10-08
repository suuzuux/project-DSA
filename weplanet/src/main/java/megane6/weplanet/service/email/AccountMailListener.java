package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 계정 안내 메일 발송 (커밋 후 비동기, 커밋된 회원 정보를 다시 읽음). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailListener {

	private final UserRepository userRepository;
	private final MarketingConsentEmailService marketingConsentEmailService;

	@Async("accountMailExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onAccountMail(AccountMailEvent event) {
		User user = userRepository.findById(event.userId()).orElse(null);
		// 수신 불가 시스템 주소로는 보내지 않는다.
		if (user == null || user.hasPlaceholderEmail()) {
			return;
		}
		switch (event.kind()) {
			case SIGNUP_WELCOME -> {
				send("회원가입 환영 메일", user,
						() -> marketingConsentEmailService.sendSignupWelcomeEmail(user, event.marketingConsentGiven()));
				if (event.marketingConsentGiven()) {
					send("커뮤니티 가입 유도 메일", user, () -> marketingConsentEmailService.sendCommunityInviteEmail(user));
				}
			}
			case MARKETING_CONSENT_CONFIRMED -> send("설정 화면 동의 확인 메일", user,
					() -> marketingConsentEmailService.sendMarketingConsentConfirmedEmail(user));
		}
	}

	// 메일 실패는 로그만 남기고 다음 메일을 계속 보낸다.
	private void send(String label, User user, Runnable mail) {
		try {
			mail.run();
		} catch (Exception e) {
			log.error("[광고성 정보 알림] {} 발송 실패: user={}", label, user.getId(), e);
		}
	}
}
