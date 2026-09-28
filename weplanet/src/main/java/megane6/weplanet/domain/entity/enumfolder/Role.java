package megane6.weplanet.domain.entity.enumfolder;

import java.util.Set;

public enum Role {
	/*
	소속사가 등록한 그룹의 멤버 계정 (ARTIST_MEMBER)
	커뮤니티 자체는 그룹 계정(ARTIST)이고, 멤버는 그 커뮤니티 안에서 "아티스트로 활동하는 사람"
	ARTIST와 역할을 나눠 findByRole(ARTIST)로 만드는 커뮤니티 목록에 멤버가 섞이지 않게 함
	 */
	FAN, ARTIST, AGENCY, ADMIN, ARTIST_MEMBER;

	// 아티스트 쪽 계정(그룹/솔로 + 그룹 멤버).
	// 닉네임은 팬 쪽과 아티스트 쪽끼리는 겹쳐도 된다 - 아티스트는 이름 옆 체크 표시로 구분된다.
	public static final Set<Role> ARTIST_SIDE = Set.of(ARTIST, ARTIST_MEMBER);
	
	public boolean isArtistSide() {
		return ARTIST_SIDE.contains(this);
	}
	
	public String authority() {
		return "ROLE_" + name();
	}
}
