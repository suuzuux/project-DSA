package megane6.weplanet.domain.entity.enumfolder;

public enum UserStatus {
	ACTIVE, DORMANT, SUSPENDED, WITHDRAWN,
	
	// 승인으로 계정만 만들어지고 아직 비밀번호를 설정하지 않은 상태 (로그인 불가).
	PENDING_ACTIVATION
}
