package megane6.weplanet.service.email;

import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.domain.entity.live.LiveSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 그룹 멤버가 한 활동은 메일에 "그룹의 멤버"로, 아티스트 본인(솔로·그룹 계정)이 한 활동은 원래 문구로 나오는지 검증한다.
class CommunityActivityEmailServiceTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final CommunityActivityEmailService service = new CommunityActivityEmailService(mailSender, messageSource());

	private final User group = user(1101L, "NOVA");
	private final User member = artistSide(user(2001L, "시우"));

	@Test
	void groupMemberLiveNamesTheMember() {
		LiveSession session = mock(LiveSession.class);
		when(session.getHost()).thenReturn(member);

		service.sendLiveStartEmail(fan(Language.KO), group, session);

		SimpleMailMessage mail = sentMail();
		assertEquals("[WePlaNet] NOVA 시우님이 라이브 방송 중이에요", mail.getSubject());
		assertTrue(mail.getText().contains("가입 중인 NOVA의 시우님이 라이브 방송을 시작했어요."));
	}

	@Test
	void groupMemberPostNamesTheMemberInFanLanguage() {
		Post post = mock(Post.class);
		when(post.getAuthor()).thenReturn(member);
		when(post.getTitle()).thenReturn("컴백 D-7");

		service.sendNewPostEmail(fan(Language.JA), group, post);

		SimpleMailMessage mail = sentMail();
		assertEquals("[WePlaNet] NOVAの시우さんの新しい投稿", mail.getSubject());
		assertTrue(mail.getText().contains("参加中のNOVAの시우さんが新しい投稿をしました。"));
	}

	// 솔로 아티스트처럼 커뮤니티 주인 본인이 방송하면 멤버 이름 없이 원래 문구
	@Test
	void artistOwnLiveKeepsOriginalText() {
		User solo = user(1105L, "한유리");
		LiveSession session = mock(LiveSession.class);
		when(session.getHost()).thenReturn(solo);

		service.sendLiveStartEmail(fan(Language.KO), solo, session);

		assertEquals("[WePlaNet] 한유리님이 라이브 방송 중이에요", sentMail().getSubject());
	}

	// 소속사 계정이 그룹 방송을 대신 켜도 멤버가 아니므로 그룹 이름만
	@Test
	void agencyHostedLiveKeepsOriginalText() {
		User agency = user(3001L, "스타라이트");   // 아티스트 쪽 계정이 아님 (isArtistSide = false)
		LiveSession session = mock(LiveSession.class);
		when(session.getHost()).thenReturn(agency);

		service.sendLiveStartEmail(fan(Language.KO), group, session);

		assertEquals("[WePlaNet] NOVA님이 라이브 방송 중이에요", sentMail().getSubject());
	}

	private SimpleMailMessage sentMail() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		return captor.getValue();
	}

	private static User user(Long id, String nickname) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getNickname()).thenReturn(nickname);
		return user;
	}

	private static User artistSide(User user) {
		when(user.isArtistSide()).thenReturn(true);
		return user;
	}

	private static User fan(Language language) {
		User fan = user(301L, "팬");
		when(fan.getEmail()).thenReturn("fan@weplanet.test");
		when(fan.getPreferredLanguage()).thenReturn(language);
		return fan;
	}

	// 실제 언어 파일(messages*.properties)로 문구를 만든다
	private static ResourceBundleMessageSource messageSource() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return source;
	}
}
