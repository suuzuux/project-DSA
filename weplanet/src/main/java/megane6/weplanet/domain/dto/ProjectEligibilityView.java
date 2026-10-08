package megane6.weplanet.domain.dto;

/** 프로젝트 등록 자격 확인 결과 (등록하기 버튼 클릭 시 응답). */
public record ProjectEligibilityView(
		boolean eligible,
		long basicCount,
		long specialCount,
		long basicRequired,
		long specialRequired,
		String message
) {
}
