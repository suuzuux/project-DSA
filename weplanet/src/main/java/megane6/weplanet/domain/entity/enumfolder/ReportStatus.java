package megane6.weplanet.domain.entity.enumfolder;

public enum ReportStatus {
	PENDING,		// 처리 대기
	DISMISSED,		// 기각 (신고는 근거 없음, 대상은 그대로 둠)
	RESOLVED		// 대상 삭제/작성자 제재 등으로 실제 처리됨
}
