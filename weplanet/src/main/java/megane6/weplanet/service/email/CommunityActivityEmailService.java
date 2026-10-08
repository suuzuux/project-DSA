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

// 가입·팔로우한 아티스트의 새 게시글/공지/라이브 시작을 팬에게 알리는 메일 한 통을 보낸다 (문구는 받는 팬의 선호 언어).
// 받을 사람 선별과 비동기 처리는 CommunityActivityNotifier 가 맡는다.
// 그룹 멤버가 쓴 게시글·시작한 라이브는 어느 멤버인지도 적는다 (공지는 작성 멤버를 저장하지 않아 그룹 이름만).
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
		message.setSubject(byWhom("mail.community.post.subject", locale, artist, post.getAuthor()));
		message.setText(msg("mail.common.greeting", locale, fan.getNickname()) + "\n\n"
				+ byWhom("mail.community.post.body", locale, artist, post.getAuthor()) + "\n\n"
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
		message.setSubject(byWhom("mail.community.live.subject", locale, artist, session.getHost()));
		message.setText(msg("mail.common.greeting", locale, fan.getNickname()) + "\n\n"
				+ byWhom("mail.community.live.body", locale, artist, session.getHost()) + "\n\n"
				+ msg("mail.community.live.visit", locale) + "\n\n"
				+ msg("mail.community.footer", locale));
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 라이브 시작 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	// 그룹 멤버가 한 활동이면 "그룹의 멤버" 문구(code + ".member"), 아티스트 본인(솔로·그룹 계정)이 했으면 원래 문구.
	// 소속사 계정이 대신 방송·작성한 경우도 원래 문구 (멤버가 아니므로)
	private String byWhom(String code, Locale locale, User artist, User actor) {
		boolean byMember = actor != null && actor.isArtistSide() && !actor.getId().equals(artist.getId());
		return byMember
				? msg(code + ".member", locale, artist.getNickname(), actor.getNickname())
				: msg(code, locale, artist.getNickname());
	}

	// 인자 없는 키는 args=null로 조회해 MessageFormat을 거치지 않게 한다 (작은따옴표 그대로 유지).
	private String msg(String code, Locale locale, Object... args) {
		return messageSource.getMessage(code, args.length == 0 ? null : args, locale);
	}
}
