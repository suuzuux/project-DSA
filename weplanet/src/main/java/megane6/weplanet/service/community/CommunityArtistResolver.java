package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.artist.GroupMemberRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

	// 커뮤니티의 아티스트 쪽 계정 id (그룹·솔로 + 활동 멤버)
	public List<Long> artistSideUserIds(Long communityId) {
		List<Long> ids = new ArrayList<>();
		ids.add(communityId);
		gmr.findByGroupIdAndLeftAtIsNullOrderByIdAsc(communityId)
				.forEach(groupMember -> ids.add(groupMember.getMember().getId()));
		return ids;
	}

	// DM 방 주인이 될 수 있는 계정인지 (솔로 본인 또는 활동 멤버, 멤버 없는 그룹은 그룹 계정).
	public boolean isDmRoomOwner(User user) {
		if (user == null || user.getRole() == null) {
			return false;
		}

		if (user.getRole() == Role.ARTIST) {
			return gmr.countByGroupIdAndLeftAtIsNull(user.getId()) == 0;
		}

		if (user.getRole() == Role.ARTIST_MEMBER) {
			return gmr.findByMember_IdAndLeftAtIsNull(user.getId()).isPresent();
		}

		return false;
	}
}
