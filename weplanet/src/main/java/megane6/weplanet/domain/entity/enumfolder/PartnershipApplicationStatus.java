package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PartnershipApplicationStatus {
	PENDING_APPROVAL("검토 대기"),
	APPROVED("승인"),
	REJECTED("반려");
	
	private final String displayName;
}
