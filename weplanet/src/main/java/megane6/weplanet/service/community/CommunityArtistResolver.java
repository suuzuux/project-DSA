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

	// 멤버별 DM: 팬이 1:1 로 대화하는 상대(DM 방 주인)가 될 수 있는 계정인지.
	// 솔로 아티스트는 본인, 그룹은 활동 중인 멤버 한 명 한 명이 방 주인이다.
	// 그룹 계정 자체는 방 주인이 아니다 (멤버가 아직 없는 그룹은 솔로처럼 그룹 계정이 방 주인).
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
