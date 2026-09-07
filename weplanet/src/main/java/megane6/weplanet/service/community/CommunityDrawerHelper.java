package megane6.weplanet.service.community;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 햄버거(드로어) 커뮤니티 목록.
 * 팬 = 가입한 커뮤니티, 아티스트 = 본인을 제외한 타 커뮤니티.
 */
@Component
public class CommunityDrawerHelper {

	public List<ArtistCardView> forViewer(User viewer,
										  List<ArtistCardView> allArtists,
										  Set<Long> joinedArtistIds) {
		if (viewer == null) {
			return List.of();
		}
		if (viewer.getRole() == Role.ARTIST) {
			return allArtists.stream()
					.filter(a -> !a.id().equals(viewer.getId()))
					.toList();
		}
		return allArtists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
	}
}
