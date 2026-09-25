package megane6.weplanet.domain.entity.enumfolder;

public enum UserStatus {
	ACTIVE, DORMANT, SUSPENDED, WITHDRAWN,
	
	// 관리자가 입점 신청을 승인해서 계정은 만들어졌지만,
	// 본인이 아직 초대 메일로 비밀번호를 설정하지 않은 상태.
	// isLoginable()이 ACTIVE만 허용하므로 이 상태에서는 로그인이 막힌다.
	PENDING_ACTIVATION
}
