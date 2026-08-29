package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.BadgeCollectionView;
import megane6.weplanet.domain.dto.BadgeView;
import megane6.weplanet.domain.dto.CollectionCardView;
import megane6.weplanet.domain.entity.FanBadge;
import megane6.weplanet.domain.entity.GroupFollow;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.FanBadgeRepository;
import megane6.weplanet.repository.GroupFollowRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "나의 컬렉션" 조회 전용 서비스.
 * <p>
 * 배지 지급은 여기서 하지 않는다. 지금은 시드로만 넣고, 조회만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {
	
	private final GroupFollowRepository groupFollowRepository;
	private final FanBadgeRepository fanBadgeRepository;
	private final FanBadgeOwnershipRepository ownershipRepository;
	private final UserRepository userRepository;
	
	// 카드에 미리보기로 띄울 배지 개수
	private static final int PREVIEW_SIZE = 3;
	
	/**
	 * 내가 가입한 커뮤니티별 배지 요약 카드 목록.
	 * <p>
	 * group_follow.group_id 는 그 아티스트의 users.id 와 같은 값으로 시딩돼 있어서
	 * (MediaGroupDataInitializer 참고) 별도 변환 없이 아티스트 id 로 쓴다.
	 */
	public List<CollectionCardView> getMyCollection(Long fanId) {
		List<Long> artistIds = groupFollowRepository.findByFanId(fanId).stream()
				.map(GroupFollow::getGroupId)
				.toList();
		
		if (artistIds.isEmpty()) {
			return List.of();
		}
		
		// 카탈로그는 커뮤니티 수와 무관하게 딱 한 번만 읽는다.
		// 반복문 안에서 읽으면 커뮤니티 6개일 때 6번 조회하게 된다(N+1).
		List<FanBadge> catalog = fanBadgeRepository.findAllByOrderByBadgeTypeAscSortOrderAsc();
		
		return userRepository.findAllById(artistIds).stream()
				.filter(user -> user.getRole() == Role.ARTIST)
				.map(artist -> toCard(fanId, artist, catalog))
				.toList();
	}
	
	/**
	 * 전체보기 모달에 띄울 한 아티스트의 배지 현황.
	 */
	public BadgeCollectionView getBadgeCollection(Long fanId, Long artistId) {
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		
		List<FanBadge> catalog = fanBadgeRepository.findAllByOrderByBadgeTypeAscSortOrderAsc();
		Set<String> earnedCodes = findEarnedCodes(fanId, artistId);
		
		return new BadgeCollectionView(
				artist.getId(),
				artist.getNickname(),
				toBadgeViews(catalog, earnedCodes, FanBadgeType.BASIC),
				toBadgeViews(catalog, earnedCodes, FanBadgeType.SPECIAL)
		);
	}
	
	// ---------- 아래는 내부 helper ----------
	
	private CollectionCardView toCard(Long fanId, User artist, List<FanBadge> catalog) {
		Set<String> earnedCodes = findEarnedCodes(fanId, artist.getId());
		
		List<BadgeView> earnedBadges = catalog.stream()
				.filter(badge -> earnedCodes.contains(badge.getBadgeCode()))
				.map(badge -> BadgeView.of(badge, true))
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
	
	/**
	 * 이 팬이 이 아티스트에게서 획득한 배지 코드들.
	 * <p>
	 * List 가 아니라 Set 으로 만드는 이유 : 카탈로그 25개를 돌면서 매번
	 * "이 코드가 획득 목록에 있나"를 확인하는데, List.contains 는 매번 처음부터
	 * 훑기 때문에 느리다. Set.contains 는 한 번에 찾는다.
	 */
	private Set<String> findEarnedCodes(Long fanId, Long artistId) {
		return ownershipRepository.findByFan_IdAndArtist_IdAndRevokedAtIsNull(fanId, artistId)
				.stream()
				.map(ownership -> ownership.getBadgeCode())
				.collect(Collectors.toSet());
	}
	
	// 카탈로그를 유형별로 걸러 BadgeView 로 바꾼다. 획득 여부는 earnedCodes 로 판단.
	private List<BadgeView> toBadgeViews(List<FanBadge> catalog, Set<String> earnedCodes, FanBadgeType type) {
		return catalog.stream()
				.filter(badge -> badge.getBadgeType() == type)
				.map(badge -> BadgeView.of(badge, earnedCodes.contains(badge.getBadgeCode())))
				.toList();
	}
	
	// 획득한 배지 중 특정 유형의 개수
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