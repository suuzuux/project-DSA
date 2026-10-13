package megane6.weplanet.service.fan;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.BadgeCollectionView;
import megane6.weplanet.domain.dto.BadgeView;
import megane6.weplanet.domain.dto.CollectionCardView;
import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.fan.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.fan.FanBadgeRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 나의 컬렉션 조회 전용 (지급은 BadgeAwardService). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {
	
	private final FanBadgeRepository fanBadgeRepository;
	private final FanBadgeOwnershipRepository ownershipRepository;
	private final UserRepository userRepository;
	private final CommunityMemberRepository communityMemberRepository;
	private final Messages messages; // 배지 이름·설명 번역
	
	// 카드 미리보기 배지 수
	private static final int PREVIEW_SIZE = 3;
	
	/** 가입한 커뮤니티별 배지 요약 카드 (community_members 기준). */
	public List<CollectionCardView> getMyCollection(Long fanId) {
		List<Long> artistIds = communityMemberRepository.findByFanId(fanId).stream()
				.map(CommunityMember::getArtistId)
				.toList();
		
		if (artistIds.isEmpty()) {
			return List.of();
		}
		
		// 카탈로그는 한 번만 읽는다 (N+1 방지).
		List<FanBadge> catalog = fanBadgeRepository.findAllByOrderByBadgeTypeAscSortOrderAsc();
		
		return userRepository.findAllById(artistIds).stream()
				.filter(user -> user.getRole() == Role.ARTIST)
				.map(artist -> toCard(fanId, artist, catalog))
				.toList();
	}
	
	/** 전체보기 모달의 아티스트별 배지 현황 */
	public BadgeCollectionView getBadgeCollection(Long fanId, Long artistId) {
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("shop.error.artistNotFound"));
		
		List<FanBadge> catalog = fanBadgeRepository.findAllByOrderByBadgeTypeAscSortOrderAsc();
		Set<String> earnedCodes = findEarnedCodes(fanId, artistId);
		
		return new BadgeCollectionView(
				artist.getId(),
				artist.getNickname(),
				toBadgeViews(catalog, earnedCodes, FanBadgeType.BASIC),
				toBadgeViews(catalog, earnedCodes, FanBadgeType.SPECIAL)
		);
	}
	
	private CollectionCardView toCard(Long fanId, User artist, List<FanBadge> catalog) {
		Set<String> earnedCodes = findEarnedCodes(fanId, artist.getId());
		
		List<BadgeView> earnedBadges = catalog.stream()
				.filter(badge -> earnedCodes.contains(badge.getBadgeCode()))
				.map(badge -> BadgeView.of(badge, true, messages))
				.toList();
		
		long basicCount = countByType(earnedBadges, catalog, FanBadgeType.BASIC);
		long specialCount = countByType(earnedBadges, catalog, FanBadgeType.SPECIAL);
		
		int rate = catalog.isEmpty()
				? 0
				: (int) (earnedBadges.size() * 100L / catalog.size());
		
		return new CollectionCardView(
				artist.getId(),
				artist.getNickname(),
				basicCount,
				specialCount,
				rate,
				earnedBadges.stream().limit(PREVIEW_SIZE).toList()
		);
	}
	
	/** 획득한 배지 코드 (조회가 잦아 Set 사용). */
	private Set<String> findEarnedCodes(Long fanId, Long artistId) {
		return ownershipRepository.findByFan_IdAndArtist_IdAndRevokedAtIsNull(fanId, artistId)
				.stream()
				.map(ownership -> ownership.getBadgeCode())
				.collect(Collectors.toSet());
	}
	
	// 카탈로그를 유형별 BadgeView 로 변환
	private List<BadgeView> toBadgeViews(List<FanBadge> catalog, Set<String> earnedCodes, FanBadgeType type) {
		return catalog.stream()
				.filter(badge -> badge.getBadgeType() == type)
				.map(badge -> BadgeView.of(badge, earnedCodes.contains(badge.getBadgeCode()), messages))
				.toList();
	}
	
	// 획득 배지 중 특정 유형 수
	private long countByType(List<BadgeView> earnedBadges, List<FanBadge> catalog, FanBadgeType type) {
		Set<String> codesOfType = catalog.stream()
				.filter(badge -> badge.getBadgeType() == type)
				.map(FanBadge::getBadgeCode)
				.collect(Collectors.toSet());
		
		return earnedBadges.stream()
				.filter(badge -> codesOfType.contains(badge.badgeCode()))
				.count();
	}
}