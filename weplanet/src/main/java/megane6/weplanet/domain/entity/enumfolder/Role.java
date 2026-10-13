package megane6.weplanet.domain.entity.enumfolder;

import java.util.Set;

public enum Role {
	// 그룹 멤버 계정 (커뮤니티는 ARTIST 그룹 계정, 멤버가 커뮤니티 목록에 섞이지 않게 역할을 나눔).
	FAN, ARTIST, AGENCY, ADMIN, ARTIST_MEMBER;

	// 아티스트 쪽 계정 (그룹·솔로 + 그룹 멤버).
	public static final Set<Role> ARTIST_SIDE = Set.of(ARTIST, ARTIST_MEMBER);
	
	public boolean isArtistSide() {
		return ARTIST_SIDE.contains(this);
	}
	
	public String authority() {
		return "ROLE_" + name();
	}
}
