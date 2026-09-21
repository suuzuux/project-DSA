package megane6.weplanet.domain.dto;

/**
 * 프로젝트 등록 자격 확인 결과. "등록하기" 버튼 눌렀을 때 화명니 받아감
 * @param eligible
 * @param basicCount
 * @param specialCount
 * @param basicRequired
 * @param specialRequired
 * @param message
 */
public record ProjectEligibilityView(
		boolean eligible,
		long basicCount,
		long specialCount,
		long basicRequired,
		long specialRequired,
		String message
) {
}
