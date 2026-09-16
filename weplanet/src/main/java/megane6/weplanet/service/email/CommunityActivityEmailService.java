package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveSession;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// [설정 - 이벤트·혜택 알림] 가입한 아티스트의 새 게시글/공지/라이브 시작을 팬에게 이메일로 알려주는 발송기.
// DormantAccountNoticeService와 같은 패턴 - JavaMailSender를 직접 주입받아 SimpleMailMessage로 보낸다.
// 실제 대상 선별(동의 여부/야간 알림 체크)과 비동기 처리는 CommunityActivityNotifier가 담당하고,
// 이 클래스는 "메일 한 통 보내기"만 책임진다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityActivityEmailService {

	private final JavaMailSender mailSender;

	public void sendNewPostEmail(User fan, User artist, Post post) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject("[WePlaNet] " + artist.getNickname() + "님의 새 게시글");
		message.setText(fan.getNickname() + "님, 안녕하세요.\n\n"
				+ "가입 중인 " + artist.getNickname() + "님이 새 글을 올렸어요.\n\n"
				+ "제목: " + post.getTitle() + "\n\n"
				+ "WePlaNet 사이트에서 바로 확인해보세요.\n\n"
				+ "이 메일은 설정 > 이벤트·혜택 알림 설정에서 '이메일로 알림 받기'를 켠 회원에게 발송됩니다. "
				+ "받고 싶지 않다면 설정 화면에서 언제든 끌 수 있어요.");
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 새 게시글 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	public void sendNewNoticeEmail(User fan, User artist, PortalNotice notice) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject("[WePlaNet] " + artist.getNickname() + "님의 새 공지");
		message.setText(fan.getNickname() + "님, 안녕하세요.\n\n"
				+ "가입 중인 " + artist.getNickname() + "님이 새 공지를 올렸어요.\n\n"
				+ "제목: " + notice.getTitle() + "\n\n"
				+ "WePlaNet 사이트에서 바로 확인해보세요.\n\n"
				+ "이 메일은 설정 > 이벤트·혜택 알림 설정에서 '이메일로 알림 받기'를 켠 회원에게 발송됩니다. "
				+ "받고 싶지 않다면 설정 화면에서 언제든 끌 수 있어요.");
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 새 공지 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}

	public void sendLiveStartEmail(User fan, User artist, LiveSession session) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(fan.getEmail());
		message.setSubject("[WePlaNet] " + artist.getNickname() + "님이 라이브 방송 중이에요");
		message.setText(fan.getNickname() + "님, 안녕하세요.\n\n"
				+ "가입 중인 " + artist.getNickname() + "님의 라이브 방송이 시작되었어요.\n\n"
				+ "WePlaNet 사이트에서 바로 접속해보세요.\n\n"
				+ "이 메일은 설정 > 이벤트·혜택 알림 설정에서 '이메일로 알림 받기'를 켠 회원에게 발송됩니다. "
				+ "받고 싶지 않다면 설정 화면에서 언제든 끌 수 있어요.");
		mailSender.send(message);
		log.info("[이벤트·혜택 알림] 라이브 시작 이메일 발송: artist={}, fan={}", artist.getId(), fan.getId());
	}
}
