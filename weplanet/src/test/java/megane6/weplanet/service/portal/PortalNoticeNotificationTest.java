package megane6.weplanet.service.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.MembershipRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.calendar.ArtistScheduleRepository;
import megane6.weplanet.repository.live.LiveCommentReportRepository;
import megane6.weplanet.repository.media.BoardMediaRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.repository.portal.PortalNoticeRepository;
import megane6.weplanet.service.FileStorageService;
import megane6.weplanet.service.community.CommunityUrls;
import megane6.weplanet.service.email.CommunityActivityNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 공지 팔로워 알림 메일은 "비공개 → 공개"로 바뀌는 순간에만 보낸다
class PortalNoticeNotificationTest {

	private final PortalNoticeRepository noticeRepository = mock(PortalNoticeRepository.class);
	private final CommunityActivityNotifier notifier = mock(CommunityActivityNotifier.class);
	private final PortalManagementService service = new PortalManagementService(noticeRepository,
			mock(ArtistScheduleRepository.class), mock(ArtistProfileRepository.class), mock(BoardMediaRepository.class),
			mock(MembershipRepository.class), mock(ReportRepository.class), mock(CommentReportRepository.class),
			mock(LiveCommentReportRepository.class), mock(FileStorageService.class), notifier,
			mock(MessageSource.class), mock(CommunityUrls.class));

	private final User artist = User.createArtist("artist01", "encoded", "그룹", "그룹", "artist01@test.com");

	@BeforeEach
	void setUp() {
		when(noticeRepository.save(any(PortalNotice.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	// 임시저장(비공개)해 둔 공지를 나중에 공개하면 알림이 간다 (예전에는 가지 않았다)
	@Test
	void publishingADraftNotifiesFollowers() {
		PortalNotice draft = PortalNotice.create(artist, "제목", "내용", false);
		when(noticeRepository.findByIdAndArtist(5L, artist)).thenReturn(Optional.of(draft));

		PortalNotice saved = service.saveNotice(artist, 5L, "제목", "내용", true, false);

		verify(notifier).notifyNewNotice(artist, saved);
	}

	// 새로 쓰면서 바로 공개하면 알림이 간다 (기존 동작 그대로)
	@Test
	void newPublishedNoticeNotifiesFollowers() {
		PortalNotice saved = service.saveNotice(artist, null, "제목", "내용", true, false);

		verify(notifier).notifyNewNotice(artist, saved);
	}

	// 이미 공개된 공지의 내용 수정, 비공개 저장은 알림을 보내지 않는다
	@Test
	void editingPublishedOrSavingDraftDoesNotNotify() {
		PortalNotice published = PortalNotice.create(artist, "제목", "내용", true);
		when(noticeRepository.findByIdAndArtist(6L, artist)).thenReturn(Optional.of(published));

		service.saveNotice(artist, 6L, "고친 제목", "고친 내용", true, false);
		service.saveNotice(artist, null, "임시저장", "내용", false, false);

		verify(notifier, never()).notifyNewNotice(any(), any());
	}
}
