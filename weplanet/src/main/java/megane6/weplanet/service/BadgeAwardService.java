package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.domain.entity.FanBadgeOwnership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.FanBadgeRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배지 지급 전담 서비스. 모든 배지 지급은 반드시 이 클래스의 award()를 거친다.
 * award()는 "멱등"하게 만든다 = 같은 요청을 몇 번 보내도 결과는 딱 한 번 지급한 것과 같다.
 * 그래서 호출하는 쪽은 "이미 받았나?"를 신경쓰지 않고, 조건만 맞으면 그냥 부르면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeAwardService {
	
	private final FanBadgeRepository fbr;
	private final FanBadgeOwnershipRepository fbrOwner;
	private final CommunityMemberRepository cmr;
	private final UserRepository ur;
	
	/**
	 * 배지 지급. 실제로 새로 지급했으면 true, 이미 있거나 대상이 아니면 false.
	 * REQUIRES_NEW : 호출한 쪽 트랜잭션과 상관없이 항상 "새 트랜잭션"에서 실행
	 * - 다음 단계에서 이 메더스는 원래 작업(글쓰기 등)이 커밋된 "후"에 불린다.
	 * 그 시점엔 원래 트랜잭션이 이미 끝나서 새 트랜잭션이 있어야 DB에 저장할 수 있다.
	 * - 배지 저장이 실패해도 이미 끝난 글쓰기까지 롤백되는 일 X
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean award(Long fanId, Long artistId, BadgeCode code) {
		if (fanId == null || artistId == null || code == null) {
			return false;
		}
		
		// 1) 이미 받은 기록이 있으면 끝 (한번 얻으면 유지 정책 + UNIQUE 제약 보호)
		// 가장 흔한 경우라 DB 조회가 제일 가벼운 이 검사를 맨 앞에 둔다.
		if (fbrOwner.existsByFan_IdAndArtist_IdAndBadgeCode(fanId, artistId, code.name())) {
			return false;
		}
		
		// 2) 가입한 커뮤니티에서만 배지를 모을 수 있다. (community_members 기준)
		if (!cmr.existsByFanIdAndArtistId(fanId, artistId)) {
			return false;
		}
		
		// 3) 패 계정만 배지를 받음 (에이전시 자동가입 계정 등은 제외)
		User fan = ur.findById(fanId).orElse(null);
		if (fan == null || fan.getRole() != Role.FAN) {
			return false;
		}
		
		User artist = ur.findById(artistId).orElse(null);
		if (artist == null || artist.getRole() != Role.ARTIST) {
			return false;
		}
		
		// 4) 카탈로그에서 배지 정보를 가져온다. 없으면 시드 SQL 누락이므로 로그만 남긴다.
		FanBadge badge = fbr.findByBadgeCode(code.name()).orElse(null);
		if (badge == null) {
			log.warn("배지 카탈로그에 없는 코드: {}", code);
			return false;
		}
		
		// 5) 지급. awardedBy = null은 "시스템 자동 지급"이라는 뜻
		fbrOwner.save(FanBadgeOwnership.award(
				fan,
				artist,
				badge.getBadgeCode(),
				badge.getBadgeName(),
				badge.getBadgeType(),
				null
		));
		log.info("배지 지급 : fanId={}, artistId={}, badge={}", fanId, artistId, code);
		return true;
	}
}
