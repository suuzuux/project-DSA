package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.portal.PortalNotice;
import megane6.weplanet.domain.entity.User;
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

// [설정 - 이벤트·혜택 알림] 게시글/공지/라이브 시작을 "누구에게" 보낼지 골라서 CommunityActivityEmailService로
// 한 명씩 보내는 역할. @Async라서 호출한 쪽(PostService 등)의 요청/응답 흐름을 막지 않는다.
// 대상 선별 기준(GroupFollow/UserFollow 통합 이후 변경): 이 아티스트 커뮤니티에 가입(CommunityMember) +
// 이 아티스트를 팔로우(UserFollow) + communityActivityEmailEnabled(이메일 알림) 켜짐 + 지금이
// 야간(21:00~08:00 KST)이면 nightNotificationAllowed까지 켜져 있어야 함.
// 원래는 "가입"(CommunityMember) 기준만 봤는데, "가입=열람 권한 / 팔로우=업데이트 받고 싶다는 의사표시"로
// 역할을 나누기로 하면서 팔로우 여부도 함께 보게 됐다. 백필 없이 바로 적용했으므로, 가입만 하고 아직
// 팔로우는 안 한 기존 회원은 이 시점부터 알림을 받지 못한다(사용자 확인된 결정).
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
		// GroupFollow/UserFollow 통합: 가입자 중에서도 이 아티스트를 팔로우(following_id == community_id
		// == artist.getId())하는 사람만 후보로 남긴다.
		java.util.Set<Long> followerIds = memberIds.stream()
				.filter(fanId -> userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(
						fanId, artist.getId(), artist.getId()))
				.collect(Collectors.toSet());
		if (followerIds.isEmpty()) {
			return List.of();
		}
		boolean night = isNightNow();
		return userRepository.findAllById(followerIds).stream()
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
