package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.GroupMemberRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityArtistResolver {
	
	private final GroupMemberRepository gmr;
	
	public Long ownCommunityId(User user) {
		if (user == null || user.getRole() == null) {
			return null;
		}
		
		if (user.getRole() == Role.ARTIST) {
			return user.getId();
		}
		
		if (user.getRole() == Role.ARTIST_MEMBER) {
			return gmr.findByMember_IdAndLeftAtIsNull(user.getId())
					.map(GroupMember::getGroupId)
					.orElse(null);
		}
		
		return null;
	}
	
	public boolean isArtistOf(User user, Long communityId) {
		return communityId != null && communityId.equals(ownCommunityId(user));
	}
}
