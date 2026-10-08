package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.UserFollow;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

// 알림 메일 대상 선별 후 비동기 발송 (가입 + 팔로우 + 수신 동의, 야간엔 야간 허용까지).
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityActivityNotifier {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalTime NIGHT_START = LocalTime.of(21, 0);
	private static final LocalTime NIGHT_END = LocalTime.of(8, 0);

	private final CommunityMemberRepository communityMemberRepository;
	private final UserFollowRepository userFollowRepository;
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
		List<Long> memberIds = communityMemberRepository.findByArtistId(artist.getId()).stream()
				.map(CommunityMember::getFanId)
				.toList();
		if (memberIds.isEmpty()) {
			return List.of();
		}
		// 가입자 중 아티스트 팔로워만 남긴다 (팔로워 목록을 한 번에 조회).
		java.util.Set<Long> artistFollowerIds = userFollowRepository
				.findByFollowingIdAndCommunityIdOrderByCreatedAtAsc(artist.getId(), artist.getId()).stream()
				.map(UserFollow::getFollowerId)
				.collect(Collectors.toSet());
		java.util.Set<Long> followerIds = memberIds.stream()
				.filter(artistFollowerIds::contains)
				.collect(Collectors.toSet());
		if (followerIds.isEmpty()) {
			return List.of();
		}
		boolean night = isNightNow();
		return userRepository.findAllById(followerIds).stream()
				// 휴면·정지·탈퇴 회원과 수신 불가 주소 제외
				.filter(User::isLoginable)
				.filter(fan -> !fan.hasPlaceholderEmail())
				.filter(User::isCommunityActivityEmailEnabled)
				.filter(fan -> !night || fan.isNightNotificationAllowed())
				.collect(Collectors.toList());
	}

	// 21:00~08:00(KST) 인지
	private boolean isNightNow() {
		LocalTime now = LocalTime.now(KST);
		return !now.isBefore(NIGHT_START) || now.isBefore(NIGHT_END);
	}
}
