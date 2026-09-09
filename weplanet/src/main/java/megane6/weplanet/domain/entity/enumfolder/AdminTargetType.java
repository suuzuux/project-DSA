package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminTargetType {
	USER("회원"),
	ARTIST("아티스트"),
	AGENCY_PERMISSION("소속사 권한"),
	PROJECT("프로젝트"),
	REPORT("신고"),
	SETTLEMENT("정산"),
	NOTICE("공지");
	
	private final String label;
}
