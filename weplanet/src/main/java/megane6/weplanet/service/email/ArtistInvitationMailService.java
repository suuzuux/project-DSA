package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.AgencyActivationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 소속사가 등록한 아티스트 계정의 활성화(비밀번호 설정) 안내 메일.
 * 링크는 소속사 활성화와 같은 /partner/activate 화면을 쓴다.
 * SETTINGS-03: 받는 쪽은 방금 만든 그룹 계정이라 선호 언어가 항상 기본값(KO)이므로,
 * 대신 이 아티스트를 등록한 소속사 사용자의 언어로 메일 문구를 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistInvitationMailService {
	
	private static final DateTimeFormatter TIME_FORMAT
			= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final JavaMailSender mailSender;
	private final MessageSource messageSource;
	
	@Value("${weplanet.base-url:http://localhost:9999}")
	private String baseUrl;
	
	// 첫 등록과 (7단계) 재발송에서 같이 쓴다.
	// 기존 호출부(PortalController, 소속사 요청 처리 중) 호환용: 지금 요청의 로케일 = 등록한 소속사 사용자의 언어.
	public void sendActivationMail(
			String to,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {
		sendActivationMail(to, artistName, agencyName, activation, LocaleContextHolder.getLocale());
	}
	
	// locale: 이 아티스트를 등록한 소속사 사용자의 언어 (보통 PreferredLocaleResolver.toLocale(agency.getPreferredLanguage()))
	public void sendActivationMail(
			String to,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation,
			Locale locale
	) {
		Locale mailLocale = locale != null ? locale : Locale.KOREAN;
		SimpleMailMessage message = new SimpleMailMessage();
		
		message.setTo(to);
		message.setSubject(messageSource.getMessage("mail.artistInvite.subject", new Object[]{artistName}, mailLocale));
		message.setText(buildBody(to, artistName, agencyName, activation, mailLocale));
		
		mailSender.send(message);
		
		log.info("아티스트 계정 활성화 메일 발송: to={}, artist={}, locale={}", to, artistName, mailLocale);
	}
	
	private String buildBody(
			String username,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation,
			Locale locale
	) {
		String activationUrl = baseUrl
				+ "/partner/activate?key=" + activation.verificationKey()
				+ "&token=" + activation.rawToken();
		
		// {0} 아티스트명, {1} 소속사명, {2} 로그인 아이디, {3} 활성화 링크, {4} 유효기간
		return messageSource.getMessage(
				"mail.artistInvite.body",
				new Object[]{
						artistName,
						agencyName,
						username,
						activationUrl,
						activation.expiresAt().format(TIME_FORMAT)
				},
				locale
		);
	}
}