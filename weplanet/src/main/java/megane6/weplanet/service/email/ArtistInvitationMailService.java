package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.AgencyActivationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 소속사가 등록한 아티스트 계정의 활성화(비밀번호 설정) 안내 메일.
 * 링크는 소속사 활성화와 같은 /partner/activate 화면을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistInvitationMailService {
	
	private static final DateTimeFormatter TIME_FORMAT
			= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final JavaMailSender mailSender;
	
	@Value("${weplanet.base-url:http://localhost:9999}")
	private String baseUrl;
	
	// 첫 등록과 (7단계) 재발송에서 같이 쓴다
	public void sendActivationMail(
			String to,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {
		SimpleMailMessage message = new SimpleMailMessage();
		
		message.setTo(to);
		message.setSubject("[WePlaNet] " + artistName + " 아티스트 계정 활성화 안내");
		message.setText(buildBody(to, artistName, agencyName, activation));
		
		mailSender.send(message);
		
		log.info("아티스트 계정 활성화 메일 발송: to={}, artist={}", to, artistName);
	}
	
	private String buildBody(
			String username,
			String artistName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {
		String activationUrl = baseUrl
				+ "/partner/activate?key=" + activation.verificationKey()
				+ "&token=" + activation.rawToken();
		
		return """
				안녕하세요, %s님.

				%s에서 WePlaNet 아티스트 계정을 만들었습니다.
				아래 링크에서 그룹 비밀번호를 설정하면 아티스트 포털에 로그인할 수 있습니다.

				■ 로그인 아이디 : %s
				■ 활성화 링크   : %s
				■ 링크 유효기간 : %s 까지

				그룹 비밀번호는 멤버 모두가 함께 쓰는 1단계 로그인 비밀번호입니다.
				로그인 후 프로필을 고르고, 멤버별 개인 비밀번호를 한 번 더 입력합니다.

				링크는 한 번만 사용할 수 있으며, 기간이 지나면 소속사에 재발송을 요청해주세요.

				감사합니다.
				WePlaNet 드림
				"""
				.formatted(
						artistName,
						agencyName,
						username,
						activationUrl,
						activation.expiresAt().format(TIME_FORMAT)
				);
	}
}