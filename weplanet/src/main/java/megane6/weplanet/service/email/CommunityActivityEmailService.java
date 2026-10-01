package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

// [설정 - 이벤트·혜택 알림] 가입한 아티스트의 새 게시글/공지/라이브 시작을 팬에게 이메일로 알려주는 발송기.
// DormantAccountNoticeService와 같은 패턴 - JavaMailSender를 직접 주입받아 SimpleMailMessage로 보낸다.
// 실제 대상 선별(동의 여부/야간 알림 체크)과 비동기 처리는 CommunityActivityNotifier가 담당하고,
// 이 클래스는 "메일 한 통 보내기"만 책임진다.
// SETTINGS-03 커밋5: 메일 문구는 받는 팬의 선호 언어(User.preferredLanguage)로 만든다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityActivityEmailService {

	private final JavaMailSender mailSender;
	private final MessageSource messageSource;

	public void sendNewPostEmail(User fan, User artist, Post post) {
		Locale locale = PreferredLocaleResolver.toLocale(fan.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject(msg("mail.community.post.subject", locale, artist.getNickname()));
		message.setText(msg("mail.common.greeting", locale, fan.getNickname()) + "\n\n"
				+ msg("mail.community.post.body", locale, artist.getNickname()) + "\n\n"
				+ msg("mail.community.titleLine", locale, post.getTitle()) + "\n\n"
				+ msg("mail.common.visitSite", locale) + "\n\n"
				+ msg("mail.community.footer", locale));
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 새 게시글 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	public void sendNewNoticeEmail(User fan, User artist, PortalNotice notice) {
		Locale locale = PreferredLocaleResolver.toLocale(fan.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject(msg("mail.community.notice.subject", locale, artist.getNickname()));
		message.setText(msg("mail.common.greeting", locale, fan.getNickname()) + "\n\n"
				+ msg("mail.community.notice.body", locale, artist.getNickname()) + "\n\n"
				+ msg("mail.community.titleLine", locale, notice.getTitle()) + "\n\n"
				+ msg("mail.common.visitSite", locale) + "\n\n"
				+ msg("mail.community.footer", locale));
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 새 공지 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	public void sendLiveStartEmail(User fan, User artist, LiveSession session) {
		Locale locale = PreferredLocaleResolver.toLocale(fan.getPreferredLanguage());
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject(msg("mail.community.live.subject", locale, artist.getNickname()));
		message.setText(msg("mail.common.greeting", locale, fan.getNickname()) + "\n\n"
				+ msg("mail.community.live.body", locale, artist.getNickname()) + "\n\n"
				+ msg("mail.community.live.visit", locale) + "\n\n"
				+ msg("mail.community.footer", locale));
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 라이브 시작 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	// 인자 없는 키는 args=null로 조회해 MessageFormat을 거치지 않게 한다 (작은따옴표 그대로 유지).
	private String msg(String code, Locale locale, Object... args) {
		return messageSource.getMessage(code, args.length == 0 ? null : args, locale);
	}
}
