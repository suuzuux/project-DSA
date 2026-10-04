package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

// 데모용 광고성 정보 메일 발송기 (문구는 받는 회원의 선호 언어).
// 가입 완료 메일(항상) / 커뮤니티 가입 유도 메일(가입 때 광고 동의) / 동의 확인 메일(설정 화면에서 알림을 켰을 때).
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingConsentEmailService {

	private final JavaMailSender mailSender;
	private final MessageSource messageSource;

	// marketingConsentGiven: 가입 시점에 광고·마케팅 동의를 했는지. 소셜 가입은 이 체크박스 자체가 없으므로
	// 항상 false로 넘어온다. true일 때만 동의 안내 문구를 메일 본문에 덧붙인다.
	public void sendSignupWelcomeEmail(User user, boolean marketingConsentGiven) {
		Locale locale = PreferredLocaleResolver.toLocale(user.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		message.setSubject(msg("mail.welcome.subject", locale));
		String consentLine = marketingConsentGiven
				? msg("mail.welcome.consentGiven", locale) + "\n\n"
						+ msg("mail.marketing.optOutHint", locale) + "\n\n"
				: "";
		message.setText(msg("mail.common.greeting", locale, user.getNickname()) + "\n\n"
				+ msg("mail.welcome.completed", locale, user.getUsername()) + "\n\n"
				+ consentLine
				+ msg("mail.common.visitSite", locale));
		mailSender.send(message);
		log.info("[광고성 정보 알림] 회원가입 환영 메일 발송: user={}, marketingConsent={}", user.getId(), marketingConsentGiven);
	}

	public void sendCommunityInviteEmail(User user) {
		Locale locale = PreferredLocaleResolver.toLocale(user.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		// 광고성 정보는 제목 첫머리에 "(광고)"를 표시해야 한다 (정보통신망법 제50조) - mail.invite.subject 키에 포함
		message.setSubject(msg("mail.invite.subject", locale));
		message.setText(msg("mail.common.greeting", locale, user.getNickname()) + "\n\n"
				+ msg("mail.invite.body", locale) + "\n\n"
				+ msg("mail.invite.cta", locale));
		mailSender.send(message);
		log.info("[광고성 정보 알림] 커뮤니티 가입 유도 메일 발송: user={}", user.getId());
	}

	public void sendMarketingConsentConfirmedEmail(User user) {
		Locale locale = PreferredLocaleResolver.toLocale(user.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		message.setSubject(msg("mail.marketing.confirmed.subject", locale));
		message.setText(msg("mail.common.greeting", locale, user.getNickname()) + "\n\n"
				+ msg("mail.marketing.confirmed.body", locale) + "\n\n"
				+ msg("mail.marketing.optOutHint", locale));
		mailSender.send(message);
		log.info("[광고성 정보 알림] 설정 화면 동의 확인 메일 발송: user={}", user.getId());
	}

	// 인자 없는 키는 args=null로 조회해 MessageFormat을 거치지 않게 한다 (작은따옴표 그대로 유지).
	private String msg(String code, Locale locale, Object... args) {
		return messageSource.getMessage(code, args.length == 0 ? null : args, locale);
	}
}
