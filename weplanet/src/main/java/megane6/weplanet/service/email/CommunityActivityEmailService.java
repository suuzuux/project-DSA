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

// 새 글·공지·라이브 시작 알림 메일 (받는 팬의 선호 언어).
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

	// 인자 없는 키는 args=null 로 조회한다 (작은따옴표 유지).
	private String msg(String code, Locale locale, Object... args) {
		return messageSource.getMessage(code, args.length == 0 ? null : args, locale);
	}
}
