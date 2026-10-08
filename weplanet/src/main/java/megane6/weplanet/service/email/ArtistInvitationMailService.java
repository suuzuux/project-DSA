package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.agency.AgencyActivationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 아티스트 계정 활성화 안내 메일 (문구는 등록한 소속사 사용자의 언어). */
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
	
	// 첫 등록·재발송 공통 (현재 요청 로케일 = 등록한 소속사 언어).
	public void sendActivationMail(
			String to,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {
		sendActivationMail(to, artistName, agencyName, activation, LocaleContextHolder.getLocale());
	}
	
	// locale: 등록한 소속사 사용자의 언어
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
		
		// {0} 아티스트명, {1} 소속사명, {2} 아이디, {3} 링크, {4} 유효기간
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