package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 햄버거 커뮤니티 목록 (비로그인: 전체 / 로그인: 가입 또는 본인 커뮤니티 + 전체). */
@Component
@RequiredArgsConstructor
public class CommunityDrawerHelper {
	
	private final CommunityArtistResolver communityArtistResolver;

	/** 드로어 상단 (아티스트는 본인 커뮤니티, 그 외는 가입 목록). */
	public List<ArtistCardView> joined(User viewer,
									   List<ArtistCardView> allArtists,
									   Set<Long> joinedArtistIds) {
		if (viewer == null || allArtists == null || allArtists.isEmpty()) {
			return List.of();
		}
		// 아티스트 쪽 계정은 본인 커뮤니티 하나만
		if (viewer.isArtistSide()) {
			Long ownCommunityId = communityArtistResolver.ownCommunityId(viewer);
			return allArtists.stream()
					.filter(a -> a.id().equals(ownCommunityId))
					.toList();
		}
		if (joinedArtistIds == null || joinedArtistIds.isEmpty()) {
			return List.of();
		}
		return allArtists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
	}

	/** 드로어 하단: 모든 커뮤니티 */
	public List<ArtistCardView> otherCommunities(User viewer, List<ArtistCardView> allArtists) {
		if (allArtists == null || allArtists.isEmpty()) {
			return List.of();
		}
		return List.copyOf(allArtists);
	}

	/** @deprecated {@link #joined} / {@link #otherCommunities} 조합을 사용 */
	@Deprecated
	public List<ArtistCardView> forViewer(User viewer,
										  List<ArtistCardView> allArtists,
										  Set<Long> joinedArtistIds) {
		return joined(viewer, allArtists, joinedArtistIds);
	}
}
