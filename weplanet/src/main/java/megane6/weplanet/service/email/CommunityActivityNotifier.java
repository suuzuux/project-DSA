package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

// [설정 - 이벤트·혜택 알림] 게시글/공지/라이브 시작을 "누구에게" 보낼지 골라서 CommunityActivityEmailService로
// 한 명씩 보내는 역할. @Async라서 호출한 쪽(PostService 등)의 요청/응답 흐름을 막지 않는다.
// 대상 선별 기준: 이 아티스트 커뮤니티에 가입(CommunityMember) + communityActivityEmailEnabled(이메일 알림) 켜짐 +
// 지금이 야간(21:00~08:00 KST)이면 nightNotificationAllowed까지 켜져 있어야 함.
// (참고: "팔로우"(GroupFollow)는 About 위젯의 팔로우 버튼 전용 기능이라 별개. 게시판 접근 권한과 기존 벨 알림도
// 모두 "가입"(CommunityMember) 기준이라 이 기능도 동일하게 맞춤)
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityActivityNotifier {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalTime NIGHT_START = LocalTime.of(21, 0);
	private static final LocalTime NIGHT_END = LocalTime.of(8, 0);

	private final CommunityMemberRepository communityMemberRepository;
	private final UserRepository userRepository;
	private final CommunityActivityEmailService emailService;

	@Async("communityNotifyExecutor")
	public void notifyNewPost(User artist, Post post) {
		for (User fan : resolveAudience(artist)) {
			try {
				emailService.sendNewPostEmail(fan, artist, post);
			} catch (Exception e) {
				log.error("[이벤트·혜택 알림] 새 게시글 이메일 발송 실패: fan={}", fan.getId(), e);
			}
		}
	}

	@Async("communityNotifyExecutor")
	public void notifyNewNotice(User artist, PortalNotice notice) {
		for (User fan : resolveAudience(artist)) {
			try {
				emailService.sendNewNoticeEmail(fan, artist, notice);
			} catch (Exception e) {
				log.error("[이벤트·혜택 알림] 새 공지 이메일 발송 실패: fan={}", fan.getId(), e);
			}
		}
	}

	@Async("communityNotifyExecutor")
	public void notifyLiveStart(User artist, LiveSession session) {
		for (User fan : resolveAudience(artist)) {
			try {
				emailService.sendLiveStartEmail(fan, artist, session);
			} catch (Exception e) {
				log.error("[이벤트·혜택 알림] 라이브 시작 이메일 발송 실패: fan={}", fan.getId(), e);
			}
		}
	}

	private List<User> resolveAudience(User artist) {
		List<Long> fanIds = communityMemberRepository.findByArtistId(artist.getId()).stream()
				.map(CommunityMember::getFanId)
				.toList();
		if (fanIds.isEmpty()) {
			return List.of();
		}
		boolean night = isNightNow();
		return userRepository.findAllById(fanIds).stream()
				.filter(User::isCommunityActivityEmailEnabled)
				.filter(fan -> !night || fan.isNightNotificationAllowed())
				.collect(Collectors.toList());
	}

	// 21:00~08:00(KST) 사이인지 - 자정을 걸치는 구간이라 "21시 이후이거나 8시 이전" 으로 판단.
	private boolean isNightNow() {
		LocalTime now = LocalTime.now(KST);
		return !now.isBefore(NIGHT_START) || now.isBefore(NIGHT_END);
	}
}
