package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// [설정 - 광고성 정보 알림] 실제 운영 시나리오는 아니고, "기능이 살아있다"는 걸 보여주기 위한 데모용 발송기.
// 가입 완료 메일은 아이디/비밀번호든 소셜 계정이든, "(선택) 광고 및 마케팅 활용 동의" 체크 여부와 무관하게
// 가입할 때마다 무조건 1통 보낸다. (UserService.signup(), OAuth2LoginSuccessHandler.createNewSocialUser())
// 시나리오 ① : 아이디/비밀번호로 가입할 때 그 체크박스까지 체크했으면, 가입 완료 메일에 동의 안내 문구가
//   붙고, 곧바로 커뮤니티 가입 유도 메일이 1통 더 간다. (UserService.signup())
// 시나리오 ② : 체크를 안 하고 가입했거나(아이디/비밀번호) 소셜 계정으로 가입한 사람이, 나중에
//   설정 > 이벤트·혜택 알림 설정 > "광고성 정보 알림 받기"를 켜면 동의 확인 메일 1통만 보낸다.
//   (UserService.updateNotificationPreference())
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingConsentEmailService {

	private final JavaMailSender mailSender;

	// marketingConsentGiven: 가입 시점에 광고·마케팅 동의를 했는지. 소셜 가입은 이 체크박스 자체가 없으므로
	// 항상 false로 넘어온다. true일 때만 동의 안내 문구를 메일 본문에 덧붙인다.
	public void sendSignupWelcomeEmail(User user, boolean marketingConsentGiven) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		message.setSubject("[WePlaNet] 회원가입을 환영합니다");
		String consentLine = marketingConsentGiven
				? "가입하실 때 '(선택) 광고 및 마케팅 활용 동의'에도 동의해주셨습니다. "
						+ "앞으로 이벤트·혜택 소식을 이메일로 보내드릴게요.\n\n"
						+ "동의는 언제든 설정 > 이벤트·혜택 알림 설정에서 다시 끌 수 있어요.\n\n"
				: "";
		message.setText(user.getNickname() + "님, 안녕하세요.\n\n"
				+ "WePlaNet 회원가입이 완료되었습니다. (아이디: " + user.getUsername() + ")\n\n"
				+ consentLine
				+ "WePlaNet 사이트에서 바로 확인해보세요.");
		mailSender.send(message);
		log.info("[광고성 정보 알림] 회원가입 환영 메일 발송: user={}, marketingConsent={}", user.getId(), marketingConsentGiven);
	}

	public void sendCommunityInviteEmail(User user) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		message.setSubject("[WePlaNet] 좋아하는 아티스트의 커뮤니티에 가입해보세요");
		message.setText(user.getNickname() + "님, 안녕하세요.\n\n"
				+ "관심있는 아티스트의 커뮤니티에 가입하면 새 게시글 · 공지 · 라이브 소식을 가장 먼저 받아볼 수 있어요.\n\n"
				+ "WePlaNet 사이트에서 커뮤니티를 둘러보고 지금 바로 가입해보세요.");
		mailSender.send(message);
		log.info("[광고성 정보 알림] 커뮤니티 가입 유도 메일 발송: user={}", user.getId());
	}

	public void sendMarketingConsentConfirmedEmail(User user) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(user.getEmail());
		message.setSubject("[WePlaNet] 광고성 정보 수신에 동의하셨습니다");
		message.setText(user.getNickname() + "님, 안녕하세요.\n\n"
				+ "광고성 정보(이벤트 · 혜택) 수신에 동의해주셔서 감사합니다. "
				+ "앞으로 WePlaNet의 다양한 이벤트 · 혜택 소식을 이메일로 보내드릴게요.\n\n"
				+ "동의는 언제든 설정 > 이벤트·혜택 알림 설정에서 다시 끌 수 있어요.");
		mailSender.send(message);
		log.info("[광고성 정보 알림] 설정 화면 동의 확인 메일 발송: user={}", user.getId());
	}
}
