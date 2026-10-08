package megane6.weplanet.service.fan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.domain.entity.FanBadgeOwnership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.fan.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.fan.FanBadgeRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 배지 지급 전담 서비스 - award() 는 멱등이라 조건만 맞으면 호출하면 된다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeAwardService {
	
	private final FanBadgeRepository fbr;
	private final FanBadgeOwnershipRepository fbrOwner;
	private final CommunityMemberRepository cmr;
	private final UserRepository ur;
	
	/** 배지 지급 (새로 지급하면 true, 커밋 후 호출되므로 REQUIRES_NEW 로 별도 트랜잭션). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean award(Long fanId, Long artistId, BadgeCode code) {
		if (fanId == null || artistId == null || code == null) {
			return false;
		}
		
		// 1) 이미 받았으면 끝 (가장 흔하고 가벼운 검사라 맨 앞).
		if (fbrOwner.existsByFan_IdAndArtist_IdAndBadgeCode(fanId, artistId, code.name())) {
			return false;
		}
		
		// 2) 가입한 커뮤니티에서만 받을 수 있다.
		if (!cmr.existsByFanIdAndArtistId(fanId, artistId)) {
			return false;
		}
		
		// 3) 팬 계정만 받는다.
		User fan = ur.findById(fanId).orElse(null);
		if (fan == null || fan.getRole() != Role.FAN) {
			return false;
		}
		
		User artist = ur.findById(artistId).orElse(null);
		if (artist == null || artist.getRole() != Role.ARTIST) {
			return false;
		}
		
		// 4) 카탈로그에 없으면 시드 누락이라 로그만 남긴다.
		FanBadge badge = fbr.findByBadgeCode(code.name()).orElse(null);
		if (badge == null) {
			log.warn("배지 카탈로그에 없는 코드: {}", code);
			return false;
		}
		
		// 5) 지급 (awardedBy = null 은 시스템 자동 지급)
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
