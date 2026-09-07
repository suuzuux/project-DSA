package megane6.weplanet.service.community;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 햄버거(드로어) 커뮤니티 목록.
 * <ul>
 *   <li>비로그인: 모든 커뮤니티</li>
 *   <li>로그인(팬 등): 가입한 커뮤니티(상단) + 모든 커뮤니티(하단, 중복 허용)</li>
 *   <li>아티스트: 본인 커뮤니티(상단) + 모든 커뮤니티(하단, 중복 허용)</li>
 * </ul>
 */
@Component
public class CommunityDrawerHelper {

	/**
	 * 드로어 상단.
	 * 아티스트 = 본인 커뮤니티, 그 외 로그인 = community_members 가입 목록.
	 */
	public List<ArtistCardView> joined(User viewer,
									   List<ArtistCardView> allArtists,
									   Set<Long> joinedArtistIds) {
		if (viewer == null || allArtists == null || allArtists.isEmpty()) {
			return List.of();
		}
		if (viewer.getRole() == Role.ARTIST) {
			return allArtists.stream()
					.filter(a -> a.id().equals(viewer.getId()))
					.toList();
		}
		if (joinedArtistIds == null || joinedArtistIds.isEmpty()) {
			return List.of();
		}
		return allArtists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
	}

	/** 드로어 하단(및 비로그인 전체): 모든 커뮤니티 */
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
