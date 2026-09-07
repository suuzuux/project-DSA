package megane6.weplanet.service.community;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 햄버거(드로어) 커뮤니티 목록.
 * - 가입 커뮤니티: community_members 기준
 * - 다른 커뮤니티(아티스트): 본인 제외 전체 (가입과 중복 가능)
 */
@Component
public class CommunityDrawerHelper {

	/** 실제로 가입한 커뮤니티 */
	public List<ArtistCardView> joined(User viewer,
									   List<ArtistCardView> allArtists,
									   Set<Long> joinedArtistIds) {
		if (viewer == null || joinedArtistIds == null || joinedArtistIds.isEmpty()) {
			return List.of();
		}
		return allArtists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
	}

	/** 아티스트용 타 커뮤니티(본인 제외). 팬/기타는 빈 목록 */
	public List<ArtistCardView> otherCommunities(User viewer, List<ArtistCardView> allArtists) {
		if (viewer == null || viewer.getRole() != Role.ARTIST) {
			return List.of();
		}
		return allArtists.stream()
				.filter(a -> !a.id().equals(viewer.getId()))
				.toList();
	}

	/** 하위 호환: 아티스트는 타 커뮤니티, 팬은 가입 목록 */
	@Deprecated
	public List<ArtistCardView> forViewer(User viewer,
										  List<ArtistCardView> allArtists,
										  Set<Long> joinedArtistIds) {
		if (viewer == null) {
			return List.of();
		}
		if (viewer.getRole() == Role.ARTIST) {
			return otherCommunities(viewer, allArtists);
		}
		return joined(viewer, allArtists, joinedArtistIds);
	}
}
